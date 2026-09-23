package com.leechanghyun.autobattler.core.ai

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.economy.ShopOffer
import com.leechanghyun.autobattler.core.masterdata.EconomyRules
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.HexCoord
import com.leechanghyun.autobattler.core.model.ItemDef
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.model.UnitDef
import com.leechanghyun.autobattler.core.planning.PlanningResult
import com.leechanghyun.autobattler.core.planning.PlanningSession

/** 봇이 한 라운드에 실제로 한 일. 테스트와 11단계 튜닝이 "왜 그랬는지"를 읽는다. */
sealed interface BotAction {
    /** [score] 를 통째로 실어 보낸다. 결과 상태가 아니라 **판단 근거**를 단언할 수 있게 하기 위해서다. */
    data class Bought(val slotIndex: Int, val unitId: String, val score: BotScore) : BotAction

    data class LeveledUp(val toLevel: Int) : BotAction
    data class Placed(val instanceId: String, val coord: HexCoord) : BotAction
    data class Benched(val instanceId: String) : BotAction
    data class CombinedItem(val itemId: String) : BotAction
    data class Equipped(val instanceId: String, val itemId: String) : BotAction

    /** 벤치가 꽉 차서 살 자리를 만들려고 판 것. 골드가 필요해서 파는 일은 하지 않는다. */
    data class SoldForSpace(val instanceId: String, val unitId: String) : BotAction
}

/** 봇의 한 라운드 기록. */
data class BotTurn(
    val playerId: String,
    val round: Int,
    val plan: BotPlan,
    val actions: List<BotAction>,
) {
    val purchases: List<BotAction.Bought> get() = actions.filterIsInstance<BotAction.Bought>()
}

/**
 * "이번에 상점 몇 번 칸을 살 것인가"만 정하는 정책. 더 살 것이 없으면 null.
 *
 * 이것이 [BotBrain] 에서 유일하게 갈라지는 지점이다. 8단계 완료 기준을 증명하는 A/B 실험이
 * **구매 선택만 다르고 나머지(레벨업·배치·아이템)는 완전히 같은** 두 봇을 필요로 하기 때문이다.
 * 차이가 이 인터페이스 하나로 좁혀져야 "시너지 방향성은 스코어링이 만든 것"이라고 말할 수 있다.
 */
fun interface BuyPolicy {
    fun chooseSlot(offer: ShopOffer, player: PlayerState, plan: BotPlan): Int?
}

/**
 * 명세서 4-7 스코어링으로 사는 정책.
 *
 * 동점이면 **낮은 슬롯 번호**가 이긴다. 난수를 쓰지 않으므로 어딘가에서 순서를 정해야 하고,
 * 슬롯 번호는 화면에 보이는 순서라 사람이 결과를 예측할 수 있다.
 */
object ScoringBuyPolicy : BuyPolicy {

    override fun chooseSlot(offer: ShopOffer, player: PlayerState, plan: BotPlan): Int? =
        scoreSlots(offer, player, plan)
            .filter { (_, score) -> score.worthBuying }
            .minWithOrNull(compareByDescending<Pair<Int, BotScore>> { it.second.total }.thenBy { it.first })
            ?.first

    /** 살 수 있는 칸마다 점수를 매긴다. 이미 산 칸과 빈 칸은 빠진다. */
    fun scoreSlots(offer: ShopOffer, player: PlayerState, plan: BotPlan): List<Pair<Int, BotScore>> =
        offer.slots.mapIndexedNotNull { index, slot ->
            val unit = slot.unit
            if (unit == null || slot.purchased) null else index to BotScoring.score(unit, player, plan)
        }
}

/**
 * AI 봇 1명의 준비 단계 행동. 명세서 4-7, 로드맵 8단계.
 *
 * ### 라운드 진행은 부르지 않는다
 * [playRound] 는 **이미 [PlanningSession.nextRound] 로 시작된** 라운드에서 행동만 한다.
 * 라운드를 진행시키는 일은 호출자의 몫이다. 9단계 증강 라운드(2-1/3-2/4-2)와 10단계 크립
 * 라운드(1-4/2-4/3-4/4-4)가 라운드 사이에 끼어들 자리를 지금 비워 두기 위해서다.
 *
 * ### 순서와 그 이유
 * ```
 * 0. 계획 고정   우선 태그 2개. 라운드 내내 다시 계산하지 않는다.
 * 1. 구매 루프   명세서 4-7 스코어링
 * 2. 레벨업      보드가 정원까지 찼고 골드가 남을 때만
 * 3. 배치        보유 유닛에서 정원만큼 다시 고른다
 * 4. 아이템 조합
 * 5. 아이템 장착
 * ```
 * **레벨업을 구매 뒤에 둔 것은 약한 선호이지, 테스트가 지키는 규칙이 아니다.** 레벨업이 뒤에 있으면
 * 경험치는 "이번 상점에 살 만한 게 없어 남은 골드"의 쓸 곳이 된다. 8인 로비 20라운드에서 순서를
 * 뒤집어 재 보니 차이는 작았다 — 가장 깊은 시너지 3.66 → 3.49, 최고 단계 1.74 → 1.60, 구매량은
 * 56.5 → 54.5 로 거의 그대로였다. 방향은 지금 순서가 낫지만, 이 크기를 단언하려면 밸런스 수치를
 * 테스트에 박아야 해서 **일부러 감시 테스트를 두지 않았다.** 11단계에서 다시 재야 한다.
 * 상점은 이번 라운드 초에 이미 뽑혔으므로 레벨업이 이번 상점 확률을 바꾸지는 못한다(다음 라운드부터).
 *
 * ### 판매는 자리를 만들 때만 한다
 * 명세서 4-7 은 판매를 한 글자도 말하지 않는다. 처음에는 그래서 아예 넣지 않았는데, 8인 로비를
 * 20라운드 돌려 보니 **모든 봇의 벤치가 9칸까지 차서 구매가 멈췄다.** 보드 정원이 레벨과 같아
 * 최대 10칸인데 사 모은 유닛은 그보다 훨씬 많아지기 때문이다. 구매가 멈추면 8단계 완료 기준인
 * "시너지 방향성 있게 구매" 자체를 관찰할 수 없다.
 *
 * 그래서 **사고 싶은 후보가 있는데 벤치가 꽉 찬 경우에만**, 그리고 **그 후보가 팔 카드보다 나을
 * 때만** 한 장 판다. 골드가 필요해서 파는 일은 하지 않는다. 무엇을 팔고 무엇이 "낫다"인지는
 * [BenchEviction] 이 정하고, `BotBrainTest` 가 그 규칙을 직접 찌른다.
 *
 * ### 리롤하지 않는다
 * 명세서 4-7 이 언급하지 않는다. "이 상점을 버릴 만한가"는 남은 풀과 목표까지의 거리를 함께 봐야
 * 하는 별도 정책이고, 8단계 완료 기준은 **주어진 상점에서 무엇을 고르는가**로 판정된다.
 * `봇은 아직 리롤하지 않는다` 테스트가 이 결정을 감시한다.
 *
 * ### combat 을 보지 않는다
 * 이 파일에 `core.combat` import 가 한 줄도 없다. 8단계 봇은 전투를 몰라도 되고,
 * 알면 ai → combat 의존이 생겨 명세서 6장의 레이어 배치가 무너진다.
 */
class BotBrain(private val buyPolicy: BuyPolicy = ScoringBuyPolicy) {

    fun playRound(session: PlanningSession): BotTurn {
        val plan = BotPlan.of(session.player, session.round)
        val actions = mutableListOf<BotAction>()

        buyUnits(session, plan, actions)
        levelUp(session, actions)
        placeUnits(session, plan, actions)
        combineItems(session, actions)
        equipItems(session, actions)

        return BotTurn(
            playerId = session.player.playerId,
            round = session.round,
            plan = plan,
            actions = actions.toList(),
        )
    }

    /**
     * 살 만한 것이 없을 때까지 산다.
     *
     * 한 칸은 한 번만 살 수 있고 상점은 5칸이므로 루프는 라운드당 최대 5회로 저절로 끝난다.
     * 별도 상한이 필요 없다. [PlanningSession.buy] 가 실패하면(벤치 가득 등) 즉시 멈춘다.
     *
     * **[plan] 은 루프 안에서 다시 만들지 않는다.** 명세서 4-7 의 "라운드 시작 시 고정"이다.
     * 점수는 매번 다시 계산한다. 복제본 수가 늘고 6단계 합성이 그 자리에서 보드를 바꾸기 때문이다.
     */
    private fun buyUnits(session: PlanningSession, plan: BotPlan, actions: MutableList<BotAction>) {
        while (true) {
            val player = session.player
            val slotIndex = buyPolicy.chooseSlot(session.offer, player, plan) ?: return
            val unitDef = session.offer.slots.getOrNull(slotIndex)?.unit ?: return
            val score = BotScoring.score(unitDef, player, plan)

            // 벤치가 꽉 찼으면 자리를 한 칸 만든다. 못 만들면 이번 라운드 구매는 끝이다.
            if (player.bench.size >= PlayerState.BENCH_SIZE &&
                !sellForSpace(session, plan, unitDef, actions)
            ) {
                return
            }

            if (session.buy(slotIndex) !is PlanningResult.Success) return
            actions += BotAction.Bought(slotIndex = slotIndex, unitId = unitDef.id, score = score)
        }
    }

    /**
     * [BenchEviction] 이 고른 한 장을 팔아 [incoming] 이 들어올 자리를 만든다. 팔지 않았으면 false.
     *
     * 보드 유닛은 건드리지 않는다. 배치해 둔 진형을 구매 한 장 때문에 무너뜨릴 이유가 없다.
     */
    private fun sellForSpace(
        session: PlanningSession,
        plan: BotPlan,
        incoming: UnitDef,
        actions: MutableList<BotAction>,
    ): Boolean {
        val player = session.player
        val victim = BenchEviction.victim(player, plan) ?: return false
        if (!BenchEviction.isUpgrade(incoming, victim, player, plan)) return false

        if (session.sell(victim.instanceId) !is PlanningResult.Success) return false
        actions += BotAction.SoldForSpace(victim.instanceId, victim.unitDef.id)
        return true
    }

    /**
     * 보드 정원이 병목일 때만 경험치를 산다.
     *
     * 정원이 남았는데 레벨을 사는 것은 골드만 버리는 일이다. 조건이 자기 제한적이라
     * "라운드당 최대 몇 번" 같은 상한 표가 필요 없다.
     */
    private fun levelUp(session: PlanningSession, actions: MutableList<BotAction>) {
        while (true) {
            val player = session.player
            if (player.level >= PlayerState.MAX_LEVEL) return
            if (player.board.size < player.boardCapacity) return
            if (player.gold - EconomyRules.BUY_EXP_COST < BotWeights.LEVEL_UP_GOLD_FLOOR) return

            val before = player.level
            if (session.buyExp() !is PlanningResult.Success) return
            if (session.player.level != before) actions += BotAction.LeveledUp(session.player.level)
        }
    }

    /**
     * 보유 유닛 전체에서 정원만큼 다시 고른다.
     *
     * ### 한 자리씩 그리디로 고른다
     * 시너지는 조합 효과라 "각자 점수를 매겨 상위 N개"로는 임계값을 맞출 수 없다. 예를 들어
     * 수호자 3명과 검사 3명 중 4칸을 고를 때, 개별 점수로 뽑으면 3+1 이 되어 어느 쪽도 4인을
     * 못 채운다. 이미 고른 유닛들을 기준으로 **한계 효용**을 다시 재면 4+0 이 나온다.
     *
     * ### 적용은 맞교환 우선
     * 내릴 유닛과 올릴 유닛을 짝지어 [PlanningSession.moveToBoard] 로 바꿔 끼운다.
     * 3단계가 만든 벤치↔보드 맞교환은 정원 검사를 타지 않으므로 벤치가 넘칠 일이 없다.
     * 먼저 다 내리고 나중에 올리면 벤치가 꽉 차 `BENCH_FULL` 로 실패할 수 있다.
     *
     * ### 진형은 8단계 범위가 아니다
     * 남은 빈 칸은 [HexBoard.coords] 순서대로 채운다. 근접/원거리 분리 같은 최적화는 전투가
     * 붙어야 좋고 나쁨을 잴 수 있는데, 8단계 로비는 전투를 돌리지 않는다. 측정할 수 없는 규칙을
     * 넣지 않는다는 이 저장소의 관행을 따른다.
     */
    private fun placeUnits(session: PlanningSession, plan: BotPlan, actions: MutableList<BotAction>) {
        val player = session.player
        val capacity = player.boardCapacity
        val chosen = chooseBoard(player.bench + player.board, capacity, plan)
        val chosenIds = chosen.map { it.instanceId }.toSet()

        val toDemote = player.board.filterNot { it.instanceId in chosenIds }
        val toPromote = chosen.filterNot { it.isOnBoard }

        // 1. 짝지어 맞교환. 정원 검사를 타지 않아 어느 쪽도 넘치지 않는다.
        val swaps = minOf(toDemote.size, toPromote.size)
        repeat(swaps) { index ->
            val coord = toDemote[index].position ?: return@repeat
            if (session.moveToBoard(toPromote[index].instanceId, coord) is PlanningResult.Success) {
                actions += BotAction.Benched(toDemote[index].instanceId)
                actions += BotAction.Placed(toPromote[index].instanceId, coord)
            }
        }

        // 2. 짝이 남은 쪽 처리. 둘 중 하나만 실행된다.
        toDemote.drop(swaps).forEach { unit ->
            if (session.returnToBench(unit.instanceId) is PlanningResult.Success) {
                actions += BotAction.Benched(unit.instanceId)
            }
        }
        toPromote.drop(swaps).forEach { unit ->
            val coord = freeCoord(session) ?: return@forEach
            if (session.moveToBoard(unit.instanceId, coord) is PlanningResult.Success) {
                actions += BotAction.Placed(unit.instanceId, coord)
            }
        }
    }

    private fun freeCoord(session: PlanningSession): HexCoord? {
        val occupied = session.player.board.mapNotNull { it.position }.toSet()
        return HexBoard.coords.firstOrNull { it !in occupied }
    }

    /** 빈 보드에서 시작해 한계 효용이 가장 큰 유닛을 한 자리씩 채운다. */
    private fun chooseBoard(all: List<BoardUnit>, capacity: Int, plan: BotPlan): List<BoardUnit> {
        if (capacity <= 0) return emptyList()

        val chosen = mutableListOf<BoardUnit>()
        val remaining = all.toMutableList()

        while (chosen.size < capacity && remaining.isNotEmpty()) {
            // instanceId 는 고유하므로 마지막 키가 동점을 반드시 끊는다. 해시 순서가 개입할 여지가 없다.
            val best = remaining.maxWith(
                compareBy<BoardUnit> { marginalValue(it, chosen, plan) }
                    .thenBy { BotScoring.instancePower(it) }
                    .thenByDescending { it.instanceId },
            )
            chosen += best
            remaining -= best
        }
        return chosen
    }

    /** 이미 고른 유닛들을 기준으로 이 유닛을 한 자리 더 쓸 가치. 구매 스코어링의 3·4항과 같은 식이다. */
    private fun marginalValue(unit: BoardUnit, chosen: List<BoardUnit>, plan: BotPlan): Double {
        val onBoard = chosen.map { if (it.isOnBoard) it else it.copy(position = HexBoard.coords.first()) }
        return BotScoring.thresholdBreakOn(unit.unitDef, onBoard) +
            BotScoring.synergyOverlapOn(unit.unitDef, onBoard, plan)
    }

    /**
     * 가방의 컴포넌트를 앞 칸부터 둘씩 합친다.
     *
     * 어떤 완성템을 노릴지의 선호는 두지 않았다. 45종의 고유 효과가 7단계에서 하나도 구현되지
     * 않아(`ItemDef.effectId` 를 읽는 코드가 없다) 지금 선호를 정하면 전부 근거 없는 값이 된다.
     * 11단계에서 효과와 함께 정한다.
     *
     * 성공할 때마다 가방이 한 칸 줄어 반드시 끝난다.
     */
    private fun combineItems(session: PlanningSession, actions: MutableList<BotAction>) {
        while (true) {
            val pair = firstCombinablePair(session.player.itemInventory) ?: return
            val result = session.combineItems(pair.first, pair.second)
            if (result !is PlanningResult.Success) return
            actions += BotAction.CombinedItem(result.item?.id ?: return)
        }
    }

    private fun firstCombinablePair(inventory: List<ItemDef>): Pair<Int, Int>? {
        inventory.indices.forEach { first ->
            ((first + 1) until inventory.size).forEach { second ->
                if (MasterData.combine(inventory[first].id, inventory[second].id) != null) {
                    return first to second
                }
            }
        }
        return null
    }

    /**
     * 명세서 4-7: "완성 아이템은 **가장 보유 스탯이 좋은 유닛**에게 우선 장착".
     *
     * 장착할 때마다 [BotScoring.instancePower] 를 다시 계산한다. 끼우면 그 유닛이 더 강해지므로
     * 자연히 칸 3개가 찰 때까지 같은 유닛에 쌓이고, 4번째부터 차선에게 간다.
     * "우선 장착"이 규칙 하나에서 그대로 나오고 따로 정할 것이 없다.
     *
     * 컴포넌트는 끼우지 않는다. 명세서가 "완성 아이템"이라고 한정했고, 끼워 두면 7단계가 정한
     * "조합은 가방 안에서만"에 걸려 합칠 수 없게 된다.
     */
    private fun equipItems(session: PlanningSession, actions: MutableList<BotAction>) {
        while (true) {
            val inventoryIndex = session.player.itemInventory.indexOfFirst { !it.isComponent }
            if (inventoryIndex < 0) return
            val item = session.player.itemInventory[inventoryIndex]

            val target = session.player.board
                .filter { it.hasFreeItemSlot }
                .maxWithOrNull(
                    compareBy<BoardUnit> { BotScoring.instancePower(it) }
                        .thenByDescending { it.instanceId },
                ) ?: return

            if (session.equip(target.instanceId, inventoryIndex) !is PlanningResult.Success) return
            actions += BotAction.Equipped(target.instanceId, item.id)
        }
    }
}
