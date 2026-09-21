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
    fun `명세서가 정한 종류 수와 일치한다`() {
        assertEquals("유닛 14종", 14, MasterData.units.size)
        assertEquals("시너지 8종", 8, MasterData.traits.size)
        assertEquals("아이템 컴포넌트 9종", 9, MasterData.itemComponents.size)
        assertEquals("증강 10종", 10, MasterData.augments.size)
        assertEquals("몬스터 4종", 4, MasterData.monsters.size)
        assertEquals("유닛마다 스킬 1개", MasterData.units.size, MasterData.skills.size)
    }

    @Test
    fun `id 는 모두 고유하다`() {
        fun <T> assertUnique(label: String, ids: List<T>) =
            assertEquals("$label id 중복", ids.size, ids.toSet().size)

        assertUnique("유닛", MasterData.units.map { it.id })
        assertUnique("시너지", MasterData.traits.map { it.id })
        assertUnique("아이템", MasterData.itemComponents.map { it.id })
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
    fun `코스트별 유닛 수가 명세서 로스터와 일치한다`() {
        val byCost = MasterData.units.groupingBy { it.cost }.eachCount()
        assertEquals(mapOf(1 to 4, 2 to 4, 3 to 3, 4 to 2, 5 to 1), byCost)
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
     * 로스터 14종으로는 도달할 수 없는 시너지 임계값이 있다는 사실을 고정해 둔다.
     *
     * 계열별 유닛 수가 3~4종뿐이라 임계값 6 은 어떤 계열도 달성할 수 없고,
     * 기계공학자/황금가문/수호자/사수는 4 도 달성할 수 없다.
     * 명세서 4-4 표 자체의 한계이므로 버그가 아니라 **기록해 둔 제약**이다.
     * 로드맵 5단계(시너지 엔진)와 11단계(밸런스)에서 로스터를 늘리거나 임계값을 낮춰야 한다.
     */
    @Test
    fun `로스터로 도달 가능한 시너지 임계값을 기록한다`() {
        val originCounts = MasterData.units.groupingBy { it.origin }.eachCount()
        assertEquals(
            mapOf(
                Origin.MECHA to 3,
                Origin.STORM_TRIBE to 4,
                Origin.ABYSSAL to 4,
                Origin.GOLDEN_HOUSE to 3,
            ),
            originCounts,
        )

        val classCounts = MasterData.units.groupingBy { it.unitClass }.eachCount()
        assertEquals(
            mapOf(
                UnitClass.WARDEN to 3,
                UnitClass.ARCANIST to 4,
                UnitClass.BLADE to 4,
                UnitClass.MARKSMAN to 3,
            ),
            classCounts,
        )

        assertTrue(
            "계열 임계값 6 은 현재 로스터로 달성 불가",
            originCounts.values.all { it < 6 },
        )
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
