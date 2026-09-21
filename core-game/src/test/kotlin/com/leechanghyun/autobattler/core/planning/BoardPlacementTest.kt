package com.leechanghyun.autobattler.core.planning

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.board.OffsetCoord
import com.leechanghyun.autobattler.core.economy.ShopRoller
import com.leechanghyun.autobattler.core.economy.UnitPool
import com.leechanghyun.autobattler.core.model.HexCoord
import com.leechanghyun.autobattler.core.model.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 로드맵 3단계 완료 기준 검증: **유닛을 헥스 보드에 배치하고 회수할 수 있다.**
 */
class BoardPlacementTest {

    private val cellA = HexBoard.fromOffset(OffsetCoord(0, 0))
    private val cellB = HexBoard.fromOffset(OffsetCoord(1, 3))
    private val cellC = HexBoard.fromOffset(OffsetCoord(3, 6))

    /** 벤치에 [count] 기를 올린 세션을 만든다. 보드 정원은 레벨과 같으므로 [level] 로 조절한다. */
    private fun sessionWithBench(count: Int, level: Int = 9): PlanningSession {
        val pool = UnitPool()
        val session = PlanningSession(
            pool = pool,
            roller = ShopRoller(pool, Random(5)),
            initialPlayer = PlayerState("p1", "나", isBot = false, gold = 500, level = level),
        )
        session.nextRound()
        while (session.player.bench.size < count) {
            val index = session.offer.slots.indexOfFirst { it.unit != null && !it.purchased }
            if (index < 0) session.reroll() else session.buy(index)
        }
        return session
    }

    private val PlanningSession.benchIds: List<String> get() = player.bench.map { it.instanceId }

    // --- 완료 기준 ---

    @Test
    fun `벤치의 유닛을 보드에 올릴 수 있다`() {
        val session = sessionWithBench(1)
        val id = session.benchIds.single()

        val result = session.moveToBoard(id, cellA)

        assertTrue("배치가 성공해야 한다", result.isSuccess)
        assertEquals("벤치가 비워진다", 0, session.player.bench.size)
        assertEquals("보드에 1기가 올라간다", 1, session.player.board.size)
        assertEquals("좌표가 기록된다", cellA, session.player.board.single().position)
        assertEquals("좌표로 찾을 수 있다", id, session.unitAt(cellA)?.instanceId)
        assertTrue(session.player.board.single().isOnBoard)
    }

    @Test
    fun `보드의 유닛을 벤치로 회수할 수 있다`() {
        val session = sessionWithBench(1)
        val id = session.benchIds.single()
        session.moveToBoard(id, cellA)

        val result = session.returnToBench(id)

        assertTrue("회수가 성공해야 한다", result.isSuccess)
        assertEquals("보드가 비워진다", 0, session.player.board.size)
        assertEquals("벤치로 돌아온다", 1, session.player.bench.size)
        assertNull("좌표가 지워진다", session.player.bench.single().position)
        assertNull("그 칸은 빈 칸이 된다", session.unitAt(cellA))
    }

    @Test
    fun `올렸다 내렸다를 반복해도 유닛이 사라지지 않는다`() {
        val session = sessionWithBench(3)
        val ids = session.benchIds

        repeat(5) {
            ids.forEachIndexed { i, id -> session.moveToBoard(id, HexBoard.coords[i]) }
            assertEquals(3, session.player.board.size)
            assertEquals(0, session.player.bench.size)

            ids.forEach { session.returnToBench(it) }
            assertEquals(0, session.player.board.size)
            assertEquals(3, session.player.bench.size)
        }
        assertEquals("개체 id 가 그대로다", ids.toSet(), session.benchIds.toSet())
    }

    // --- 이동과 교체 ---

    @Test
    fun `보드 안에서 빈 칸으로 옮길 수 있다`() {
        val session = sessionWithBench(1)
        val id = session.benchIds.single()
        session.moveToBoard(id, cellA)

        assertTrue(session.moveToBoard(id, cellC).isSuccess)
        assertEquals(1, session.player.board.size)
        assertEquals(cellC, session.player.board.single().position)
        assertNull(session.unitAt(cellA))
    }

    @Test
    fun `이미 있는 칸으로 옮기면 두 유닛이 자리를 맞바꾼다`() {
        val session = sessionWithBench(2)
        val (first, second) = session.benchIds
        session.moveToBoard(first, cellA)
        session.moveToBoard(second, cellB)

        assertTrue(session.moveToBoard(first, cellB).isSuccess)

        assertEquals("둘 다 보드에 남는다", 2, session.player.board.size)
        assertEquals(first, session.unitAt(cellB)?.instanceId)
        assertEquals(second, session.unitAt(cellA)?.instanceId)
    }

    @Test
    fun `벤치 유닛을 보드 유닛과 맞바꿀 수 있다`() {
        // 보드 정원이 1인 레벨 1에서도 교체는 가능해야 한다.
        val session = sessionWithBench(2, level = 1)
        val (onBoard, onBench) = session.benchIds
        assertTrue(session.moveToBoard(onBoard, cellA).isSuccess)

        assertTrue("정원이 차 있어도 교체는 된다", session.moveToBoard(onBench, cellA).isSuccess)

        assertEquals(1, session.player.board.size)
        assertEquals(1, session.player.bench.size)
        assertEquals(onBench, session.unitAt(cellA)?.instanceId)
        assertEquals(onBoard, session.player.bench.single().instanceId)
        assertNull("벤치로 내려간 유닛의 좌표는 지워진다", session.player.bench.single().position)
    }

    @Test
    fun `자기 자리로 옮기면 아무 일도 일어나지 않는다`() {
        val session = sessionWithBench(1)
        val id = session.benchIds.single()
        session.moveToBoard(id, cellA)

        assertTrue(session.moveToBoard(id, cellA).isSuccess)
        assertEquals(1, session.player.board.size)
        assertEquals(cellA, session.player.board.single().position)
    }

    // --- 실패 조건 ---

    @Test
    fun `보드 정원은 레벨과 같다`() {
        val session = sessionWithBench(4, level = 3)
        val ids = session.benchIds

        assertEquals(3, session.player.boardCapacity)
        assertTrue(session.moveToBoard(ids[0], HexBoard.coords[0]).isSuccess)
        assertTrue(session.moveToBoard(ids[1], HexBoard.coords[1]).isSuccess)
        assertTrue(session.moveToBoard(ids[2], HexBoard.coords[2]).isSuccess)

        assertEquals(
            PlanningResult.Failure(PlanningError.BOARD_FULL),
            session.moveToBoard(ids[3], HexBoard.coords[3]),
        )
        assertEquals("정원을 넘지 않는다", 3, session.player.board.size)
        assertEquals("실패한 유닛은 벤치에 남는다", 1, session.player.bench.size)
    }

    @Test
    fun `보드 밖 좌표에는 올릴 수 없다`() {
        val session = sessionWithBench(1)
        val id = session.benchIds.single()

        assertEquals(
            PlanningResult.Failure(PlanningError.INVALID_COORD),
            session.moveToBoard(id, HexCoord(99, 99)),
        )
        assertEquals("벤치에 그대로 있다", 1, session.player.bench.size)
        assertEquals(0, session.player.board.size)
    }

    @Test
    fun `없는 유닛은 올리거나 내릴 수 없다`() {
        val session = sessionWithBench(1)
        assertEquals(
            PlanningResult.Failure(PlanningError.UNIT_NOT_FOUND),
            session.moveToBoard("없는id", cellA),
        )
        assertEquals(
            PlanningResult.Failure(PlanningError.UNIT_NOT_FOUND),
            session.returnToBench("없는id"),
        )
    }

    @Test
    fun `벤치에 있는 유닛은 회수 대상이 아니다`() {
        val session = sessionWithBench(1)
        val id = session.benchIds.single()
        assertEquals(
            PlanningResult.Failure(PlanningError.UNIT_NOT_FOUND),
            session.returnToBench(id),
        )
    }

    @Test
    fun `벤치가 꽉 차면 회수할 수 없다`() {
        val session = sessionWithBench(PlayerState.BENCH_SIZE)
        val id = session.benchIds.first()
        session.moveToBoard(id, cellA)

        // 빈 자리가 생겼으니 한 기를 더 사서 벤치를 다시 채운다.
        while (session.player.bench.size < PlayerState.BENCH_SIZE) {
            val index = session.offer.slots.indexOfFirst { it.unit != null && !it.purchased }
            if (index < 0) session.reroll() else session.buy(index)
        }

        assertEquals(
            PlanningResult.Failure(PlanningError.BENCH_FULL),
            session.returnToBench(id),
        )
        assertEquals("보드에 그대로 있다", 1, session.player.board.size)
    }

    // --- 다른 기능과의 관계 ---

    @Test
    fun `보드의 유닛도 팔 수 있고 카드가 풀로 돌아간다`() {
        val pool = UnitPool()
        val session = PlanningSession(
            pool,
            ShopRoller(pool, Random(5)),
            PlayerState("p1", "나", isBot = false, gold = 100, level = 9),
        )
        session.nextRound()
        session.buy(session.offer.slots.indexOfFirst { it.unit != null })
        val unit = session.player.bench.single()
        session.moveToBoard(unit.instanceId, cellA)

        val remainingBefore = pool.remaining(unit.unitDef.id)
        assertTrue(session.sell(unit.instanceId).isSuccess)

        assertEquals("보드에서 사라진다", 0, session.player.board.size)
        assertEquals("카드가 풀로 돌아간다", remainingBefore + 1, pool.remaining(unit.unitDef.id))
    }

    @Test
    fun `라운드를 넘겨도 배치가 유지된다`() {
        val session = sessionWithBench(2)
        val (first, second) = session.benchIds
        session.moveToBoard(first, cellA)
        session.moveToBoard(second, cellB)

        session.nextRound()

        assertEquals(2, session.player.board.size)
        assertEquals(first, session.unitAt(cellA)?.instanceId)
        assertEquals(second, session.unitAt(cellB)?.instanceId)
    }

    @Test
    fun `보드에 두 유닛이 같은 칸을 차지하지 않는다`() {
        val session = sessionWithBench(5)
        val ids = session.benchIds

        // 모든 유닛을 같은 칸으로 반복해서 밀어 넣어도 좌표는 항상 서로 다르다.
        repeat(3) {
            ids.forEach { session.moveToBoard(it, cellB) }
            val positions = session.player.board.mapNotNull { it.position }
            assertEquals("좌표 중복 없음", positions.size, positions.toSet().size)
        }
    }
}
