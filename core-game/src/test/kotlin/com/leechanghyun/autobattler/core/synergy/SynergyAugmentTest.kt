package com.leechanghyun.autobattler.core.synergy

import com.leechanghyun.autobattler.core.augment.AugmentEffect
import com.leechanghyun.autobattler.core.augment.effect
import com.leechanghyun.autobattler.core.economy.Economy
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.AugmentDef
import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.model.UnitClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 로드맵 9단계. 증강 전투 버프가 시너지 해석에 합류하는 자리를 고정한다.
 *
 * 10단계가 실제 전투를 붙일 때 이 이음매를 놓치기 가장 쉽다. 보드만 받는 `resolve(board)` 는
 * 눈에 잘 띄고 증강을 볼 수 없는데, 그것을 써도 전투는 정상으로 보인다. 증강만 사라질 뿐이다.
 */
class SynergyAugmentTest {

    private fun augmentOf(effect: AugmentEffect): AugmentDef =
        MasterData.augments.first { it.effect == effect }

    private fun playerWith(vararg augments: AugmentDef, board: List<com.leechanghyun.autobattler.core.model.BoardUnit>) =
        PlayerState(playerId = "p", displayName = "나", isBot = false, board = board, augments = augments.toList())

    private val stormBoard = SynergyFixtures.board(SynergyFixtures.defsOf(Origin.STORM_TRIBE, 2))
    private val marksmanBoard = SynergyFixtures.board(SynergyFixtures.defsOf(UnitClass.MARKSMAN, 1))

    @Test
    fun `증강 전투 버프는 보드만 보는 해석에는 없다`() {
        // 10단계가 CombatSimulator.setupFrom 에 넘길 SynergyState 는 반드시
        // resolve(PlayerState) 에서 나와야 한다. resolve(board) 를 집어 들면 증강이 통째로 빠진다.
        val player = playerWith(augmentOf(AugmentEffect.TEAM_ARMOR), board = stormBoard)

        val fromState = SynergyEngine.resolve(player)
        val fromBoard = SynergyEngine.resolve(player.board)

        assertTrue("증강 방어력이 상태 쪽 해석에 없다", fromState.combat.teamWide.armorFlat > 0)
        assertEquals("보드만 보는 해석에 증강이 섞였다", 0, fromBoard.combat.teamWide.armorFlat)
        assertNotEquals(fromBoard.combat, fromState.combat)
        assertEquals("증강은 시너지 발동 목록을 바꾸지 않는다", fromBoard.activeTraits, fromState.activeTraits)
    }

    @Test
    fun `폭풍의가호는 폭풍의 부족에게만 붙는다`() {
        val player = playerWith(augmentOf(AugmentEffect.ORIGIN_ATTACK_SPEED), board = stormBoard)
        val buffs = SynergyEngine.resolve(player).combat

        val storm = MasterData.units.first { it.origin == Origin.STORM_TRIBE }
        val other = MasterData.units.first { it.origin != Origin.STORM_TRIBE }

        assertTrue("폭풍의 부족이 공격속도를 못 받았다", buffs.forUnit(storm).attackSpeedPercent > 0f)
        assertEquals(
            "다른 계열까지 공격속도를 받았다",
            SynergyEngine.resolve(player.board).combat.forUnit(other).attackSpeedPercent,
            buffs.forUnit(other).attackSpeedPercent,
            1e-6f,
        )
    }

    @Test
    fun `사수의감각은 사수 시너지가 발동하지 않아도 사수에게 붙는다`() {
        // "사수 시너지 유닛" 은 사수 태그를 단 유닛이라는 뜻이다. 사수 1명이면 시너지는 미발동인데
        // 그 1명은 치명타를 띄워야 한다. 발동 조건을 걸면 명세서에 없는 규칙을 더하는 것이다.
        val player = playerWith(augmentOf(AugmentEffect.CLASS_CRIT_CHANCE), board = marksmanBoard)
        val state = SynergyEngine.resolve(player)

        val marksmanTrait = state.activeTraits.first { it.traitId == MasterData.trait(UnitClass.MARKSMAN).id }
        assertEquals("사수 1명이면 미발동이어야 이 테스트가 뜻을 갖는다", 0, marksmanTrait.tier)

        val marksman = MasterData.units.first { it.unitClass == UnitClass.MARKSMAN }
        val notMarksman = MasterData.units.first { it.unitClass != UnitClass.MARKSMAN }
        assertTrue("미발동이라고 치명타가 안 붙었다", state.combat.forUnit(marksman).hasCrit)
        assertTrue("사수가 아닌 유닛에게 치명타가 붙었다", !state.combat.forUnit(notMarksman).hasCrit)
    }

    @Test
    fun `증강 골드와 시너지 골드는 끝까지 따로다`() {
        // 황금손길을 SynergyState.goldPerRound 에 합치면 GoldenHouseTest 가 검증하는
        // "황금가문 골드" 의 뜻이 두 가지가 된다. 수입에는 함께 들어오되 출처는 나뉘어야 한다.
        val goldenHouse = SynergyFixtures.board(SynergyFixtures.defsOf(Origin.GOLDEN_HOUSE, 2))
        val touch = augmentOf(AugmentEffect.GOLD_PER_ROUND)
        val withBoth = playerWith(touch, board = goldenHouse)
        val boardOnly = withBoth.copy(augments = emptyList())

        val synergyGold = SynergyEngine.resolve(withBoth).goldPerRound
        assertTrue("황금가문 2인이 골드를 주지 않는다", synergyGold > 0)
        assertEquals(
            "증강 골드가 시너지 골드에 섞여 들어갔다",
            SynergyEngine.resolve(boardOnly).goldPerRound,
            synergyGold,
        )
        assertTrue(
            "증강 골드가 수입에 반영되지 않았다",
            Economy.roundIncome(withBoth) > Economy.roundIncome(boardOnly),
        )
    }
}
