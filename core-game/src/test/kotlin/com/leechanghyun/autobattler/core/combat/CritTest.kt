package com.leechanghyun.autobattler.core.combat

import com.leechanghyun.autobattler.core.synergy.SynergyTables
import com.leechanghyun.autobattler.core.synergy.UnitBuffs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 로드맵 9단계. 증강 사수의감각의 치명타.
 *
 * 명세서 4-8 은 "치명타 확률 +20%" 라고 적지만 4단계 전투는 난수를 한 줄도 쓰지 않는다.
 * 폭풍의 부족 연쇄 번개와 똑같이 정수 충전으로 옮겼으므로, 확률이 아니라 **몇 번째 공격에서
 * 터지는가**를 정수로 단언할 수 있다.
 *
 * 시너지 보드를 거치지 않고 보정을 직접 주입한다. 여기서 보는 것은 전투 쪽 동작이다.
 * 보드와 증강이 이 보정을 만들어 내는지는 `SynergyAugmentTest` 가 본다.
 */
class CritTest {

    private val simulator = CombatSimulator()

    /** 허수아비 하나를 세우고 공격자가 [attacks] 번 때리는 동안의 전투. 간격이 10틱이다. */
    private fun battle(critCharge: Int, attacks: Int, targetArmor: Int = 0): CombatOutcome {
        val attacker = CombatFixtures.unit(
            "attacker", CombatTeam.PLAYER, row = 4, col = 3,
            def = CombatFixtures.def(baseHp = 100000, baseAttack = 100, attackRange = 1),
            buffs = UnitBuffs(critChargePerAttack = critCharge),
        )
        val dummy = CombatFixtures.unit(
            "enemy", CombatTeam.ENEMY, row = 3, col = 3,
            def = CombatFixtures.def(baseHp = 100000, baseAttack = 0, attackRange = 8),
            buffs = UnitBuffs(armorFlat = targetArmor),
        )
        return simulator.simulate(listOf(attacker, dummy), maxTicks = 1 + 10 * (attacks - 1))
    }

    private fun CombatOutcome.hitsBy(id: String) =
        events.filterIsInstance<CombatEvent.Attacked>().filter { it.attackerId == id }

    @Test
    fun `치명타는 공격 횟수와 충전량만으로 발동 횟수가 정해진다`() {
        listOf(10, 20, 25, 50).forEach { charge ->
            listOf(1, 5, 10, 13).forEach { attacks ->
                val hits = battle(charge, attacks).hitsBy("attacker")
                assertEquals("$attacks 번 공격이 안 일어났다", attacks, hits.size)
                assertEquals(
                    "충전 $charge, $attacks 번 공격",
                    attacks * charge / CombatRules.CRIT_CHARGE_FULL,
                    hits.count { it.crit },
                )
            }
        }
    }

    @Test
    fun `명세서의 20퍼센트는 다섯 번에 한 번이다`() {
        val charge = 20
        val hits = battle(charge, attacks = 10).hitsBy("attacker")

        assertEquals(
            "충전 $charge 이 CRIT_CHARGE_FULL 과 맞물려 다섯 번에 한 번이어야 한다",
            listOf(false, false, false, false, true, false, false, false, false, true),
            hits.map { it.crit },
        )
    }

    @Test
    fun `치명타는 더 아프고 방어력 감산 전에 곱해진다`() {
        val armor = 50
        val hits = battle(critCharge = 20, attacks = 5, targetArmor = armor).hitsBy("attacker")
        val normal = hits.first { !it.crit }
        val crit = hits.first { it.crit }

        assertTrue("치명타가 더 아프지 않다", crit.damage > normal.damage)
        assertEquals("방어력 감산 전에 곱해야 한다", CombatRules.damageAfterArmor(100, armor), normal.damage)
        assertEquals(
            "감산 뒤에 곱하면 방어력 높은 대상에게 치명타가 더 유리해진다",
            CombatRules.damageAfterArmor(CombatRules.critDamage(100), armor),
            crit.damage,
        )
    }

    @Test
    fun `증강이 없으면 전투는 9단계 이전과 완전히 같다`() {
        val without = battle(critCharge = 0, attacks = 10)

        assertTrue("충전량이 0인데 치명타가 터졌다", without.hitsBy("attacker").none { it.crit })
        assertNotEquals(without.hitsBy("attacker").map { it.damage }, battle(20, 10).hitsBy("attacker").map { it.damage })
    }

    @Test
    fun `치명타와 연쇄 번개는 같은 주기로 돌아 항상 함께 터진다`() {
        // 두 상한이 100 으로 같고 폭풍의 부족 1단계 충전량도 사수의감각과 같은 값이라, 폭풍의
        // 부족이면서 사수인 유닛은 두 효과가 영원히 붙어 다닌다. 난수가 없으니 어긋날 수 없다.
        // 원작이라면 독립 사건일 둘이 완전 상관이 되는 것은 밸런스 문제이고 11단계 몫이다.
        val stormCharge = SynergyTables.STORM_CHARGE[0]
        val attacker = CombatFixtures.unit(
            "attacker", CombatTeam.PLAYER, row = 4, col = 3,
            def = CombatFixtures.def(baseHp = 100000, baseAttack = 100, attackRange = 1),
            buffs = UnitBuffs(
                critChargePerAttack = stormCharge,
                chainChargePerAttack = stormCharge,
                chainDamage = 50,
                chainTargets = 1,
            ),
        )
        fun dummy(id: String, col: Int) = CombatFixtures.unit(
            id, CombatTeam.ENEMY, row = 3, col = col,
            def = CombatFixtures.def(baseHp = 100000, baseAttack = 0, attackRange = 8),
        )
        val outcome = simulator.simulate(
            listOf(attacker, dummy("enemy_a", 3), dummy("enemy_b", 2)),
            maxTicks = 1 + 10 * 19,
        )

        val critTicks = outcome.events.filterIsInstance<CombatEvent.Attacked>()
            .filter { it.attackerId == "attacker" && it.crit }.map { it.tick }
        val chainTicks = outcome.events.filterIsInstance<CombatEvent.ChainLightning>().map { it.tick }

        assertTrue("치명타가 한 번도 안 터지면 이 테스트는 뜻이 없다", critTicks.isNotEmpty())
        assertEquals(
            "CRIT_CHARGE_FULL 과 CHAIN_CHARGE_FULL 을 다르게 두면 이 위상 고정이 풀린다",
            critTicks,
            chainTicks,
        )
    }
}
