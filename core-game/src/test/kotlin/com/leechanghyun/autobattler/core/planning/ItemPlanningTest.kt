package com.leechanghyun.autobattler.core.planning

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.board.OffsetCoord
import com.leechanghyun.autobattler.core.economy.ShopRoller
import com.leechanghyun.autobattler.core.economy.UnitPool
import com.leechanghyun.autobattler.core.items.ItemStats
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.ItemDef
import com.leechanghyun.autobattler.core.model.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 로드맵 7단계 완료 기준 검증: **컴포넌트 2개 → 완성템 조합, 유닛 장착까지 정상 동작한다.** 명세서 4-5.
 */
class ItemPlanningTest {

    private val might = MasterData.item("comp_might")
    private val wisdom = MasterData.item("comp_wisdom")
    private val vitality = MasterData.item("comp_vitality")

    private fun session(
        bench: List<BoardUnit> = emptyList(),
        board: List<BoardUnit> = emptyList(),
        inventory: List<ItemDef> = emptyList(),
    ): PlanningSession {
        val pool = UnitPool()
        return PlanningSession(
            pool = pool,
            roller = ShopRoller(pool, Random(1)),
            initialPlayer = PlayerState(
                playerId = "p1",
                displayName = "나",
                isBot = false,
                gold = 50,
                level = 9,
                bench = bench,
                board = board,
                itemInventory = inventory,
            ),
        )
    }

    private fun unit(id: String = "u0", onBoardAt: Int? = null, items: List<ItemDef> = emptyList()) = BoardUnit(
        instanceId = id,
        unitDef = MasterData.units[0],
        position = onBoardAt?.let { HexBoard.fromOffset(OffsetCoord(row = 3, col = it)) },
        items = items,
    )

    private fun error(result: PlanningResult) = (result as PlanningResult.Failure).error

    // --- 완료 기준 ---

    @Test
    fun `컴포넌트 2개를 합치면 완성 아이템 하나가 된다`() {
        val session = session(inventory = listOf(might, wisdom))

        val result = session.combineItems(0, 1)

        val expected = requireNotNull(MasterData.combine(might.id, wisdom.id))
        assertEquals(expected, (result as PlanningResult.Success).item)
        assertEquals("재료 둘은 가방에서 빠진다", listOf(expected), session.player.itemInventory)
        assertFalse("완성 아이템은 컴포넌트가 아니다", expected.isComponent)
    }

    @Test
    fun `완성 아이템을 유닛에 장착하면 전투 스탯이 올라간다`() {
        val completed = requireNotNull(MasterData.combine(might.id, wisdom.id))
        val session = session(board = listOf(unit(onBoardAt = 2)), inventory = listOf(completed))

        val result = session.equip("u0", 0)

        assertTrue(result.isSuccess)
        assertEquals("가방에서 빠진다", emptyList<ItemDef>(), session.player.itemInventory)
        assertEquals(listOf(completed), session.player.board.single().items)
        assertNotEquals(
            "장착했는데 전투 보정이 무보정이면 끼운 의미가 없다",
            com.leechanghyun.autobattler.core.synergy.UnitBuffs.NONE,
            ItemStats.buffsOf(session.player.board.single().items),
        )
    }

    // --- 조합 ---

    @Test
    fun `아이템 id 가 아니라 가방 칸 번호로 고른다`() {
        // 같은 컴포넌트를 여러 개 들고 있는 것이 정상이다. id 로 고르면 어느 것을 먹었는지 알 수 없다.
        val session = session(inventory = listOf(might, might, might))

        session.combineItems(0, 1)

        val doubled = requireNotNull(MasterData.combine(might.id, might.id))
        assertEquals("셋 중 둘만 먹는다", listOf(might, doubled), session.player.itemInventory)
    }

    @Test
    fun `같은 칸을 두 번 고르면 실패한다`() {
        val session = session(inventory = listOf(might))
        assertEquals(PlanningError.SAME_ITEM_SLOT, error(session.combineItems(0, 0)))
        assertEquals("실패하면 가방이 그대로다", listOf(might), session.player.itemInventory)
    }

    @Test
    fun `없는 칸을 고르면 조합 이전에 실패한다`() {
        val session = session(inventory = listOf(might))
        assertEquals(PlanningError.ITEM_NOT_FOUND, error(session.combineItems(0, 1)))
        assertEquals(PlanningError.ITEM_NOT_FOUND, error(session.combineItems(5, 5)))
        assertEquals(PlanningError.ITEM_NOT_FOUND, error(session.combineItems(-1, 0)))
    }

    @Test
    fun `완성 아이템끼리는 다시 조합되지 않는다`() {
        val completed = requireNotNull(MasterData.combine(might.id, wisdom.id))
        val session = session(inventory = listOf(completed, completed))

        assertEquals(PlanningError.ITEM_NOT_COMBINABLE, error(session.combineItems(0, 1)))
        assertEquals(listOf(completed, completed), session.player.itemInventory)
    }

    @Test
    fun `컴포넌트 9종의 모든 조합 45가지가 실제로 만들어진다`() {
        val components = MasterData.itemComponents
        val made = mutableSetOf<String>()

        components.indices.forEach { i ->
            (i until components.size).forEach { j ->
                val session = session(inventory = listOf(components[i], components[j]))
                val result = session.combineItems(0, 1)
                val item = (result as PlanningResult.Success).item
                made += requireNotNull(item).id
            }
        }
        assertEquals("완성 아이템 45종에 도달할 수 없는 것이 있으면 안 된다", 45, made.size)
        assertEquals(MasterData.completedItems.map { it.id }.toSet(), made)
    }

    // --- 장착 / 해제 ---

    @Test
    fun `벤치 유닛에도 장착할 수 있다`() {
        // 준비 단계에는 벤치와 보드를 자유롭게 오간다. 보드에만 허용하면 올렸다 내릴 때마다 막힌다.
        val session = session(bench = listOf(unit()), inventory = listOf(might))

        assertTrue(session.equip("u0", 0).isSuccess)
        assertEquals(listOf(might), session.player.bench.single().items)
    }

    @Test
    fun `컴포넌트도 그대로 장착할 수 있다`() {
        // 칸은 아이템 개수를 세지 조합 단계를 보지 않는다.
        val session = session(board = listOf(unit(onBoardAt = 1)), inventory = listOf(might))
        assertTrue(session.equip("u0", 0).isSuccess)
        assertEquals(listOf(might), session.player.board.single().items)
    }

    @Test
    fun `아이템 칸은 세 개까지다`() {
        val session = session(bench = listOf(unit()), inventory = listOf(might, wisdom, vitality, might))

        repeat(BoardUnit.MAX_ITEM_SLOTS) { assertTrue(session.equip("u0", 0).isSuccess) }

        assertEquals(BoardUnit.MAX_ITEM_SLOTS, session.player.bench.single().items.size)
        assertEquals(PlanningError.ITEM_SLOTS_FULL, error(session.equip("u0", 0)))
        assertEquals("실패하면 가방에 그대로 남는다", listOf(might), session.player.itemInventory)
    }

    @Test
    fun `해제하면 가방으로 돌아온다`() {
        val session = session(board = listOf(unit(onBoardAt = 0, items = listOf(might, wisdom))))

        val result = session.unequip("u0", 0)

        assertEquals(might, (result as PlanningResult.Success).item)
        assertEquals("남은 아이템은 그대로다", listOf(wisdom), session.player.board.single().items)
        assertEquals(listOf(might), session.player.itemInventory)
        assertEquals("해제해도 보드에서 내려가지 않는다", 1, session.player.board.size)
    }

    @Test
    fun `없는 유닛이나 없는 칸은 실패한다`() {
        val session = session(bench = listOf(unit(items = listOf(might))), inventory = listOf(wisdom))

        assertEquals(PlanningError.UNIT_NOT_FOUND, error(session.equip("없는유닛", 0)))
        assertEquals(PlanningError.UNIT_NOT_FOUND, error(session.unequip("없는유닛", 0)))
        assertEquals(PlanningError.ITEM_NOT_FOUND, error(session.equip("u0", 9)))
        assertEquals(PlanningError.ITEM_NOT_FOUND, error(session.unequip("u0", 9)))
    }

    // --- 다른 조작과의 관계 ---

    @Test
    fun `유닛을 팔면 끼워 둔 아이템이 가방으로 돌아온다`() {
        val session = session(board = listOf(unit(onBoardAt = 0, items = listOf(might, wisdom))))

        assertTrue(session.sell("u0").isSuccess)

        assertEquals("유닛과 함께 사라지면 되돌릴 방법이 없다", listOf(might, wisdom), session.player.itemInventory)
        assertEquals(0, session.player.board.size)
    }

    @Test
    fun `합성하면 소모된 유닛의 아이템이 살아남은 유닛에 모인다`() {
        // 6단계 StarUp 이 예약해 둔 경로가 7단계에서 실제 아이템으로 처음 돌아간다.
        val def = MasterData.units[0]
        fun copy(id: String, items: List<ItemDef>) = BoardUnit(instanceId = id, unitDef = def, items = items)
        val session = session(
            bench = listOf(copy("a", listOf(might)), copy("b", listOf(wisdom)), copy("c", listOf(vitality))),
        )

        val result = StarUp.apply(session.player)

        assertTrue(result.merged)
        val merged = result.player.bench.single()
        assertEquals(2, merged.starLevel)
        assertEquals(listOf(might, wisdom, vitality), merged.items)
        assertEquals("칸에 다 들어갔으므로 가방으로 넘어간 것은 없다", emptyList<ItemDef>(), result.player.itemInventory)
    }

    @Test
    fun `합성으로 칸이 넘치면 나머지는 가방으로 간다`() {
        val def = MasterData.units[0]
        fun copy(id: String, items: List<ItemDef>) = BoardUnit(instanceId = id, unitDef = def, items = items)
        val session = session(
            bench = listOf(
                copy("a", listOf(might, might)),
                copy("b", listOf(wisdom, wisdom)),
                copy("c", listOf(vitality)),
            ),
        )

        val result = StarUp.apply(session.player)
        val merged = result.player.bench.single()

        assertEquals(BoardUnit.MAX_ITEM_SLOTS, merged.items.size)
        assertEquals("아이템 5개 중 3개만 남고 2개가 넘친다", 2, result.player.itemInventory.size)
        assertEquals(
            "한 개도 사라지지 않는다",
            5,
            merged.items.size + result.player.itemInventory.size,
        )
        assertEquals(result.player.itemInventory, result.events.single().returnedItems)
    }

    @Test
    fun `아이템 총량은 조합과 장착으로 변하지 않는다`() {
        // 컴포넌트 2개가 완성템 1개가 되므로 "개수"가 아니라 **컴포넌트 환산 장수**로 센다.
        // 아이템이 생기는 문은 grantItems 뿐이므로, 그 밖의 어떤 조작도 이 값을 바꾸면 버그다.
        fun componentCount(item: ItemDef) = if (item.isComponent) 1 else item.recipe.size
        fun total(player: PlayerState) =
            player.itemInventory.sumOf { componentCount(it) } +
                (player.bench + player.board).flatMap { it.items }.sumOf { componentCount(it) }

        val session = session(bench = listOf(unit("u0"), unit("u1", items = emptyList())))
        session.grantItems(listOf(might, wisdom, vitality, might))
        val before = total(session.player)
        assertEquals(4, before)

        session.combineItems(0, 1)
        session.equip("u0", 0)
        session.equip("u0", 0)
        session.unequip("u0", 0)
        session.equip("u1", 0)
        session.sell("u1")
        session.combineItems(0, 1)

        assertEquals("조합·장착·해제·판매는 아이템을 만들지도 없애지도 않는다", before, total(session.player))
    }

    @Test
    fun `실패한 조작은 상태를 전혀 바꾸지 않는다`() {
        val session = session(bench = listOf(unit(items = listOf(might, wisdom, vitality))), inventory = listOf(might))
        val before = session.player

        assertFalse(session.equip("u0", 0).isSuccess)
        assertFalse(session.equip("없는유닛", 0).isSuccess)
        assertFalse(session.unequip("u0", 7).isSuccess)
        assertFalse(session.combineItems(0, 1).isSuccess)

        assertEquals(before, session.player)
    }

    @Test
    fun `같은 입력이면 항상 같은 결과다`() {
        fun run(): PlayerState {
            val session = session(bench = listOf(unit()), inventory = listOf(might, wisdom, vitality))
            session.combineItems(0, 1)
            session.equip("u0", 0)
            session.equip("u0", 0)
            return session.player
        }
        assertEquals(run(), run())
    }
}
