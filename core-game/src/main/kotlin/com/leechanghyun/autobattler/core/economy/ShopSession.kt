package com.leechanghyun.autobattler.core.economy

import com.leechanghyun.autobattler.core.masterdata.EconomyRules
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.PlayerState

/** 상점 조작이 실패한 이유. UI 가 사용자에게 보여줄 메시지를 고르는 데 쓴다. */
enum class ShopError {
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
}

/** 상점 조작 결과. 실패하면 상태가 전혀 바뀌지 않는다. */
sealed interface ShopResult {
    data class Success(val unit: BoardUnit? = null, val goldSpent: Int = 0) : ShopResult
    data class Failure(val error: ShopError) : ShopResult

    val isSuccess: Boolean get() = this is Success
}

/**
 * 한 플레이어의 준비 단계(구매/판매/리롤/레벨업)를 담당한다. 명세서 3장, 4-1.
 *
 * 공용 유닛 풀, 상점, 플레이어 상태를 한데 묶어 **항상 함께 갱신**한다.
 * 따로 두면 "상점에서는 샀는데 풀에서는 안 빠진" 같은 어긋남이 생기기 쉽다.
 *
 * 안드로이드 의존성이 없어 단위테스트로 전부 검증할 수 있다.
 * 스레드 안전하지 않으며, 한 플레이어의 조작은 단일 코루틴에서 순차 처리한다는 전제다.
 *
 * 로드맵 3단계(보드 배치)와 6단계(합성)가 붙기 전이라 구매한 유닛은 모두 벤치로 간다.
 */
class ShopSession(
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
    fun reroll(): ShopResult {
        if (player.gold < EconomyRules.REROLL_COST) return ShopResult.Failure(ShopError.NOT_ENOUGH_GOLD)
        player = player.copy(gold = player.gold - EconomyRules.REROLL_COST)
        offer = roller.roll(level = player.level, previous = offer)
        return ShopResult.Success(goldSpent = EconomyRules.REROLL_COST)
    }

    /**
     * 상점 [slotIndex] 칸의 유닛을 사서 벤치에 올린다.
     *
     * 로드맵 2단계 완료 기준인 "유닛 구매 → 벤치 배치"가 이 함수다.
     * 상점에 뜬 시점에 이미 풀에서 빠졌으므로 구매 자체는 풀을 건드리지 않는다.
     */
    fun buy(slotIndex: Int): ShopResult {
        val slot = offer.slots.getOrNull(slotIndex) ?: return ShopResult.Failure(ShopError.SLOT_UNAVAILABLE)
        val unitDef = slot.unit
        if (unitDef == null || slot.purchased) return ShopResult.Failure(ShopError.SLOT_UNAVAILABLE)
        if (player.gold < unitDef.cost) return ShopResult.Failure(ShopError.NOT_ENOUGH_GOLD)
        if (player.bench.size >= PlayerState.BENCH_SIZE) return ShopResult.Failure(ShopError.BENCH_FULL)

        val bought = BoardUnit(instanceId = nextInstanceId(), unitDef = unitDef)
        player = player.copy(
            gold = player.gold - unitDef.cost,
            bench = player.bench + bought,
        )
        offer = offer.markPurchased(slotIndex)
        return ShopResult.Success(unit = bought, goldSpent = unitDef.cost)
    }

    /**
     * 벤치나 보드의 유닛을 판다. 골드를 돌려받고 소모했던 카드가 공용 풀로 돌아간다.
     *
     * 2성/3성 유닛은 각각 3장/9장을 한 번에 되돌린다.
     */
    fun sell(instanceId: String): ShopResult {
        val unit = (player.bench + player.board).firstOrNull { it.instanceId == instanceId }
            ?: return ShopResult.Failure(ShopError.UNIT_NOT_FOUND)

        val price = Economy.sellPrice(unit)
        repeat(unit.copiesConsumed) { pool.giveBack(unit.unitDef.id) }

        player = player.copy(
            gold = player.gold + price,
            bench = player.bench.filterNot { it.instanceId == instanceId },
            board = player.board.filterNot { it.instanceId == instanceId },
        )
        return ShopResult.Success(unit = unit, goldSpent = -price)
    }

    /** 경험치를 산다. 명세서 4-1: 4골드로 경험치 4. */
    fun buyExp(): ShopResult {
        if (player.level >= PlayerState.MAX_LEVEL) return ShopResult.Failure(ShopError.MAX_LEVEL)
        if (player.gold < EconomyRules.BUY_EXP_COST) return ShopResult.Failure(ShopError.NOT_ENOUGH_GOLD)

        player = Economy.grantExp(
            player.copy(gold = player.gold - EconomyRules.BUY_EXP_COST),
            EconomyRules.BUY_EXP_AMOUNT,
        )
        return ShopResult.Success(goldSpent = EconomyRules.BUY_EXP_COST)
    }

    /** 전투 결과를 반영한다. 실제 전투 판정은 로드맵 4단계에서 붙는다. */
    fun recordResult(won: Boolean, hpLoss: Int = 0) {
        player = Economy.recordResult(player, won, hpLoss)
    }

    private fun nextInstanceId(): String = "u${instanceCounter++}"
}
