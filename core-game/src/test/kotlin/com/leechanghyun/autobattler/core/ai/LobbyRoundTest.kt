package com.leechanghyun.autobattler.core.ai

import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.synergy.SynergyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 로드맵 8단계 완료 기준 — "8인 라운드 순환 시 AI가 시너지 방향성 있게 구매".
 *
 * ### 무엇을 재는가
 * 기준의 동사는 **구매**다. 그래서 이 테스트가 재는 것은 "끝난 보드가 얼마나 센가"가 아니라
 * **산 카드가 그 라운드의 목표 태그와 겹치는 비율**이다. 보드는 구매 말고도 배치·판매가 함께
 * 만들고, 그 둘은 대조군도 똑같이 쓰므로 보드만 봐서는 구매의 공을 가려낼 수 없다.
 * 실제로 8인 로비 20라운드에서 두 봇의 보드 시너지 깊이는 사실상 같게 나온다.
 *
 * ### 대조군
 * [LeftmostBuyPolicy] 는 점수를 한 번도 계산하지 않고 살 수 있는 왼쪽 칸부터 산다.
 * 골드 규칙·레벨업·배치·아이템·판매는 [BotBrain] 이 양쪽에 똑같이 적용하므로,
 * 두 봇의 차이는 [BuyPolicy] 하나뿐이다.
 *
 * ### 좌석 편향을 어떻게 지우는가
 * `PlanningSession.nextRound` 가 "직전 상점 반납 + 새 상점 롤"을 한 함수에서 하므로 좌석 0 은
 * 다른 좌석의 잔여 카드가 풀로 돌아오기 전에 뽑는다. 그래서 모든 측정을 **짝수 좌석이 스코어링인
 * 판과 홀수 좌석이 스코어링인 판**에서 함께 돌린다(미러런). 좌석 번호가 유리하다면 양쪽에
 * 똑같이 유리하므로 상쇄된다.
 */
class LobbyRoundTest {

    private companion object {
        const val ROUNDS = 20
        val SEEDS = 1..5

        /** 사람 자리. 명세서 1-3 "싱글 플레이어 vs AI 봇 7". */
        const val HUMAN_SEAT = 7
    }

    private fun lobby(seed: Int, scoringEven: Boolean): LobbyFixture {
        val policies: List<BuyPolicy?> = (0 until 8).map { seat ->
            when {
                seat == HUMAN_SEAT -> null
                (seat % 2 == 0) == scoringEven -> ScoringBuyPolicy
                else -> LeftmostBuyPolicy
            }
        }
        return LobbyFixture(seed, policies)
    }

    private fun isScoring(seat: Int, scoringEven: Boolean) =
        seat != HUMAN_SEAT && (seat % 2 == 0) == scoringEven

    /**
     * 그 좌석이 이번 판에서 산 카드 중 **목표 태그와 겹치는 것**의 비율.
     *
     * 벤치가 꽉 차 [BotAction.SoldForSpace] 를 거쳐 들어온 구매는 뺀다. 그 교체 판정은
     * [BotBrain] 이 양쪽에 똑같이 거는 것이고 목표 태그를 보므로, 포함하면 대조군의 구매까지
     * 방향성이 생겨 정책 차이가 흐려진다. 빼고 재면 순수하게 [BuyPolicy] 가 고른 카드만 남는다.
     */
    private fun unguardedMatchRate(turns: List<BotTurn>, playerId: String): Double {
        val matches = turns.filter { it.playerId == playerId }.flatMap { turn ->
            turn.actions.mapIndexedNotNull { index, action ->
                if (action !is BotAction.Bought) return@mapIndexedNotNull null
                if (index > 0 && turn.actions[index - 1] is BotAction.SoldForSpace) return@mapIndexedNotNull null
                turn.plan.priorityMatchCount(MasterData.unit(action.unitId)) > 0
            }
        }
        assertTrue("$playerId 가 가드 없이 산 카드가 한 장도 없다", matches.isNotEmpty())
        return matches.count { it }.toDouble() / matches.size
    }

    @Test
    fun `8인 로비를 순환하면 스코어링 봇이 대조군보다 목표 태그에 맞춰 산다`() {
        val scoring = mutableListOf<Double>()
        val naive = mutableListOf<Double>()

        for (seed in SEEDS) {
            for (scoringEven in listOf(true, false)) {
                val lobby = lobby(seed, scoringEven)
                val turns = lobby.playRounds(ROUNDS)
                (0 until 8).forEach { seat ->
                    if (seat == HUMAN_SEAT) return@forEach
                    val rate = unguardedMatchRate(turns, "seat$seat")
                    if (isScoring(seat, scoringEven)) scoring += rate else naive += rate
                }
            }
        }

        assertEquals("두 집단의 표본 수가 같아야 짝비교가 성립한다", scoring.size, naive.size)

        // 측정값(시드 1..5, 미러런): 스코어링 0.50, 대조군 0.38, 짝비교 29승 6패.
        // 임계값은 여유 있게 둔다. 11단계 밸런싱이 로스터 수치를 바꾸면 절대값은 움직이지만
        // "스코어링이 더 방향성 있게 산다"는 관계는 움직이지 않아야 한다.
        val scoringRate = scoring.average()
        val naiveRate = naive.average()
        val message = "스코어링=$scoringRate 대조군=$naiveRate"
        assertTrue("스코어링 봇이 목표 태그 카드를 절반 가까이 사야 한다: $message", scoringRate >= 0.45)
        assertTrue("대조군과의 차이가 사라졌다: $message", scoringRate - naiveRate >= 0.05)

        val wins = scoring.indices.count { scoring[it] > naive[it] }
        assertTrue(
            "좌석별 짝비교에서 스코어링이 과반을 이겨야 한다: $wins/${scoring.size}",
            wins * 2 > scoring.size,
        )
    }

    @Test
    fun `8인 로비를 순환하면 모든 봇이 시너지를 발동한다`() {
        for (seed in SEEDS) {
            for (scoringEven in listOf(true, false)) {
                val lobby = lobby(seed, scoringEven)
                lobby.playRounds(ROUNDS)
                lobby.seats.forEachIndexed { seat, it ->
                    if (seat == HUMAN_SEAT) return@forEachIndexed
                    assertTrue(
                        "시드 $seed 좌석 $seat 봇이 $ROUNDS 라운드 동안 시너지를 하나도 못 켰다",
                        it.activeTraitCount >= 1,
                    )
                    assertEquals(
                        "시드 $seed 좌석 $seat 봇의 보드가 정원까지 차지 않았다",
                        it.player.boardCapacity,
                        it.player.board.size,
                    )
                }
            }
        }
    }

    @Test
    fun `사람 자리는 상점만 받고 아무것도 사지 않는다`() {
        val lobby = lobby(seed = 1, scoringEven = true)
        lobby.playRounds(ROUNDS)

        val human = lobby.seats[HUMAN_SEAT]
        assertTrue("사람 자리에 봇이 붙었다", human.brain == null)
        assertTrue("사람 자리가 유닛을 샀다", human.player.board.isEmpty() && human.player.bench.isEmpty())
        assertTrue("사람 자리에 상점이 뜨지 않았다", human.offer.slots.any { it.unit != null })
    }

    @Test
    fun `봇의 판매는 언제나 바로 뒤의 구매를 위한 것이다`() {
        val turns = lobby(seed = 3, scoringEven = true).let { it.playRounds(ROUNDS) }

        turns.forEach { turn ->
            turn.actions.forEachIndexed { index, action ->
                if (action !is BotAction.SoldForSpace) return@forEachIndexed
                val next = turn.actions.getOrNull(index + 1)
                assertTrue(
                    "${turn.playerId} ${turn.round}라운드: 구매로 이어지지 않는 판매가 있다 ($action → $next)",
                    next is BotAction.Bought,
                )
            }
        }
    }

    /**
     * 교체 가드가 살아 있는지 본다.
     *
     * 가드를 빼면 벤치가 찬 뒤로 "사면 팔고 팔면 산다"가 되어 판매/구매 비가 0.6 근처까지 올라갔다.
     * 가드가 있으면 0.35 아래다. 0.5 는 그 사이에 둔 선이다.
     */
    @Test
    fun `봇이 벤치를 회전문처럼 쓰지 않는다`() {
        for (seed in SEEDS) {
            val turns = lobby(seed, scoringEven = true).playRounds(ROUNDS)
            (0 until 8).forEach { seat ->
                if (seat == HUMAN_SEAT) return@forEach
                val mine = turns.filter { it.playerId == "seat$seat" }
                val bought = mine.sumOf { it.purchases.size }
                val sold = mine.sumOf { turn -> turn.actions.count { it is BotAction.SoldForSpace } }
                assertTrue("시드 $seed 좌석 $seat 봇이 한 장도 사지 않았다", bought > 0)
                assertTrue(
                    "시드 $seed 좌석 $seat 봇이 산 $bought 장 중 $sold 장을 되팔았다",
                    sold.toDouble() / bought < 0.5,
                )
            }
        }
    }

    /**
     * 8단계 봇은 리롤하지 않는다. 명세서 4-7 이 리롤을 말하지 않기 때문이다.
     *
     * 리롤이 들어오면 라운드 도중 상점 칸의 유닛이 통째로 바뀌므로, 사지 않은 칸의 내용이
     * 라운드 시작 때와 같은지로 잡는다.
     */
    @Test
    fun `봇은 아직 리롤하지 않는다`() {
        val lobby = lobby(seed = 4, scoringEven = true)
        repeat(ROUNDS) {
            lobby.seats.forEach { it.session.nextRound() }
            lobby.seats.forEachIndexed { seat, it ->
                val before = it.offer.slots.map { slot -> slot.unit?.id }
                it.brain?.playRound(it.session)
                val after = it.offer.slots.map { slot -> slot.unit?.id }
                assertEquals("좌석 $seat 의 상점이 라운드 도중 다시 뽑혔다", before, after)
            }
        }
    }

    @Test
    fun `8인 로비는 카드를 만들지도 잃지도 않는다`() {
        for (seed in SEEDS) {
            val lobby = lobby(seed, scoringEven = true)
            lobby.playRounds(ROUNDS)
            assertEquals(
                "시드 $seed: 풀 + 상점 + 보유 장수가 시작 재고와 달라졌다",
                lobby.startingCardCount(),
                lobby.cardsInPlay(),
            )
        }
    }

    @Test
    fun `같은 시드로 두 번 돌리면 8석이 완전히 같다`() {
        val first = lobby(seed = 2, scoringEven = true).also { it.playRounds(ROUNDS) }
        val second = lobby(seed = 2, scoringEven = true).also { it.playRounds(ROUNDS) }
        assertEquals(first.snapshot(), second.snapshot())
    }

    @Test
    fun `시드가 다르면 로비 결과도 다르다`() {
        val first = lobby(seed = 2, scoringEven = true).also { it.playRounds(ROUNDS) }
        val second = lobby(seed = 3, scoringEven = true).also { it.playRounds(ROUNDS) }
        assertTrue("시드를 바꿔도 결과가 같다면 시드가 쓰이지 않는 것이다", first.snapshot() != second.snapshot())
    }

    /**
     * 8단계 봇은 전투를 돌리지 않는다. 완료 기준의 동사가 "구매"이고,
     * 11단계 완료 기준이 "8인 풀 매치"를 따로 요구하기 때문이다.
     *
     * 전투를 붙이면 측정이 순환한다. 스코어링 봇이 이겨 연승 골드를 더 받고 그 골드로 시너지를
     * 더 올리게 되어, "스코어링이 시너지를 만들었다"를 증명할 수 없게 된다.
     */
    @Test
    fun `로비를 순환해도 전투 결과는 정산되지 않는다`() {
        val lobby = lobby(seed = 5, scoringEven = true)
        lobby.playRounds(ROUNDS)
        lobby.seats.forEach {
            assertEquals("${it.player.playerId} 의 체력이 깎였다", PlayerState.STARTING_HP, it.player.hp)
            assertEquals("${it.player.playerId} 의 연승이 쌓였다", 0, it.player.winStreak)
            assertEquals("${it.player.playerId} 의 연패가 쌓였다", 0, it.player.loseStreak)
        }
    }

    /** 보드 시너지는 실제로 켜져 있어야 한다. 봇이 태그를 세기만 하고 배치를 안 하면 여기서 걸린다. */
    @Test
    fun `봇의 보드에는 2단계 이상 시너지가 하나는 선다`() {
        var reached = 0
        var total = 0
        for (seed in SEEDS) {
            for (scoringEven in listOf(true, false)) {
                val lobby = lobby(seed, scoringEven)
                lobby.playRounds(ROUNDS)
                lobby.botSeats.forEach { seat ->
                    total++
                    if (SynergyEngine.activeTraits(seat.player.board).any { it.tier >= 2 }) reached++
                }
            }
        }
        assertTrue("봇 $total 명 중 $reached 명만 2단계 시너지에 닿았다", reached * 2 > total)
    }
}
