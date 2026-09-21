package com.leechanghyun.autobattler.core.combat

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.board.HexGrid
import com.leechanghyun.autobattler.core.board.OffsetCoord
import com.leechanghyun.autobattler.core.model.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 배치 보드 두 개를 맞붙인 전장의 좌표 변환을 검증한다. 명세서 4-6. */
class CombatFieldTest {

    @Test
    fun `전장은 배치 보드 두 개를 위아래로 붙인 크기다`() {
        assertEquals(HexBoard.COLUMNS, CombatField.COLUMNS)
        assertEquals(HexBoard.ROWS * 2, CombatField.ROWS)
        assertEquals(56, CombatField.coords.size)
    }

    @Test
    fun `두 진영의 칸은 겹치지 않고 합치면 전장 전체다`() {
        val player = CombatField.halfFor(CombatTeam.PLAYER).toSet()
        val enemy = CombatField.halfFor(CombatTeam.ENEMY).toSet()

        assertEquals("플레이어 진영은 28칸", 28, player.size)
        assertEquals("적 진영은 28칸", 28, enemy.size)
        assertEquals("겹치는 칸이 없다", emptySet<HexCoord>(), player intersect enemy)
        assertEquals("합치면 전장 전체다", CombatField.coords.toSet(), player + enemy)
    }

    @Test
    fun `적은 위쪽 절반 플레이어는 아래쪽 절반을 쓴다`() {
        CombatField.halfFor(CombatTeam.ENEMY).forEach { coord ->
            assertTrue("$coord 는 위쪽 절반이다", CombatField.grid.toOffset(coord).row < HexBoard.ROWS)
        }
        CombatField.halfFor(CombatTeam.PLAYER).forEach { coord ->
            assertTrue("$coord 는 아래쪽 절반이다", CombatField.grid.toOffset(coord).row >= HexBoard.ROWS)
        }
    }

    @Test
    fun `양쪽 최전방은 전장 한가운데에서 맞닿는다`() {
        val frontRow = HexBoard.ROWS - 1
        for (col in 0 until HexBoard.COLUMNS) {
            val placement = HexBoard.fromOffset(OffsetCoord(row = frontRow, col = col))
            val player = CombatField.toField(placement, CombatTeam.PLAYER)
            val enemy = CombatField.toField(placement, CombatTeam.ENEMY)
            assertEquals("$col 열의 최전방끼리는 이웃이다", 1, HexGrid.distance(player, enemy))
        }
    }

    @Test
    fun `양쪽 후열은 전장에서 가장 멀다`() {
        val placement = HexBoard.fromOffset(OffsetCoord(row = 0, col = 3))
        val player = CombatField.toField(placement, CombatTeam.PLAYER)
        val enemy = CombatField.toField(placement, CombatTeam.ENEMY)
        assertEquals(CombatField.ROWS - 1, HexGrid.distance(player, enemy))
    }

    @Test
    fun `같은 배치 좌표라도 진영이 다르면 다른 칸이 된다`() {
        HexBoard.coords.forEach { placement ->
            assertNotEquals(
                "$placement",
                CombatField.toField(placement, CombatTeam.PLAYER),
                CombatField.toField(placement, CombatTeam.ENEMY),
            )
        }
    }

    @Test
    fun `변환 결과는 항상 전장 안이다`() {
        CombatTeam.entries.forEach { team ->
            HexBoard.coords.forEach { placement ->
                val field = CombatField.toField(placement, team)
                assertTrue("$placement -> $field ($team)", CombatField.contains(field))
            }
        }
    }

    @Test
    fun `보드 밖 좌표는 전장으로 옮길 수 없다`() {
        assertThrows(IllegalArgumentException::class.java) {
            CombatField.toField(HexCoord(99, 99), CombatTeam.PLAYER)
        }
    }

    @Test
    fun `진영의 상대는 서로를 가리킨다`() {
        assertEquals(CombatTeam.ENEMY, CombatTeam.PLAYER.opponent)
        assertEquals(CombatTeam.PLAYER, CombatTeam.ENEMY.opponent)
        CombatTeam.entries.forEach { assertEquals(it, it.opponent.opponent) }
    }
}
