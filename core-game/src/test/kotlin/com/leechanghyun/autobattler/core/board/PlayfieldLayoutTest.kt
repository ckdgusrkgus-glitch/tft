package com.leechanghyun.autobattler.core.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 드래그앤드롭의 핵심인 "손가락이 놓인 지점이 어느 칸인가"를 검증한다.
 *
 * 이 계산이 틀리면 화면에서는 멀쩡해 보여도 엉뚱한 칸에 유닛이 놓인다.
 * 그런 버그는 에뮬레이터로 찾기 어려우므로 여기서 수치로 막는다.
 */
class PlayfieldLayoutTest {

    private val layout = PlayfieldLayout.forWidth(1080f)

    @Test
    fun `보드가 가로 폭 안에 들어간다`() {
        HexBoard.coords.forEach { coord ->
            val corners = layout.cornersOf(coord)
            corners.forEach { corner ->
                assertTrue("$coord 의 꼭지점이 왼쪽으로 삐져나갔다: ${corner.x}", corner.x >= -1f)
                assertTrue("$coord 의 꼭지점이 오른쪽으로 삐져나갔다: ${corner.x}", corner.x <= layout.widthPx + 1f)
                assertTrue("$coord 의 꼭지점이 위로 삐져나갔다: ${corner.y}", corner.y >= -1f)
                assertTrue("$coord 의 꼭지점이 보드 아래로 넘어갔다", corner.y <= layout.boardHeight + 1f)
            }
        }
    }

    @Test
    fun `칸의 중심을 누르면 그 칸이 나온다`() {
        HexBoard.coords.forEach { coord ->
            assertEquals("$coord 의 중심", coord, layout.coordAt(layout.centerOf(coord)))
        }
    }

    @Test
    fun `중심에서 조금 벗어나도 같은 칸이 나온다`() {
        // 육각형 내접원 반지름의 절반만큼 흔들어도 같은 칸이어야 한다.
        val wobble = layout.hexWidth / 4f
        HexBoard.coords.forEach { coord ->
            val center = layout.centerOf(coord)
            listOf(
                PixelPoint(center.x + wobble, center.y),
                PixelPoint(center.x - wobble, center.y),
                PixelPoint(center.x, center.y + wobble),
                PixelPoint(center.x, center.y - wobble),
            ).forEach { point ->
                assertEquals("$coord 근처 $point", coord, layout.coordAt(point))
            }
        }
    }

    @Test
    fun `이웃 칸의 중심 사이 거리는 모두 같다`() {
        val center = HexBoard.fromOffset(OffsetCoord(1, 3))
        val origin = layout.centerOf(center)
        val distances = HexBoard.neighbors(center).map { neighbor ->
            val point = layout.centerOf(neighbor)
            val dx = point.x - origin.x
            val dy = point.y - origin.y
            kotlin.math.sqrt(dx * dx + dy * dy)
        }
        val first = distances.first()
        distances.forEach { assertEquals(first, it, 0.5f) }
        assertEquals("이웃 간 간격은 육각형 가로폭과 같다", layout.hexWidth, first, 0.5f)
    }

    @Test
    fun `홀수 줄은 반 칸 오른쪽으로 밀린다`() {
        val row0 = layout.centerOf(HexBoard.fromOffset(OffsetCoord(0, 0)))
        val row1 = layout.centerOf(HexBoard.fromOffset(OffsetCoord(1, 0)))
        assertEquals("반 칸만큼 차이", layout.hexWidth / 2f, row1.x - row0.x, 0.5f)
        assertTrue("아래 줄이 더 아래에 있다", row1.y > row0.y)
    }

    @Test
    fun `보드 밖을 누르면 칸이 없다`() {
        assertNull("보드 위쪽", layout.coordAt(PixelPoint(layout.widthPx / 2f, -200f)))
        assertNull("보드 아래쪽", layout.coordAt(PixelPoint(layout.widthPx / 2f, layout.boardHeight + 200f)))
        assertNull("보드 왼쪽", layout.coordAt(PixelPoint(-200f, layout.boardHeight / 2f)))
        assertNull("보드 오른쪽", layout.coordAt(PixelPoint(layout.widthPx + 200f, layout.boardHeight / 2f)))
    }

    @Test
    fun `보드 칸에 놓으면 그 칸이 대상이 된다`() {
        val coord = HexBoard.fromOffset(OffsetCoord(2, 4))
        assertEquals(DropTarget.Board(coord), layout.hitTest(layout.centerOf(coord)))
    }

    @Test
    fun `벤치 영역에 놓으면 벤치가 대상이 된다`() {
        repeat(layout.benchSlots) { index ->
            val rect = layout.benchSlotRect(index)
            assertEquals(
                "벤치 $index 번 칸",
                DropTarget.Bench,
                layout.hitTest(PixelPoint(rect.centerX, rect.centerY)),
            )
        }
    }

    @Test
    fun `보드와 벤치 사이 빈 공간에 놓으면 대상이 없다`() {
        val gapY = (layout.boardHeight + layout.benchTop) / 2f
        assertTrue("보드 아래이면서 벤치 위인 지점이 존재한다", gapY > layout.boardHeight && gapY < layout.benchTop)
        assertNull(layout.hitTest(PixelPoint(layout.widthPx / 2f, gapY)))
    }

    @Test
    fun `벤치 칸은 겹치지 않고 가로 폭을 채운다`() {
        val rects = (0 until layout.benchSlots).map(layout::benchSlotRect)
        rects.zipWithNext { left, right ->
            assertTrue("벤치 칸이 겹친다", left.right <= right.left + 0.01f)
        }
        assertEquals("첫 칸은 왼쪽 끝에서 시작", 0f, rects.first().left, 0.01f)
        assertEquals("마지막 칸은 오른쪽 끝에서 끝난다", layout.widthPx, rects.last().right, 0.01f)
    }

    @Test
    fun `가로 폭이 달라져도 비율이 유지된다`() {
        listOf(480f, 720f, 1080f, 1440f).forEach { width ->
            val scaled = PlayfieldLayout.forWidth(width)
            HexBoard.coords.forEach { coord ->
                assertEquals("폭 $width, $coord", coord, scaled.coordAt(scaled.centerOf(coord)))
            }
            assertTrue("폭 $width 에서 벤치가 보드 아래에 있다", scaled.benchTop > scaled.boardHeight)
        }
    }

    @Test
    fun `모든 칸의 중심이 서로 충분히 떨어져 있다`() {
        val centers = HexBoard.coords.map(layout::centerOf)
        centers.forEachIndexed { i, a ->
            centers.drop(i + 1).forEach { b ->
                val gap = kotlin.math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
                assertTrue("중심이 너무 가깝다: $gap", gap > layout.hexSize)
            }
        }
    }

    @Test
    fun `히트 테스트 결과는 항상 그 지점에서 가장 가까운 칸이다`() {
        // 보드 전체를 촘촘히 훑어 가장 가까운 중심과 일치하는지 확인한다.
        // 두 칸의 정확한 경계선 위에 떨어진 점은 어느 쪽이 나와도 맞으므로 검사에서 제외한다.
        fun squaredDistance(a: PixelPoint, b: PixelPoint): Float =
            (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)

        var checked = 0
        var skipped = 0
        var x = 0f
        while (x < layout.widthPx) {
            var y = 0f
            while (y < layout.boardHeight) {
                val point = PixelPoint(x, y)
                val hit = layout.coordAt(point)
                if (hit != null) {
                    val sorted = HexBoard.coords.sortedBy { squaredDistance(layout.centerOf(it), point) }
                    val nearest = sorted[0]
                    val runnerUp = sorted[1]
                    val onBoundary = abs(
                        squaredDistance(layout.centerOf(nearest), point) -
                            squaredDistance(layout.centerOf(runnerUp), point),
                    ) < 1f

                    if (onBoundary) {
                        skipped++
                        assertTrue(
                            "경계선 위에서는 두 후보 중 하나여야 한다",
                            hit == nearest || hit == runnerUp,
                        )
                    } else {
                        assertEquals("($x, $y) 에서 가장 가까운 칸", nearest, hit)
                        checked++
                    }
                }
                y += 7f
            }
            x += 7f
        }
        assertTrue("검사한 지점이 충분히 많다", checked > 500)
        assertTrue("경계선 제외가 전체를 삼키지 않았다", skipped < checked / 10)
    }

    @Test
    fun `육각형 꼭지점은 중심에서 같은 거리에 있다`() {
        val coord = HexBoard.fromOffset(OffsetCoord(1, 2))
        val center = layout.centerOf(coord)
        layout.cornersOf(coord).forEach { corner ->
            val radius = kotlin.math.sqrt(
                (corner.x - center.x) * (corner.x - center.x) + (corner.y - center.y) * (corner.y - center.y),
            )
            assertEquals(layout.hexSize, radius, 0.5f)
        }
        val top = layout.cornersOf(coord).first()
        assertTrue("첫 꼭지점은 중심 바로 위다", abs(top.x - center.x) < 0.5f && top.y < center.y)
    }

    @Test
    fun `벤치 칸 수는 플레이어 벤치 크기와 같다`() {
        assertEquals(9, layout.benchSlots)
        assertNotNull(layout.benchSlotRect(8))
    }
}
