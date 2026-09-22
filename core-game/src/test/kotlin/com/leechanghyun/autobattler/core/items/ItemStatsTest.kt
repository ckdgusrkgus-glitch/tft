package com.leechanghyun.autobattler.core.items

import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.ItemDef
import com.leechanghyun.autobattler.core.model.StatType
import com.leechanghyun.autobattler.core.synergy.UnitBuffs
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 아이템 → [UnitBuffs] 환산 규칙을 고정한다. 로드맵 7단계.
 *
 * 수치를 쓰는 단언은 마스터 데이터 대신 **직접 만든 아이템**을 쓴다. 11단계 밸런스에서
 * 컴포넌트 수치를 조정할 때 규칙 테스트가 같이 빨개지면 안 되기 때문이다.
 */
class ItemStatsTest {

    /** 규칙만 검사하기 위한 가짜 아이템. 마스터 데이터 수치와 무관하다. */
    private fun synthetic(id: String, vararg stats: Pair<StatType, Float>) = ItemDef(
        id = id,
        name = id,
        statModifiers = stats.toMap(),
        isComponent = true,
    )

    private fun nonDefaultFieldsOf(buffs: UnitBuffs): List<String> =
        UnitBuffs::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .filter { field ->
                field.isAccessible = true
                when (val value = field.get(buffs)) {
                    is Int -> value != 0
                    is Float -> value != 0f
                    else -> false
                }
            }
            .map { it.name }

    @Test
    fun `아이템이 없으면 무보정이다`() {
        assertEquals(UnitBuffs.NONE, ItemStats.buffsOf(emptyList()))
    }

    @Test
    fun `스탯 하나는 정확히 항목 하나로만 간다`() {
        // targetFieldOf 는 컴파일러용 덫이라 buffsOf 가 부르지 않는다. 둘이 어긋나면
        // 덫은 멀쩡한데 실제 환산만 틀린 상태가 되므로 여기서 대조한다.
        StatType.entries.forEach { stat ->
            val buffs = ItemStats.buffsOf(synthetic("syn_${stat.name}", stat to 1f))
            val target = ItemStats.targetFieldOf(stat)
            val changed = nonDefaultFieldsOf(buffs)

            if (target == null) {
                assertEquals("$stat 은 아직 전투에 닿지 않는다고 선언했는데 보정이 생겼다", emptyList<String>(), changed)
            } else {
                assertEquals("$stat 은 $target 하나로만 가야 한다", listOf(target), changed)
            }
        }
    }

    @Test
    fun `반올림은 아이템마다가 아니라 합계에 한 번만 한다`() {
        // 0.5 짜리 둘. 아이템마다 반올림하면 1 + 1 = 2 가 되고, 합계에 한 번만 하면 1.0 -> 1 이다.
        val half = synthetic("syn_half", StatType.MAX_HP to 0.5f)
        assertEquals(
            "아이템마다 반올림하면 컴포넌트 2개의 합 == 완성 아이템 이라는 전제가 깨진다",
            1,
            ItemStats.buffsOf(listOf(half, half)).hpFlat,
        )

        val small = synthetic("syn_small", StatType.ATTACK_DAMAGE to 0.4f)
        assertEquals(0, ItemStats.buffsOf(small).attackFlat)
        assertEquals("0.4 셋은 1.2 이므로 1 이다", 1, ItemStats.buffsOf(listOf(small, small, small)).attackFlat)
    }

    @Test
    fun `방어력과 마법저항력은 아직 한 풀이다`() {
        // 쪼개려면 먼저 모든 피해에 물리/마법 종류를 붙여야 한다. 7단계에서 하지 않기로 한 결정이며
        // 이 테스트가 빨개지는 날이 그 결정을 뒤집은 날이다. UnitBuffs 문서도 함께 고쳐라.
        val armor = synthetic("syn_armor", StatType.ARMOR to 7f)
        val resist = synthetic("syn_resist", StatType.MAGIC_RESIST to 11f)

        assertEquals(7, ItemStats.buffsOf(armor).armorFlat)
        assertEquals(11, ItemStats.buffsOf(resist).armorFlat)
        assertEquals("둘이 같은 항목에 쌓인다", 18, ItemStats.buffsOf(listOf(armor, resist)).armorFlat)
        assertEquals(listOf("armorFlat"), nonDefaultFieldsOf(ItemStats.buffsOf(listOf(armor, resist))))
    }

    @Test
    fun `치명타와 체력 재생은 아직 전투에 닿지 않는다`() {
        // 전투에 난수가 한 줄도 없어 치명타 확률을 쓸 수 없고, 틱마다 회복시키는 장치도 없다.
        // 아무 항목에나 흘려 넣으면 "붙었다"는 착각만 만들어 11단계 밸런스가 틀린 값 위에서 돌아간다.
        assertEquals(
            UnitBuffs.NONE,
            ItemStats.buffsOf(synthetic("syn_crit", StatType.CRIT_CHANCE to 0.9f)),
        )
        assertEquals(
            UnitBuffs.NONE,
            ItemStats.buffsOf(synthetic("syn_regen", StatType.HP_REGEN to 99f)),
        )
    }

    @Test
    fun `여러 아이템의 같은 스탯은 더해진다`() {
        val ad = synthetic("syn_ad", StatType.ATTACK_DAMAGE to 6f)
        assertEquals(6, ItemStats.buffsOf(listOf(ad)).attackFlat)
        assertEquals(12, ItemStats.buffsOf(listOf(ad, ad)).attackFlat)
        assertEquals("같은 아이템 중복 장착 금지 규칙은 명세서에 없다", 18, ItemStats.buffsOf(listOf(ad, ad, ad)).attackFlat)
    }

    @Test
    fun `아이템 하나가 여러 스탯을 줘도 각자 제 항목으로 간다`() {
        val mixed = synthetic(
            "syn_mixed",
            StatType.MAX_HP to 100f,
            StatType.ATTACK_DAMAGE to 5f,
            StatType.ABILITY_POWER to 8f,
            StatType.ATTACK_SPEED to 0.25f,
            StatType.ARMOR to 3f,
            StatType.MANA to 12f,
        )
        val buffs = ItemStats.buffsOf(mixed)

        assertEquals(100, buffs.hpFlat)
        assertEquals(5, buffs.attackFlat)
        assertEquals(8, buffs.skillPowerFlat)
        assertEquals(0.25f, buffs.attackSpeedPercent, 1e-6f)
        assertEquals(3, buffs.armorFlat)
        assertEquals(12, buffs.startingManaFlat)
    }

    @Test
    fun `아이템 보정에 비율 항목은 공격속도뿐이다`() {
        // 아이템은 전부 가산이다. 비율 항목에 가산 수치가 잘못 흘러들면 +150 체력이 +15000% 가 된다.
        val everyStat = synthetic("syn_every", *StatType.entries.map { it to 1f }.toTypedArray())
        val buffs = ItemStats.buffsOf(everyStat)

        assertEquals(0f, buffs.hpPercent, 0f)
        assertEquals(0f, buffs.attackPercent, 0f)
        assertEquals(0f, buffs.skillPowerPercent, 0f)
        assertEquals(0f, buffs.shieldPercentOfMaxHp, 0f)
        assertEquals("공격속도만 비율이다", 1f, buffs.attackSpeedPercent, 0f)
    }

    @Test
    fun `아이템은 쉴드나 연쇄 번개를 주지 않는다`() {
        // 그 둘은 시너지 전용이다. 아이템이 건드리면 심연의 아이들 단계 테스트가 조용히 흔들린다.
        val everyStat = synthetic("syn_every", *StatType.entries.map { it to 50f }.toTypedArray())
        val buffs = ItemStats.buffsOf(everyStat)

        assertEquals(0, buffs.shieldPeriodTicks)
        assertEquals(0, buffs.chainChargePerAttack)
        assertEquals(0, buffs.chainDamage)
        assertEquals(0, buffs.chainTargets)
    }

    @Test
    fun `완성 아이템의 보정은 재료 컴포넌트 둘의 보정과 같다`() {
        // 45종 전부. CompletedItems 가 스탯을 재료 합으로 만들고, 환산도 합계에 한 번만 반올림하므로
        // "조합해서 끼는 것"과 "재료 둘을 그냥 끼는 것"이 스탯상 완전히 같아야 한다.
        MasterData.completedItems.forEach { completed ->
            val components = completed.recipe.map { MasterData.item(it) }
            assertEquals(
                "${completed.name}(${completed.id})",
                ItemStats.buffsOf(components),
                ItemStats.buffsOf(completed),
            )
        }
    }

    @Test
    fun `컴포넌트 9종은 전부 알려진 스탯만 쓴다`() {
        MasterData.itemComponents.forEach { component ->
            assertTrue("${component.name} 이 스탯을 하나도 안 준다", component.statModifiers.isNotEmpty())
        }
        // 9종 중 관통(치명타)·재생(체력재생) 둘만 아직 전투에 닿지 않는다. 그 둘이 늘거나 줄면 알아야 한다.
        val silent = MasterData.itemComponents.filter { ItemStats.buffsOf(it) == UnitBuffs.NONE }
        assertEquals(
            "전투에 닿지 않는 컴포넌트가 바뀌었다",
            listOf("comp_pierce", "comp_regen"),
            silent.map { it.id }.sorted(),
        )
    }

    @Test
    fun `고유 효과는 아직 스탯 환산에 영향을 주지 않는다`() {
        // effectId 를 읽는 코드가 아직 없다는 사실을 못박는다. 45종 중 하나라도 effectId 로 분기하기
        // 시작하면 여기가 빨개진다. 그때 이 테스트를 지우는 것이 구현의 첫 단계다.
        MasterData.completedItems.forEach { completed ->
            assertNotEquals("${completed.id} 에 효과 키가 없다", "", completed.effectId)
            assertEquals(
                "${completed.id} 의 스탯 환산이 effectId 에 따라 달라졌다",
                ItemStats.buffsOf(completed.copy(effectId = "")),
                ItemStats.buffsOf(completed),
            )
        }
    }
}
