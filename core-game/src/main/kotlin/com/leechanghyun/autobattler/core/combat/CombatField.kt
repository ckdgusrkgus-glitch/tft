package com.leechanghyun.autobattler.core.combat

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.board.HexGrid
import com.leechanghyun.autobattler.core.board.OffsetCoord
import com.leechanghyun.autobattler.core.model.HexCoord

/** 전장에서의 진영. */
enum class CombatTeam {
    PLAYER,
    ENEMY,
    ;

    val opponent: CombatTeam get() = if (this == PLAYER) ENEMY else PLAYER
}

/**
 * 두 진영이 맞붙는 전장. 배치용 보드([HexBoard], 7x4) 두 개를 위아래로 붙인 7x8 격자다.
 *
 * 줄 번호는 위가 0, 아래가 7이다. 적 진영이 위쪽(0~3), 플레이어 진영이 아래쪽(4~7)을 쓴다.
 * 각 진영의 배치 보드에서 **아래쪽 줄이 최전방**이므로, 플레이어의 3번 줄과 적의 3번 줄이
 * 전장 한가운데(4번 줄과 3번 줄)에서 마주 보도록 플레이어 쪽만 위아래를 뒤집어 옮긴다.
 */
object CombatField {

    const val ROWS = HexBoard.ROWS * 2
    const val COLUMNS = HexBoard.COLUMNS

    val grid = HexGrid(rows = ROWS, columns = COLUMNS)

    val coords: List<HexCoord> get() = grid.coords

    fun contains(coord: HexCoord): Boolean = grid.contains(coord)

    /** 배치 좌표를 [team] 진영의 전장 좌표로 옮긴다. */
    fun toField(placement: HexCoord, team: CombatTeam): HexCoord {
        require(HexBoard.contains(placement)) { "배치 보드 밖의 좌표다: $placement" }
        val offset = HexBoard.toOffset(placement)
        val fieldRow = when (team) {
            // 플레이어는 아래쪽 절반을 쓰고, 최전방(3번 줄)이 가운데로 오도록 뒤집는다.
            CombatTeam.PLAYER -> ROWS - 1 - offset.row
            // 적은 위쪽 절반을 그대로 쓴다. 3번 줄이 이미 가운데를 향한다.
            CombatTeam.ENEMY -> offset.row
        }
        return grid.fromOffset(OffsetCoord(row = fieldRow, col = offset.col))
    }

    /** [team] 진영이 쓰는 전장 칸들. */
    fun halfFor(team: CombatTeam): List<HexCoord> =
        HexBoard.coords.map { toField(it, team) }
}
