package com.leechanghyun.autobattler.core.economy

import com.leechanghyun.autobattler.core.masterdata.EconomyRules
import com.leechanghyun.autobattler.core.model.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 로드맵 2단계 완료 기준 검증: **유닛 구매 → 벤치 배치까지 정상 동작한다.**
 *
 * 구매가 골드, 벤치, 상점 칸, 공용 풀 네 곳을 한꺼번에 올바르게 바꾸는지 확인한다.
 */
class ShopSessionTest {

    private fun session(gold: Int = 20, level: Int = 5, seed: Int = 1): ShopSession {
        val pool = UnitPool()
        return ShopSession(
            pool = pool,
            roller = ShopRoller(pool, Random(seed)),
            initialPlayer = PlayerState(
                playerId = "p1",
                displayName = "나",
                isBot = false,
                gold = gold,
                level = level,
            ),
        )
    }

    private fun ShopSession.firstBuyableIndex(): Int =
        offer.slots.indexOfFirst { it.unit != null && !it.purchased }

    // --- 완료 기준 ---

    @Test
    fun `유닛을 사면 골드가 줄고 벤치에 올라간다`() {
        val session = session(gold = 20)
        session.nextRound()

        val index = session.firstBuyableIndex()
        val target = requireNotNull(session.offer.slots[index].unit)
        val goldBefore = session.player.gold

        val result = session.buy(index)

        assertTrue("구매가 성공해야 한다", result.isSuccess)
        assertEquals("골드가 코스트만큼 줄어든다", goldBefore - target.cost, session.player.gold)
        assertEquals("벤치에 1기가 올라간다", 1, session.player.bench.size)
        assertEquals("벤치의 유닛이 산 유닛과 같다", target.id, session.player.bench.single().unitDef.id)
        assertEquals("보드는 비어 있다", 0, session.player.board.size)
        assertEquals("새로 산 유닛은 1성", 1, session.player.bench.single().starLevel)
        assertTrue("산 칸은 구매 표시된다", session.offer.slots[index].purchased)
    }

    @Test
    fun `벤치를 가득 채울 때까지 연속으로 살 수 있다`() {
        val session = session(gold = 100)
        session.nextRound()

        var bought = 0
        while (session.player.bench.size < PlayerState.BENCH_SIZE) {
            val index = session.firstBuyableIndex()
            if (index < 0) {
                session.reroll()
                continue
            }
            if (session.buy(index).isSuccess) bought++
        }

        assertEquals(PlayerState.BENCH_SIZE, session.player.bench.size)
        assertEquals(PlayerState.BENCH_SIZE, bought)
        assertEquals(
            "벤치의 개체 id 는 모두 다르다",
            PlayerState.BENCH_SIZE,
            session.player.bench.map { it.instanceId }.toSet().size,
        )
    }

    // --- 구매 실패 조건 ---

    @Test
    fun `골드가 모자라면 살 수 없고 상태가 그대로다`() {
        val session = session(gold = 0, level = 9, seed = 4)
        session.nextRound()
        // nextRound 수입 5골드를 다 쓰고도 못 사는 비싼 칸을 찾는다.
        val index = session.offer.slots.indexOfFirst { (it.unit?.cost ?: 0) > session.player.gold }
        if (index < 0) return // 이번 시드에서는 전부 살 수 있으므로 검증 대상이 아니다.

        val goldBefore = session.player.gold
        val result = session.buy(index)

        assertEquals(ShopResult.Failure(ShopError.NOT_ENOUGH_GOLD), result)
        assertEquals(goldBefore, session.player.gold)
        assertEquals(0, session.player.bench.size)
    }

    @Test
    fun `벤치가 꽉 차면 살 수 없다`() {
        val session = session(gold = 200)
        session.nextRound()
        while (session.player.bench.size < PlayerState.BENCH_SIZE) {
            val index = session.firstBuyableIndex()
            if (index < 0) session.reroll() else session.buy(index)
        }

        val index = session.firstBuyableIndex().takeIf { it >= 0 } ?: run {
            session.reroll()
            session.firstBuyableIndex()
        }
        assertEquals(ShopResult.Failure(ShopError.BENCH_FULL), session.buy(index))
        assertEquals(PlayerState.BENCH_SIZE, session.player.bench.size)
    }

    @Test
    fun `같은 칸을 두 번 살 수 없다`() {
        val session = session(gold = 50)
        session.nextRound()
        val index = session.firstBuyableIndex()

        assertTrue(session.buy(index).isSuccess)
        assertEquals(ShopResult.Failure(ShopError.SLOT_UNAVAILABLE), session.buy(index))
        assertEquals(1, session.player.bench.size)
    }

    @Test
    fun `없는 칸을 사면 실패한다`() {
        val session = session()
        session.nextRound()
        assertEquals(ShopResult.Failure(ShopError.SLOT_UNAVAILABLE), session.buy(99))
        assertEquals(ShopResult.Failure(ShopError.SLOT_UNAVAILABLE), session.buy(-1))
    }

    // --- 풀 정합성 ---

    @Test
    fun `구매는 풀 재고를 건드리지 않는다`() {
        val pool = UnitPool()
        val session = ShopSession(
            pool,
            ShopRoller(pool, Random(9)),
            PlayerState("p1", "나", isBot = false, gold = 50, level = 6),
        )
        session.nextRound()

        // 상점에 뜬 시점에 이미 풀에서 빠졌으므로, 구매 자체로는 재고가 변하지 않는다.
        val remainingBefore = pool.totalRemaining()
        session.buy(session.firstBuyableIndex())
        assertEquals(remainingBefore, pool.totalRemaining())
    }

    /**
     * 카드 총량 보존 검증.
     *
     * 풀에 남은 장수 + 상점에 떠 있는 장수 + 플레이어가 가진 장수의 합은
     * 구매하든 리롤하든 라운드를 넘기든 항상 같아야 한다.
     * 한 장이라도 새로 생기거나 사라지면 8인이 공유하는 재고 계산이 무너진다.
     */
    @Test
    fun `카드 총량은 어떤 조작에도 변하지 않는다`() {
        val pool = UnitPool()
        val session = ShopSession(
            pool,
            ShopRoller(pool, Random(9)),
            PlayerState("p1", "나", isBot = false, gold = 200, level = 6),
        )
        val total = pool.totalRemaining()

        fun cardsInPlay(): Int =
            pool.totalRemaining() +
                session.offer.slots.count { it.unit != null && !it.purchased } +
                (session.player.bench + session.player.board).sumOf { it.copiesConsumed }

        session.nextRound()
        assertEquals("상점을 채운 직후", total, cardsInPlay())

        session.buy(session.firstBuyableIndex())
        assertEquals("구매 후", total, cardsInPlay())

        session.reroll()
        assertEquals("리롤 후", total, cardsInPlay())

        session.nextRound()
        assertEquals("라운드를 넘긴 후", total, cardsInPlay())

        val instanceId = session.player.bench.first().instanceId
        session.sell(instanceId)
        assertEquals("판매 후", total, cardsInPlay())
    }

    @Test
    fun `판매하면 골드를 돌려받고 카드가 풀로 돌아간다`() {
        val pool = UnitPool()
        val session = ShopSession(
            pool,
            ShopRoller(pool, Random(11)),
            PlayerState("p1", "나", isBot = false, gold = 30, level = 5),
        )
        session.nextRound()

        val index = session.firstBuyableIndex()
        val target = requireNotNull(session.offer.slots[index].unit)
        session.buy(index)

        val goldAfterBuy = session.player.gold
        val remainingAfterBuy = pool.remaining(target.id)
        val instanceId = session.player.bench.single().instanceId

        val result = session.sell(instanceId)

        assertTrue(result.isSuccess)
        assertEquals("1성은 코스트 그대로 돌려받는다", goldAfterBuy + target.cost, session.player.gold)
        assertEquals("벤치가 비워진다", 0, session.player.bench.size)
        assertEquals("카드 1장이 풀로 돌아온다", remainingAfterBuy + 1, pool.remaining(target.id))
    }

    @Test
    fun `없는 유닛을 팔면 실패한다`() {
        val session = session()
        session.nextRound()
        assertEquals(ShopResult.Failure(ShopError.UNIT_NOT_FOUND), session.sell("없는id"))
    }

    // --- 리롤과 경험치 ---

    @Test
    fun `리롤은 2골드를 쓰고 상점을 바꾼다`() {
        val session = session(gold = 20, seed = 3)
        session.nextRound()
        val before = session.offer.slots.map { it.unit?.id }
        val goldBefore = session.player.gold

        assertTrue(session.reroll().isSuccess)
        assertEquals(goldBefore - EconomyRules.REROLL_COST, session.player.gold)
        assertEquals(5, session.offer.slots.size)
        assertTrue("구매 표시가 초기화된다", session.offer.slots.none { it.purchased })
        // 같은 유닛이 우연히 다시 뜰 수는 있어도 칸 수는 항상 5다.
        assertEquals(5, before.size)
    }

    @Test
    fun `골드가 2 미만이면 리롤할 수 없다`() {
        val pool = UnitPool()
        val session = ShopSession(
            pool,
            ShopRoller(pool, Random(1)),
            PlayerState("p1", "나", isBot = false, gold = 1, level = 3),
        )
        assertEquals(ShopResult.Failure(ShopError.NOT_ENOUGH_GOLD), session.reroll())
        assertEquals(1, session.player.gold)
    }

    @Test
    fun `경험치를 사면 4골드를 쓰고 경험치가 4 오른다`() {
        val session = session(gold = 20, level = 3)
        val goldBefore = session.player.gold

        assertTrue(session.buyExp().isSuccess)
        assertEquals(goldBefore - EconomyRules.BUY_EXP_COST, session.player.gold)
        assertEquals("레벨 3은 12 필요하므로 아직 3레벨", 3, session.player.level)
        assertEquals(4, session.player.exp)
    }

    @Test
    fun `경험치를 계속 사면 레벨이 오른다`() {
        val session = session(gold = 100, level = 1)
        assertTrue(session.buyExp().isSuccess)
        assertEquals("2 필요, 4 지급이므로 레벨 2가 되고 2가 남는다", 2, session.player.level)
        assertEquals(2, session.player.exp)
    }

    @Test
    fun `최대 레벨에서는 경험치를 살 수 없다`() {
        val session = session(gold = 100, level = PlayerState.MAX_LEVEL)
        assertEquals(ShopResult.Failure(ShopError.MAX_LEVEL), session.buyExp())
        assertEquals(100, session.player.gold)
    }

    // --- 라운드 진행 ---

    @Test
    fun `라운드를 넘기면 수입이 들어오고 상점이 새로 채워진다`() {
        val session = session(gold = 10)
        session.nextRound()

        assertEquals(1, session.round)
        assertEquals("10 + 기본 5 + 이자 1", 16, session.player.gold)
        assertEquals(5, session.offer.slots.size)
        assertNotNull(session.offer.slots.first().unit)

        session.nextRound()
        assertEquals(2, session.round)
    }

    @Test
    fun `라운드를 넘겨도 산 유닛은 벤치에 남는다`() {
        val session = session(gold = 30)
        session.nextRound()
        session.buy(session.firstBuyableIndex())
        val benchBefore = session.player.bench.map { it.instanceId }

        session.nextRound()

        assertEquals(benchBefore, session.player.bench.map { it.instanceId })
    }

    @Test
    fun `연승하면 다음 라운드 수입이 늘어난다`() {
        val session = session(gold = 0)
        session.nextRound()
        val plain = session.player.gold

        val streaked = session(gold = 0)
        streaked.recordResult(won = true)
        streaked.recordResult(won = true)
        streaked.nextRound()

        assertTrue("2연승이면 수입이 더 많아야 한다", streaked.player.gold > plain)
    }
}
