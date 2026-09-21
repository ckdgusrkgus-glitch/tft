package com.leechanghyun.autobattler.core.planning

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.economy.Economy
import com.leechanghyun.autobattler.core.economy.ShopOffer
import com.leechanghyun.autobattler.core.economy.ShopRoller
import com.leechanghyun.autobattler.core.economy.UnitPool
import com.leechanghyun.autobattler.core.masterdata.EconomyRules
import com.leechanghyun.autobattler.core.model.HexCoord
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.PlayerState

/** 준비 단계 조작이 실패한 이유. UI 가 사용자에게 보여줄 메시지를 고르는 데 쓴다. */
enum class PlanningError {
    /** 골드가 모자란다. */
    NOT_ENOUGH_GOLD,

    /** 벤치가 꽉 찼다. */
    BENCH_FULL,

    /** 빈 칸이거나 이미 구매한 칸이다. */
    SLOT_UNAVAILABLE,

    /** 해당 유닛을 가지고 있지 않다. */
    UNIT_NOT_FOUND,

    /** 이미 최대 레벨이다. */
    MAX_LEVEL,

    /** 보드 밖의 좌표다. */
    INVALID_COORD,

    /** 보드에 올릴 수 있는 수(= 레벨)를 이미 채웠다. */
    BOARD_FULL,
}

/** 준비 단계 조작 결과. 실패하면 상태가 전혀 바뀌지 않는다. */
sealed interface PlanningResult {
    data class Success(val unit: BoardUnit? = null, val goldSpent: Int = 0) : PlanningResult
    data class Failure(val error: PlanningError) : PlanningResult

    val isSuccess: Boolean get() = this is Success
}

/**
 * 한 플레이어의 준비 단계를 담당한다. 명세서 3장.
 *
 * 구매/판매/리롤/레벨업(4-1)과 헥스 보드 배치(4-6)가 모두 여기를 거친다.
 *
 * 공용 유닛 풀, 상점, 플레이어 상태를 한데 묶어 **항상 함께 갱신**한다.
 * 따로 두면 "상점에서는 샀는데 풀에서는 안 빠진" 같은 어긋남이 생기기 쉽다.
 *
 * 안드로이드 의존성이 없어 단위테스트로 전부 검증할 수 있다.
 * 스레드 안전하지 않으며, 한 플레이어의 조작은 단일 코루틴에서 순차 처리한다는 전제다.
 *
 * 구매한 유닛은 일단 벤치로 가고, [moveToBoard] 로 보드에 올린다.
 * 자동 합성은 로드맵 6단계에서 붙는다.
 */
class PlanningSession(
    private val pool: UnitPool,
    private val roller: ShopRoller,
    initialPlayer: PlayerState,
) {
    var player: PlayerState = initialPlayer
        private set

    var offer: ShopOffer = ShopOffer.EMPTY
        private set

    /** 진행한 라운드 수. [nextRound] 를 부를 때마다 올라간다. */
    var round: Int = 0
        private set

    private var instanceCounter = 0

    /**
     * 다음 라운드를 시작한다. 수입과 자동 경험치를 지급하고 상점을 새로 채운다.
     *
     * 리롤과 달리 골드를 쓰지 않는다.
     */
    fun nextRound(): PlayerState {
        round++
        player = Economy.startRound(player)
        offer = roller.roll(level = player.level, previous = offer)
        return player
    }

    /**
     * 상점을 새로고침한다. 명세서 4-1: 2골드.
     *
     * 이미 구매한 칸의 유닛은 벤치에 있으므로 풀로 돌아가지 않는다.
     */
    fun reroll(): PlanningResult {
        if (player.gold < EconomyRules.REROLL_COST) return PlanningResult.Failure(PlanningError.NOT_ENOUGH_GOLD)
        player = player.copy(gold = player.gold - EconomyRules.REROLL_COST)
        offer = roller.roll(level = player.level, previous = offer)
        return PlanningResult.Success(goldSpent = EconomyRules.REROLL_COST)
    }

    /**
     * 상점 [slotIndex] 칸의 유닛을 사서 벤치에 올린다.
     *
     * 로드맵 2단계 완료 기준인 "유닛 구매 → 벤치 배치"가 이 함수다.
     * 상점에 뜬 시점에 이미 풀에서 빠졌으므로 구매 자체는 풀을 건드리지 않는다.
     */
    fun buy(slotIndex: Int): PlanningResult {
        val slot = offer.slots.getOrNull(slotIndex) ?: return PlanningResult.Failure(PlanningError.SLOT_UNAVAILABLE)
        val unitDef = slot.unit
        if (unitDef == null || slot.purchased) return PlanningResult.Failure(PlanningError.SLOT_UNAVAILABLE)
        if (player.gold < unitDef.cost) return PlanningResult.Failure(PlanningError.NOT_ENOUGH_GOLD)
        if (player.bench.size >= PlayerState.BENCH_SIZE) return PlanningResult.Failure(PlanningError.BENCH_FULL)

        val bought = BoardUnit(instanceId = nextInstanceId(), unitDef = unitDef)
        player = player.copy(
            gold = player.gold - unitDef.cost,
            bench = player.bench + bought,
        )
        offer = offer.markPurchased(slotIndex)
        return PlanningResult.Success(unit = bought, goldSpent = unitDef.cost)
    }

    /**
     * 벤치나 보드의 유닛을 판다. 골드를 돌려받고 소모했던 카드가 공용 풀로 돌아간다.
     *
     * 2성/3성 유닛은 각각 3장/9장을 한 번에 되돌린다.
     */
    fun sell(instanceId: String): PlanningResult {
        val unit = (player.bench + player.board).firstOrNull { it.instanceId == instanceId }
            ?: return PlanningResult.Failure(PlanningError.UNIT_NOT_FOUND)

        val price = Economy.sellPrice(unit)
        repeat(unit.copiesConsumed) { pool.giveBack(unit.unitDef.id) }

        player = player.copy(
            gold = player.gold + price,
            bench = player.bench.filterNot { it.instanceId == instanceId },
            board = player.board.filterNot { it.instanceId == instanceId },
        )
        return PlanningResult.Success(unit = unit, goldSpent = -price)
    }

    /** 경험치를 산다. 명세서 4-1: 4골드로 경험치 4. */
    fun buyExp(): PlanningResult {
        if (player.level >= PlayerState.MAX_LEVEL) return PlanningResult.Failure(PlanningError.MAX_LEVEL)
        if (player.gold < EconomyRules.BUY_EXP_COST) return PlanningResult.Failure(PlanningError.NOT_ENOUGH_GOLD)

        player = Economy.grantExp(
            player.copy(gold = player.gold - EconomyRules.BUY_EXP_COST),
            EconomyRules.BUY_EXP_AMOUNT,
        )
        return PlanningResult.Success(goldSpent = EconomyRules.BUY_EXP_COST)
    }

    /**
     * 유닛을 보드의 [coord] 칸으로 옮긴다. 벤치에서 올릴 때도, 보드 안에서 자리를 바꿀 때도 이 함수를 쓴다.
     *
     * 로드맵 3단계 완료 기준인 "유닛을 헥스 보드에 배치"가 이 함수다.
     *
     * - 빈 칸으로 옮길 때만 보드 정원(= 레벨)을 확인한다.
     * - 이미 다른 유닛이 있는 칸이면 **두 유닛의 자리를 맞바꾼다.** 정원은 변하지 않으므로
     *   보드가 꽉 찬 상태에서도 벤치의 유닛과 보드의 유닛을 바꿔 넣을 수 있다.
     */
    fun moveToBoard(instanceId: String, coord: HexCoord): PlanningResult {
        if (!HexBoard.contains(coord)) return PlanningResult.Failure(PlanningError.INVALID_COORD)

        val onBench = player.bench.firstOrNull { it.instanceId == instanceId }
        val onBoard = player.board.firstOrNull { it.instanceId == instanceId }
        val moving = onBench ?: onBoard ?: return PlanningResult.Failure(PlanningError.UNIT_NOT_FOUND)

        val occupant = player.board.firstOrNull { it.position == coord }
        if (occupant?.instanceId == instanceId) return PlanningResult.Success(unit = moving)

        player = when {
            // 벤치 → 빈 칸
            onBench != null && occupant == null -> {
                if (player.board.size >= player.boardCapacity) {
                    return PlanningResult.Failure(PlanningError.BOARD_FULL)
                }
                player.copy(
                    bench = player.bench.filterNot { it.instanceId == instanceId },
                    board = player.board + moving.copy(position = coord),
                )
            }

            // 벤치 ↔ 보드 맞교환
            onBench != null -> player.copy(
                bench = player.bench.map { if (it.instanceId == instanceId) occupant!!.copy(position = null) else it },
                board = player.board.map { if (it.instanceId == occupant!!.instanceId) moving.copy(position = coord) else it },
            )

            // 보드 안에서 빈 칸으로 이동
            occupant == null -> player.copy(
                board = player.board.map { if (it.instanceId == instanceId) it.copy(position = coord) else it },
            )

            // 보드 안에서 두 유닛 자리 맞교환
            else -> {
                val from = moving.position
                player.copy(
                    board = player.board.map {
                        when (it.instanceId) {
                            instanceId -> it.copy(position = coord)
                            occupant.instanceId -> it.copy(position = from)
                            else -> it
                        }
                    },
                )
            }
        }
        return PlanningResult.Success(unit = moving)
    }

    /**
     * 보드의 유닛을 벤치로 내린다.
     *
     * 로드맵 3단계 완료 기준의 "회수"가 이 함수다.
     */
    fun returnToBench(instanceId: String): PlanningResult {
        val unit = player.board.firstOrNull { it.instanceId == instanceId }
            ?: return PlanningResult.Failure(PlanningError.UNIT_NOT_FOUND)
        if (player.bench.size >= PlayerState.BENCH_SIZE) {
            return PlanningResult.Failure(PlanningError.BENCH_FULL)
        }

        player = player.copy(
            board = player.board.filterNot { it.instanceId == instanceId },
            bench = player.bench + unit.copy(position = null),
        )
        return PlanningResult.Success(unit = unit)
    }

    /** [coord] 칸에 있는 유닛. 빈 칸이면 null. */
    fun unitAt(coord: HexCoord) = player.board.firstOrNull { it.position == coord }

    /** 전투 결과를 반영한다. 실제 전투 판정은 로드맵 4단계에서 붙는다. */
    fun recordResult(won: Boolean, hpLoss: Int = 0) {
        player = Economy.recordResult(player, won, hpLoss)
    }

    private fun nextInstanceId(): String = "u${instanceCounter++}"
}
