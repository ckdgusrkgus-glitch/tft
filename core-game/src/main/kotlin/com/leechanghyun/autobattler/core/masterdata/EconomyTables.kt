package com.leechanghyun.autobattler.core.masterdata

/**
 * 레벨별 상점 등급 확률표. 명세서 4-1.
 *
 * 바깥 Map 의 키는 플레이어 레벨, 안쪽 List 는 1~5코스트 확률(합 1.0)이다.
 * 레벨 1 은 명세서 표에 없다. 게임 시작 직후 레벨 2 로 올라가는 구간이라
 * 레벨 2 와 동일하게 1코스트 100% 로 둔다.
 */
val SHOP_ODDS_BY_LEVEL: Map<Int, List<Double>> = mapOf(
    1 to listOf(1.00, 0.00, 0.00, 0.00, 0.00),
    2 to listOf(1.00, 0.00, 0.00, 0.00, 0.00),
    3 to listOf(0.75, 0.25, 0.00, 0.00, 0.00),
    4 to listOf(0.55, 0.30, 0.15, 0.00, 0.00),
    5 to listOf(0.45, 0.33, 0.20, 0.02, 0.00),
    6 to listOf(0.30, 0.40, 0.25, 0.05, 0.00),
    7 to listOf(0.20, 0.33, 0.36, 0.10, 0.01),
    8 to listOf(0.18, 0.27, 0.32, 0.20, 0.03),
    9 to listOf(0.15, 0.20, 0.25, 0.30, 0.10),
    10 to listOf(0.15, 0.20, 0.25, 0.30, 0.10),
)

/**
 * 코스트별, 유닛 1종당 풀(pool) 개수. 명세서 4-1.
 *
 * 풀은 8명이 함께 쓰는 공용 자원이다. 예를 들어 1코스트 유닛은 종류마다 22장씩 존재하고,
 * 누군가 사 가면 남은 사람의 상점에는 덜 나온다. 풀 차감 로직은 [com.leechanghyun.autobattler.core.economy.UnitPool] 에 있다.
 */
val POOL_SIZE_BY_COST: Map<Int, Int> = mapOf(
    1 to 22,
    2 to 20,
    3 to 17,
    4 to 10,
    5 to 9,
)

/**
 * 레벨업에 필요한 경험치. 명세서 4-2.
 *
 * 키는 현재 레벨, 값은 다음 레벨로 오르는 데 필요한 경험치다.
 * 명세서 표의 열 제목은 "누적 경험치"지만 행이 "1→2", "2→3" 형태라
 * **각 레벨업 1회에 필요한 양**으로 읽었다. 이 해석이 틀렸다면 [cumulativeExpToLevel] 만 바꾸면 된다.
 */
val EXP_TO_NEXT_LEVEL: Map<Int, Int> = mapOf(
    1 to 2,
    2 to 6,
    3 to 12,
    4 to 20,
    5 to 30,
    6 to 43,
    7 to 59,
    8 to 78,
    9 to 100,
)

/** 레벨 1부터 [level] 까지 올리는 데 드는 총 경험치. */
fun cumulativeExpToLevel(level: Int): Int =
    (1 until level).sumOf { EXP_TO_NEXT_LEVEL[it] ?: 0 }

/**
 * 골드/상점 관련 상수. 명세서 4-1.
 *
 * 연승·연패 보너스 표([STREAK_BONUS])만 명세서에 수치가 없어 임의 초기값이고,
 * 나머지는 명세서 확정값이다.
 */
object EconomyRules {
    /** 매 라운드 기본 지급 골드. */
    const val BASE_INCOME = 5

    /** 상점 새로고침(리롤) 비용. */
    const val REROLL_COST = 2

    /** 경험치 구매 비용과 그때 얻는 경험치. */
    const val BUY_EXP_COST = 4
    const val BUY_EXP_AMOUNT = 4

    /** 이자: 보유 골드 10당 1골드, 최대 5골드. */
    const val INTEREST_PER_GOLD = 10
    const val MAX_INTEREST = 5

    /** 상점 슬롯 수. 명세서 4-1: 5칸. */
    const val SHOP_SLOT_COUNT = 5

    /** 라운드마다 자동으로 주는 경험치. 명세서에 "소량"이라고만 있어 임의 초기값이다. */
    const val PASSIVE_EXP_PER_ROUND = 2

    /**
     * 연승/연패 길이별 추가 골드. 명세서에 수치가 없어 임의 초기값으로 채웠다.
     * 키는 연승 또는 연패 횟수, 값은 추가 골드다. 표에 없는 더 긴 연속은 마지막 값을 쓴다.
     */
    val STREAK_BONUS: Map<Int, Int> = mapOf(
        2 to 1,
        3 to 1,
        4 to 2,
        5 to 3,
    )

    /** 보유 골드에 대한 이자 계산. */
    fun interestFor(gold: Int): Int = minOf(gold / INTEREST_PER_GOLD, MAX_INTEREST)

    /** 연승 또는 연패 [streak] 에 대한 보너스 골드. */
    fun streakBonusFor(streak: Int): Int {
        if (streak < 2) return 0
        val maxKey = STREAK_BONUS.keys.max()
        return STREAK_BONUS[minOf(streak, maxKey)] ?: 0
    }
}
