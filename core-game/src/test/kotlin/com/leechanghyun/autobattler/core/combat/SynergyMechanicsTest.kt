package com.leechanghyun.autobattler.core.combat

import com.leechanghyun.autobattler.core.synergy.SynergyTables
import com.leechanghyun.autobattler.core.synergy.UnitBuffs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 5단계가 전투 엔진에 새로 들여온 세 가지 장치를 검증한다.
 * 방어력(기계공학자), 주기 쉴드(심연의 아이들), 연쇄 번개(폭풍의 부족).
 *
 * 시너지 보드를 거치지 않고 보정을 직접 주입한다. 여기서 보는 것은 "보드가 버프를 만드는가"가
 * 아니라 "버프가 전투에서 어떻게 동작하는가"이기 때문이다.
 */
class SynergyMechanicsTest {

    private val simulator = CombatSimulator()

    // --- 방어력 ---

    @Test
    fun `방어력은 나눗셈으로 줄이고 최소 1 피해는 들어간다`() {
        assertEquals("방어력이 0이면 항등이다", 100, CombatRules.damageAfterArmor(100, 0))
        assertEquals("음수 방어력도 항등이다", 100, CombatRules.damageAfterArmor(100, -10))
        assertEquals("방어력이 K 와 같으면 절반", 50, CombatRules.damageAfterArmor(100, CombatRules.ARMOR_K))
        assertEquals(CombatRules.MIN_DAMAGE, CombatRules.damageAfterArmor(1, 99999))
        assertEquals("0 피해는 0 그대로", 0, CombatRules.damageAfterArmor(0, 50))
    }

    @Test
    fun `방어력은 점수라서 여러 출처로 나눠 줘도 결과가 같다`() {
        // 퍼센트였다면 이 성질이 깨진다. 7단계 아이템과 9단계 증강이 같은 숫자에 합류할 수 있는 근거다.
        val split = UnitBuffs(armorFlat = 15) + UnitBuffs(armorFlat = 20)
        val whole = UnitBuffs(armorFlat = 35)
        assertEquals(whole.armorFlat, split.armorFlat)
        assertEquals(
            CombatRules.damageAfterArmor(200, whole.armorFlat),
            CombatRules.damageAfterArmor(200, split.armorFlat),
        )
    }

    @Test
    fun `방어력이 있으면 실제로 덜 맞는다`() {
        val armor = SynergyTables.MECHA_ARMOR[0]
        val unit = CombatFixtures.unit(
            "u", CombatTeam.PLAYER, row = 4, col = 0,
            def = CombatFixtures.def(baseHp = 10000),
            buffs = UnitBuffs(armorFlat = armor),
        )
        val result = unit.takeDamage(100)

        assertEquals(CombatRules.damageAfterArmor(100, armor), result.hpLost)
        assertTrue("실제로 줄어야 한다", result.hpLost < 100)
        assertEquals(unit.maxHp - result.hpLost, unit.hp)
    }

    // --- 쉴드 ---

    @Test
    fun `쉴드는 체력과 분리되어 먼저 흡수된다`() {
        val unit = shieldedUnit(baseHp = 1000, percent = 0.10f)
        assertEquals(100, unit.shieldPerRefresh)

        unit.refreshShield()
        assertEquals(100, unit.shield)
        assertEquals("쉴드를 채워도 체력은 그대로다", 1000, unit.hp)

        val partly = unit.takeDamage(60)
        assertEquals(60, partly.absorbed)
        assertEquals(0, partly.hpLost)
        assertEquals(40, unit.shield)
        assertEquals(1000, unit.hp)

        val through = unit.takeDamage(100)
        assertEquals("남은 쉴드 40 만 먹는다", 40, through.absorbed)
        assertEquals(60, through.hpLost)
        assertEquals(0, unit.shield)
        assertEquals(940, unit.hp)
        assertEquals(100, through.total)
    }

    @Test
    fun `쉴드 갱신은 덮어쓰기라 누적되지 않는다`() {
        // 누적하면 흡수량이 피해량을 넘어 시간 초과 무승부가 쏟아진다.
        val unit = shieldedUnit(baseHp = 1000, percent = 0.10f)
        unit.refreshShield()
        unit.refreshShield()
        unit.refreshShield()
        assertEquals(100, unit.shield)
    }

    @Test
    fun `쉴드는 주기마다 첫 틱부터 채워진다`() {
        val period = SynergyTables.ABYSSAL_SHIELD_PERIOD_TICKS
        val player = shieldedUnit(baseHp = 1000, percent = 0.10f, id = "player")
        val enemy = CombatFixtures.unit(
            "enemy", CombatTeam.ENEMY, row = 3, col = 3,
            def = CombatFixtures.def(baseHp = 100000, baseAttack = 0),
        )

        val outcome = simulator.simulate(listOf(player, enemy), maxTicks = period * 2 + 1)
        val ticks = outcome.events.filterIsInstance<CombatEvent.Shielded>()
            .filter { it.unitId == "player" }.map { it.tick }

        assertEquals("첫 틱부터 주기마다 채워진다", listOf(1, period + 1, period * 2 + 1), ticks)
        assertEquals(100, outcome.events.filterIsInstance<CombatEvent.Shielded>().first().amount)
    }

    @Test
    fun `쉴드는 생존자 체력 집계에 섞이지 않는다`() {
        // 쉴드를 hp 에 더해 구현했다면 시간 초과 승패 판정이 조용히 달라진다.
        val player = shieldedUnit(baseHp = 1000, percent = 0.10f, id = "player")
        val enemy = CombatFixtures.unit(
            "enemy", CombatTeam.ENEMY, row = 3, col = 3,
            def = CombatFixtures.def(baseHp = 1000, baseAttack = 0),
        )
        val outcome = simulator.simulate(listOf(player, enemy), maxTicks = 40)

        assertTrue(player.shield > 0)
        assertEquals("생존자 목록은 체력만 센다", player.hp, outcome.survivors.getValue("player"))
        assertEquals(CombatWinner.DRAW, outcome.winner)
    }

    @Test
    fun `쉴드가 없는 유닛은 쉴드 이벤트를 내지 않는다`() {
        val outcome = simulator.simulate(
            listOf(
                CombatFixtures.unit("player", CombatTeam.PLAYER, row = 4, col = 3, def = CombatFixtures.def(baseAttack = 0)),
                CombatFixtures.unit("enemy", CombatTeam.ENEMY, row = 3, col = 3, def = CombatFixtures.def(baseAttack = 0)),
            ),
            maxTicks = 100,
        )
        assertTrue(outcome.events.none { it is CombatEvent.Shielded })
    }

    // --- 연쇄 번개 ---

    /**
     * 허수아비 3기를 세우고 공격자가 [attacks] 번 기본 공격하는 동안의 전투를 돌린다.
     *
     * 공격 간격이 10틱이라 공격은 1, 11, 21... 틱에 일어난다. 그래서 `1 + 10 x (n - 1)` 틱까지
     * 돌리면 정확히 n 번 때린다.
     */
    private fun chainBattle(tierIndex: Int, attacks: Int): CombatOutcome {
        val attacker = CombatFixtures.unit(
            "attacker", CombatTeam.PLAYER, row = 4, col = 3,
            def = CombatFixtures.def(baseHp = 100000, baseAttack = 1, attackRange = 1),
            buffs = UnitBuffs(
                chainChargePerAttack = SynergyTables.STORM_CHARGE[tierIndex],
                chainDamage = SynergyTables.STORM_DAMAGE[tierIndex],
                chainTargets = SynergyTables.STORM_TARGETS[tierIndex],
            ),
        )
        // 사거리를 넓게 줘서 제자리에 서 있게 한다. 공격력이 0 이라 공격자를 죽이지 못한다.
        fun dummy(id: String, col: Int) = CombatFixtures.unit(
            id, CombatTeam.ENEMY, row = 3, col = col,
            def = CombatFixtures.def(baseHp = 100000, baseAttack = 0, attackRange = 8),
        )
        return simulator.simulate(
            listOf(attacker, dummy("enemy_a", 3), dummy("enemy_b", 2), dummy("enemy_c", 4)),
            maxTicks = 1 + 10 * (attacks - 1),
        )
    }

    @Test
    fun `연쇄 번개는 공격 횟수와 충전량만으로 발동 횟수가 정해진다`() {
        // 난수 대신 정수 충전이라 발동 횟수가 닫힌 식으로 나온다. 11단계가 충전량을 어떻게 바꿔도
        // 이 단언은 살아남는다.
        SynergyTables.STORM_CHARGE.indices.forEach { tierIndex ->
            val charge = SynergyTables.STORM_CHARGE[tierIndex]
            listOf(1, 5, 10, 13).forEach { attacks ->
                val procs = chainBattle(tierIndex, attacks)
                    .events.count { it is CombatEvent.ChainLightning }
                assertEquals(
                    "${tierIndex + 1}단계(충전 $charge), $attacks 번 공격",
                    attacks * charge / CombatRules.CHAIN_CHARGE_FULL,
                    procs,
                )
            }
        }
    }

    @Test
    fun `연쇄 번개의 장기 발동 빈도가 명세서의 확률과 같다`() {
        // 충전량 20 이면 5번에 1번, 즉 20% 다. 기대값이 확률과 같고 분산만 0 이다.
        val charge = SynergyTables.STORM_CHARGE[0]
        val attacks = 100 / charge * 3
        val procs = chainBattle(0, attacks).events.count { it is CombatEvent.ChainLightning }
        assertEquals(procs * CombatRules.CHAIN_CHARGE_FULL, attacks * charge)
    }

    @Test
    fun `연쇄 번개는 주 대상을 빼고 정해진 수만큼 튄다`() {
        val tierIndex = SynergyTables.STORM_TARGETS.indexOfFirst { it >= 2 }
        val outcome = chainBattle(tierIndex, attacks = 10)
        val chain = outcome.events.filterIsInstance<CombatEvent.ChainLightning>().first()

        assertEquals("attacker", chain.sourceId)
        assertEquals("enemy_a", chain.primaryTargetId)
        assertTrue("주 대상은 연쇄 대상에서 빠진다", chain.primaryTargetId !in chain.targetIds)
        assertEquals(SynergyTables.STORM_TARGETS[tierIndex], chain.targetIds.size)
        assertEquals(SynergyTables.STORM_DAMAGE[tierIndex], chain.damagePerTarget)
    }

    @Test
    fun `연쇄 번개는 같은 배치에서 항상 같은 대상을 같은 순서로 친다`() {
        val first = chainBattle(2, attacks = 10)
        val second = chainBattle(2, attacks = 10)
        assertEquals("대상 순서까지 같다", first.events, second.events)
    }

    @Test
    fun `폭풍 태그가 없는 유닛은 연쇄 번개를 내지 않는다`() {
        val attacker = CombatFixtures.unit(
            "attacker", CombatTeam.PLAYER, row = 4, col = 3,
            def = CombatFixtures.def(baseHp = 100000, baseAttack = 1),
        )
        fun dummy(id: String, col: Int) = CombatFixtures.unit(
            id, CombatTeam.ENEMY, row = 3, col = col,
            def = CombatFixtures.def(baseHp = 100000, baseAttack = 0, attackRange = 8),
        )
        val outcome = simulator.simulate(
            listOf(attacker, dummy("enemy_a", 3), dummy("enemy_b", 2)),
            maxTicks = 101,
        )
        assertTrue(outcome.events.none { it is CombatEvent.ChainLightning })
        assertEquals(0, attacker.chainCharge)
    }

    @Test
    fun `연쇄 번개 피해도 방어력과 쉴드를 지난다`() {
        // 별도 경로로 피해를 주면 방어력이 무시되는 흔한 버그를 막는다.
        val attacker = CombatFixtures.unit(
            "attacker", CombatTeam.PLAYER, row = 4, col = 3,
            def = CombatFixtures.def(baseHp = 100000, baseAttack = 1, attackRange = 1),
            buffs = UnitBuffs(chainChargePerAttack = 100, chainDamage = 200, chainTargets = 1),
        )
        val primary = CombatFixtures.unit(
            "enemy_a", CombatTeam.ENEMY, row = 3, col = 3,
            def = CombatFixtures.def(baseHp = 100000, baseAttack = 0, attackRange = 8),
        )
        val bounced = CombatFixtures.unit(
            "enemy_b", CombatTeam.ENEMY, row = 3, col = 2,
            def = CombatFixtures.def(baseHp = 100000, baseAttack = 0, attackRange = 8),
            buffs = UnitBuffs(armorFlat = CombatRules.ARMOR_K),
        )

        val before = bounced.hp
        simulator.simulate(listOf(attacker, primary, bounced), maxTicks = 1)

        assertEquals("방어력 K 면 절반만 들어간다", before - 100, bounced.hp)
        assertTrue("연쇄에 맞으면 마나도 오른다", bounced.mana >= CombatRules.MANA_PER_HIT_TAKEN)
    }

    /**
     * 불발이라도 충전은 소모돼야 한다.
     *
     * 소모하지 않으면 적이 하나만 남은 동안 충전이 무한정 쌓이고, 두 번째 적이 생기는 순간
     * 몰아서 터진다. 그래서 "번개가 안 나갔다"만 보면 안 되고 [CombatUnit.chainCharge] 까지 봐야 한다.
     * 충전량을 60 으로 둔 것은 두 번 때리면 100 을 넘겨 한 번 소모되고 20 이 남기 때문이다.
     * 100 으로 두면 소모해도 안 해도 0 이라 두 구현을 구별하지 못한다.
     */
    @Test
    fun `튈 곳이 없으면 충전만 쓰고 불발한다`() {
        val attacker = CombatFixtures.unit(
            "attacker", CombatTeam.PLAYER, row = 4, col = 3,
            def = CombatFixtures.def(baseHp = 100000, baseAttack = 1, attackRange = 1),
            buffs = UnitBuffs(chainChargePerAttack = 60, chainDamage = 200, chainTargets = 3),
        )
        val lonely = CombatFixtures.unit(
            "enemy_a", CombatTeam.ENEMY, row = 3, col = 3,
            def = CombatFixtures.def(baseHp = 100000, baseAttack = 0, attackRange = 8),
        )
        val outcome = simulator.simulate(listOf(attacker, lonely), maxTicks = 11)

        assertEquals(
            "폭풍 유닛이 두 번 때렸다",
            2,
            outcome.events.count { it is CombatEvent.Attacked && it.attackerId == "attacker" },
        )
        assertTrue("적이 하나뿐이면 튀지 않는다", outcome.events.none { it is CombatEvent.ChainLightning })
        assertEquals(
            "불발해도 충전은 소모된다",
            2 * 60 - CombatRules.CHAIN_CHARGE_FULL,
            attacker.chainCharge,
        )
    }

    private fun shieldedUnit(baseHp: Int, percent: Float, id: String = "u") = CombatFixtures.unit(
        id, CombatTeam.PLAYER, row = 4, col = 3,
        def = CombatFixtures.def(baseHp = baseHp, baseAttack = 0),
        buffs = UnitBuffs(
            shieldPercentOfMaxHp = percent,
            shieldPeriodTicks = SynergyTables.ABYSSAL_SHIELD_PERIOD_TICKS,
        ),
    )
}
