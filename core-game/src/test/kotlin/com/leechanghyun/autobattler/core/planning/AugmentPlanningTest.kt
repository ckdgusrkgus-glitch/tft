package com.leechanghyun.autobattler.core.planning

import com.leechanghyun.autobattler.core.augment.AugmentEffect
import com.leechanghyun.autobattler.core.augment.AugmentRoller
import com.leechanghyun.autobattler.core.augment.effect
import com.leechanghyun.autobattler.core.economy.Economy
import com.leechanghyun.autobattler.core.economy.ShopRoller
import com.leechanghyun.autobattler.core.economy.UnitPool
import com.leechanghyun.autobattler.core.masterdata.AUGMENT_STAGE_ROUNDS
import com.leechanghyun.autobattler.core.masterdata.EconomyRules
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.AugmentDef
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.model.StageRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 로드맵 9단계 완료 기준의 **노출** 절반: 2-1 / 3-2 / 4-2 라운드에만 증강 후보가 나온다.
 *
 * 화면은 [PlanningSession.pendingAugments] 가 비어 있지 않을 때 모달을 띄운다. 그러므로 어느
 * 라운드에 이 값이 차는가가 곧 완료 기준이고, 화면 없이 여기서 전부 단언할 수 있다.
 */
class AugmentPlanningTest {

    private fun session(
        player: PlayerState = PlayerState(playerId = "p", displayName = "나", isBot = false),
        catalog: List<AugmentDef> = MasterData.augments,
    ): PlanningSession {
        val pool = UnitPool()
        return PlanningSession(
            pool = pool,
            roller = ShopRoller(pool, Random(11)),
            initialPlayer = player,
            augmentRoller = AugmentRoller(catalog = catalog, random = Random(5)),
        )
    }

    private fun augmentOf(effect: AugmentEffect): AugmentDef =
        MasterData.augments.first { it.effect == effect }

    /** 해당 증강이 반드시 후보에 들도록 카탈로그를 3종으로 줄인다. */
    private fun catalogContaining(augment: AugmentDef): List<AugmentDef> =
        listOf(augment) + MasterData.augments.filter { it.id != augment.id }.take(2)

    @Test
    fun `증강 후보는 2-1 3-2 4-2 라운드에만 나온다`() {
        val session = session()
        val offered = mutableListOf<String>()

        repeat(16) {
            session.nextRound()
            if (session.pendingAugments != null) {
                offered += session.roundLabel
                // 고르지 않으면 다음 라운드 후보가 막히므로 바로 고른다.
                session.chooseAugment(session.pendingAugments!!.first().id)
            }
        }

        assertEquals(listOf("2-1", "3-2", "4-2"), offered)
        assertEquals(AUGMENT_STAGE_ROUNDS.map { it.label }, offered)
    }

    @Test
    fun `아직 시작하지 않은 세션에는 라운드도 후보도 없다`() {
        val session = session()

        assertNull(session.stageRound)
        assertEquals("-", session.roundLabel)
        assertNull(session.pendingAugments)
        assertFalse(session.awaitingAugmentChoice)
    }

    @Test
    fun `후보에 없는 증강은 고를 수 없고 상태도 그대로다`() {
        val session = session()
        repeat(StageRound.of(2, 1).flat) { session.nextRound() }
        val offered = requireNotNull(session.pendingAugments)
        val notOffered = MasterData.augments.first { it !in offered }
        val before = session.player

        val result = session.chooseAugment(notOffered.id)

        assertEquals(PlanningResult.Failure(PlanningError.AUGMENT_NOT_OFFERED), result)
        assertEquals("실패했는데 상태가 바뀌었다", before, session.player)
        assertEquals("실패했는데 후보가 사라졌다", offered, session.pendingAugments)
    }

    @Test
    fun `증강 라운드가 아니면 고를 수 없다`() {
        val session = session()
        session.nextRound()

        assertEquals(
            PlanningResult.Failure(PlanningError.NO_AUGMENT_OFFER),
            session.chooseAugment(MasterData.augments.first().id),
        )
    }

    @Test
    fun `고르지 않고 다음 증강 라운드까지 가도 후보는 사라지지 않는다`() {
        // 명세서 7장이 증강 화면을 모달로 지정했으므로 화면은 awaitingAugmentChoice 동안
        // 다음 라운드를 막아야 한다. 그래도 엔진 쪽에서 후보가 조용히 증발하지는 않는다.
        //
        // 다음 **증강 라운드**까지 가야 뜻이 있다. 평범한 라운드는 애초에 후보를 뽑지 않으므로
        // 덮어쓰기 방지 장치를 지워도 아무 일이 없다. 3-2 에서 덮어써야 비로소 드러난다.
        val session = session()
        repeat(StageRound.of(2, 1).flat) { session.nextRound() }
        val offered = requireNotNull(session.pendingAugments)

        repeat(StageRound.of(3, 2).flat - StageRound.of(2, 1).flat) { session.nextRound() }
        assertEquals("다음 증강 라운드까지 오지 않았다", "3-2", session.roundLabel)

        assertEquals("넘어갔더니 2-1 의 후보가 3-2 의 후보로 덮였다", offered, session.pendingAugments)
        assertTrue(session.awaitingAugmentChoice)
        assertTrue(session.chooseAugment(offered.first().id) is PlanningResult.Success)
    }

    @Test
    fun `황금손길은 다음 라운드 수입부터 매번 더 준다`() {
        // roundIncome 만 보면 복리로 새는 실수를 잡지 못한다. 실제로 startRound 를 여러 번
        // 돌려 누적 골드를 본다.
        val touch = augmentOf(AugmentEffect.GOLD_PER_ROUND)
        val withAugment = session(catalog = catalogContaining(touch))
        val without = session()

        repeat(StageRound.of(2, 1).flat) {
            withAugment.nextRound()
            without.nextRound()
        }
        assertEquals("증강을 고르기 전에는 골드가 같아야 한다", without.player.gold, withAugment.player.gold)
        assertTrue(withAugment.chooseAugment(touch.id) is PlanningResult.Success)

        val perRound = Economy.roundIncome(withAugment.player) - Economy.roundIncome(without.player)
        assertTrue("황금손길이 수입을 늘리지 않는다", perRound > 0)

        repeat(3) {
            withAugment.nextRound()
            without.nextRound()
        }
        assertTrue(
            "3라운드를 돌렸는데 골드 차이가 라운드당 증가분보다 작다",
            withAugment.player.gold - without.player.gold >= perRound * 3,
        )
    }

    @Test
    fun `심연의계약은 체력을 가져가지만 탈락시키지는 않는다`() {
        val pact = augmentOf(AugmentEffect.HP_FOR_GOLD)
        val session = session(catalog = catalogContaining(pact))
        repeat(StageRound.of(2, 1).flat) { session.nextRound() }
        val before = session.player.hp

        assertTrue(session.chooseAugment(pact.id) is PlanningResult.Success)

        assertTrue("체력을 가져가지 않았다", session.player.hp < before)
        assertFalse("고른 순간 탈락했다", session.player.isEliminated)
    }

    @Test
    fun `재고정리는 라운드마다 리롤 한 번을 공짜로 만든다`() {
        val clearance = augmentOf(AugmentEffect.FREE_REROLL)
        val session = session(
            player = PlayerState(playerId = "p", displayName = "나", isBot = false, gold = 50),
            catalog = catalogContaining(clearance),
        )
        repeat(StageRound.of(2, 1).flat) { session.nextRound() }
        assertEquals("고르기 전에는 정가다", EconomyRules.REROLL_COST, session.rerollCost)
        assertTrue(session.chooseAugment(clearance.id) is PlanningResult.Success)

        assertTrue(session.rerollIsFree)
        assertEquals(0, session.rerollCost)

        val goldBefore = session.player.gold
        val first = session.reroll()
        assertEquals("첫 리롤이 공짜가 아니다", goldBefore, session.player.gold)
        // 화면이 "얼마 썼는지" 를 이 값으로 읽는다. 골드만 보면 비용이 틀려도 눈치채지 못한다.
        assertEquals(PlanningResult.Success(goldSpent = 0), first)

        assertFalse("무료 리롤이 두 번 이상 나온다", session.rerollIsFree)
        assertEquals(EconomyRules.REROLL_COST, session.rerollCost)
        assertEquals(PlanningResult.Success(goldSpent = EconomyRules.REROLL_COST), session.reroll())
        assertEquals("두 번째 리롤이 골드를 쓰지 않았다", goldBefore - EconomyRules.REROLL_COST, session.player.gold)

        session.nextRound()
        assertTrue("다음 라운드에 무료 리롤이 돌아오지 않는다", session.rerollIsFree)
    }

    @Test
    fun `증강이 없으면 리롤은 언제나 정가다`() {
        val session = session(
            player = PlayerState(playerId = "p", displayName = "나", isBot = false, gold = 50),
        )
        repeat(6) {
            session.nextRound()
            assertEquals(EconomyRules.REROLL_COST, session.rerollCost)
            assertFalse(session.rerollIsFree)
            val before = session.player.gold
            assertEquals(PlanningResult.Success(goldSpent = EconomyRules.REROLL_COST), session.reroll())
            assertEquals(before - EconomyRules.REROLL_COST, session.player.gold)
        }
    }

    @Test
    fun `아이템을 주는 증강은 가방으로 들어온다`() {
        listOf(AugmentEffect.GRANT_COMPONENT, AugmentEffect.GRANT_COMPLETED_ITEM).forEach { effect ->
            val augment = augmentOf(effect)
            val session = session(catalog = catalogContaining(augment))
            repeat(StageRound.of(2, 1).flat) { session.nextRound() }
            assertTrue(session.player.itemInventory.isEmpty())

            assertTrue(session.chooseAugment(augment.id) is PlanningResult.Success)

            assertEquals("${augment.name} 이 아이템을 정확히 1개 주지 않았다", 1, session.player.itemInventory.size)
            val granted = session.player.itemInventory.single()
            assertNotNull(MasterData.item(granted.id))
            val expectComponent = effect == AugmentEffect.GRANT_COMPONENT
            assertEquals("${augment.name} 이 잘못된 종류를 줬다", expectComponent, granted.isComponent)
        }
    }
}
