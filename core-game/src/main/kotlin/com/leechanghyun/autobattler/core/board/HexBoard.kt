package com.leechanghyun.autobattler.core.board

import com.leechanghyun.autobattler.core.model.HexCoord
import kotlin.math.abs

/** 오프셋 좌표(행, 열). 사람이 읽기 쉬운 격자 표기이며 저장과 계산은 axial 로 한다. */
data class OffsetCoord(val row: Int, val col: Int)

/**
 * 플레이어 한 명의 헥스 보드. 명세서 4-6.
 *
 * ### axial 좌표란
 * 육각형 격자는 정사각 격자처럼 (행, 열)로 다루면 이웃 계산이 지저분해진다.
 * axial 좌표는 두 축 (q, r)만 쓰고 세 번째 축 s 를 `q + r + s = 0` 으로 유도해,
 * 거리와 이웃을 간단한 식으로 얻는다. 저장과 계산은 전부 axial 로 하고,
 * 화면 배치처럼 "몇 번째 줄 몇 번째 칸"이 필요할 때만 [OffsetCoord] 로 바꾼다.
 *
 * ### 보드 크기
 * 가로 7칸 x 세로 4줄, 총 28칸이다. 명세서에 크기 규정이 없어 원작 관행을 따랐고
 * 11단계에서 조정할 수 있다. 홀수 줄이 오른쪽으로 반 칸 밀리는 odd-r 배치다.
 */
object HexBoard {

    const val ROWS = 4
    const val COLUMNS = 7

    /** 보드의 모든 칸. 위쪽 줄부터, 각 줄은 왼쪽부터 나열된다. */
    val coords: List<HexCoord> = buildList {
        for (row in 0 until ROWS) {
            for (col in 0 until COLUMNS) {
                add(fromOffset(OffsetCoord(row, col)))
            }
        }
    }

    private val coordSet: Set<HexCoord> = coords.toSet()

    /** 보드 안의 칸인지. */
    fun contains(coord: HexCoord): Boolean = coord in coordSet

    /** 오프셋(행, 열) → axial. odd-r 배치 기준이다. */
    fun fromOffset(offset: OffsetCoord): HexCoord =
        HexCoord(q = offset.col - (offset.row - (offset.row and 1)) / 2, r = offset.row)

    /** axial → 오프셋(행, 열). */
    fun toOffset(coord: HexCoord): OffsetCoord =
        OffsetCoord(row = coord.r, col = coord.q + (coord.r - (coord.r and 1)) / 2)

    /**
     * 두 칸 사이의 거리. 몇 번 걸어야 닿는지를 뜻한다.
     *
     * cube 좌표에서 세 축 차이의 절댓값 합을 2로 나눈 값이다.
     */
    fun distance(a: HexCoord, b: HexCoord): Int =
        (abs(a.q - b.q) + abs(a.r - b.r) + abs(a.s - b.s)) / 2

    /** 보드 안에 있는 이웃 칸들. 가장자리는 6개보다 적다. */
    fun neighbors(coord: HexCoord): List<HexCoord> =
        DIRECTIONS.map { (dq, dr) -> HexCoord(coord.q + dq, coord.r + dr) }.filter(::contains)

    /** axial 육각형의 여섯 방향. */
    private val DIRECTIONS = listOf(
        1 to 0,
        1 to -1,
        0 to -1,
        -1 to 0,
        -1 to 1,
        0 to 1,
    )
}
