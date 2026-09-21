package com.leechanghyun.autobattler.core.masterdata

import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.TraitKind
import com.leechanghyun.autobattler.core.model.UnitClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 명세서 4장 표가 코드에 빠짐없이 들어왔는지 검증한다.
 *
 * 마스터 데이터는 손으로 옮겨 적는 값이라 누락/오타가 가장 잘 생기는 곳이다.
 * 여기서 막지 못하면 4~11단계 로직이 전부 잘못된 값 위에서 돌게 된다.
 */
class MasterDataTest {

    @Test
    fun `마스터 데이터의 종류 수가 맞다`() {
        assertEquals("유닛 24종 (명세서 14종 + 확장 10종)", 24, MasterData.units.size)
        assertEquals("시너지 8종", 8, MasterData.traits.size)
        assertEquals("아이템 컴포넌트 9종", 9, MasterData.itemComponents.size)
        assertEquals("완성 아이템 45종", 45, MasterData.completedItems.size)
        assertEquals("아이템 전체 54종", 54, MasterData.items.size)
        assertEquals("증강 10종", 10, MasterData.augments.size)
        assertEquals("몬스터 4종", 4, MasterData.monsters.size)
        assertEquals("유닛마다 스킬 1개", MasterData.units.size, MasterData.skills.size)
    }

    @Test
    fun `명세서 원본 14종은 그대로 남아 있다`() {
        assertEquals(14, SPEC_UNIT_DEFS.size)
        assertEquals(10, EXPANSION_UNIT_DEFS.size)
        assertTrue("원본 유닛은 전체 로스터에 그대로 포함된다", MasterData.units.containsAll(SPEC_UNIT_DEFS))
    }

    @Test
    fun `id 는 모두 고유하다`() {
        fun <T> assertUnique(label: String, ids: List<T>) =
            assertEquals("$label id 중복", ids.size, ids.toSet().size)

        assertUnique("유닛", MasterData.units.map { it.id })
        assertUnique("시너지", MasterData.traits.map { it.id })
        assertUnique("아이템", MasterData.items.map { it.id })
        assertUnique("증강", MasterData.augments.map { it.id })
        assertUnique("몬스터", MasterData.monsters.map { it.id })
        assertUnique("스킬", MasterData.skills.map { it.id })
    }

    @Test
    fun `모든 유닛이 존재하는 스킬을 가리킨다`() {
        MasterData.units.forEach { unit ->
            assertNotNull("유닛 ${unit.name} 의 스킬", MasterData.skill(unit.skillId))
        }
    }

    @Test
    fun `코스트별 유닛 수가 확장 후 분포와 일치한다`() {
        val byCost = MasterData.units.groupingBy { it.cost }.eachCount()
        assertEquals(mapOf(1 to 6, 2 to 6, 3 to 5, 4 to 4, 5 to 3), byCost)
        assertEquals(24, byCost.values.sum())
    }

    @Test
    fun `대표 유닛의 스탯이 명세서 표와 같다`() {
        val guard = MasterData.unit("steel_guard")
        assertEquals("강철수호병", guard.name)
        assertEquals(1, guard.cost)
        assertEquals(Origin.MECHA, guard.origin)
        assertEquals(UnitClass.WARDEN, guard.unitClass)
        assertEquals(700, guard.baseHp)
        assertEquals(50, guard.baseAttack)
        assertEquals(1, guard.attackRange)

        val apostle = MasterData.unit("apostle_of_end")
        assertEquals("종말의 사도", apostle.name)
        assertEquals(5, apostle.cost)
        assertEquals(Origin.ABYSSAL, apostle.origin)
        assertEquals(UnitClass.ARCANIST, apostle.unitClass)
        assertEquals(850, apostle.baseHp)
        assertEquals(110, apostle.baseAttack)
        assertEquals(3, apostle.attackRange)
    }

    @Test
    fun `계열 시너지는 2-4-6, 직업 시너지는 2-4 임계값을 쓴다`() {
        MasterData.traits.filter { it.kind == TraitKind.ORIGIN }.forEach {
            assertEquals("${it.name} 임계값", listOf(2, 4, 6), it.thresholds)
        }
        MasterData.traits.filter { it.kind == TraitKind.CLASS }.forEach {
            assertEquals("${it.name} 임계값", listOf(2, 4), it.thresholds)
        }
    }

    @Test
    fun `모든 계열과 직업에 대응하는 시너지가 있다`() {
        Origin.entries.forEach { assertNotNull(MasterData.trait(it)) }
        UnitClass.entries.forEach { assertNotNull(MasterData.trait(it)) }
    }

    /**
     * 확장한 로스터 24종으로 모든 시너지 임계값이 달성 가능한지 검증한다.
     *
     * 명세서 원본 14종에서는 계열 임계값 6을 어느 계열도 달성할 수 없었고,
     * 기계공학자/황금가문은 4도 막혀 있었다. 계열 6종 / 직업 6종으로 맞춰 해소했다.
     */
    @Test
    fun `모든 시너지 임계값이 로스터로 달성 가능하다`() {
        val originCounts = MasterData.units.groupingBy { it.origin }.eachCount()
        assertEquals(
            mapOf(
                Origin.MECHA to 6,
                Origin.STORM_TRIBE to 6,
                Origin.ABYSSAL to 6,
                Origin.GOLDEN_HOUSE to 6,
            ),
            originCounts,
        )

        val classCounts = MasterData.units.groupingBy { it.unitClass }.eachCount()
        assertEquals(
            mapOf(
                UnitClass.WARDEN to 6,
                UnitClass.ARCANIST to 6,
                UnitClass.BLADE to 6,
                UnitClass.MARKSMAN to 6,
            ),
            classCounts,
        )

        MasterData.traits.forEach { trait ->
            val available = when (trait.kind) {
                TraitKind.ORIGIN -> originCounts.getValue(Origin.entries.first { it.traitId == trait.id })
                TraitKind.CLASS -> classCounts.getValue(UnitClass.entries.first { it.traitId == trait.id })
            }
            val highest = trait.thresholds.max()
            assertTrue(
                "${trait.name} 의 최고 임계값 $highest 를 채우려면 ${highest}종이 필요한데 $available 종뿐이다",
                available >= highest,
            )
        }
    }

    @Test
    fun `증강 발동 라운드는 2-1, 3-2, 4-2 이다`() {
        assertEquals(listOf(2 to 1, 3 to 2, 4 to 2), AUGMENT_ROUNDS)
        assertEquals(3, AUGMENT_CHOICES_PER_ROUND)
    }

    @Test
    fun `크립 라운드 몬스터가 스테이지 1에서 4까지 하나씩 있다`() {
        assertEquals(listOf(1, 2, 3, 4), MasterData.monsters.map { it.stage }.sorted())
        assertEquals(listOf("1-4", "2-4", "3-4", "4-4"), MasterData.monsters.map { it.roundLabel }.sorted())

        val golem = requireNotNull(MasterData.monsterForStage(2))
        assertEquals(1800, golem.hp)
        assertEquals(2, golem.minions?.count)
        assertEquals(250, golem.minions?.hp)

        val dragon = requireNotNull(MasterData.monsterForStage(4))
        assertEquals(4500, dragon.hp)
        assertTrue("고대수호룡은 완성 아이템을 준다", dragon.rewardCompletedItem)
    }
}
