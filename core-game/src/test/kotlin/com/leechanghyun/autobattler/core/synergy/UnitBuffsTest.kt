package com.leechanghyun.autobattler.core.synergy

import com.leechanghyun.autobattler.core.masterdata.MasterData
import java.lang.reflect.Modifier
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
    fun `모든 보정 항목이 빠짐없이 더해진다`() {
        // [UnitBuffs.plus] 에 줄 하나를 빠뜨리면 그 항목만 조용히 0 이 된다. 시너지나 아이템 하나가
        // 통째로 무효가 되는데 기존 테스트는 전부 통과한다. 항목이 늘어날 때마다 생기는 사고라
        // 반사성 검사(a + NONE == a)로 막는다. 항목이 하나라도 0 이면 그 항목은 검사되지 않으므로
        // 리플렉션으로 "전부 0 이 아니다"까지 강제한다.
        val all = UnitBuffs(
            hpFlat = 11,
            hpPercent = 0.12f,
            attackFlat = 13,
            attackPercent = 0.14f,
            skillPowerFlat = 15,
            skillPowerPercent = 0.16f,
            attackSpeedPercent = 0.17f,
            armorFlat = 18,
            shieldPercentOfMaxHp = 0.19f,
            shieldPeriodTicks = 20,
            chainChargePerAttack = 21,
            chainDamage = 22,
            chainTargets = 23,
            critChargePerAttack = 24,
            startingManaFlat = 25,
        )

        val fields = UnitBuffs::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
        assertEquals(
            "UnitBuffs 에 항목을 더했으면 위 all 에도 0 이 아닌 값을 넣어라",
            15,
            fields.size,
        )
        fields.forEach { field ->
            field.isAccessible = true
            val isDefault = when (val value = field.get(all)) {
                is Int -> value == 0
                is Float -> value == 0f
                else -> throw AssertionError("${field.name} 타입은 이 테스트가 모른다: ${value?.javaClass}")
            }
            assertFalse("${field.name} 이 0 이면 아래 단언이 그 항목을 검사하지 못한다", isDefault)
        }

        assertEquals("plus 에서 빠진 항목이 있으면 여기서 0 이 되어 어긋난다", all, all + UnitBuffs.NONE)
        assertEquals(all, UnitBuffs.NONE + all)
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
