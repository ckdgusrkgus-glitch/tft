package com.leechanghyun.autobattler.core.economy

import com.leechanghyun.autobattler.core.masterdata.MasterData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

/** 공용 유닛 풀의 재고 증감을 검증한다. 명세서 4-1. */
class UnitPoolTest {

    @Test
    fun `초기 재고는 코스트별 풀 크기와 유닛 종류 수의 곱이다`() {
        val pool = UnitPool()
        assertEquals("1코스트 4종 x 22장", 4 * 22, pool.remainingOfCost(1))
        assertEquals("2코스트 4종 x 20장", 4 * 20, pool.remainingOfCost(2))
        assertEquals("3코스트 3종 x 17장", 3 * 17, pool.remainingOfCost(3))
        assertEquals("4코스트 2종 x 10장", 2 * 10, pool.remainingOfCost(4))
        assertEquals("5코스트 1종 x 9장", 9, pool.remainingOfCost(5))
        assertEquals(4 * 22 + 4 * 20 + 3 * 17 + 2 * 10 + 9, pool.totalRemaining())
    }

    @Test
    fun `카드를 꺼내면 재고가 줄고 되돌리면 복구된다`() {
        val pool = UnitPool()
        val before = pool.remaining("steel_guard")

        assertNotNull(pool.take("steel_guard"))
        assertEquals(before - 1, pool.remaining("steel_guard"))

        pool.giveBack("steel_guard")
        assertEquals(before, pool.remaining("steel_guard"))
    }

    @Test
    fun `재고가 바닥나면 더 꺼낼 수 없다`() {
        val pool = UnitPool()
        val stock = pool.remaining("apostle_of_end")
        repeat(stock) { assertNotNull(pool.take("apostle_of_end")) }

        assertEquals(0, pool.remaining("apostle_of_end"))
        assertNull("재고 0이면 null", pool.take("apostle_of_end"))
        assertNull("등급 전체가 비면 null", pool.takeRandomOfCost(5, Random(0)))
    }

    @Test
    fun `등급 무작위 추첨은 그 등급의 유닛만 돌려준다`() {
        val pool = UnitPool()
        val random = Random(42)
        repeat(50) {
            val unit = requireNotNull(pool.takeRandomOfCost(2, random))
            assertEquals("2코스트만 나와야 한다", 2, unit.cost)
        }
        assertEquals(4 * 20 - 50, pool.remainingOfCost(2))
    }

    @Test
    fun `추첨은 재고가 많은 유닛을 더 자주 뽑는다`() {
        val pool = UnitPool()
        // 1코스트 4종 중 3종의 재고를 1장만 남기고 모두 비운다.
        val ones = MasterData.unitsByCost.getValue(1)
        val plentiful = ones.first()
        ones.drop(1).forEach { unit ->
            repeat(pool.remaining(unit.id) - 1) { pool.take(unit.id) }
        }

        val random = Random(7)
        val draws = List(60) {
            val unit = requireNotNull(pool.takeRandomOfCost(1, random))
            pool.giveBack(unit.id)
            unit.id
        }
        val plentifulShare = draws.count { it == plentiful.id } / draws.size.toDouble()
        assertEquals("재고 22 대 1x3 이면 압도적으로 많이 뽑혀야 한다", true, plentifulShare > 0.7)
    }
}
