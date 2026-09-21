package com.leechanghyun.autobattler.core.board

import com.leechanghyun.autobattler.core.model.HexCoord
import com.leechanghyun.autobattler.core.model.PlayerState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 화면상의 한 점. Compose 의 Offset 에 의존하지 않기 위해 따로 둔다. */
data class PixelPoint(val x: Float, val y: Float)

/** 사각형 영역. 벤치 칸을 그리고 맞히는 데 쓴다. */
data class PixelRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(point: PixelPoint): Boolean =
        point.x in left..right && point.y in top..bottom
}

/** 드래그를 놓은 지점이 무엇이었는지. */
sealed interface DropTarget {
    /** 보드의 한 칸. */
    data class Board(val coord: HexCoord) : DropTarget

    /** 벤치. 칸 번호는 쓰지 않고 "벤치로 내린다"는 뜻만 갖는다. */
    data object Bench : DropTarget
}

/**
 * 보드와 벤치를 하나의 좌표계로 배치하고, 화면 좌표를 칸으로 되돌린다. 명세서 4-6.
 *
 * 드래그앤드롭에서 가장 틀리기 쉬운 부분이 "손가락이 놓인 지점이 어느 칸인가"다.
 * 그 계산을 안드로이드에 의존하지 않는 여기에 두어 단위테스트로 검증한다.
 * Compose 쪽은 이 값을 받아 그리고, 터치 좌표를 [hitTest] 에 넘기기만 한다.
 *
 * 꼭지점이 위를 향하는(pointy-top) 육각형을 쓴다. 한 변의 길이를 `size` 라 할 때
 * 가로폭은 `sqrt(3) * size`, 세로 높이는 `2 * size` 이고 줄 간격은 `1.5 * size` 다.
 */
class PlayfieldLayout(
    val widthPx: Float,
    val hexSize: Float,
    val benchTop: Float,
    val benchSlotSize: Float,
    val benchSlots: Int = PlayerState.BENCH_SIZE,
) {
    /** 육각형 하나의 가로폭. */
    val hexWidth: Float get() = SQRT3 * hexSize

    /** 보드 영역의 높이. */
    val boardHeight: Float get() = hexSize * (1.5f * (HexBoard.ROWS - 1) + 2f)

    /** 보드 + 간격 + 벤치를 모두 포함한 높이. */
    val totalHeight: Float get() = benchTop + benchSlotSize

    /** 보드를 가로 가운데로 맞추기 위한 왼쪽 여백. */
    private val boardLeft: Float
        get() = (widthPx - hexWidth * (HexBoard.COLUMNS + 0.5f)) / 2f

    /** 칸 [coord] 의 중심 좌표. */
    fun centerOf(coord: HexCoord): PixelPoint {
        val x = boardLeft + hexWidth * (coord.q + coord.r / 2f) + hexWidth / 2f
        val y = hexSize * 1.5f * coord.r + hexSize
        return PixelPoint(x, y)
    }

    /** 칸 [coord] 을 그릴 육각형의 꼭지점 6개. 위에서부터 시계 방향이다. */
    fun cornersOf(coord: HexCoord): List<PixelPoint> {
        val center = centerOf(coord)
        return (0 until 6).map { i ->
            // pointy-top 은 첫 꼭지점이 바로 위(-90도)에 온다.
            val angle = Math.PI / 3.0 * i - Math.PI / 2.0
            PixelPoint(
                x = center.x + hexSize * kotlin.math.cos(angle).toFloat(),
                y = center.y + hexSize * kotlin.math.sin(angle).toFloat(),
            )
        }
    }

    /** 벤치 [index] 번 칸의 영역. */
    fun benchSlotRect(index: Int): PixelRect {
        val gap = 2f
        val slotWidth = (widthPx - gap * (benchSlots - 1)) / benchSlots
        val left = index * (slotWidth + gap)
        return PixelRect(left, benchTop, left + slotWidth, benchTop + benchSlotSize)
    }

    /** 벤치 영역 전체. */
    val benchRect: PixelRect
        get() = PixelRect(0f, benchTop, widthPx, benchTop + benchSlotSize)

    /**
     * 화면 좌표가 놓인 대상을 찾는다.
     *
     * 보드 칸도 벤치도 아니면 null 이며, 이때 드래그는 취소된 것으로 본다.
     */
    fun hitTest(point: PixelPoint): DropTarget? {
        if (benchRect.contains(point)) return DropTarget.Bench
        val coord = coordAt(point) ?: return null
        return DropTarget.Board(coord)
    }

    /**
     * 화면 좌표를 보드 칸으로 되돌린다. 보드 밖이면 null.
     *
     * 픽셀 → 분수 axial 좌표로 바꾼 뒤, cube 반올림으로 가장 가까운 정수 칸을 고른다.
     * 단순 반올림은 세 축의 합이 0이라는 제약을 깨뜨려 엉뚱한 칸이 나오므로,
     * 오차가 가장 큰 축을 나머지 둘로부터 다시 계산한다.
     */
    fun coordAt(point: PixelPoint): HexCoord? {
        val localX = point.x - boardLeft - hexWidth / 2f
        val localY = point.y - hexSize

        val fractionalR = (2f / 3f) * localY / hexSize
        val fractionalQ = (SQRT3 / 3f * localX - localY / 3f) / hexSize

        val rounded = cubeRound(fractionalQ, fractionalR)
        return rounded.takeIf(HexBoard::contains)
    }

    private fun cubeRound(fractionalQ: Float, fractionalR: Float): HexCoord {
        val fractionalS = -fractionalQ - fractionalR
        var q = fractionalQ.roundToInt()
        var r = fractionalR.roundToInt()
        val s = fractionalS.roundToInt()

        val deltaQ = abs(q - fractionalQ)
        val deltaR = abs(r - fractionalR)
        val deltaS = abs(s - fractionalS)

        if (deltaQ > deltaR && deltaQ > deltaS) {
            q = -r - s
        } else if (deltaR > deltaS) {
            r = -q - s
        }
        return HexCoord(q, r)
    }

    companion object {
        private val SQRT3 = sqrt(3f)

        /** 보드와 벤치 사이 간격을 육각형 크기의 몇 배로 둘지. */
        private const val BENCH_GAP_RATIO = 0.6f

        /**
         * 가로 폭에 맞춰 배치를 만든다.
         *
         * 한 줄에 7칸이 들어가고 홀수 줄이 반 칸 밀리므로 가로로 7.5칸 분량이 필요하다.
         */
        fun forWidth(widthPx: Float, benchSlots: Int = PlayerState.BENCH_SIZE): PlayfieldLayout {
            require(widthPx > 0f) { "가로 폭은 0보다 커야 한다" }
            val hexSize = widthPx / (SQRT3 * (HexBoard.COLUMNS + 0.5f))
            val boardHeight = hexSize * (1.5f * (HexBoard.ROWS - 1) + 2f)
            val benchSlotSize = widthPx / benchSlots
            return PlayfieldLayout(
                widthPx = widthPx,
                hexSize = hexSize,
                benchTop = boardHeight + hexSize * BENCH_GAP_RATIO,
                benchSlotSize = benchSlotSize,
                benchSlots = benchSlots,
            )
        }
    }
}
