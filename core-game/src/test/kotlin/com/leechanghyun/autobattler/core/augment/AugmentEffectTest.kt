package com.leechanghyun.autobattler.core.augment

import com.leechanghyun.autobattler.core.economy.Economy
import com.leechanghyun.autobattler.core.economy.ShopRoller
import com.leechanghyun.autobattler.core.economy.UnitPool
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.masterdata.traitId
import com.leechanghyun.autobattler.core.model.AugmentDef
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.model.StageRound
import com.leechanghyun.autobattler.core.model.UnitClass
import com.leechanghyun.autobattler.core.planning.PlanningResult
import com.leechanghyun.autobattler.core.planning.PlanningSession
import com.leechanghyun.autobattler.core.synergy.SynergyEngine
import com.leechanghyun.autobattler.core.synergy.SynergyFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 로드맵 9단계 완료 기준의 **적용** 절반. 증강 10종이 하나도 빠짐없이 실제로 무언가를 바꾼다.
 *
 * 효과가 표에만 있고 처리 분기가 없어도 [AugmentDef.effectId] 가 문자열인 한 컴파일은 통과하고
 * 화면에도 정상으로 뜬다. 고르면 아무 일도 일어나지 않을 뿐이다. 그 실패를 잡는 것이 이 파일이다.
 *
 * 방법은 시너지 쪽 `SynergyEngineTest.모든 시너지가 선언한 범위대로 실제 효과를 낸다` 와 같다.
 * [AugmentTables.SCOPE] 가 "이 증강은 이 면을 바꾼다" 고 선언하면 그 면을 실제로 읽어 본다.
 * 밸런스 수치는 한 번도 단언하지 않으므로 11단계 튜닝이 이 파일을 깨지 않는다.
 */
class AugmentEffectTest {

    /** 수호자 3명. 전열강화(임계값 -1)가 단계를 올리는 것이 보이는 최소 구성이다. */
    private val board: List<BoardUnit> = SynergyFixtures.board(SynergyFixtures.defsOf(UnitClass.WARDEN, 3))

    private val base = PlayerState(
        playerId = "p",
        displayName = "나",
        isBot = false,
        gold = 20,
        board = board,
    )

    private fun defOf(effect: AugmentEffect): AugmentDef =
        MasterData.augments.first { it.effect == effect }

    @Test
    fun `증강 10종이 모두 선언한 면을 실제로 바꾼다`() {
        AugmentEffect.entries.forEach { effect ->
            val augment = defOf(effect)
            val scope = requireNotNull(AugmentTables.SCOPE[effect])
            val after = AugmentRules.applyOnPick(base, augment, Random(7))

            when (scope) {
                AugmentScope.ECONOMY -> assertTrue(
                    "${augment.name} 이 라운드 수입을 늘리지 않는다",
                    Economy.roundIncome(after) > Economy.roundIncome(base),
                )

                AugmentScope.EXP -> {
                    val granted = Economy.grantExp(after, AugmentRules.expGrantedBy(augment))
                    assertNotEquals(
                        "${augment.name} 이 경험치를 주지 않는다",
                        base.level to base.exp,
                        granted.level to granted.exp,
                    )
                }

                AugmentScope.ITEM -> assertEquals(
                    "${augment.name} 이 아이템을 1개 주지 않는다",
                    base.itemInventory.size + 1,
                    after.itemInventory.size,
                )

                AugmentScope.SYNERGY -> {
                    val before = SynergyEngine.resolve(base).activeTraits
                    val now = SynergyEngine.resolve(after).activeTraits
                    assertNotEquals("${augment.name} 이 시너지 단계를 바꾸지 않는다", before, now)
                }

                AugmentScope.COMBAT -> assertNotEquals(
                    "${augment.name} 이 전투 보정을 바꾸지 않는다",
                    SynergyEngine.resolve(base).combat,
                    SynergyEngine.resolve(after).combat,
                )

                AugmentScope.SHOP -> assertNotEquals(
                    "${augment.name} 이 상점 조작 비용을 바꾸지 않는다",
                    sessionOf(base).rerollCost,
                    sessionOf(after).rerollCost,
                )
            }
        }
    }

    @Test
    fun `증강 10종이 모두 선택 경로를 통과한다`() {
        // 위 테스트는 규칙 함수를 직접 부른다. 이것은 화면이 실제로 쓰는 길
        // (제시 → chooseAugment → 상태 반영)이 10종 전부에 이어져 있는지를 본다.
        AugmentEffect.entries.forEach { effect ->
            val augment = defOf(effect)
            val session = sessionOfferingOnly(augment)
            advanceToFirstAugmentRound(session)

            val offered = requireNotNull(session.pendingAugments) { "${augment.name} 이 제시되지 않았다" }
            assertTrue("${augment.name} 이 후보에 없다", offered.any { it.id == augment.id })
            assertTrue(session.awaitingAugmentChoice)

            val before = session.player
            assertTrue(session.chooseAugment(augment.id) is PlanningResult.Success)
            assertEquals(
                "${augment.name} 이 보유 목록에 들어가지 않았다",
                before.augments + augment,
                session.player.augments,
            )
            assertEquals("고른 뒤에도 후보가 남아 있다", null, session.pendingAugments)
        }
    }

    @Test
    fun `전열강화는 수호자 임계값만 깎는다`() {
        val augment = defOf(AugmentEffect.TRAIT_THRESHOLD_DISCOUNT)
        val discounts = AugmentRules.thresholdDiscounts(listOf(augment))

        assertEquals("깎이는 시너지가 수호자 하나여야 한다", setOf(UnitClass.WARDEN.traitId), discounts.keys)
        val warden = MasterData.trait(UnitClass.WARDEN)
        val before = SynergyEngine.activeTraits(board).first { it.traitId == warden.id }
        val after = SynergyEngine.activeTraits(board, discounts).first { it.traitId == warden.id }

        assertEquals("구성원 수는 그대로다", before.memberCount, after.memberCount)
        assertEquals("요구 인원이 1 줄어야 한다", before.effectiveThresholds.map { it - 1 }, after.effectiveThresholds)
        assertTrue("같은 인원으로 단계가 올라야 한다", after.tier > before.tier)
    }

    /** 후보가 [only] 를 반드시 포함하도록 카탈로그를 3종으로 줄인 세션. */
    private fun sessionOfferingOnly(only: AugmentDef): PlanningSession {
        val filler = MasterData.augments.filter { it.id != only.id }.take(2)
        return sessionOf(base, AugmentRoller(catalog = listOf(only) + filler, random = Random(3)))
    }

    private fun sessionOf(
        player: PlayerState,
        augmentRoller: AugmentRoller = AugmentRoller(random = Random(3)),
    ): PlanningSession {
        val pool = UnitPool()
        return PlanningSession(
            pool = pool,
            roller = ShopRoller(pool, Random(1)),
            initialPlayer = player,
            augmentRoller = augmentRoller,
        )
    }

    private fun advanceToFirstAugmentRound(session: PlanningSession) {
        val target = StageRound.of(2, 1).flat
        repeat(target) { session.nextRound() }
        assertEquals("2-1 라운드가 아니다", "2-1", session.roundLabel)
    }
}
