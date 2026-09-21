package com.leechanghyun.autobattler.core.masterdata

import org.junit.Assert.assertEquals
import org.junit.Test

/** 명세서 4-1 상점 확률표, 풀 크기표와 4-2 경험치표를 검증한다. */
class EconomyTablesTest {

    @Test
    fun `레벨별 상점 확률의 합은 항상 1이다`() {
        SHOP_ODDS_BY_LEVEL.forEach { (level, odds) ->
            assertEquals("레벨 $level 확률 합", 1.0, odds.sum(), 1e-9)
            assertEquals("레벨 $level 은 5개 등급", 5, odds.size)
        }
    }

    @Test
    fun `명세서 표의 레벨 2에서 9까지가 모두 들어있다`() {
        (2..9).forEach { level ->
            assertEquals("레벨 $level", true, SHOP_ODDS_BY_LEVEL.containsKey(level))
        }
        assertEquals(listOf(0.45, 0.33, 0.20, 0.02, 0.00), SHOP_ODDS_BY_LEVEL.getValue(5))
        assertEquals(listOf(0.15, 0.20, 0.25, 0.30, 0.10), SHOP_ODDS_BY_LEVEL.getValue(9))
    }

    @Test
    fun `풀 크기표가 명세서와 같다`() {
        assertEquals(mapOf(1 to 22, 2 to 20, 3 to 17, 4 to 10, 5 to 9), POOL_SIZE_BY_COST)
    }

    @Test
    fun `경험치표가 명세서와 같다`() {
        assertEquals(
            mapOf(1 to 2, 2 to 6, 3 to 12, 4 to 20, 5 to 30, 6 to 43, 7 to 59, 8 to 78, 9 to 100),
            EXP_TO_NEXT_LEVEL,
        )
        assertEquals("레벨 1에서 3까지 총 8", 8, cumulativeExpToLevel(3))
        assertEquals("레벨 1에서 10까지 총합", 350, cumulativeExpToLevel(10))
    }

    @Test
    fun `이자는 골드 10당 1이고 최대 5다`() {
        assertEquals(0, EconomyRules.interestFor(9))
        assertEquals(1, EconomyRules.interestFor(10))
        assertEquals(5, EconomyRules.interestFor(50))
        assertEquals("50골드를 넘어도 5에서 멈춘다", 5, EconomyRules.interestFor(120))
    }

    @Test
    fun `연속 2회부터 보너스가 붙고 표를 넘으면 마지막 값을 쓴다`() {
        assertEquals(0, EconomyRules.streakBonusFor(1))
        assertEquals(1, EconomyRules.streakBonusFor(2))
        assertEquals(2, EconomyRules.streakBonusFor(4))
        assertEquals(3, EconomyRules.streakBonusFor(5))
        assertEquals(3, EconomyRules.streakBonusFor(9))
    }
}
