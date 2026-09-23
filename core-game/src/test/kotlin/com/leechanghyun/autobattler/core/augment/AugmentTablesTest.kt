package com.leechanghyun.autobattler.core.augment

import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 로드맵 9단계. 증강 마스터 데이터와 수치표가 서로 어긋나지 않는지 본다. */
class AugmentTablesTest {

    @Test
    fun `마스터 데이터의 효과 id 10종이 전부 해석된다`() {
        val effects = MasterData.augments.map { it.effect }

        assertEquals("증강 10종의 효과가 서로 달라야 한다", AugmentEffect.entries.toSet(), effects.toSet())
        assertEquals(AugmentEffect.entries.size, effects.size)
    }

    @Test
    fun `모든 효과가 바꾸는 면을 선언한다`() {
        assertEquals(AugmentEffect.entries.toSet(), AugmentTables.SCOPE.keys)
    }

    @Test
    fun `체력 대가는 탈락시키지 않는다`() {
        // 명세서의 "체력 -100" 과 이 엔진의 시작 체력 100 이 부딪친다. 그대로 빼면 hp 가 0 이 되어
        // PlayerState.isEliminated 가 참이 되고, 고르는 순간 지는 선택지가 된다.
        val pact = MasterData.augments.first { it.effect == AugmentEffect.HP_FOR_GOLD }
        assertTrue("대가가 없으면 막을 것도 없다", AugmentRules.hpCostOf(pact) > 0)

        for (hp in 1..PlayerState.STARTING_HP) {
            val left = AugmentRules.hpAfterCost(hp, pact)
            assertTrue("체력 $hp 에서 고르면 $left 이 되어 탈락한다", left >= 1)
            assertTrue("남는 체력이 원래보다 많을 수는 없다", left <= hp)
        }
    }

    @Test
    fun `대가가 없는 증강은 체력을 건드리지 않는다`() {
        MasterData.augments
            .filter { it.effect != AugmentEffect.HP_FOR_GOLD }
            .forEach { augment ->
                assertEquals("${augment.name} 이 체력을 가져간다", 0, AugmentRules.hpCostOf(augment))
                assertEquals(
                    PlayerState.STARTING_HP,
                    AugmentRules.hpAfterCost(PlayerState.STARTING_HP, augment),
                )
            }
    }
}
