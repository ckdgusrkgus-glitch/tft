package com.leechanghyun.autobattler.core.economy

import com.leechanghyun.autobattler.core.masterdata.SHOP_ODDS_BY_LEVEL
import com.leechanghyun.autobattler.core.model.UnitDef
import kotlin.random.Random

/**
 * 레벨별 상점 등급 확률표 조회와 등급 추첨. 명세서 4-1.
 *
 * 상점 슬롯 1칸은 두 단계로 정해진다.
 *   1. 이 클래스가 확률표를 보고 **등급(코스트)** 을 뽑는다.
 *   2. [UnitPool] 이 그 등급의 남은 재고에서 **유닛 1종**을 뽑는다.
 */
object ShopOdds {

    /** 레벨 [level] 의 1~5코스트 확률. 표에 없는 레벨은 가장 가까운 레벨 값을 쓴다. */
    fun oddsFor(level: Int): List<Double> {
        SHOP_ODDS_BY_LEVEL[level]?.let { return it }
        val clamped = level.coerceIn(SHOP_ODDS_BY_LEVEL.keys.min(), SHOP_ODDS_BY_LEVEL.keys.max())
        return requireNotNull(SHOP_ODDS_BY_LEVEL[clamped]) { "레벨 $level 의 상점 확률을 찾을 수 없다" }
    }

    /**
     * 레벨 [level] 기준으로 등급 1개를 뽑는다.
     *
     * @return 1..5 코스트
     */
    fun rollCost(level: Int, random: Random): Int {
        val odds = oddsFor(level)
        var ticket = random.nextDouble()
        for ((index, probability) in odds.withIndex()) {
            ticket -= probability
            if (ticket < 0.0) return index + UnitDef.MIN_COST
        }
        // 부동소수점 오차로 마지막 칸을 넘어가는 경우를 대비한 방어 코드.
        return odds.indexOfLast { it > 0.0 } + UnitDef.MIN_COST
    }
}
