package com.leechanghyun.autobattler.core.ai

import com.leechanghyun.autobattler.core.economy.ShopOffer
import com.leechanghyun.autobattler.core.economy.ShopRoller
import com.leechanghyun.autobattler.core.economy.UnitPool
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.planning.PlanningSession
import com.leechanghyun.autobattler.core.synergy.SynergyEngine
import kotlin.random.Random

/**
 * 8인 로비를 라운드 단위로 돌리는 테스트 하네스. 로드맵 8단계 완료 기준을 재기 위한 장치다.
 *
 * ### 왜 프로덕션 클래스가 아닌가
 * 오늘 이것을 부르는 프로덕션 코드가 없다. `ShopViewModel` 은 한 사람의 준비 단계만 다루고,
 * 8인 루프를 화면에 붙이는 일은 10단계 전투/관전 화면의 몫이다.
 *
 * 그리고 **진짜 `Lobby` 가 들고 있어야 할 것을 8단계는 하나도 정할 수 없다.** 스테이지-라운드
 * 번호 체계(명세서 4-8 은 "2-1, 3-2, 4-2", 4-9 는 "1-4, 2-4, 3-4, 4-4"를 요구하지만 명세서는
 * 스테이지당 라운드 수를 끝까지 적지 않는다), 증강 라운드와 크립 라운드 삽입, 대진, 체력 정산,
 * 탈락과 등수 — 전부 9·10·11단계 소유다. 지금 만들면 반드시 뒤엎힌다.
 *
 * ### 전투를 돌리지 않는다
 * 8단계 완료 기준의 동사는 "구매"다("8인 라운드 순환 시 AI가 시너지 방향성 있게 **구매**").
 * 11단계 완료 기준이 "8인 풀 매치 반복 테스트 / 승패 편차 없이 안정적으로 게임 종료까지 진행"이므로
 * 8인이 끝까지 붙는 것은 명시적으로 11단계의 몫이다.
 *
 * 전투를 붙이면 측정도 오염된다. 스코어링 봇이 이겨 연승 골드를 더 받고 그 골드로 시너지를 더
 * 올리게 되어, "스코어링이 시너지를 만들었다"를 순환 논리 없이 증명할 수 없다.
 *
 * ### 공유하는 것
 * [UnitPool] 하나와 [ShopRoller] 하나(= 난수 스트림 하나)를 8석이 나눠 쓴다. 풀 공유가 명세서
 * 4-1 경제의 전부이고, 난수 스트림이 하나여야 시드 하나로 로비 전체가 재현된다.
 *
 * @param buyPolicies 좌석별 구매 정책. **null 은 사람 자리**로, 상점만 받고 아무것도 사지 않는다.
 *   명세서 1-3 "싱글 플레이어 vs AI 봇 7"의 사람 자리이며, 그 자리도 상점이 풀에서 카드를 빼고
 *   리롤 때 되돌리는 경로를 함께 돌린다.
 */
internal class LobbyFixture(
    seed: Int,
    buyPolicies: List<BuyPolicy?>,
    startingGold: Int = PlayerState.STARTING_GOLD,
) {
    val pool = UnitPool()
    private val roller = ShopRoller(pool, Random(seed))

    val seats: List<Seat> = buyPolicies.mapIndexed { index, policy ->
        Seat(
            session = PlanningSession(
                pool = pool,
                roller = roller,
                initialPlayer = PlayerState(
                    playerId = "seat$index",
                    displayName = "좌석$index",
                    isBot = policy != null,
                    gold = startingGold,
                ),
            ),
            brain = policy?.let { BotBrain(it) },
        )
    }

    val botSeats: List<Seat> get() = seats.filter { it.brain != null }

    private val totalCards = pool.totalRemaining()

    class Seat(val session: PlanningSession, val brain: BotBrain?) {
        val player: PlayerState get() = session.player
        val offer: ShopOffer get() = session.offer

        /** 발동한 시너지 단계의 합. "많이 샀나"가 아니라 "한 방향으로 모았나"를 잰다. */
        val tierSum: Int get() = SynergyEngine.activeTraits(player.board).sumOf { it.tier }

        /** 한 시너지를 얼마나 깊게 밀었는가. */
        val maxTier: Int get() = SynergyEngine.activeTraits(player.board).maxOfOrNull { it.tier } ?: 0

        /** 실제로 발동한 시너지 종 수. */
        val activeTraitCount: Int get() = SynergyEngine.activeTraits(player.board).count { it.isActive }
    }

    /**
     * 한 라운드를 돌린다. **2패스**다.
     *
     * 1패스에서 8석 전부가 수입과 상점을 받고, 2패스에서 좌석 순서대로 행동한다.
     * 섞으면 좌석 0의 구매 직후 좌석 1이 뽑게 되어 좌석 순서가 결과에 직접 개입한다.
     *
     * 다만 편향이 완전히 사라지지는 않는다. [PlanningSession.nextRound] 가 "직전 상점 반납 +
     * 새 상점 롤"을 한 함수에서 하므로 좌석 0 은 좌석 1~7 의 잔여 카드가 돌아오기 전의 풀에서 뽑는다.
     * 그래서 완료 기준 테스트가 **좌석 배정을 뒤집은 미러런**을 함께 요구한다.
     */
    fun playRound(): List<BotTurn> {
        seats.forEach { it.session.nextRound() }
        return seats.mapNotNull { seat -> seat.brain?.playRound(seat.session) }
    }

    fun playRounds(count: Int): List<BotTurn> = (1..count).flatMap { playRound() }

    /** 풀에 남은 장수 + 8석의 상점에 떠 있는 장수 + 8석이 보유한 장수. 시작 재고와 같아야 한다. */
    fun cardsInPlay(): Int =
        pool.totalRemaining() +
            seats.sumOf { seat -> seat.offer.slots.count { it.unit != null && !it.purchased } } +
            seats.sumOf { seat -> (seat.player.bench + seat.player.board).sumOf { it.copiesConsumed } }

    fun startingCardCount(): Int = totalCards

    /** 결정론 검사용 덤프. 같은 시드면 두 실행의 이 문자열이 완전히 같아야 한다. */
    fun snapshot(): String = seats.joinToString("\n") { seat ->
        val player = seat.player
        val board = player.board.sortedBy { it.instanceId }
            .joinToString(",") { "${it.instanceId}:${it.unitDef.id}:${it.starLevel}:${it.position}:${it.items.map { i -> i.id }}" }
        val bench = player.bench.sortedBy { it.instanceId }
            .joinToString(",") { "${it.instanceId}:${it.unitDef.id}:${it.starLevel}" }
        "${player.playerId} gold=${player.gold} lv=${player.level} exp=${player.exp} " +
            "bag=${player.itemInventory.map { it.id }} board=[$board] bench=[$bench]"
    }
}

/**
 * 대조군. **점수를 한 번도 계산하지 않고** 살 수 있는 가장 왼쪽 칸부터 산다.
 *
 * 골드 규칙([BotScoring.canAfford])은 스코어링 봇과 똑같이 지킨다. 두 봇의 차이가
 * "어느 칸을 고르는가" 하나로 좁혀져야, 시너지 차이의 원인을 명세서 4-7 스코어링으로 지목할 수 있다.
 * 레벨업·배치·아이템은 같은 [BotBrain] 이 처리하므로 그쪽도 완전히 동일하다.
 */
internal object LeftmostBuyPolicy : BuyPolicy {
    override fun chooseSlot(offer: ShopOffer, player: PlayerState, plan: BotPlan): Int? =
        offer.slots.indexOfFirst { slot ->
            val unit = slot.unit
            unit != null && !slot.purchased && BotScoring.canAfford(unit, player)
        }.takeIf { it >= 0 }
}
