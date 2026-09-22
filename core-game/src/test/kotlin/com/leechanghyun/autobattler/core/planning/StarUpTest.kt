package com.leechanghyun.autobattler.core.planning

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.board.OffsetCoord
import com.leechanghyun.autobattler.core.economy.ShopRoller
import com.leechanghyun.autobattler.core.economy.UnitPool
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.model.UnitDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 로드맵 6단계 완료 기준 검증: **동일 유닛 3개가 자동 합성된다.** 명세서 4-3.
 *
 * [StarUp] 은 순수 함수라 [PlayerState] 를 직접 만들어 넣는다. 상점을 거치지 않으므로
 * 어떤 유닛이 몇 개 있는지 정확히 통제할 수 있다.
 */
class StarUpTest {

    private val alpha: UnitDef = MasterData.units[0]
    private val beta: UnitDef = MasterData.units[1]

    private fun coord(index: Int) = HexBoard.fromOffset(OffsetCoord(row = 3, col = index))

    private fun unit(
        id: String,
        def: UnitDef = alpha,
        starLevel: Int = 1,
        onBoardAt: Int? = null,
    ) = BoardUnit(
        instanceId = id,
        unitDef = def,
        starLevel = starLevel,
        position = onBoardAt?.let { coord(it) },
    )

    private fun player(bench: List<BoardUnit> = emptyList(), board: List<BoardUnit> = emptyList()) =
        PlayerState(playerId = "p1", displayName = "나", isBot = false, level = 9, bench = bench, board = board)

    // --- 완료 기준 ---

    @Test
    fun `같은 유닛 3개는 2성 하나로 합쳐진다`() {
        val before = player(bench = listOf(unit("a"), unit("b"), unit("c")))

        val after = StarUp.apply(before)

        assertTrue("합성이 일어났다", after.merged)
        val survivor = after.player.bench.single()
        assertEquals("2성이 된다", 2, survivor.starLevel)
        assertEquals("같은 유닛이다", alpha.id, survivor.unitDef.id)
        assertEquals("보드는 그대로 비어 있다", 0, after.player.board.size)
    }

    @Test
    fun `두 개까지는 합성되지 않는다`() {
        val before = player(bench = listOf(unit("a"), unit("b")))

        val after = StarUp.apply(before)

        assertFalse(after.merged)
        assertEquals("상태가 그대로다", before, after.player)
    }

    @Test
    fun `다른 유닛끼리는 합성되지 않는다`() {
        val before = player(bench = listOf(unit("a", alpha), unit("b", beta), unit("c", alpha)))

        val after = StarUp.apply(before)

        assertFalse(after.merged)
        assertEquals(3, after.player.bench.size)
    }

    @Test
    fun `성 등급이 다르면 합성되지 않는다`() {
        val before = player(bench = listOf(unit("a", starLevel = 1), unit("b", starLevel = 2), unit("c", starLevel = 1)))

        val after = StarUp.apply(before)

        assertFalse(after.merged)
    }

    // --- 연쇄 합성 ---

    @Test
    fun `1성 9개는 3성 하나가 된다`() {
        val before = player(bench = (0 until 9).map { unit("u$it") })

        val after = StarUp.apply(before)

        val survivor = after.player.bench.single()
        assertEquals("3성이 된다", 3, survivor.starLevel)
        assertEquals(
            "2성 세 번 + 3성 한 번, 총 네 번 합성된다",
            listOf(1 to 2, 1 to 2, 1 to 2, 2 to 3),
            after.events.map { it.fromStar to it.toStar },
        )
    }

    @Test
    fun `3성은 더 이상 올라가지 않는다`() {
        val before = player(bench = (0 until 3).map { unit("u$it", starLevel = 3) })

        val after = StarUp.apply(before)

        assertFalse("최대 성이라 합성 대상이 아니다", after.merged)
        assertEquals(3, after.player.bench.size)
    }

    @Test
    fun `네 개가 있으면 셋만 먹고 하나는 남는다`() {
        val before = player(bench = (0 until 4).map { unit("u$it") })

        val after = StarUp.apply(before)

        assertEquals(2, after.player.bench.size)
        assertEquals(
            "2성 하나와 1성 하나가 남는다",
            listOf(1, 2),
            after.player.bench.map { it.starLevel }.sorted(),
        )
    }

    // --- 벤치와 보드를 함께 본다 ---

    @Test
    fun `벤치 둘과 보드 하나도 합성된다`() {
        val before = player(
            bench = listOf(unit("a"), unit("b")),
            board = listOf(unit("c", onBoardAt = 2)),
        )

        val after = StarUp.apply(before)

        assertTrue(after.merged)
        assertEquals("벤치가 비워진다", 0, after.player.bench.size)
        val survivor = after.player.board.single()
        assertEquals("보드에 2성으로 남는다", 2, survivor.starLevel)
        assertEquals("보드에 있던 개체가 살아남아 자리를 지킨다", "c", survivor.instanceId)
        assertEquals("좌표도 그대로다", coord(2), survivor.position)
    }

    @Test
    fun `셋 다 벤치면 결과도 벤치에 남는다`() {
        val before = player(bench = listOf(unit("a"), unit("b"), unit("c")))

        val after = StarUp.apply(before)

        assertEquals("a", after.player.bench.single().instanceId)
        assertNull("벤치에 있으므로 좌표가 없다", after.player.bench.single().position)
    }

    @Test
    fun `보드 유닛 둘이 함께 먹히면 보드 인원이 하나 준다`() {
        val before = player(
            bench = listOf(unit("a")),
            board = listOf(unit("b", onBoardAt = 1), unit("c", onBoardAt = 2)),
        )

        val after = StarUp.apply(before)

        assertEquals("보드 인원이 2에서 1로 준다", 1, after.player.board.size)
        assertEquals("정원을 넘기는 일은 생기지 않는다", 0, after.player.bench.size)
        assertEquals("보드의 첫 유닛이 살아남는다", "b", after.player.board.single().instanceId)
        assertEquals(coord(1), after.player.board.single().position)
    }

    /**
     * 벤치를 먼저 소모한다. 보드를 먼저 먹으면 배치해 둔 진형이 임의로 무너진다.
     *
     * 벤치 3개와 보드 1개가 있으면 벤치 셋만 먹고 보드 유닛은 건드리지 않아야 한다.
     */
    @Test
    fun `벤치를 먼저 소모해 보드 진형을 지킨다`() {
        val before = player(
            bench = listOf(unit("a"), unit("b"), unit("c")),
            board = listOf(unit("d", onBoardAt = 3)),
        )

        val after = StarUp.apply(before)

        assertEquals("보드 유닛은 그대로 1성으로 남는다", 1, after.player.board.single().starLevel)
        assertEquals("d", after.player.board.single().instanceId)
        assertEquals("벤치에 2성 하나가 생긴다", 2, after.player.bench.single().starLevel)
        assertEquals(
            "소모된 것은 벤치 셋뿐이다",
            listOf("a", "b", "c"),
            after.events.single().consumedInstanceIds,
        )
    }

    // --- 풀 장수 보존 ---

    @Test
    fun `합성해도 소모한 카드 장수 총합은 변하지 않는다`() {
        val before = player(bench = (0 until 9).map { unit("u$it") })
        val cardsBefore = (before.bench + before.board).sumOf { it.copiesConsumed }

        val after = StarUp.apply(before)
        val cardsAfter = (after.player.bench + after.player.board).sumOf { it.copiesConsumed }

        assertEquals("1성 9장 = 3성 1개", 9, cardsBefore)
        assertEquals(cardsBefore, cardsAfter)
    }

    // --- 이벤트 ---

    @Test
    fun `합성 기록이 무엇을 먹고 무엇이 남았는지 말해 준다`() {
        val before = player(bench = listOf(unit("a"), unit("b"), unit("c")))

        val event = StarUp.apply(before).events.single()

        assertEquals(alpha.id, event.unitDefId)
        assertEquals(1, event.fromStar)
        assertEquals(2, event.toStar)
        assertEquals("a", event.resultInstanceId)
        assertEquals(listOf("a", "b", "c"), event.consumedInstanceIds)
    }

    @Test
    fun `연쇄 합성을 거쳐도 산 유닛이 무엇이 되었는지 따라갈 수 있다`() {
        val before = player(bench = (0 until 9).map { unit("u$it") })

        val after = StarUp.apply(before)

        val finalId = after.player.bench.single().instanceId
        (0 until 9).forEach { index ->
            assertEquals(
                "u$index 는 최종 3성으로 이어진다",
                finalId,
                after.trace("u$index"),
            )
        }
    }

    @Test
    fun `합성에 끼지 않은 개체의 id 는 그대로다`() {
        val before = player(bench = listOf(unit("a"), unit("b"), unit("c"), unit("x", beta)))

        val after = StarUp.apply(before)

        assertEquals("x", after.trace("x"))
    }

    // --- 상점 구매 경로 전체 ---

    /**
     * 6단계 완료 기준을 **실제 구매 경로로** 확인한다.
     *
     * [StarUp] 단독 테스트는 엔진이 옳은지만 말한다. 이 테스트는 `PlanningSession.buy` 가 그 엔진을
     * 실제로 부르는지를 본다. 둘을 잇는 한 줄을 지워도 앞의 테스트들은 전부 초록색이기 때문이다.
     *
     * 풀에 1코스트 유닛 한 종만 넣어 상점이 그 유닛만 내놓게 만든다. 난수 시드에 기대지 않는다.
     */
    @Test
    fun `같은 유닛을 상점에서 세 번 사면 그 자리에서 2성이 된다`() {
        val only = MasterData.units.first { it.cost == 1 }
        val pool = UnitPool(units = listOf(only))
        val session = PlanningSession(
            pool = pool,
            roller = ShopRoller(pool, Random(1)),
            initialPlayer = PlayerState("p1", "나", isBot = false, gold = 50, level = 5),
        )
        session.nextRound()

        val results = (0 until 3).map { time ->
            val index = session.offer.slots.indexOfFirst { it.unit != null && !it.purchased }
            if (index < 0) {
                session.reroll()
                session.buy(session.offer.slots.indexOfFirst { it.unit != null && !it.purchased })
            } else {
                session.buy(index)
            }.also { assertTrue("${time + 1}번째 구매가 성공해야 한다", it.isSuccess) }
        }

        assertEquals("벤치에 2성 하나만 남는다", 1, session.player.bench.size)
        assertEquals(2, session.player.bench.single().starLevel)

        val last = results.last() as PlanningResult.Success
        assertEquals("세 번째 구매가 합성을 일으킨다", 1, last.starUps.size)
        assertEquals(
            "앞의 두 번은 합성이 없다",
            listOf(0, 0),
            results.dropLast(1).map { (it as PlanningResult.Success).starUps.size },
        )
        assertEquals(
            "돌려주는 유닛은 사라진 1성이 아니라 완성된 2성이다",
            session.player.bench.single().instanceId,
            last.unit?.instanceId,
        )
        assertEquals(2, last.unit?.starLevel)
    }

    @Test
    fun `상점 구매로 합성돼도 카드 총량은 그대로다`() {
        val only = MasterData.units.first { it.cost == 1 }
        val pool = UnitPool(units = listOf(only))
        val session = PlanningSession(
            pool = pool,
            roller = ShopRoller(pool, Random(3)),
            initialPlayer = PlayerState("p1", "나", isBot = false, gold = 50, level = 5),
        )
        val total = pool.totalRemaining()
        session.nextRound()

        fun cardsInPlay(): Int =
            pool.totalRemaining() +
                session.offer.slots.count { it.unit != null && !it.purchased } +
                (session.player.bench + session.player.board).sumOf { it.copiesConsumed }

        repeat(3) {
            val index = session.offer.slots.indexOfFirst { it.unit != null && !it.purchased }
            if (index < 0) session.reroll() else session.buy(index)
        }

        assertEquals("2성이 만들어졌다", 2, session.player.bench.single().starLevel)
        assertEquals("합성은 카드를 만들지도 없애지도 않는다", total, cardsInPlay())
    }

    // --- 결정론 ---

    @Test
    fun `같은 입력이면 항상 같은 결과다`() {
        val before = player(
            bench = listOf(unit("a"), unit("b", beta), unit("c"), unit("d", beta), unit("e"), unit("f", beta)),
            board = listOf(unit("g", onBoardAt = 0)),
        )

        val first = StarUp.apply(before)
        val second = StarUp.apply(before)

        assertEquals(first.player, second.player)
        assertEquals(first.events, second.events)
    }
}
