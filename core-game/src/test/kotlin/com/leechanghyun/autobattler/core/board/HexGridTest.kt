package com.leechanghyun.autobattler.core.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 크기를 바꿔도 헥스 격자 규칙이 그대로 성립하는지 본다.
 *
 * 배치 보드(7x4)와 전투 전장(7x8)이 같은 [HexGrid] 를 쓰므로, 4줄에서만 맞는 계산이
 * 8줄에서 틀리면 전투가 통째로 어긋난다.
 */
class HexGridTest {

    private val sizes = listOf(1 to 1, 4 to 7, 8 to 7, 9 to 5)

    @Test
    fun `칸 수는 줄 수 곱하기 열 수다`() {
        sizes.forEach { (rows, columns) ->
            val grid = HexGrid(rows, columns)
            assertEquals("${rows}x$columns", rows * columns, grid.coords.size)
            assertEquals("${rows}x$columns 에 중복 칸이 없다", rows * columns, grid.coords.toSet().size)
        }
    }

    @Test
    fun `오프셋과 axial 은 어떤 크기에서도 왕복한다`() {
        sizes.forEach { (rows, columns) ->
            val grid = HexGrid(rows, columns)
            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    val offset = OffsetCoord(row, col)
                    val axial = grid.fromOffset(offset)
                    assertEquals("${rows}x$columns ($row, $col)", offset, grid.toOffset(axial))
                    assertTrue("${rows}x$columns ($row, $col) 은 격자 안이다", grid.contains(axial))
                }
            }
        }
    }

    @Test
    fun `이웃은 6개 이하이고 전부 격자 안이며 거리가 1이다`() {
        val grid = HexGrid(rows = 8, columns = 7)
        grid.coords.forEach { coord ->
            val neighbors = grid.neighbors(coord)
            assertTrue("$coord 의 이웃은 6개 이하다", neighbors.size <= 6)
            assertEquals("$coord 의 이웃에 중복이 없다", neighbors.size, neighbors.toSet().size)
            neighbors.forEach { neighbor ->
                assertTrue("$neighbor 는 격자 안이다", grid.contains(neighbor))
                assertEquals("$coord - $neighbor", 1, HexGrid.distance(coord, neighbor))
            }
        }
    }

    @Test
    fun `가운데 칸은 이웃이 정확히 6개다`() {
        val grid = HexGrid(rows = 8, columns = 7)
        val center = grid.fromOffset(OffsetCoord(row = 4, col = 3))
        assertEquals(6, grid.neighbors(center).size)
    }

    @Test
    fun `이웃 관계는 서로 대칭이다`() {
        val grid = HexGrid(rows = 8, columns = 7)
        grid.coords.forEach { coord ->
            grid.neighbors(coord).forEach { neighbor ->
                assertTrue("$neighbor 도 $coord 를 이웃으로 본다", coord in grid.neighbors(neighbor))
            }
        }
    }

    @Test
    fun `거리는 대칭이고 삼각부등식을 만족한다`() {
        val grid = HexGrid(rows = 8, columns = 7)
        val sample = grid.coords.filterIndexed { index, _ -> index % 5 == 0 }
        for (a in sample) {
            for (b in sample) {
                assertEquals(HexGrid.distance(a, b), HexGrid.distance(b, a))
                for (c in sample) {
                    assertTrue(
                        "$a -> $c -> $b 가 지름길일 수 없다",
                        HexGrid.distance(a, b) <= HexGrid.distance(a, c) + HexGrid.distance(c, b),
                    )
                }
            }
        }
    }

    @Test
    fun `격자 밖 좌표는 포함되지 않는다`() {
        val grid = HexGrid(rows = 8, columns = 7)
        assertFalse(grid.contains(grid.fromOffset(OffsetCoord(0, 0)).copy(q = -1)))
        assertFalse(grid.contains(grid.fromOffset(OffsetCoord(7, 6)).copy(r = 8)))
    }

    @Test
    fun `한 칸에서 다른 칸까지 이웃만 밟아 갈 수 있다`() {
        val grid = HexGrid(rows = 8, columns = 7)
        val start = grid.fromOffset(OffsetCoord(0, 0))

        // BFS 로 닿는 칸을 모으면 전장 전체가 나와야 한다. 갈라진 섬이 있으면 유닛이 서로 못 만난다.
        val seen = mutableSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            grid.neighbors(queue.removeFirst()).forEach { if (seen.add(it)) queue += it }
        }
        assertEquals(grid.coords.size, seen.size)
    }
}
