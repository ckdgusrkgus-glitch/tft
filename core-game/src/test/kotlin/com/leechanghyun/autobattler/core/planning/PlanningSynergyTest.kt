package com.leechanghyun.autobattler.core.planning

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.board.OffsetCoord
import com.leechanghyun.autobattler.core.economy.ShopRoller
import com.leechanghyun.autobattler.core.economy.UnitPool
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.model.UnitClass
import com.leechanghyun.autobattler.core.model.UnitDef
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * 로드맵 5단계 완료 기준을 준비 단계에서 문자 그대로 검증한다:
 * **보드 구성을 바꾸면 시너지가 따라 움직인다.**
 *
 * 보드 구성을 실제로 바꿀 수 있는 경로를 전부 덮는다.
 */
class PlanningSynergyTest {

    private val wardens: List<UnitDef> = MasterData.units.filter { it.unitClass == UnitClass.WARDEN }.take(2)
    private val wardenId = "warden"

    private fun session(bench: List<BoardUnit>): PlanningSession {
        val pool = UnitPool()
        return PlanningSession(
            pool = pool,
            roller = ShopRoller(pool, Random(1)),
            initialPlayer = PlayerState(
                playerId = "p1",
                displayName = "나",
                isBot = false,
                gold = 50,
                level = 9,
                bench = bench,
            ),
        )
    }

    private fun benchOf(defs: List<UnitDef>) =
        defs.mapIndexed { index, def -> BoardUnit(instanceId = "u$index", unitDef = def) }

    private fun coord(col: Int) = HexBoard.fromOffset(OffsetCoord(row = 3, col = col))

    @Test
    fun `유닛을 올리고 내리면 시너지 단계가 따라 움직인다`() {
        val session = session(benchOf(wardens))
        assertEquals("벤치에만 있으면 발동하지 않는다", 0, session.synergy.tierOf(wardenId))

        session.moveToBoard("u0", coord(0))
        assertEquals(1, session.synergy.memberCountOf(wardenId))
        assertEquals("한 명으로는 미달", 0, session.synergy.tierOf(wardenId))

        session.moveToBoard("u1", coord(1))
        assertEquals("두 명이면 1단계", 1, session.synergy.tierOf(wardenId))

        session.returnToBench("u1")
        assertEquals("내리면 다시 미달", 0, session.synergy.tierOf(wardenId))

        session.moveToBoard("u1", coord(1))
        assertEquals(1, session.synergy.tierOf(wardenId))

        session.sell("u1")
        assertEquals("팔아도 내려간다", 0, session.synergy.tierOf(wardenId))
    }

    @Test
    fun `보드 안에서 자리만 바꾸면 시너지는 그대로다`() {
        val session = session(benchOf(wardens))
        session.moveToBoard("u0", coord(0))
        session.moveToBoard("u1", coord(1))
        assertEquals(1, session.synergy.tierOf(wardenId))

        session.moveToBoard("u0", coord(1)) // 두 유닛 자리 맞교환
        assertEquals("구성이 그대로라 단계도 그대로다", 1, session.synergy.tierOf(wardenId))

        session.moveToBoard("u0", coord(5)) // 빈 칸으로 이동
        assertEquals(1, session.synergy.tierOf(wardenId))
    }

    @Test
    fun `같은 유닛을 두 장 올려도 시너지가 오르지 않는다`() {
        // 종 기준 집계 규칙이 플레이어에게 드러나는 유일한 순간이다.
        val session = session(benchOf(listOf(wardens[0], wardens[0])))
        session.moveToBoard("u0", coord(0))
        session.moveToBoard("u1", coord(1))

        assertEquals(2, session.player.board.size)
        assertEquals("같은 유닛 두 장은 한 명이다", 1, session.synergy.memberCountOf(wardenId))
        assertEquals(0, session.synergy.tierOf(wardenId))
    }

    @Test
    fun `시너지 상태는 화면이 바로 그릴 수 있는 값을 준다`() {
        val session = session(benchOf(wardens))
        session.moveToBoard("u0", coord(0))

        val warden = requireNotNull(session.synergy.traitOf(wardenId))
        assertEquals(MasterData.trait(wardenId).name, warden.name)
        assertEquals(1, warden.memberCount)
        assertEquals(2, warden.nextThreshold)
    }
}
