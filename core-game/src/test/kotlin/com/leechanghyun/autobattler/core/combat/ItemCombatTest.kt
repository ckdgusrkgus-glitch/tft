package com.leechanghyun.autobattler.core.combat

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.board.OffsetCoord
import com.leechanghyun.autobattler.core.items.ItemStats
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.ItemDef
import com.leechanghyun.autobattler.core.model.StatType
import com.leechanghyun.autobattler.core.synergy.SynergyEngine
import com.leechanghyun.autobattler.core.synergy.SynergyFixtures
import com.leechanghyun.autobattler.core.synergy.UnitBuffs
import com.leechanghyun.autobattler.core.model.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 장착한 아이템이 실제 전투 수치까지 닿는지 확인한다. 로드맵 7단계.
 *
 * [ItemStats] 단위테스트는 환산표만 본다. 환산이 맞아도 [CombatUnit.from] 이 부르지 않으면
 * 게임에서는 아무 일도 일어나지 않고, 그래도 다른 테스트는 전부 통과한다. 그 구멍을 여기서 막는다.
 */
class ItemCombatTest {

    private fun item(id: String, vararg stats: Pair<StatType, Float>) = ItemDef(
        id = id,
        name = id,
        statModifiers = stats.toMap(),
        isComponent = true,
    )

    /**
     * [CombatUnit.from] 은 스킬을 [MasterData] 에서 찾으므로 여기서만은 실제 로스터 유닛을 쓴다.
     * 그래서 이 테스트들은 마스터 데이터 수치에 기대지 않고 **무장착과의 차이**만 단언한다.
     */
    private val rosterDef = MasterData.units.first()

    private fun placed(items: List<ItemDef>, id: String = "u0", col: Int = 2) = BoardUnit(
        instanceId = id,
        unitDef = rosterDef,
        position = HexBoard.fromOffset(OffsetCoord(row = 3, col = col)),
        items = items,
    )

    @Test
    fun `장착한 아이템이 전투 스탯에 실제로 반영된다`() {
        // 이음매 검사다. ItemStats 가 아무리 맞아도 CombatUnit.from 이 부르지 않으면
        // 게임에서는 아무 일도 일어나지 않는데 다른 테스트는 전부 통과한다.
        val gear = listOf(
            item("i_hp", StatType.MAX_HP to 300f),
            item("i_ad", StatType.ATTACK_DAMAGE to 50f),
            item("i_as", StatType.ATTACK_SPEED to 1.0f),
        )

        val bare = CombatUnit.from(placed(emptyList()), CombatTeam.PLAYER)!!
        val geared = CombatUnit.from(placed(gear), CombatTeam.PLAYER)!!

        assertEquals(UnitBuffs.NONE, bare.buffs)
        assertEquals("from 이 items 를 읽지 않으면 여기가 무보정이 된다", ItemStats.buffsOf(gear), geared.buffs)

        assertEquals(bare.maxHp + 300, geared.maxHp)
        assertEquals("최대 체력이 올랐으면 시작 체력도 같이 올라야 한다", geared.maxHp, geared.hp)
        assertEquals(bare.attackDamage + 50, geared.attackDamage)
        assertTrue(
            "공격속도 +100% 면 간격이 줄어야 한다: ${bare.attackIntervalTicks} -> ${geared.attackIntervalTicks}",
            geared.attackIntervalTicks < bare.attackIntervalTicks,
        )
    }

    @Test
    fun `공격속도 아이템은 나눗셈 전에 곱해진다`() {
        // 결과 틱 수에 곱하면 정수 반올림이 작은 버프를 비대칭으로 먹는다. 수치를 정확히 못박는다.
        val def = CombatFixtures.def(attackSpeed = 1.0f)
        fun intervalWith(vararg stats: Pair<StatType, Float>) = CombatFixtures.unit(
            "u", CombatTeam.PLAYER, 7, 0,
            def = def,
            buffs = ItemStats.buffsOf(item("i_as", *stats)),
        ).attackIntervalTicks

        assertEquals(10, intervalWith(StatType.ATTACK_SPEED to 0f))
        assertEquals("공속 +100% 면 간격이 절반이다", 5, intervalWith(StatType.ATTACK_SPEED to 1.0f))
        assertEquals("공속 +25% 면 10 / 1.25 = 8 이다", 8, intervalWith(StatType.ATTACK_SPEED to 0.25f))
    }

    @Test
    fun `아이템은 방어력과 시작 마나도 올린다`() {
        val def = CombatFixtures.def(baseHp = 1000)
        val skill = CombatFixtures.skill(manaCost = 100, startingMana = 10)

        val geared = CombatUnit(
            id = "u",
            team = CombatTeam.PLAYER,
            def = def,
            skill = skill,
            starLevel = 1,
            position = CombatField.grid.fromOffset(OffsetCoord(3, 2)),
            buffs = ItemStats.buffsOf(
                listOf(
                    item("i_armor", StatType.ARMOR to 30f),
                    item("i_mr", StatType.MAGIC_RESIST to 20f),
                    item("i_mana", StatType.MANA to 25f),
                ),
            ),
        )

        assertEquals("방어력과 마법저항력은 한 풀이다", 50, geared.armor)
        assertEquals(35, geared.mana)
        assertEquals(
            "방어력 50 이면 피해가 100 / 150 으로 줄어든다",
            CombatRules.damageAfterArmor(150, 50),
            geared.takeDamage(150).hpLost,
        )
    }

    @Test
    fun `시작 마나는 스킬 소모 마나를 넘지 않는다`() {
        val skill = CombatFixtures.skill(manaCost = 30, startingMana = 10)
        val unit = CombatFixtures.unit(
            "u", CombatTeam.PLAYER, 7, 0,
            skill = skill,
            buffs = UnitBuffs(startingManaFlat = 999),
        )
        assertEquals("가득 참을 넘겨 시작하면 spendMana 가 초과분을 버린다", 30, unit.mana)
        assertTrue(unit.canCastSkill)
    }

    @Test
    fun `벤치 유닛의 아이템은 전투에 오지 않는다`() {
        val benched = BoardUnit(
            instanceId = "bench",
            unitDef = CombatFixtures.def(),
            position = null,
            items = listOf(item("i_ad", StatType.ATTACK_DAMAGE to 999f)),
        )
        assertNull(CombatUnit.from(benched, CombatTeam.PLAYER))
    }

    @Test
    fun `아이템 보정과 시너지 보정은 함께 더해진다`() {
        // 둘 중 하나가 다른 하나를 덮어쓰는 것이 가장 흔한 사고다. 검사 시너지(공격력 비율)와
        // 공격력 가산 아이템을 같이 올려 "가산 먼저, 비율 나중"까지 한 번에 확인한다.
        val defs = SynergyFixtures.defsOf(com.leechanghyun.autobattler.core.model.UnitClass.BLADE, 2)
        val gear = listOf(item("i_ad", StatType.ATTACK_DAMAGE to 40f))
        val board = SynergyFixtures.board(defs).mapIndexed { index, unit ->
            if (index == 0) unit.copy(items = gear) else unit
        }
        val synergy = SynergyEngine.resolve(
            PlayerState(playerId = "p", displayName = "p", isBot = false, board = board),
        )
        assertTrue("검사 시너지가 켜져 있어야 의미가 있는 테스트다", synergy.combat.forUnit(defs[0]).attackPercent > 0f)

        val setup = CombatSimulator.setupFrom(board, emptyList(), playerSynergy = synergy)
        val gearedUnit = setup.units.first { it.id == "player_${board[0].instanceId}" }
        val bareUnit = setup.units.first { it.id == "player_${board[1].instanceId}" }

        val percent = synergy.combat.forUnit(defs[0]).attackPercent
        assertEquals(
            "아이템 가산이 시너지 비율의 혜택을 함께 받아야 한다",
            UnitBuffs.scale(board[0].attack, flat = 40, percent = percent),
            gearedUnit.attackDamage,
        )
        assertEquals(UnitBuffs.scale(board[1].attack, flat = 0, percent = percent), bareUnit.attackDamage)
        assertNotEquals("아이템이 실제로 차이를 만들어야 한다", bareUnit.attackDamage, gearedUnit.attackDamage)
    }

    @Test
    fun `진영 접두어가 붙은 개체 id 형식은 그대로다`() {
        // 5단계 테스트 여러 개가 "player_u0" 형식을 직접 단언한다. 아이템을 끼우면서
        // CombatUnit.from 을 한 번만 부르도록 고쳤으므로 형식이 바뀌지 않았는지 못박는다.
        val board = listOf(placed(listOf(item("i", StatType.MAX_HP to 1f))))
        val setup = CombatSimulator.setupFrom(board, board)

        assertEquals(
            listOf("player_u0", "enemy_u0"),
            setup.units.map { it.id },
        )
    }

    @Test
    fun `아이템이 없으면 전투가 6단계와 한 틱도 다르지 않다`() {
        // 아이템 경로를 끼워 넣으면서 무장착 전투가 조용히 달라지는 것이 가장 위험한 회귀다.
        val defs = SynergyFixtures.defsOf(com.leechanghyun.autobattler.core.model.Origin.MECHA, 3)
        val player = SynergyFixtures.board(defs, idPrefix = "p")
        val enemy = SynergyFixtures.board(defs, idPrefix = "e")

        val withEmptyLists = CombatSimulator().simulate(CombatSimulator.setupFrom(player, enemy))
        val withoutItemsField = CombatSimulator().simulate(
            CombatSimulator.setupFrom(
                player.map { it.copy(items = emptyList()) },
                enemy.map { it.copy(items = emptyList()) },
            ),
        )
        assertEquals(withEmptyLists, withoutItemsField)
        assertTrue("실제로 싸움이 일어나야 비교에 의미가 있다", withEmptyLists.events.isNotEmpty())
    }

    @Test
    fun `완성 아이템의 고유 효과 키는 아직 전투를 바꾸지 않는다`() {
        // effectId 로 분기하는 코드가 전투에 한 줄도 없다는 사실을 못박는다. 45종 중 하나라도
        // 효과를 붙이면 여기가 빨개진다. 그때 이 테스트를 지우는 것이 구현의 첫 단계다.
        val defs = SynergyFixtures.defsOf(com.leechanghyun.autobattler.core.model.Origin.MECHA, 2)
        val sword = MasterData.item("item_executioner_blade")

        fun outcomeWith(equipped: ItemDef): CombatOutcome {
            val player = SynergyFixtures.board(defs, idPrefix = "p")
                .mapIndexed { index, unit -> if (index == 0) unit.copy(items = listOf(equipped)) else unit }
            val enemy = SynergyFixtures.board(defs, idPrefix = "e")
            return CombatSimulator().simulate(CombatSimulator.setupFrom(player, enemy))
        }

        assertEquals(
            "고유 효과가 붙었다면 이 두 전투가 달라져야 한다",
            outcomeWith(sword.copy(effectId = "")),
            outcomeWith(sword),
        )
    }

    @Test
    fun `장착한 유닛이 더 세다`() {
        // 수치가 아니라 방향만 본다. 11단계에서 컴포넌트 수치를 바꿔도 이 단언은 살아 있어야 한다.
        fun oneOnOne(playerItems: List<ItemDef>): CombatOutcome {
            val player = listOf(placed(playerItems, id = "p0", col = 3))
            val enemy = listOf(placed(emptyList(), id = "e0", col = 3))
            return CombatSimulator().simulate(CombatSimulator.setupFrom(player, enemy))
        }

        // 무장착 거울전은 무승부가 아니라 적이 이긴다. 마지막 틱에 양쪽이 서로를 죽일 때
        // 시뮬레이터가 유닛을 한 기씩 순서대로 처리하므로 먼저 때린 쪽만 살아남기 때문이다.
        // 4단계부터 있던 성질이고 7단계가 만든 것이 아니라 그대로 두되, 그래서 여기서는
        // 승패가 아니라 **적에게 남은 체력**으로 본다. 11단계 재검토 대상이다.
        val might = MasterData.item("comp_might")
        val enemyHpLeft = (0..BoardUnit.MAX_ITEM_SLOTS).map { count ->
            oneOnOne(List(count) { might }).survivors["enemy_e0"] ?: 0
        }

        assertTrue(
            "공격력 아이템을 더할수록 적이 더 많이 깎여야 한다: ${'$'}enemyHpLeft",
            enemyHpLeft.zipWithNext().all { (fewer, more) -> more <= fewer },
        )
        assertTrue("아이템이 아무 차이도 못 만들었다", enemyHpLeft.last() < enemyHpLeft.first())
        assertEquals("칸을 다 채우면 같은 유닛을 이긴다", 0, enemyHpLeft.last())
        assertEquals(CombatWinner.PLAYER, oneOnOne(List(BoardUnit.MAX_ITEM_SLOTS) { might }).winner)
    }
}
