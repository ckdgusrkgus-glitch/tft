package com.leechanghyun.autobattler.core.ai

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.board.OffsetCoord
import com.leechanghyun.autobattler.core.economy.ShopOffer
import com.leechanghyun.autobattler.core.economy.ShopRoller
import com.leechanghyun.autobattler.core.economy.UnitPool
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.masterdata.traitId
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.model.UnitDef
import com.leechanghyun.autobattler.core.planning.PlanningSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [BotBrain] 이 한 라운드에 지키는 규칙 중, 8인 로비 결과로는 드러나지 않는 것들. 로드맵 8단계.
 *
 * 로비 테스트는 20라운드를 돌린 **결과**를 본다. 그래서 한 번의 판단이 달라져도 벤치에 쌓인
 * 여유분이 흡수해 결과가 거의 같아지는 규칙이 있다. 실제로 뮤테이션을 돌려 보니 레벨업 정원 조건과
 * 판매 후보의 보유 수 키가 그랬다 — **고장 내도 307개 테스트가 전부 초록색이었다.**
 * 여기 있는 테스트들은 그 규칙 하나씩을 상점 뽑기와 무관하게 직접 찌른다.
 */
class BotBrainTest {

    /** 아무것도 사지 않는 정책. 구매를 빼고 나머지 단계만 관찰하기 위한 것이다. */
    private object NeverBuyPolicy : BuyPolicy {
        override fun chooseSlot(offer: ShopOffer, player: PlayerState, plan: BotPlan): Int? = null
    }

    private fun unit(def: UnitDef, id: String, onBoardAt: Int? = null, starLevel: Int = 1) = BoardUnit(
        instanceId = id,
        unitDef = def,
        starLevel = starLevel,
        position = onBoardAt?.let { HexBoard.fromOffset(OffsetCoord(row = 3 - it / 7, col = it % 7)) },
    )

    private fun session(player: PlayerState): PlanningSession {
        val pool = UnitPool()
        return PlanningSession(pool, ShopRoller(pool, Random(1)), player)
    }

    private fun player(
        level: Int,
        gold: Int,
        board: List<BoardUnit> = emptyList(),
        bench: List<BoardUnit> = emptyList(),
    ) = PlayerState(
        playerId = "bot",
        displayName = "봇",
        isBot = true,
        gold = gold,
        level = level,
        board = board,
        bench = bench,
    )

    // --- 레벨업: 보드 정원이 병목일 때만 산다 ---

    @Test
    fun `보드에 빈 자리가 남아 있으면 레벨을 사지 않는다`() {
        val defs = MasterData.units.take(2)
        // 레벨 3 = 정원 3 인데 2기만 올라가 있다. 자리가 남았으니 경험치를 살 이유가 없다.
        val session = session(
            player(
                level = 3,
                gold = 200,
                board = listOf(unit(defs[0], "a", onBoardAt = 0), unit(defs[1], "b", onBoardAt = 1)),
            ),
        )
        session.nextRound()
        val goldBefore = session.player.gold

        val turn = BotBrain(NeverBuyPolicy).playRound(session)

        assertEquals("자리가 남았는데 레벨을 샀다", 3, session.player.level)
        assertTrue("레벨업 기록이 남았다: ${turn.actions}", turn.actions.none { it is BotAction.LeveledUp })
        assertEquals("경험치를 사느라 골드가 빠졌다", goldBefore, session.player.gold)
    }

    @Test
    fun `보드가 정원까지 차면 레벨을 한 번만 산다`() {
        val defs = MasterData.units.take(2)
        val session = session(
            player(
                level = 2,
                gold = 200,
                board = listOf(unit(defs[0], "a", onBoardAt = 0), unit(defs[1], "b", onBoardAt = 1)),
            ),
        )
        session.nextRound()

        val turn = BotBrain(NeverBuyPolicy).playRound(session)

        assertEquals("정원이 찼는데 레벨을 사지 않았다", 3, session.player.level)
        assertEquals(
            "한 라운드에 정원보다 앞서 여러 단계를 올렸다: ${turn.actions}",
            1,
            turn.actions.count { it is BotAction.LeveledUp },
        )
    }

    // --- 벤치 비우기: 무엇을 내보내는가 ---

    /** 우선 태그를 하나도 건드리지 않는 계획. 첫 번째 키를 동점으로 만들어 두 번째 키를 드러낸다. */
    private val noPlan = BotPlan(priorityTraitIds = emptyList(), isEarlyGame = false)

    @Test
    fun `벤치를 비울 때 2성 직전 카드는 남기고 약한 한 장짜리를 내보낸다`() {
        val weakest = MasterData.units.minBy { BotScoring.power(it) }
        val stronger = MasterData.units
            .filter { it.id != weakest.id }
            .sortedBy { BotScoring.power(it) }

        // 가장 약한 종을 2장 들고 있다. 보유 수 키가 없으면 전투력만 보고 이 카드가 팔린다.
        val bench = listOf(
            unit(weakest, "weak1"),
            unit(weakest, "weak2"),
            unit(stronger[0], "other0"),
            unit(stronger[1], "other1"),
        )
        val victim = BenchEviction.victim(player(level = 5, gold = 10, bench = bench), noPlan)

        assertNotNull(victim)
        assertEquals(
            "2장 모아 둔 종을 먼저 팔았다. 스타업이 영영 완성되지 않는다",
            stronger[0].id,
            victim!!.unitDef.id,
        )
    }

    @Test
    fun `벤치를 비울 때 우선 태그를 가진 카드는 보유 수가 적어도 남긴다`() {
        val priority = MasterData.units.first()
        val plan = BotPlan(priorityTraitIds = listOf(priority.origin.traitId), isEarlyGame = false)
        val offPlan = MasterData.units.first {
            it.origin.traitId != priority.origin.traitId && it.unitClass.traitId != priority.origin.traitId
        }

        // 일부러 **태그 없는 카드 쪽에** 유리한 조건을 준다. 2장 들고 있으므로 보유 수 키만 보면
        // 1장짜리 태그 카드가 먼저 팔린다. 우선 태그가 첫 키일 때만 이 단언이 성립한다.
        val bench = listOf(unit(priority, "p1"), unit(offPlan, "x1"), unit(offPlan, "x2"))
        val victim = BenchEviction.victim(player(level = 5, gold = 10, bench = bench), plan)

        assertEquals("우선 태그 카드를 먼저 팔았다", offPlan.id, victim?.unitDef?.id)
    }

    @Test
    fun `우선 태그를 가진 카드는 보유 수가 적어도 벤치를 비우고 들어온다`() {
        val priority = MasterData.units.minBy { BotScoring.power(it) }
        val plan = BotPlan(priorityTraitIds = listOf(priority.origin.traitId), isEarlyGame = false)
        val offPlan = MasterData.units
            .filter { it.origin.traitId != priority.origin.traitId && it.unitClass.traitId != priority.origin.traitId }
            .maxBy { BotScoring.power(it) }

        // 밀려날 카드가 더 세고 2장이나 모여 있다. 우선 태그가 첫 키가 아니면 들어오지 못한다.
        val state = player(
            level = 5,
            gold = 10,
            bench = listOf(unit(offPlan, "x1"), unit(offPlan, "x2")),
        )

        assertTrue(
            "우선 태그 카드가 보유 수에 밀렸다",
            BenchEviction.isUpgrade(priority, unit(offPlan, "x1"), state, plan),
        )
    }

    // --- 벤치 비우기: 내보낼 만한가 ---

    @Test
    fun `더 나쁜 카드를 들이려고 벤치를 비우지는 않는다`() {
        val strong = MasterData.units.maxBy { BotScoring.power(it) }
        val weak = MasterData.units.minBy { BotScoring.power(it) }
        val state = player(level = 5, gold = 10, bench = listOf(unit(strong, "s1")))

        assertFalse(
            "약한 카드를 들이려고 센 카드를 팔았다. 이러면 벤치가 회전문이 된다",
            BenchEviction.isUpgrade(weak, unit(strong, "s1"), state, noPlan),
        )
    }

    @Test
    fun `보유 수가 줄어드는 교체는 전투력이 세도 하지 않는다`() {
        val stacked = MasterData.units.minBy { BotScoring.power(it) }
        val strong = MasterData.units.filter { it.id != stacked.id }.maxBy { BotScoring.power(it) }

        // 약한 종을 2장 모아 둔 상태. 들어올 센 카드는 1장짜리라 스타업이 멀어진다.
        val state = player(
            level = 5,
            gold = 10,
            bench = listOf(unit(stacked, "k1"), unit(stacked, "k2")),
        )

        assertFalse(
            "전투력만 보고 2장 모아 둔 종을 밀어냈다",
            BenchEviction.isUpgrade(strong, unit(stacked, "k1"), state, noPlan),
        )
    }

    @Test
    fun `보유 수가 같으면 전투력이 센 쪽이 들어온다`() {
        val weak = MasterData.units.minBy { BotScoring.power(it) }
        val strong = MasterData.units.filter { it.id != weak.id }.maxBy { BotScoring.power(it) }
        val state = player(level = 5, gold = 10, bench = listOf(unit(weak, "w1")))

        assertTrue(
            "양쪽 다 1장인데 센 카드가 막혔다",
            BenchEviction.isUpgrade(strong, unit(weak, "w1"), state, noPlan),
        )
    }

    @Test
    fun `2성이 걸린 세 장째는 벤치를 비워서라도 들인다`() {
        val target = MasterData.units.minBy { BotScoring.power(it) }
        val strongerOneOff = MasterData.units
            .filter { it.id != target.id }
            .maxBy { BotScoring.power(it) }

        // 이미 2장 들고 있으므로 살 경우 3장이 된다. 밀려날 카드는 더 세지만 1장짜리다.
        val state = player(
            level = 5,
            gold = 10,
            bench = listOf(unit(target, "t1"), unit(target, "t2"), unit(strongerOneOff, "s1")),
        )

        assertTrue(
            "세 장째가 더 약하다는 이유로 막혔다. 스타업 항이 아무리 커도 2성이 안 된다",
            BenchEviction.isUpgrade(target, unit(strongerOneOff, "s1"), state, noPlan),
        )
    }
}
