package com.leechanghyun.autobattler.core.board

import com.leechanghyun.autobattler.core.model.HexCoord

/** 오프셋 좌표(행, 열). 사람이 읽기 쉬운 격자 표기이며 저장과 계산은 axial 로 한다. */
data class OffsetCoord(val row: Int, val col: Int)

/**
 * 플레이어 한 명의 배치용 보드. 명세서 4-6.
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
 *
 * 전투는 양쪽 진영이 맞붙으므로 이 보드 두 개를 위아래로 붙인
 * [com.leechanghyun.autobattler.core.combat.CombatField] 위에서 벌어진다.
 */
object HexBoard {

    const val ROWS = 4
    const val COLUMNS = 7

    /** 배치용 격자. 계산은 전부 여기에 위임한다. */
    val grid = HexGrid(rows = ROWS, columns = COLUMNS)

    /** 보드의 모든 칸. 위쪽 줄부터, 각 줄은 왼쪽부터 나열된다. */
    val coords: List<HexCoord> get() = grid.coords

    fun contains(coord: HexCoord): Boolean = grid.contains(coord)

    /** 오프셋(행, 열) → axial. odd-r 배치 기준이다. */
    fun fromOffset(offset: OffsetCoord): HexCoord = grid.fromOffset(offset)

    /** axial → 오프셋(행, 열). */
    fun toOffset(coord: HexCoord): OffsetCoord = grid.toOffset(coord)

    /** 두 칸 사이의 거리. */
    fun distance(a: HexCoord, b: HexCoord): Int = HexGrid.distance(a, b)

    /** 보드 안에 있는 이웃 칸들. */
    fun neighbors(coord: HexCoord): List<HexCoord> = grid.neighbors(coord)
}
