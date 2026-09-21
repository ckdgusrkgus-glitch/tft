package com.leechanghyun.autobattler.core.economy

import com.leechanghyun.autobattler.core.masterdata.EconomyRules
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.masterdata.SHOP_ODDS_BY_LEVEL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 로드맵 1단계 완료 기준 검증: **상점 5칸에 실제 유닛 데이터가 랜덤으로 노출된다.**
 *
 * 이 테스트가 통과하면 UI 없이도 "5칸이 채워지고", "채워진 값이 마스터 데이터의 실제 유닛이며",
 * "매번 달라진다"는 세 가지가 보장된다.
 */
class ShopRollerTest {

    private fun roller(seed: Int) = ShopRoller(UnitPool(), Random(seed))

    @Test
    fun `상점은 항상 5칸을 돌려준다`() {
        val offer = roller(1).roll(level = 5)
        assertEquals(EconomyRules.SHOP_SLOT_COUNT, offer.slots.size)
        assertEquals(5, offer.slots.size)
    }

    @Test
    fun `5칸이 모두 마스터 데이터의 실제 유닛으로 채워진다`() {
        val validIds = MasterData.units.map { it.id }.toSet()
        val offer = roller(2).roll(level = 6)

        assertEquals("빈 칸 없이 5칸", 5, offer.units.size)
        offer.units.forEach { unit ->
            assertTrue("실제 유닛 id 여야 한다: ${unit.id}", unit.id in validIds)
            assertNotNull("이름이 있어야 한다", unit.name)
            assertTrue("체력이 채워져 있어야 한다", unit.baseHp > 0)
            assertNotNull("스킬이 연결돼 있어야 한다", MasterData.skill(unit.skillId))
        }
    }

    @Test
    fun `시드가 다르면 상점 구성이 달라진다`() {
        val offers = (1..20).map { seed -> roller(seed).roll(level = 7).units.map { it.id } }
        assertTrue("20번 굴려 최소 10가지 이상 조합이 나와야 한다", offers.toSet().size >= 10)
    }

    @Test
    fun `같은 시드면 같은 상점이 나온다`() {
        val a = roller(99).roll(level = 7).units.map { it.id }
        val b = roller(99).roll(level = 7).units.map { it.id }
        assertEquals("시드 고정 시 재현 가능해야 한다", a, b)
    }

    @Test
    fun `레벨 2에서는 1코스트만 나온다`() {
        val roller = roller(5)
        var offer = roller.roll(level = 2)
        repeat(40) {
            offer.units.forEach { assertEquals("레벨 2는 1코스트 100%", 1, it.cost) }
            offer = roller.roll(level = 2, previous = offer)
        }
    }

    @Test
    fun `레벨 9에서는 5코스트도 나온다`() {
        val roller = roller(11)
        var offer = roller.roll(level = 9)
        val seenCosts = mutableSetOf<Int>()
        repeat(200) {
            offer.units.forEach { seenCosts += it.cost }
            offer = roller.roll(level = 9, previous = offer)
        }
        assertTrue("레벨 9는 5코스트 10% 확률이 있다", 5 in seenCosts)
        assertTrue("고레벨에서도 1코스트는 계속 나온다", 1 in seenCosts)
    }

    @Test
    fun `노출된 유닛은 풀에서 빠지고 리롤하면 되돌아온다`() {
        val pool = UnitPool()
        val roller = ShopRoller(pool, Random(3))
        val total = pool.totalRemaining()

        val offer = roller.roll(level = 6)
        assertEquals("상점에 뜬 5장만큼 재고가 준다", total - 5, pool.totalRemaining())

        roller.roll(level = 6, previous = offer)
        assertEquals("리롤하면 이전 5장이 돌아오고 새 5장이 빠진다", total - 5, pool.totalRemaining())

        roller.giveBackOffer(offer)
    }

    @Test
    fun `등급 추첨 분포가 확률표에 근사한다`() {
        val random = Random(123)
        val level = 7
        val trials = 30_000
        val counts = IntArray(6)
        repeat(trials) { counts[ShopOdds.rollCost(level, random)]++ }

        val expected = SHOP_ODDS_BY_LEVEL.getValue(level)
        expected.forEachIndexed { index, probability ->
            val cost = index + 1
            val actual = counts[cost] / trials.toDouble()
            assertEquals("레벨 $level 의 ${cost}코스트 비율", probability, actual, 0.015)
        }
    }

    @Test
    fun `풀이 마르면 빈 칸이 생길 뿐 예외가 나지 않는다`() {
        val pool = UnitPool()
        // 모든 유닛의 재고를 완전히 비운다.
        MasterData.units.forEach { unit ->
            repeat(pool.remaining(unit.id)) { pool.take(unit.id) }
        }
        assertEquals(0, pool.totalRemaining())

        val offer = ShopRoller(pool, Random(1)).roll(level = 8)
        assertEquals("칸 수는 유지된다", 5, offer.slots.size)
        assertEquals("채워진 유닛은 없다", 0, offer.units.size)
        assertTrue("모든 칸이 빈 칸", offer.slots.all { it.isEmpty })
    }
}
