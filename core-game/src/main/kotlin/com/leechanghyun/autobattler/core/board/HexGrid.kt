package com.leechanghyun.autobattler.core.board

import com.leechanghyun.autobattler.core.model.HexCoord
import kotlin.math.abs

/**
 * 직사각형 모양으로 잘라낸 헥스 격자. 명세서 4-6.
 *
 * 배치용 보드(7x4)와 전투용 전장(7x8)이 같은 규칙을 쓰므로 크기만 다른 인스턴스로 만든다.
 * 홀수 줄이 오른쪽으로 반 칸 밀리는 odd-r 배치다.
 */
class HexGrid(val rows: Int, val columns: Int) {

    /** 격자의 모든 칸. 위쪽 줄부터, 각 줄은 왼쪽부터 나열된다. */
    val coords: List<HexCoord> = buildList {
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                add(fromOffset(OffsetCoord(row, col)))
            }
        }
    }

    private val coordSet: Set<HexCoord> = coords.toSet()

    fun contains(coord: HexCoord): Boolean = coord in coordSet

    /** 오프셋(행, 열) → axial. */
    fun fromOffset(offset: OffsetCoord): HexCoord =
        HexCoord(q = offset.col - (offset.row - (offset.row and 1)) / 2, r = offset.row)

    /** axial → 오프셋(행, 열). */
    fun toOffset(coord: HexCoord): OffsetCoord =
        OffsetCoord(row = coord.r, col = coord.q + (coord.r - (coord.r and 1)) / 2)

    /** 격자 안에 있는 이웃 칸들. 가장자리는 6개보다 적다. */
    fun neighbors(coord: HexCoord): List<HexCoord> =
        DIRECTIONS.map { (dq, dr) -> HexCoord(coord.q + dq, coord.r + dr) }.filter(::contains)

    companion object {
        /** axial 육각형의 여섯 방향. */
        val DIRECTIONS = listOf(1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1)

        /**
         * 두 칸 사이의 거리. 몇 번 걸어야 닿는지를 뜻한다.
         *
         * cube 좌표에서 세 축 차이의 절댓값 합을 2로 나눈 값이며, 격자 크기와 무관하다.
         */
        fun distance(a: HexCoord, b: HexCoord): Int =
            (abs(a.q - b.q) + abs(a.r - b.r) + abs(a.s - b.s)) / 2
    }
}
