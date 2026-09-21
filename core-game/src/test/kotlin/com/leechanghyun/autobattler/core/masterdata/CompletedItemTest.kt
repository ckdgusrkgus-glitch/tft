package com.leechanghyun.autobattler.core.masterdata

import com.leechanghyun.autobattler.core.model.ItemDef
import com.leechanghyun.autobattler.core.model.StatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 완성 아이템 9x9 조합표를 검증한다.
 *
 * 컴포넌트 9종을 2개씩 조합하면 순서를 무시했을 때 9 x 10 / 2 = 45가지가 나온다.
 * 이 45칸이 빠짐없이, 중복 없이 채워져 있어야 로드맵 7단계(아이템 조합)가 성립한다.
 */
class CompletedItemTest {

    private val componentIds = MasterData.itemComponents.map { it.id }

    @Test
    fun `조합 가능한 45가지가 빠짐없이 채워져 있다`() {
        val expected = buildSet {
            componentIds.forEachIndexed { i, first ->
                componentIds.drop(i).forEach { second ->
                    add(ItemDef.recipeKeyOf(first, second))
                }
            }
        }
        assertEquals("경우의 수는 45가지", 45, expected.size)

        val actual = MasterData.completedItems.map { it.recipeKey }.toSet()
        assertEquals("조합식에 중복이 없다", MasterData.completedItems.size, actual.size)
        assertEquals("45칸이 모두 채워져 있다", expected, actual)
    }

    @Test
    fun `어떤 컴포넌트 두 개를 조합해도 완성 아이템이 나온다`() {
        componentIds.forEach { first ->
            componentIds.forEach { second ->
                assertNotNull(
                    "$first + $second 조합이 비어 있다",
                    MasterData.combine(first, second),
                )
            }
        }
    }

    @Test
    fun `조합은 순서와 무관하다`() {
        assertEquals(
            MasterData.combine("comp_might", "comp_wisdom"),
            MasterData.combine("comp_wisdom", "comp_might"),
        )
    }

    @Test
    fun `완성 아이템으로는 조합할 수 없다`() {
        assertNull(MasterData.combine("item_infinity_blade", "comp_might"))
        assertNull(MasterData.combine("comp_might", "없는_컴포넌트"))
    }

    @Test
    fun `완성 아이템의 스탯은 재료 두 개의 합이다`() {
        MasterData.completedItems.forEach { completed ->
            val expected = mutableMapOf<StatType, Float>()
            completed.recipe.forEach { componentId ->
                MasterData.item(componentId).statModifiers.forEach { (stat, value) ->
                    expected[stat] = (expected[stat] ?: 0f) + value
                }
            }
            assertEquals("${completed.name} 의 스탯", expected, completed.statModifiers)
        }
    }

    @Test
    fun `같은 컴포넌트 두 개로 만든 아이템은 해당 스탯이 두 배다`() {
        val deathblade = requireNotNull(MasterData.combine("comp_might", "comp_might"))
        assertEquals("처형검", deathblade.name)
        assertEquals(
            MasterData.item("comp_might").statModifiers.getValue(StatType.ATTACK_DAMAGE) * 2f,
            deathblade.statModifiers.getValue(StatType.ATTACK_DAMAGE),
            1e-4f,
        )
    }

    @Test
    fun `모든 완성 아이템이 이름과 효과 설명을 가진다`() {
        MasterData.completedItems.forEach { item ->
            assertTrue("${item.id} 의 이름이 비었다", item.name.isNotBlank())
            assertTrue("${item.id} 의 효과 설명이 비었다", item.description.isNotBlank())
            assertTrue("${item.id} 의 effectId 가 비었다", item.effectId.isNotBlank())
            assertTrue("${item.id} 는 완성 아이템이어야 한다", !item.isComponent)
            assertEquals("${item.id} 의 재료는 2개", 2, item.recipe.size)
        }
    }

    @Test
    fun `컴포넌트는 조합식과 효과 설명을 갖지 않는다`() {
        MasterData.itemComponents.forEach { component ->
            assertTrue(component.isComponent)
            assertTrue("${component.id} 는 조합식이 없어야 한다", component.recipe.isEmpty())
            assertEquals("", component.description)
        }
    }
}
