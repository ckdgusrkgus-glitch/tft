package com.leechanghyun.autobattler.core.combat

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.board.HexGrid
import com.leechanghyun.autobattler.core.board.OffsetCoord
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.BoardUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 틱 기반 자동전투의 승패 판정을 검증한다. 명세서 4-6, 로드맵 4단계 완료 기준.
 *
 * 유닛 행동 순서는 id 순이므로 아래 테스트는 "enemy" 가 "player" 보다 먼저 움직인다는 전제를 쓴다.
 */
class CombatSimulatorTest {

    private val simulator = CombatSimulator()

    /** 맞붙은 1v1 한 판. 매번 새 유닛을 만들어야 한다. 전투 중에 유닛 상태가 바뀌기 때문이다. */
    private fun duel(
        playerHp: Int = 1000,
        playerAttack: Int = 200,
        enemyHp: Int = 500,
        enemyAttack: Int = 50,
    ) = listOf(
        CombatFixtures.unit(
            "player", CombatTeam.PLAYER, row = 4, col = 3,
            def = CombatFixtures.def(baseHp = playerHp, baseAttack = playerAttack),
        ),
        CombatFixtures.unit(
            "enemy", CombatTeam.ENEMY, row = 3, col = 3,
            def = CombatFixtures.def(baseHp = enemyHp, baseAttack = enemyAttack),
        ),
    )

    @Test
    fun `1v1 에서 더 센 쪽이 이기고 진 쪽은 전멸한다`() {
        val units = duel()
        val outcome = simulator.simulate(units)

        assertEquals(CombatWinner.PLAYER, outcome.winner)
        assertFalse("전멸로 끝났으므로 시간 초과가 아니다", outcome.timedOut)
        assertEquals("살아남은 유닛은 플레이어 하나다", setOf("player"), outcome.survivors.keys)
        assertTrue("사망 이벤트가 남는다", outcome.events.any { it is CombatEvent.Died && it.unitId == "enemy" })
        assertEquals("패배한 쪽 유닛은 체력이 0이다", 0, units.first { it.id == "enemy" }.hp)
    }

    @Test
    fun `공격 간격과 피해량대로 정확한 틱에 전투가 끝난다`() {
        // 공격 간격 10틱. 적 체력 500, 플레이어 공격력 200 이므로 3번 때려야 죽는다.
        // 플레이어의 공격은 1, 11, 21틱이고 21틱에 적이 죽는다.
        val outcome = simulator.simulate(duel())

        assertEquals(21, outcome.ticks)
        assertEquals("1틱이 100ms 다", 2100, outcome.durationMillis)
        assertEquals("적이 3번 맞는다", 3, outcome.events.count { it is CombatEvent.Attacked && it.targetId == "enemy" })
        assertEquals("플레이어는 3번 맞는다", 3, outcome.events.count { it is CombatEvent.Attacked && it.targetId == "player" })
        assertEquals("플레이어가 50씩 3번 맞아 850 남는다", 850, outcome.survivors.getValue("player"))
    }

    @Test
    fun `같은 배치를 넣으면 항상 같은 결과가 나온다`() {
        val first = simulator.simulate(duel(playerHp = 900, playerAttack = 130, enemyHp = 800, enemyAttack = 110))
        val second = simulator.simulate(duel(playerHp = 900, playerAttack = 130, enemyHp = 800, enemyAttack = 110))

        assertEquals(first.winner, second.winner)
        assertEquals(first.ticks, second.ticks)
        assertEquals(first.survivors, second.survivors)
        assertEquals("이벤트 하나하나까지 같다", first.events, second.events)
    }

    @Test
    fun `사거리 밖의 근접 유닛은 적에게 걸어가서 붙는다`() {
        // 적은 전장을 다 덮는 사거리라 제자리에서 쏘고, 플레이어만 움직인다.
        val player = CombatFixtures.unit(
            "player", CombatTeam.PLAYER, row = 7, col = 3,
            def = CombatFixtures.def(baseHp = 5000, baseAttack = 500, attackRange = 1),
        )
        val enemy = CombatFixtures.unit(
            "enemy", CombatTeam.ENEMY, row = 0, col = 3,
            def = CombatFixtures.def(baseHp = 100, baseAttack = 1, attackRange = 8),
        )
        val start = HexGrid.distance(player.position, enemy.position)
        assertEquals("전장 양 끝이면 7칸 떨어져 있다", 7, start)

        val outcome = simulator.simulate(listOf(player, enemy))

        assertEquals(CombatWinner.PLAYER, outcome.winner)
        assertEquals("6칸 움직이면 사거리 안이다", 6, outcome.events.count { it is CombatEvent.Moved && it.unitId == "player" })
        assertEquals("제자리에서 쏘는 적은 움직이지 않는다", 0, outcome.events.count { it is CombatEvent.Moved && it.unitId == "enemy" })
        assertEquals("붙어서 때린다", 1, HexGrid.distance(player.position, enemy.position))
    }

    @Test
    fun `이동은 한 번에 한 칸씩이고 이동 간격을 지킨다`() {
        val player = CombatFixtures.unit(
            "player", CombatTeam.PLAYER, row = 7, col = 3,
            def = CombatFixtures.def(baseHp = 5000, baseAttack = 500, attackRange = 1),
        )
        val enemy = CombatFixtures.unit(
            "enemy", CombatTeam.ENEMY, row = 0, col = 3,
            def = CombatFixtures.def(baseHp = 100, baseAttack = 1, attackRange = 8),
        )
        val moves = simulator.simulate(listOf(player, enemy))
            .events.filterIsInstance<CombatEvent.Moved>()

        moves.forEach { assertEquals("${it.from} -> ${it.to} 는 한 칸이다", 1, HexGrid.distance(it.from, it.to)) }
        moves.zipWithNext { a, b ->
            assertEquals("이동 간격은 ${CombatRules.MOVE_INTERVAL_TICKS}틱이다", CombatRules.MOVE_INTERVAL_TICKS, b.tick - a.tick)
            assertEquals("앞 이동이 끝난 칸에서 이어 간다", a.to, b.from)
        }
    }

    @Test
    fun `마나가 가득 차 있으면 기본 공격 대신 스킬이 나간다`() {
        val caster = CombatFixtures.unit(
            "player", CombatTeam.PLAYER, row = 4, col = 3,
            def = CombatFixtures.def(baseHp = 2000, baseAttack = 10),
            skill = CombatFixtures.skill(id = "burst", manaCost = 30, startingMana = 30, basePower = 200),
        )
        val enemy = CombatFixtures.unit(
            "enemy", CombatTeam.ENEMY, row = 3, col = 3,
            def = CombatFixtures.def(baseHp = 1000, baseAttack = 10),
        )

        val outcome = simulator.simulate(listOf(caster, enemy), maxTicks = 1)

        val cast = outcome.events.filterIsInstance<CombatEvent.SkillCast>().single()
        assertEquals(1, cast.tick)
        assertEquals("player", cast.casterId)
        assertEquals("burst", cast.skillId)
        assertEquals(listOf("enemy"), cast.targetIds)
        assertEquals(200, cast.damagePerTarget)
        assertEquals("스킬 피해가 실제로 들어간다", 800, enemy.hp)
        assertEquals("스킬을 쓰면 마나를 비운다", 0, caster.mana)
        assertTrue("스킬을 쓴 틱에는 기본 공격을 하지 않는다", outcome.events.none { it is CombatEvent.Attacked && it.attackerId == "player" })
    }

    @Test
    fun `광역 스킬은 대상 주변의 적까지 함께 맞춘다`() {
        val caster = CombatFixtures.unit(
            "player", CombatTeam.PLAYER, row = 4, col = 3,
            def = CombatFixtures.def(baseHp = 3000, baseAttack = 10, attackRange = 8),
            skill = CombatFixtures.skill(id = "blast", manaCost = 30, startingMana = 30, basePower = 200, areaRadius = 1),
        )
        val near = CombatFixtures.unit("enemy_a", CombatTeam.ENEMY, row = 3, col = 3, def = CombatFixtures.def(baseHp = 1000))
        val alsoNear = CombatFixtures.unit("enemy_b", CombatTeam.ENEMY, row = 3, col = 2, def = CombatFixtures.def(baseHp = 1000))
        val far = CombatFixtures.unit("enemy_far", CombatTeam.ENEMY, row = 0, col = 0, def = CombatFixtures.def(baseHp = 1000))

        val outcome = simulator.simulate(listOf(caster, near, alsoNear, far), maxTicks = 1)

        val cast = outcome.events.filterIsInstance<CombatEvent.SkillCast>().single()
        assertEquals("반경 1 안의 두 적만 맞는다", listOf("enemy_a", "enemy_b"), cast.targetIds)
        assertEquals(800, near.hp)
        assertEquals(800, alsoNear.hp)
        assertEquals("멀리 있는 적은 멀쩡하다", far.maxHp, far.hp)
    }

    @Test
    fun `시간 안에 못 끝내면 남은 체력이 많은 쪽이 이긴다`() {
        val outcome = simulator.simulate(
            duel(playerHp = 1000, playerAttack = 10, enemyHp = 1000, enemyAttack = 5),
            maxTicks = 100,
        )

        assertTrue(outcome.timedOut)
        assertEquals(100, outcome.ticks)
        assertEquals(CombatWinner.PLAYER, outcome.winner)
        assertEquals("양쪽 다 살아 있다", 2, outcome.survivors.size)
        assertTrue(outcome.survivors.getValue("player") > outcome.survivors.getValue("enemy"))
    }

    @Test
    fun `서로 못 죽이고 체력도 같으면 무승부다`() {
        val outcome = simulator.simulate(
            duel(playerHp = 1000, playerAttack = 0, enemyHp = 1000, enemyAttack = 0),
            maxTicks = 50,
        )

        assertTrue(outcome.timedOut)
        assertEquals(CombatWinner.DRAW, outcome.winner)
        assertEquals(0, outcome.playerHpLoss())
    }

    @Test
    fun `패배하면 살아남은 적 수만큼 체력을 더 잃는다`() {
        val lost = simulator.simulate(duel(playerHp = 100, playerAttack = 10, enemyHp = 1000, enemyAttack = 200))
        assertEquals(CombatWinner.ENEMY, lost.winner)
        assertEquals("기본 ${CombatOutcome.BASE_HP_LOSS} + 생존 적 1기", CombatOutcome.BASE_HP_LOSS + 1, lost.playerHpLoss())

        val won = simulator.simulate(duel())
        assertEquals("이기면 체력을 잃지 않는다", 0, won.playerHpLoss())
    }

    @Test
    fun `여러 기가 붙어도 승패가 나고 이벤트가 일관된다`() {
        val units = (2..4).map { col ->
            CombatFixtures.unit(
                "player_$col", CombatTeam.PLAYER, row = 4, col = col,
                def = CombatFixtures.def(baseHp = 1000, baseAttack = 150),
            )
        } + (2..4).map { col ->
            CombatFixtures.unit(
                "enemy_$col", CombatTeam.ENEMY, row = 3, col = col,
                def = CombatFixtures.def(baseHp = 800, baseAttack = 100),
            )
        }

        val outcome = simulator.simulate(units)

        assertEquals(CombatWinner.PLAYER, outcome.winner)
        assertFalse(outcome.timedOut)
        assertEquals("적 3기가 전부 죽는다", 3, outcome.events.count { it is CombatEvent.Died })
        assertEquals(
            "한 유닛의 사망 이벤트는 한 번뿐이다",
            outcome.events.filterIsInstance<CombatEvent.Died>().map { it.unitId }.toSet().size,
            outcome.events.count { it is CombatEvent.Died },
        )
        assertEquals("죽은 유닛은 생존 목록에 없다", 3, outcome.survivors.size)
        assertTrue("이벤트는 틱 순서대로 쌓인다", outcome.events.map { it.tick }.zipWithNext().all { (a, b) -> a <= b })
        assertTrue("죽은 뒤에는 공격하지 않는다", outcome.events.none { it is CombatEvent.Attacked && it.damage == 0 })
    }

    @Test
    fun `보드 배치를 그대로 전투에 넘길 수 있다`() {
        val def = MasterData.units.first()
        val placement = HexBoard.fromOffset(OffsetCoord(row = 3, col = 3))

        val units = CombatSimulator.unitsFrom(
            playerBoard = listOf(
                BoardUnit("u1", def, position = placement),
                BoardUnit("u2", def, position = null),
            ),
            enemyBoard = listOf(BoardUnit("u1", def, position = placement)),
        )

        assertEquals("벤치 유닛은 빠진다", 2, units.size)
        assertEquals("id 가 겹쳐도 진영 접두사로 구분된다", setOf("player_u1", "enemy_u1"), units.map { it.id }.toSet())
        assertEquals(1, HexGrid.distance(units[0].position, units[1].position))

        val outcome = simulator.simulate(units)
        assertTrue("같은 유닛끼리 붙어도 승패는 난다", outcome.winner in CombatWinner.entries)
    }

    @Test
    fun `잘못된 입력은 시뮬레이션 전에 걸러진다`() {
        assertThrows("id 가 겹치면 거부한다", IllegalArgumentException::class.java) {
            simulator.simulate(
                listOf(
                    CombatFixtures.unit("same", CombatTeam.PLAYER, row = 4, col = 3),
                    CombatFixtures.unit("same", CombatTeam.ENEMY, row = 3, col = 3),
                ),
            )
        }
    }
}
