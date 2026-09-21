package com.leechanghyun.autobattler.core.synergy

import com.leechanghyun.autobattler.core.masterdata.MasterData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 보정 묶음의 합산 규칙과 적용 순서를 고정한다.
 *
 * 7단계 아이템과 9단계 증강이 같은 타입에 합류하므로, 여기서 정한 계약이 그때 그대로 쓰인다.
 */
class UnitBuffsTest {

    @Test
    fun `보정은 항목별로 더해진다`() {
        val a = UnitBuffs(hpFlat = 10, attackPercent = 0.1f, armorFlat = 15)
        val b = UnitBuffs(hpFlat = 5, attackPercent = 0.2f, armorFlat = 20)
        val sum = a + b

        assertEquals(15, sum.hpFlat)
        assertEquals(0.3f, sum.attackPercent, 1e-6f)
        assertEquals("방어력이 점수인 이유가 이것이다", 35, sum.armorFlat)
    }

    @Test
    fun `무보정은 합산의 항등원이고 결합법칙이 성립한다`() {
        val a = UnitBuffs(hpPercent = 0.2f, armorFlat = 15)
        val b = UnitBuffs(attackPercent = 0.35f, chainDamage = 60)
        val c = UnitBuffs(skillPowerPercent = 0.45f, attackSpeedPercent = 0.15f)

        assertEquals(a, a + UnitBuffs.NONE)
        assertEquals(a, UnitBuffs.NONE + a)
        assertEquals((a + b) + c, a + (b + c))
    }

    @Test
    fun `쉴드 주기는 더하지 않고 짧은 쪽을 쓴다`() {
        assertEquals(30, (UnitBuffs(shieldPeriodTicks = 30) + UnitBuffs.NONE).shieldPeriodTicks)
        assertEquals(30, (UnitBuffs.NONE + UnitBuffs(shieldPeriodTicks = 30)).shieldPeriodTicks)
        assertEquals(
            "주기는 더할 수 있는 양이 아니다",
            20,
            (UnitBuffs(shieldPeriodTicks = 30) + UnitBuffs(shieldPeriodTicks = 20)).shieldPeriodTicks,
        )
    }

    @Test
    fun `쉴드와 연쇄 번개는 필요한 값이 다 있어야 켜진다`() {
        assertFalse(UnitBuffs.NONE.hasShield)
        assertFalse("주기만 있고 양이 없으면 쉴드가 아니다", UnitBuffs(shieldPeriodTicks = 30).hasShield)
        assertTrue(UnitBuffs(shieldPeriodTicks = 30, shieldPercentOfMaxHp = 0.08f).hasShield)

        assertFalse(UnitBuffs.NONE.hasChain)
        assertFalse(UnitBuffs(chainChargePerAttack = 20).hasChain)
        assertTrue(UnitBuffs(chainChargePerAttack = 20, chainDamage = 60, chainTargets = 1).hasChain)
    }

    @Test
    fun `가산이 먼저고 비율이 나중이다`() {
        // 7단계 아이템의 +10 공격력이 검사 시너지 비율의 혜택을 함께 받는다는 결정을 못박는다.
        assertEquals("110 도 130 도 아니다", 132, UnitBuffs.scale(100, flat = 10, percent = 0.20f))
    }

    @Test
    fun `보정이 없으면 원래 값이 그대로다`() {
        listOf(0, 1, 7, 100, 700, 1800, 3240, 99999).forEach { value ->
            assertEquals("$value 는 무보정에서 그대로", value, UnitBuffs.scale(value, 0, 0f))
        }
    }

    @Test
    fun `수치표는 시너지 단계 수와 길이가 같고 단계마다 커진다`() {
        // 11단계 가드레일. SynergyTables 의 init 가 이미 검사하지만, 클래스를 건드리지 않는 변경으로도
        // 표가 깨지지 않도록 테스트로도 남긴다.
        fun check(traitId: String, values: List<Float>) {
            assertEquals("$traitId 수치표 길이", MasterData.trait(traitId).thresholds.size, values.size)
            assertTrue("$traitId 수치는 전부 양수", values.all { it > 0f })
            assertTrue("$traitId 수치는 단계마다 커진다", values.zipWithNext().all { (a, b) -> b > a })
        }

        check("mecha", SynergyTables.MECHA_ARMOR.map { it.toFloat() })
        check("abyssal", SynergyTables.ABYSSAL_SHIELD_PERCENT)
        check("golden_house", SynergyTables.GOLDEN_HOUSE_GOLD.map { it.toFloat() })
        check("storm_tribe", SynergyTables.STORM_CHARGE.map { it.toFloat() })
        check("storm_tribe", SynergyTables.STORM_DAMAGE.map { it.toFloat() })
        check("storm_tribe", SynergyTables.STORM_TARGETS.map { it.toFloat() })
        check("blade", SynergyTables.BLADE_ATTACK_PERCENT)
        check("arcanist", SynergyTables.ARCANIST_SKILL_POWER_PERCENT)
        check("marksman", SynergyTables.MARKSMAN_ATTACK_SPEED_PERCENT)
        check("warden", SynergyTables.WARDEN_HP_PERCENT)

        assertEquals("범위표가 8종을 빠짐없이 덮는다", MasterData.traits.map { it.id }.toSet(), SynergyTables.SCOPE.keys)
    }

    @Test
    fun `소환물은 로스터에 없다`() {
        // 로스터에 들어가면 공용 풀 재고가 되어 상점에 뜬다.
        assertTrue(MasterData.units.none { it.id == SynergyTables.GOLEM_UNIT_ID })
    }
}
