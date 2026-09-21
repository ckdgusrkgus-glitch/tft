package com.leechanghyun.autobattler.core.economy

import com.leechanghyun.autobattler.core.masterdata.EconomyRules
import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.synergy.SynergyFixtures
import com.leechanghyun.autobattler.core.synergy.SynergyTables
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 황금가문은 8종 중 유일하게 전투가 아니라 경제에 붙는다. 명세서 4-4.
 *
 * 시너지 골드가 이자 계산에 섞이지 않는지, 그리고 5단계 이전 경제 기대값이 움직이지 않았는지 본다.
 */
class GoldenHouseTest {

    private fun player(gold: Int, boardDefs: List<com.leechanghyun.autobattler.core.model.UnitDef>) =
        PlayerState(
            playerId = "p1",
            displayName = "나",
            isBot = false,
            gold = gold,
            level = 9,
            board = SynergyFixtures.board(boardDefs),
        )

    @Test
    fun `황금가문 골드는 수입에 더해진다`() {
        val defs = SynergyFixtures.defsOf(Origin.GOLDEN_HOUSE, 2)
        val state = player(gold = 30, boardDefs = defs)

        assertEquals(
            EconomyRules.BASE_INCOME + EconomyRules.interestFor(30) + SynergyTables.GOLDEN_HOUSE_GOLD[0],
            Economy.roundIncome(state),
        )
    }

    @Test
    fun `시너지 골드는 이번 라운드 이자 원금에 섞이지 않는다`() {
        // 30골드의 이자는 3이다. 시너지 골드 2를 먼저 주머니에 넣었다면 32가 되어도 이자는 여전히 3이지만,
        // 39골드였다면 41이 되어 이자가 한 단계 튄다. 그 경로를 막았는지 본다.
        val defs = SynergyFixtures.defsOf(Origin.GOLDEN_HOUSE, 2)
        val state = player(gold = 39, boardDefs = defs)

        val expected = EconomyRules.BASE_INCOME +
            EconomyRules.interestFor(39) +
            SynergyTables.GOLDEN_HOUSE_GOLD[0]
        assertEquals(expected, Economy.roundIncome(state))
        assertEquals("39골드의 이자는 3이다", 3, EconomyRules.interestFor(39))
        assertEquals("41골드였다면 4가 됐을 것이다", 4, EconomyRules.interestFor(41))
    }

    @Test
    fun `단계가 오르면 받는 골드도 오른다`() {
        SynergyTables.GOLDEN_HOUSE_GOLD.indices.forEach { tierIndex ->
            val count = com.leechanghyun.autobattler.core.masterdata.MasterData.trait("golden_house")
                .thresholds[tierIndex]
            val state = player(gold = 0, boardDefs = SynergyFixtures.defsOf(Origin.GOLDEN_HOUSE, count))
            assertEquals(
                "$count 명",
                EconomyRules.BASE_INCOME + SynergyTables.GOLDEN_HOUSE_GOLD[tierIndex],
                Economy.roundIncome(state),
            )
        }
    }

    @Test
    fun `보드가 비었거나 미달이면 5단계 이전 수입 그대로다`() {
        val empty = player(gold = 30, boardDefs = emptyList())
        assertEquals(EconomyRules.BASE_INCOME + EconomyRules.interestFor(30), Economy.roundIncome(empty))

        val notEnough = player(gold = 30, boardDefs = SynergyFixtures.defsOf(Origin.GOLDEN_HOUSE, 1))
        assertEquals(
            "1명이면 발동하지 않는다",
            EconomyRules.BASE_INCOME + EconomyRules.interestFor(30),
            Economy.roundIncome(notEnough),
        )
    }

    @Test
    fun `벤치의 황금가문은 골드를 주지 않는다`() {
        val defs = SynergyFixtures.defsOf(Origin.GOLDEN_HOUSE, 4)
        val benched = PlayerState(
            playerId = "p1",
            displayName = "나",
            isBot = false,
            gold = 0,
            bench = SynergyFixtures.bench(defs),
        )
        assertEquals(EconomyRules.BASE_INCOME, Economy.roundIncome(benched))
    }
}
