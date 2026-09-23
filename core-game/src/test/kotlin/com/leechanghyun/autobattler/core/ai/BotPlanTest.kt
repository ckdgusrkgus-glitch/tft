package com.leechanghyun.autobattler.core.ai

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.board.OffsetCoord
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.masterdata.traitId
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.model.UnitDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 명세서 4-7 의 "우선 시너지 2개 고정"과 "초반 예외"를 검증한다. 로드맵 8단계.
 */
class BotPlanTest {

    private fun unit(def: UnitDef, id: String, slot: Int) = BoardUnit(
        instanceId = id,
        unitDef = def,
        position = HexBoard.fromOffset(OffsetCoord(row = 3 - slot / 7, col = slot % 7)),
    )

    private fun player(board: List<BoardUnit> = emptyList(), bench: List<BoardUnit> = emptyList()) =
        PlayerState(playerId = "bot", displayName = "봇", isBot = true, level = 9, board = board, bench = bench)

    private fun defsOf(origin: Origin, count: Int) =
        MasterData.units.filter { it.origin == origin }.take(count).also { require(it.size == count) }

    @Test
    fun `보드가 비어 있으면 우선 태그가 없다`() {
        assertEquals(emptyList<String>(), BotPlan.of(player(), round = 5).priorityTraitIds)
    }

    @Test
    fun `가장 많이 보유한 태그 두 개를 고른다`() {
        // 명세서 4-7: "가장 많이 보유한 태그 2개를 우선 시너지로 고정".
        val mecha = defsOf(Origin.MECHA, 3)
        val abyssal = defsOf(Origin.ABYSSAL, 1)
        val board = mecha.mapIndexed { i, def -> unit(def, "m$i", i) } + unit(abyssal[0], "a", 3)

        val plan = BotPlan.of(player(board = board), round = 5)

        assertEquals("두 개를 넘지 않는다", BotPlan.PRIORITY_TRAIT_COUNT, plan.priorityTraitIds.size)
        assertEquals(
            "3명인 기계공학자가 1등이어야 한다",
            Origin.MECHA.traitId,
            plan.priorityTraitIds.first(),
        )
    }

    @Test
    fun `벤치는 우선 태그에 세지 않는다`() {
        // 5단계가 정한 "보드에 올라간 유닛만 센다"를 봇도 그대로 따른다.
        // 벤치를 세면 싸우지도 않는 유닛이 봇의 방향을 정하게 된다.
        val mecha = defsOf(Origin.MECHA, 3)
        val benchOnly = player(bench = mecha.mapIndexed { i, def -> BoardUnit("b$i", def) })

        assertEquals(emptyList<String>(), BotPlan.of(benchOnly, round = 5).priorityTraitIds)
    }

    @Test
    fun `인원이 같으면 마스터 데이터 순서로 끊는다`() {
        // 해시 맵 순회 순서가 결과에 새어 들어가면 같은 시드로 돌려도 결과가 달라진다.
        // 계열 4종이 각 1명이면 MasterData.traits 순서상 앞의 둘이 뽑혀야 하고, 매번 같아야 한다.
        val oneEach = Origin.entries.mapIndexed { index, origin ->
            unit(MasterData.units.first { it.origin == origin }, "u$index", index)
        }
        val state = player(board = oneEach)

        val expected = MasterData.traits
            .map { it.id }
            .filter { id -> oneEach.any { it.unitDef.origin.traitId == id || it.unitDef.unitClass.traitId == id } }

        val repeated = List(20) { BotPlan.of(state, round = 5).priorityTraitIds }
        assertTrue("20번 다 같아야 한다: ${repeated.distinct()}", repeated.distinct().size == 1)
        assertEquals(expected.take(BotPlan.PRIORITY_TRAIT_COUNT), repeated.first())
    }

    @Test
    fun `초반 예외는 라운드와 빈 보드를 모두 요구한다`() {
        // 명세서 4-7: "보드가 비어있는 초반(1~3라운드)". 두 조건의 AND 다.
        val empty = player()
        val occupied = player(board = listOf(unit(MasterData.units.first(), "u", 0)))

        assertTrue(BotPlan.of(empty, round = 1).isEarlyGame)
        assertTrue(BotPlan.of(empty, round = BotPlan.EARLY_GAME_LAST_ROUND).isEarlyGame)
        assertFalse("4라운드부터는 초반이 아니다", BotPlan.of(empty, round = 4).isEarlyGame)
        assertFalse("보드에 유닛이 있으면 이미 방향이 있다", BotPlan.of(occupied, round = 1).isEarlyGame)
    }

    @Test
    fun `우선 태그를 가진 유닛만 일치로 센다`() {
        val mechaDef = MasterData.units.first { it.origin == Origin.MECHA }
        val plan = BotPlan(
            priorityTraitIds = listOf(Origin.MECHA.traitId, mechaDef.unitClass.traitId),
            isEarlyGame = false,
        )
        val other = MasterData.units.first {
            it.origin != Origin.MECHA && it.unitClass != mechaDef.unitClass
        }

        assertEquals("계열과 직업 둘 다 맞는다", 2, plan.priorityMatchCount(mechaDef))
        assertEquals(0, plan.priorityMatchCount(other))
    }
}
