package com.leechanghyun.autobattler.core.board

import com.leechanghyun.autobattler.core.model.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 헥스 보드의 좌표계를 검증한다. 명세서 4-6. */
class HexBoardTest {

    @Test
    fun `보드는 7칸 x 4줄로 28칸이다`() {
        assertEquals(7, HexBoard.COLUMNS)
        assertEquals(4, HexBoard.ROWS)
        assertEquals(28, HexBoard.coords.size)
        assertEquals("중복된 칸이 없다", 28, HexBoard.coords.toSet().size)
    }

    @Test
    fun `오프셋과 axial 은 서로 정확히 되돌아간다`() {
        for (row in 0 until HexBoard.ROWS) {
            for (col in 0 until HexBoard.COLUMNS) {
                val offset = OffsetCoord(row, col)
                val axial = HexBoard.fromOffset(offset)
                assertEquals("($row, $col) 왕복", offset, HexBoard.toOffset(axial))
                assertTrue("($row, $col) 은 보드 안이다", HexBoard.contains(axial))
            }
        }
    }

    @Test
    fun `axial 좌표의 세 축 합은 항상 0이다`() {
        HexBoard.coords.forEach { coord ->
            assertEquals("${coord} 의 q + r + s", 0, coord.q + coord.r + coord.s)
        }
    }

    @Test
    fun `보드 밖 좌표는 포함되지 않는다`() {
        assertFalse(HexBoard.contains(HexCoord(0, -1)))
        assertFalse(HexBoard.contains(HexCoord(0, HexBoard.ROWS)))
        assertFalse(HexBoard.contains(HexCoord(HexBoard.COLUMNS, 0)))
        assertFalse(HexBoard.contains(HexCoord(-1, 0)))
    }

    @Test
    fun `자기 자신과의 거리는 0이고 이웃과는 1이다`() {
        val center = HexBoard.fromOffset(OffsetCoord(1, 3))
        assertEquals(0, HexBoard.distance(center, center))
        HexBoard.neighbors(center).forEach { neighbor ->
            assertEquals("$neighbor 는 이웃", 1, HexBoard.distance(center, neighbor))
        }
    }

    @Test
    fun `거리는 어느 쪽에서 재도 같다`() {
        val a = HexBoard.fromOffset(OffsetCoord(0, 0))
        val b = HexBoard.fromOffset(OffsetCoord(3, 6))
        assertEquals(HexBoard.distance(a, b), HexBoard.distance(b, a))
        assertTrue("서로 다른 칸의 거리는 0보다 크다", HexBoard.distance(a, b) > 0)
    }

    @Test
    fun `같은 줄에서 한 칸 옆은 거리가 1이다`() {
        val left = HexBoard.fromOffset(OffsetCoord(2, 2))
        val right = HexBoard.fromOffset(OffsetCoord(2, 3))
        assertEquals(1, HexBoard.distance(left, right))
        assertEquals("세 칸 떨어지면 3", 3, HexBoard.distance(left, HexBoard.fromOffset(OffsetCoord(2, 5))))
    }

    @Test
    fun `이웃 관계는 서로에게 성립한다`() {
        HexBoard.coords.forEach { coord ->
            HexBoard.neighbors(coord).forEach { neighbor ->
                assertTrue(
                    "$coord 와 $neighbor 는 서로 이웃이어야 한다",
                    coord in HexBoard.neighbors(neighbor),
                )
            }
        }
    }

    @Test
    fun `가운데 칸은 이웃이 6개, 모서리는 그보다 적다`() {
        val middle = HexBoard.fromOffset(OffsetCoord(1, 3))
        assertEquals(6, HexBoard.neighbors(middle).size)

        val topLeft = HexBoard.fromOffset(OffsetCoord(0, 0))
        assertTrue("모서리 이웃은 6개 미만", HexBoard.neighbors(topLeft).size < 6)
        assertTrue("그래도 이웃은 있다", HexBoard.neighbors(topLeft).isNotEmpty())
    }

    @Test
    fun `모든 이웃은 거리 1이고 거리 1인 보드 안 칸은 모두 이웃이다`() {
        HexBoard.coords.forEach { coord ->
            val neighbors = HexBoard.neighbors(coord).toSet()
            neighbors.forEach { assertEquals(1, HexBoard.distance(coord, it)) }

            val withinOne = HexBoard.coords.filter { it != coord && HexBoard.distance(coord, it) == 1 }
            assertEquals("$coord 의 거리 1 칸 목록", withinOne.toSet(), neighbors)
        }
    }
}
