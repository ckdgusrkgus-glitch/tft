package com.leechanghyun.autobattler.core.model

/**
 * 플레이어(사람 1명 + AI 봇 7명) 1인의 상태. 명세서 5장 데이터 모델 초안.
 *
 * 전투/경제 로직이 이 값을 받아 새 값을 돌려주는 불변 객체로 다룬다.
 */
data class PlayerState(
    val playerId: String,
    val displayName: String,
    val isBot: Boolean,
    val hp: Int = STARTING_HP,
    val gold: Int = STARTING_GOLD,
    val level: Int = STARTING_LEVEL,
    val exp: Int = 0,
    val winStreak: Int = 0,
    val loseStreak: Int = 0,
    val bench: List<BoardUnit> = emptyList(),
    val board: List<BoardUnit> = emptyList(),
    val augments: List<AugmentDef> = emptyList(),
    val itemInventory: List<ItemDef> = emptyList(),
) {
    val isEliminated: Boolean get() = hp <= 0

    /** 보드에 올릴 수 있는 유닛 수는 레벨과 같다. */
    val boardCapacity: Int get() = level

    companion object {
        const val STARTING_HP = 100
        const val STARTING_GOLD = 0
        const val STARTING_LEVEL = 1
        const val MAX_LEVEL = 10

        /** 벤치 칸 수. 명세서에 수치가 없어 원작 관행대로 9칸으로 둔 임의 초기값이다. */
        const val BENCH_SIZE = 9
    }
}
