package com.leechanghyun.autobattler.core.synergy

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.board.OffsetCoord
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.masterdata.traitId
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.UnitClass
import org.junit.Assert.assertEquals
import org.junit.Test

/** 시너지 인원 집계 규칙을 고정한다. 명세서 4-4. */
class TraitTallyTest {

    private val steelGuard = MasterData.unit("steel_guard") // 기계공학자 / 수호자

    @Test
    fun `같은 유닛을 여러 장 올려도 한 명으로 센다`() {
        // 명세서가 침묵하는 부분이라 코드가 유일한 진실이다. 같은 유닛을 두 장 사기 전까지는
        // 보이지 않는 규칙이므로 반드시 테스트로 못박는다.
        val board = (0 until 3).map { index ->
            BoardUnit(
                instanceId = "dup$index",
                unitDef = steelGuard,
                position = HexBoard.fromOffset(OffsetCoord(row = 3, col = index)),
            )
        }
        val tally = TraitTally.of(board)

        assertEquals(1, tally.count(Origin.MECHA.traitId))
        assertEquals(1, tally.count(UnitClass.WARDEN.traitId))
    }

    @Test
    fun `벤치에 있는 유닛은 세지 않는다`() {
        val defs = SynergyFixtures.defsOf(UnitClass.WARDEN, 2)
        assertEquals(TraitTally.EMPTY, TraitTally.of(SynergyFixtures.bench(defs)))

        val mixed = SynergyFixtures.board(defs.take(1)) + SynergyFixtures.bench(defs.drop(1))
        assertEquals("올라간 한 명만 센다", 1, TraitTally.of(mixed).count(UnitClass.WARDEN.traitId))
    }

    @Test
    fun `빈 보드는 아무 시너지도 세지 않는다`() {
        assertEquals(TraitTally.EMPTY, TraitTally.of(emptyList()))
        assertEquals(0, TraitTally.of(emptyList()).count(Origin.MECHA.traitId))
    }

    @Test
    fun `유닛 하나가 계열과 직업을 동시에 올린다`() {
        val tally = TraitTally.of(SynergyFixtures.board(listOf(steelGuard)))
        assertEquals(1, tally.count(Origin.MECHA.traitId))
        assertEquals(1, tally.count(UnitClass.WARDEN.traitId))
        assertEquals("다른 계열은 0", 0, tally.count(Origin.ABYSSAL.traitId))
    }

    @Test
    fun `서로 다른 유닛은 각각 센다`() {
        val tally = TraitTally.of(SynergyFixtures.board(SynergyFixtures.defsOf(Origin.MECHA, 6)))
        assertEquals(6, tally.count(Origin.MECHA.traitId))
    }
}
