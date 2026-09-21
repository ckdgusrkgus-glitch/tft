package com.leechanghyun.autobattler.core.synergy

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.board.OffsetCoord
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.masterdata.traitId
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.UnitClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 로드맵 5단계 완료 기준의 앞쪽 절반을 검증한다: **보드 구성이 시너지 단계를 정확히 정한다.**
 *
 * 밸런스 수치는 11단계에서 전부 바뀌므로 이 파일의 단언에는 수치가 거의 등장하지 않는다.
 * 규칙만 본다.
 */
class SynergyEngineTest {

    private val wardenId = UnitClass.WARDEN.traitId

    @Test
    fun `시너지는 보드 구성만의 순수 함수다`() {
        val defs = SynergyFixtures.defsOf(UnitClass.WARDEN, 4)

        val first = SynergyEngine.resolve(SynergyFixtures.board(defs, idPrefix = "a"))
        // 같은 유닛 종류를 다른 개체 id, 다른 좌표, 다른 성 등급으로 다시 올린다.
        val shuffled = defs.reversed().mapIndexed { index, def ->
            BoardUnit(
                instanceId = "z$index",
                unitDef = def,
                starLevel = if (index % 2 == 0) 2 else 3,
                position = HexBoard.fromOffset(OffsetCoord(row = index % 4, col = 6 - index)),
            )
        }
        val second = SynergyEngine.resolve(shuffled)

        assertEquals("개체와 좌표와 성 등급은 시너지에 영향을 주지 않는다", first, second)
        assertEquals("같은 보드를 두 번 풀어도 같다", first, SynergyEngine.resolve(shuffled))
    }

    @Test
    fun `임계값을 넘는 순간 단계가 정확히 1 오른다`() {
        // 8종 전부를 0명부터 6명까지 훑는다. 밸런스 수치가 한 개도 등장하지 않는다.
        MasterData.traits.forEach { trait ->
            for (count in 0..6) {
                val board = SynergyFixtures.board(SynergyFixtures.defsOfTrait(trait.id, count))
                val active = SynergyEngine.activeTraits(board).firstOrNull { it.traitId == trait.id }

                val expectedTier = trait.thresholds.count { count >= it }
                if (count == 0) {
                    assertNull("${trait.name} 구성원이 없으면 목록에도 없다", active)
                    continue
                }
                requireNotNull(active)
                assertEquals("${trait.name} $count 명의 단계", expectedTier, active.tier)
                assertEquals("${trait.name} $count 명의 인원", count, active.memberCount)
                assertEquals(
                    "${trait.name} $count 명의 발동 임계값",
                    trait.thresholds.lastOrNull { count >= it },
                    active.activeThreshold,
                )
                assertEquals(
                    "${trait.name} $count 명의 다음 임계값",
                    trait.thresholds.firstOrNull { count < it },
                    active.nextThreshold,
                )
            }
        }
    }

    @Test
    fun `미달이어도 목록에 남고 화면이 쓸 값을 준다`() {
        val state = SynergyEngine.resolve(SynergyFixtures.board(SynergyFixtures.defsOf(UnitClass.WARDEN, 1)))
        val warden = requireNotNull(state.traitOf(wardenId))

        assertEquals(1, warden.memberCount)
        assertEquals(0, warden.tier)
        assertFalse(warden.isActive)
        assertNull(warden.activeThreshold)
        assertNull("미발동이면 효과 문구도 없다", warden.effectText)
        assertEquals("화면의 회색 '수호자 1/2' 표기", 2, warden.nextThreshold)
        assertTrue("목록에는 남는다", state.activeTraits.any { it.traitId == wardenId })
        assertTrue("발동 목록에는 없다", state.active.none { it.traitId == wardenId })
        assertEquals("미발동 시너지는 버프를 주지 않는다", TeamBuffs.NONE, state.combat)
    }

    @Test
    fun `발동하면 명세서 효과 문구가 함께 온다`() {
        val state = SynergyEngine.resolve(SynergyFixtures.board(SynergyFixtures.defsOf(UnitClass.WARDEN, 2)))
        val warden = requireNotNull(state.traitOf(wardenId))

        assertEquals(1, warden.tier)
        assertEquals(MasterData.trait(wardenId).effectsByThreshold[2], warden.effectText)
    }

    @Test
    fun `임계값 할인은 요구 인원을 낮추고 1 미만으로 내려가지 않는다`() {
        // 9단계 증강 전열강화("수호자 시너지 임계값 요구 인원 -1")가 리팩터 없이 들어옴을 증명한다.
        val board = SynergyFixtures.board(SynergyFixtures.defsOf(UnitClass.WARDEN, 1))

        val discounted = SynergyEngine.activeTraits(board, mapOf(wardenId to 1))
            .first { it.traitId == wardenId }
        assertEquals("1명으로도 발동한다", 1, discounted.tier)
        assertEquals(1, discounted.activeThreshold)
        assertEquals(3, discounted.nextThreshold)

        val huge = SynergyEngine.activeTraits(board, mapOf(wardenId to 5)).first { it.traitId == wardenId }
        assertTrue("임계값은 1 밑으로 내려가지 않는다", huge.effectiveThresholds.all { it >= 1 })
        assertEquals(
            "빈 보드는 아무리 할인해도 발동하지 않는다",
            emptyList<ActiveTrait>(),
            SynergyEngine.activeTraits(emptyList(), mapOf(wardenId to 5)),
        )
    }

    @Test
    fun `효과 문구는 할인 전 임계값을 키로 찾는다`() {
        // effectsByThreshold 의 키는 기본 임계값이다. 할인된 값으로 찾으면 키가 없어 null 이 된다.
        val board = SynergyFixtures.board(SynergyFixtures.defsOf(UnitClass.WARDEN, 1))
        val discounted = SynergyEngine.activeTraits(board, mapOf(wardenId to 1))
            .first { it.traitId == wardenId }

        assertEquals(1, discounted.tier)
        assertEquals(MasterData.trait(wardenId).effectsByThreshold[2], discounted.effectText)
    }

    @Test
    fun `할인이 없으면 엔진과 마스터 데이터의 임계값 판정이 일치한다`() {
        // 새로 만든 단계 계산과 기존 TraitDef.activeThreshold 가 갈라질 수 없게 못박는다.
        MasterData.traits.forEach { trait ->
            for (count in 1..6) {
                val active = ActiveTrait.of(trait, count)
                assertEquals(
                    "${trait.name} $count 명",
                    trait.activeThreshold(count),
                    active.activeThreshold,
                )
            }
        }
    }

    @Test
    fun `모든 시너지가 선언한 범위대로 실제 효과를 낸다`() {
        // SynergyTables.SCOPE 는 문서용 데이터라 엔진이 읽지 않는다. 그래서 엔진의 when 과 어긋나도
        // 컴파일러가 잡아 주지 못한다. 이 테스트가 둘을 묶어 둔다.
        // 엔진의 when 에 케이스가 빠져도 여기서 터진다.
        MasterData.traits.forEach { trait ->
            val defs = SynergyFixtures.defsOfTrait(trait.id, trait.thresholds.last())
            val state = SynergyEngine.resolve(SynergyFixtures.board(defs))
            assertEquals("${trait.name} 최고 단계", trait.thresholds.size, state.tierOf(trait.id))

            when (requireNotNull(SynergyTables.SCOPE[trait.id])) {
                TraitScope.TEAM -> assertNotEquals(
                    "${trait.name} 는 아군 전체 범위인데 팀 버프가 비어 있다",
                    UnitBuffs.NONE,
                    state.combat.teamWide,
                )

                // 해당 태그 칸에 실제로 값이 들어갔는지 본다. "무언가 바뀌었다"로는 부족하다.
                // 같은 보드가 다른 시너지도 함께 올리기 때문이다.
                TraitScope.TAGGED -> assertNotEquals(
                    "${trait.name} 는 태그 한정인데 그 태그 칸이 비어 있다",
                    UnitBuffs.NONE,
                    taggedBuffOf(trait.id, state),
                )

                TraitScope.ECONOMY -> assertTrue(
                    "${trait.name} 는 경제 효과인데 골드가 0 이다",
                    state.goldPerRound > 0,
                )
            }
        }
    }

    @Test
    fun `태그 한정 버프는 그 태그가 없는 유닛에게 가지 않는다`() {
        // 범위를 잘못 배선하면 검사 공격력 버프가 마법사에게도 붙는다. 조용히 지나가기 쉬운 실수다.
        // 각 시너지가 건드리는 필드는 그 시너지만 쓰므로, 다른 시너지가 함께 발동해도 이 비교는 성립한다.
        val probes: List<Triple<String, (UnitBuffs) -> Float, String>> = listOf(
            Triple(UnitClass.BLADE.traitId, { it.attackPercent }, "공격력"),
            Triple(UnitClass.ARCANIST.traitId, { it.skillPowerPercent }, "주문력"),
            Triple(UnitClass.MARKSMAN.traitId, { it.attackSpeedPercent }, "공격속도"),
            Triple(UnitClass.WARDEN.traitId, { it.hpPercent }, "최대 체력"),
            Triple(Origin.STORM_TRIBE.traitId, { it.chainChargePerAttack.toFloat() }, "연쇄 충전"),
        )

        probes.forEach { (traitId, field, label) ->
            val defs = SynergyFixtures.defsOfTrait(traitId, MasterData.trait(traitId).thresholds.last())
            val state = SynergyEngine.resolve(SynergyFixtures.board(defs))
            val outsider = MasterData.units.first { it.origin.traitId != traitId && it.unitClass.traitId != traitId }

            assertTrue("${MasterData.trait(traitId).name} 구성원은 $label 버프를 받는다", field(state.combat.forUnit(defs[0])) > 0f)
            assertEquals(
                "${MasterData.trait(traitId).name} 가 아닌 ${outsider.name} 에게 $label 버프가 샜다",
                0f,
                field(state.combat.forUnit(outsider)),
                1e-6f,
            )
        }
    }

    /** 해당 시너지 태그 칸에 들어간 버프. 계열이면 byOrigin, 직업이면 byClass 에서 꺼낸다. */
    private fun taggedBuffOf(traitId: String, state: SynergyState): UnitBuffs {
        Origin.entries.firstOrNull { it.traitId == traitId }?.let { return state.combat.byOrigin[it] ?: UnitBuffs.NONE }
        val unitClass = requireNotNull(UnitClass.entries.firstOrNull { it.traitId == traitId })
        return state.combat.byClass[unitClass] ?: UnitBuffs.NONE
    }

    @Test
    fun `빈 보드는 아무 효과도 없다`() {
        val state = SynergyEngine.resolve(emptyList())
        assertEquals(emptyList<ActiveTrait>(), state.activeTraits)
        assertEquals(TeamBuffs.NONE, state.combat)
        assertEquals(0, state.goldPerRound)
        assertEquals(0, state.tierOf(wardenId))
        assertEquals(0, state.memberCountOf(wardenId))
    }

    @Test
    fun `시너지 순서는 마스터 데이터 순서를 따라 흔들리지 않는다`() {
        val board = SynergyFixtures.board(SynergyFixtures.defsOf(UnitClass.WARDEN, 3))
        val ids = SynergyEngine.resolve(board).activeTraits.map { it.traitId }
        assertEquals(ids, MasterData.traits.map { it.id }.filter { it in ids })
    }
}
