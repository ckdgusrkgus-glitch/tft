package com.leechanghyun.autobattler.core.combat

import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.UnitClass
import com.leechanghyun.autobattler.core.synergy.SynergyEngine
import com.leechanghyun.autobattler.core.synergy.SynergyFixtures
import com.leechanghyun.autobattler.core.synergy.SynergyState
import com.leechanghyun.autobattler.core.synergy.SynergyTables
import com.leechanghyun.autobattler.core.synergy.UnitBuffs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 로드맵 5단계 완료 기준을 검증한다: **보드 구성에 따라 버프가 정상 적용된다.**
 *
 * 기대값은 전부 [SynergyTables] 에서 읽어서 계산한다. 11단계가 수치를 바꿔도 이 테스트는 그대로
 * 통과해야 하고, 통과하지 못한다면 그건 수치가 아니라 규칙이 깨진 것이다.
 */
class SynergyCombatTest {

    private val simulator = CombatSimulator()

    /** 시너지를 받는 쪽(플레이어)과 무보정 상대를 세운다. */
    private fun setup(
        playerDefs: List<com.leechanghyun.autobattler.core.model.UnitDef>,
        withSynergy: Boolean = true,
    ): CombatSetup {
        val board = SynergyFixtures.board(playerDefs, idPrefix = "p")
        val enemy = SynergyFixtures.board(playerDefs, idPrefix = "e")
        return CombatSimulator.setupFrom(
            playerBoard = board,
            enemyBoard = enemy,
            playerSynergy = if (withSynergy) SynergyEngine.resolve(board) else SynergyState.NONE,
        )
    }

    private fun CombatSetup.player(index: Int) = units.first { it.id == "player_p$index" }

    private fun CombatSetup.enemy(index: Int) = units.first { it.id == "enemy_e$index" }

    // --- 회귀 기준점 ---

    @Test
    fun `시너지를 넘기지 않으면 4단계 전투와 완전히 같다`() {
        val defs = SynergyFixtures.defsOf(UnitClass.WARDEN, 2)
        val board = SynergyFixtures.board(defs, idPrefix = "p")
        val enemy = SynergyFixtures.board(defs, idPrefix = "e")

        val legacy = CombatSimulator.unitsFrom(board, enemy)
        val setup = CombatSimulator.setupFrom(board, enemy)

        assertEquals(legacy.map { it.id }, setup.units.map { it.id })
        assertEquals(legacy.map { it.maxHp }, setup.units.map { it.maxHp })
        assertEquals(legacy.map { it.attackDamage }, setup.units.map { it.attackDamage })
        assertTrue("무보정이면 방어력이 0", setup.units.all { it.armor == 0 })
        assertTrue("무보정이면 쉴드가 없다", setup.units.all { it.shieldPerRefresh == 0 })

        assertEquals(
            "틱 수까지 같다",
            simulator.simulate(legacy).ticks,
            simulator.simulate(setup).ticks,
        )
    }

    // --- 직업 시너지 4종 ---

    @Test
    fun `수호자 버프는 최대 체력과 시작 체력을 함께 올린다`() {
        val defs = SynergyFixtures.defsOf(UnitClass.WARDEN, 2)
        val unit = setup(defs).player(0)

        val expected = UnitBuffs.scale(defs[0].baseHp, 0, SynergyTables.WARDEN_HP_PERCENT[0])
        assertEquals(expected, unit.maxHp)
        assertEquals("최대 체력이 올랐는데 시작 체력이 안 오르면 안 된다", unit.maxHp, unit.hp)
        assertTrue("실제로 올라야 한다", unit.maxHp > defs[0].baseHp)
    }

    @Test
    fun `검사 버프는 공격력을 올리고 기본 공격 피해에 그대로 나타난다`() {
        val defs = SynergyFixtures.defsOf(UnitClass.BLADE, 2)
        val buffed = setup(defs)
        val plain = setup(defs, withSynergy = false)

        val expected = UnitBuffs.scale(defs[0].baseAttack, 0, SynergyTables.BLADE_ATTACK_PERCENT[0])
        assertEquals(expected, buffed.player(0).attackDamage)
        assertTrue(buffed.player(0).attackDamage > plain.player(0).attackDamage)

        fun firstHit(setup: CombatSetup) = simulator.simulate(setup)
            .events.filterIsInstance<CombatEvent.Attacked>()
            .first { it.attackerId == "player_p0" }.damage

        assertTrue("버프된 공격이 더 아파야 한다", firstHit(setup(defs)) > firstHit(setup(defs, withSynergy = false)))
    }

    @Test
    fun `마법사 버프는 주문력을 올리고 스킬 이벤트에 그대로 나타난다`() {
        val defs = SynergyFixtures.defsOf(UnitClass.ARCANIST, 2)
        val unit = setup(defs).player(0)

        val basePower = MasterData.skill(defs[0].skillId).basePower
        assertEquals(
            UnitBuffs.scale(basePower, 0, SynergyTables.ARCANIST_SKILL_POWER_PERCENT[0]),
            unit.skillPower,
        )

        val cast = simulator.simulate(setup(defs))
            .events.filterIsInstance<CombatEvent.SkillCast>()
            .firstOrNull { it.casterId == "player_p0" }
        requireNotNull(cast) { "마법사가 스킬을 한 번도 못 썼다" }
        assertEquals("이벤트가 실제 위력과 어긋나면 안 된다", unit.skillPower, cast.damagePerTarget)
    }

    @Test
    fun `사수 버프는 로스터의 모든 사수 유닛의 공격 간격을 실제로 줄인다`() {
        // 양자화 지뢰의 방어선이다. 공격 간격이 정수 틱이라 작은 비율은 통째로 사라지는데,
        // 그러면 "버프를 걸었는데 아무 일도 안 일어난다"가 조용히 통과한다.
        val marksmen = MasterData.units.filter { it.unitClass == UnitClass.MARKSMAN }
        assertEquals("로스터의 사수는 6종이다", 6, marksmen.size)

        marksmen.forEach { def ->
            val base = CombatFixtures.unit("u", CombatTeam.PLAYER, row = 4, col = 0, def = def).attackIntervalTicks
            SynergyTables.MARKSMAN_ATTACK_SPEED_PERCENT.forEachIndexed { tierIndex, percent ->
                val buffed = CombatFixtures.unit(
                    "u", CombatTeam.PLAYER, row = 4, col = 0, def = def,
                    buffs = UnitBuffs(attackSpeedPercent = percent),
                ).attackIntervalTicks
                assertTrue(
                    "${def.name}(공속 ${def.attackSpeed})의 ${tierIndex + 1}단계 버프가 간격을 줄이지 못했다: $base -> $buffed",
                    buffed < base,
                )
            }
        }
    }

    // --- 범위 규칙 ---

    @Test
    fun `태그 한정 버프는 그 태그 유닛에게만 붙는다`() {
        val wardens = SynergyFixtures.defsOf(UnitClass.WARDEN, 2)
        val outsider = MasterData.units.first { it.unitClass != UnitClass.WARDEN }
        val setup = setup(wardens + outsider)

        assertTrue("수호자는 체력이 오른다", setup.player(0).maxHp > wardens[0].baseHp)
        assertEquals("수호자가 아니면 그대로다", outsider.baseHp, setup.player(2).maxHp)
    }

    @Test
    fun `아군 전체 버프는 태그가 없는 유닛에게도 붙는다`() {
        val mechas = SynergyFixtures.defsOf(com.leechanghyun.autobattler.core.model.Origin.MECHA, 2)
        val outsider = MasterData.units.first { it.origin != com.leechanghyun.autobattler.core.model.Origin.MECHA }
        val setup = setup(mechas + outsider)

        val expected = SynergyTables.MECHA_ARMOR[0]
        assertEquals(expected, setup.player(0).armor)
        assertEquals("기계공학자가 아니어도 아군이면 받는다", expected, setup.player(2).armor)
    }

    @Test
    fun `적 진영도 자기 보드의 시너지를 받는다`() {
        val defs = SynergyFixtures.defsOf(UnitClass.WARDEN, 2)
        val board = SynergyFixtures.board(defs, idPrefix = "p")
        val enemyBoard = SynergyFixtures.board(defs, idPrefix = "e")

        val setup = CombatSimulator.setupFrom(
            playerBoard = board,
            enemyBoard = enemyBoard,
            playerSynergy = SynergyState.NONE,
            enemySynergy = SynergyEngine.resolve(enemyBoard),
        )

        assertEquals("플레이어는 무보정", defs[0].baseHp, setup.player(0).maxHp)
        assertTrue("적은 자기 시너지를 받는다", setup.enemy(0).maxHp > defs[0].baseHp)
    }

    // --- 보류 ---

    @Test
    fun `기계공학자 6단계는 소환 명세만 내고 전투에는 합류하지 않는다`() {
        // 보류를 조용한 누락이 아니라 관측 가능한 상태로 만든다. 거대 골렘은 10단계다.
        val defs = SynergyFixtures.defsOf(com.leechanghyun.autobattler.core.model.Origin.MECHA, 6)
        val board = SynergyFixtures.board(defs, idPrefix = "p")
        val state = SynergyEngine.resolve(board)

        assertEquals(3, state.tierOf("mecha"))
        assertEquals(1, state.combat.summons.size)
        assertEquals(SynergyTables.GOLEM_UNIT_ID, state.combat.summons.first().unitDefId)
        assertEquals("mecha", state.combat.summons.first().sourceTraitId)

        val enemy = SynergyFixtures.board(defs, idPrefix = "e")
        val setup = CombatSimulator.setupFrom(board, enemy, playerSynergy = state)
        assertEquals("소환 명세가 나와도 전투 유닛은 늘지 않는다", board.size + enemy.size, setup.units.size)
    }

    // --- 10단계용 사실 ---

    @Test
    fun `발동한 시너지가 0틱 이벤트로 남는다`() {
        val defs = SynergyFixtures.defsOf(UnitClass.WARDEN, 2)
        val setup = setup(defs)
        val outcome = simulator.simulate(setup)

        val activations = outcome.events.filterIsInstance<CombatEvent.TraitActivated>()
        assertTrue("발동한 시너지가 이벤트로 남는다", activations.isNotEmpty())
        assertTrue("시너지는 전투 내내 불변이므로 전부 0틱이다", activations.all { it.tick == 0 })
        assertTrue("플레이어 쪽 발동이다", activations.all { it.team == CombatTeam.PLAYER })
        assertEquals(
            "발동 목록과 이벤트가 일치한다",
            setup.synergyFor(CombatTeam.PLAYER).active.map { it.traitId }.toSet(),
            activations.map { it.traitId }.toSet(),
        )
        assertEquals(setup.synergyByTeam, outcome.synergyByTeam)
        assertTrue("이벤트는 틱 순서대로 쌓인다", outcome.events.map { it.tick }.zipWithNext().all { (a, b) -> a <= b })
    }

    // --- 완료 기준 ---

    @Test
    fun `보드 구성에 따라 버프가 실제 전투 결과를 바꾼다`() {
        // 5단계 완료 기준 그 자체다. 완전히 같은 두 보드를 붙이되 한쪽에만 시너지를 준다.
        // 밸런스 수치가 단언에 한 개도 등장하지 않으므로 11단계가 수치를 바꿔도 의미가 유지된다.
        val defs = SynergyFixtures.defsOf(com.leechanghyun.autobattler.core.model.Origin.MECHA, 6)

        val plain = simulator.simulate(setup(defs, withSynergy = false))
        val buffed = simulator.simulate(setup(defs))

        assertNotEquals("버프가 승패를 바꾼다", plain.winner, buffed.winner)
        assertEquals(CombatWinner.PLAYER, buffed.winner)

        val plainSurvivors = plain.survivors.keys.count { it.startsWith("player_") }
        val buffedSurvivors = buffed.survivors.keys.count { it.startsWith("player_") }
        assertTrue("버프를 받으면 더 많이 살아남는다: $plainSurvivors -> $buffedSurvivors", buffedSurvivors > plainSurvivors)
    }
}
