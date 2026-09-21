package com.leechanghyun.autobattler.core.combat

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.board.OffsetCoord
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.BoardUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 전투 유닛 1기의 스탯 계산과 상태 변화를 검증한다. 명세서 4-3, 4-6. */
class CombatUnitTest {

    @Test
    fun `성 등급 배율이 체력 공격력 스킬 위력에 모두 적용된다`() {
        val def = CombatFixtures.def(baseHp = 1000, baseAttack = 100)
        val skill = CombatFixtures.skill(basePower = 200)

        val one = CombatFixtures.unit("a", CombatTeam.PLAYER, 7, 0, def = def, skill = skill, starLevel = 1)
        val two = CombatFixtures.unit("b", CombatTeam.PLAYER, 7, 1, def = def, skill = skill, starLevel = 2)
        val three = CombatFixtures.unit("c", CombatTeam.PLAYER, 7, 2, def = def, skill = skill, starLevel = 3)

        assertEquals(1000, one.maxHp)
        assertEquals(1800, two.maxHp)
        assertEquals(3240, three.maxHp)

        assertEquals(100, one.attackDamage)
        assertEquals(180, two.attackDamage)
        assertEquals(324, three.attackDamage)

        assertEquals(200, one.skillPower)
        assertEquals(360, two.skillPower)
        assertEquals(648, three.skillPower)
    }

    @Test
    fun `3성 스탯은 버림 오차 없이 3점24배다`() {
        // 1.8f 는 이진 부동소수로 딱 떨어지지 않아 1.8f * 1.8f 가 3.2399998 이 된다.
        // 버리면 3240 이어야 할 값이 3239 가 되므로 반올림해야 한다.
        val def = CombatFixtures.def(baseHp = 1000, baseAttack = 1000)
        val unit = CombatFixtures.unit("u", CombatTeam.PLAYER, 7, 0, def = def, starLevel = 3)

        assertEquals(3240, unit.maxHp)
        assertEquals(3240, unit.attackDamage)

        val placed = BoardUnit(instanceId = "u", unitDef = def, starLevel = 3)
        // 이 불변식은 무보정 보드에 한해 성립한다. BoardUnit 은 영속성과 동등성의 단위라
        // 버프 상태를 얹으면 "같은 유닛"이 이웃에 따라 equals 가 달라진다. 버프된 값은 CombatUnit 에만 있다.
        assertEquals("배치 유닛과 전투 유닛의 스탯이 어긋나면 안 된다", placed.hp, unit.maxHp)
        assertEquals(placed.attack, unit.attackDamage)
    }

    @Test
    fun `공격 간격은 공격속도의 역수이고 최소 1틱이다`() {
        fun intervalOf(attackSpeed: Float) =
            CombatFixtures.unit("u", CombatTeam.PLAYER, 7, 0, def = CombatFixtures.def(attackSpeed = attackSpeed))
                .attackIntervalTicks

        assertEquals("초당 1회 = 1000ms = 10틱", 10, intervalOf(1.0f))
        assertEquals("초당 2회 = 500ms = 5틱", 5, intervalOf(2.0f))
        assertEquals("초당 0.5회 = 2000ms = 20틱", 20, intervalOf(0.5f))
        assertEquals("아무리 빨라도 1틱마다 한 번이다", 1, intervalOf(50f))
    }

    @Test
    fun `유닛은 가득 찬 체력과 시작 마나로 전투를 시작한다`() {
        val unit = CombatFixtures.unit(
            "u", CombatTeam.PLAYER, 7, 0,
            skill = CombatFixtures.skill(manaCost = 60, startingMana = 15),
        )
        assertEquals(unit.maxHp, unit.hp)
        assertEquals(15, unit.mana)
        assertTrue(unit.isAlive)
        assertFalse(unit.canCastSkill)
    }

    @Test
    fun `남은 체력보다 큰 피해는 남은 만큼만 깎인다`() {
        val unit = CombatFixtures.unit("u", CombatTeam.PLAYER, 7, 0, def = CombatFixtures.def(baseHp = 100))

        assertEquals(30, unit.takeDamage(30).hpLost)
        assertEquals(70, unit.hp)

        assertEquals("남은 70만 들어간다", 70, unit.takeDamage(500).hpLost)
        assertEquals(0, unit.hp)
        assertFalse(unit.isAlive)
    }

    @Test
    fun `마나는 필요량을 넘겨 쌓이지 않고 죽은 유닛은 얻지 못한다`() {
        val unit = CombatFixtures.unit(
            "u", CombatTeam.PLAYER, 7, 0,
            def = CombatFixtures.def(baseHp = 100),
            skill = CombatFixtures.skill(manaCost = 30, startingMana = 0),
        )

        unit.gainMana(20)
        assertEquals(20, unit.mana)
        unit.gainMana(50)
        assertEquals("30을 넘지 않는다", 30, unit.mana)
        assertTrue(unit.canCastSkill)

        unit.spendMana()
        assertEquals(0, unit.mana)
        assertFalse(unit.canCastSkill)

        unit.takeDamage(100)
        unit.gainMana(30)
        assertEquals("죽은 뒤에는 마나가 차지 않는다", 0, unit.mana)
        assertFalse(unit.canCastSkill)
    }

    @Test
    fun `벤치에 있는 유닛은 전투에 참가하지 않는다`() {
        val def = MasterData.units.first()
        val benched = BoardUnit(instanceId = "bench-1", unitDef = def, position = null)
        assertNull(CombatUnit.from(benched, CombatTeam.PLAYER))
    }

    @Test
    fun `보드에 올라간 유닛은 진영에 맞는 전장 좌표를 받는다`() {
        val def = MasterData.units.first()
        val placement = HexBoard.fromOffset(OffsetCoord(row = 3, col = 2))
        val placed = BoardUnit(instanceId = "board-1", unitDef = def, position = placement)

        val player = CombatUnit.from(placed, CombatTeam.PLAYER)
        val enemy = CombatUnit.from(placed, CombatTeam.ENEMY)

        assertNotNull(player)
        assertNotNull(enemy)
        assertEquals(CombatField.toField(placement, CombatTeam.PLAYER), player!!.position)
        assertEquals(CombatField.toField(placement, CombatTeam.ENEMY), enemy!!.position)
        assertEquals(MasterData.skill(def.skillId), player.skill)
    }
}
