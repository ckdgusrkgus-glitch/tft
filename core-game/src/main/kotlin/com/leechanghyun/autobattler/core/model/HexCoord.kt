package com.leechanghyun.autobattler.core.model

/**
 * 헥사고날 그리드의 axial 좌표. 명세서 4-6.
 *
 * axial 좌표계는 육각형 격자를 (q, r) 두 축으로 표현하는 방식이다.
 * 세 번째 축 s 는 q + r + s = 0 이라는 제약에서 유도되므로 따로 저장하지 않는다.
 */
data class HexCoord(val q: Int, val r: Int) {
    /** cube 좌표의 세 번째 축. 거리 계산에 쓴다. */
    val s: Int get() = -q - r
}
