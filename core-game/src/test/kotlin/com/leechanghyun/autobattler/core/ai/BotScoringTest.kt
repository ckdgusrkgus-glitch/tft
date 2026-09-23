package com.leechanghyun.autobattler.core.ai

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.board.OffsetCoord
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.masterdata.traitId
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.model.UnitClass
import com.leechanghyun.autobattler.core.model.UnitDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 명세서 4-7 스코어링의 다섯 항을 하나씩 검증한다. 로드맵 8단계.
 *
 * 항을 따로 단언하는 이유는, 합계만 보면 어느 항이 0 이 되어도 다른 항이 덮어 테스트가 통과하기
 * 때문이다. 항별로 잡아야 "스코어링이 죽은 코드인데 초록색"을 막을 수 있다.
 */
class BotScoringTest {

    private fun player(
        gold: Int = 50,
        level: Int = 5,
        bench: List<BoardUnit> = emptyList(),
        board: List<BoardUnit> = emptyList(),
    ) = PlayerState(
        playerId = "bot",
        displayName = "봇",
        isBot = true,
        gold = gold,
        level = level,
        bench = bench,
        board = board,
    )

    private fun unit(def: UnitDef, id: String, onBoardAt: Int? = null, starLevel: Int = 1) = BoardUnit(
        instanceId = id,
        unitDef = def,
        starLevel = starLevel,
        position = onBoardAt?.let { HexBoard.fromOffset(OffsetCoord(row = 3 - it / 7, col = it % 7)) },
    )

    private fun defsOf(origin: Origin, count: Int) =
        MasterData.units.filter { it.origin == origin }.take(count)
            .also { require(it.size == count) }

    private fun defsOf(unitClass: UnitClass, count: Int) =
        MasterData.units.filter { it.unitClass == unitClass }.take(count)
            .also { require(it.size == count) }

    private val noPlan = BotPlan(priorityTraitIds = emptyList(), isEarlyGame = false)

    // --- 1항: 코스트 대비 레벨 적정성 ---

    @Test
    fun `기대 코스트는 상점 확률표에서 나오고 레벨이 오를수록 커진다`() {
        // 새 표를 만들지 않고 명세서 4-1 의 확정 확률표에서 유도한다는 결정을 못박는다.
        val byLevel = (2..10).map { BotScoring.expectedCost(it) }

        assertEquals("레벨 2 는 1코스트 100% 라 정확히 1.0", 1.0, BotScoring.expectedCost(2), 1e-9)
        assertEquals("레벨 9 확률표의 가중평균", 3.0, BotScoring.expectedCost(9), 1e-9)
        assertTrue("레벨이 오르면 기대 코스트가 줄지 않는다: $byLevel", byLevel.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `적정 코스트에서 멀수록 점수가 깎인다`() {
        val oneCost = MasterData.units.first { it.cost == 1 }
        val fourCost = MasterData.units.first { it.cost == 4 }
        val fiveCost = MasterData.units.first { it.cost == 5 }

        assertTrue(
            "레벨 2 에서는 1코스트가 낫다",
            BotScoring.levelFitnessTerm(oneCost, 2) > BotScoring.levelFitnessTerm(fourCost, 2),
        )
        assertTrue(
            "레벨 9 에서는 뒤집힌다",
            BotScoring.levelFitnessTerm(fourCost, 9) > BotScoring.levelFitnessTerm(oneCost, 9),
        )
        assertTrue("이 항은 0 이하다", BotScoring.levelFitnessTerm(oneCost, 2) <= 0.0)

        // 이 척도는 거리의 절댓값이라 **양쪽으로 대칭**이다. 레벨 9 의 기대 코스트가 정확히 3.0 이라
        // 1코스트와 5코스트가 똑같이 -2 를 받는다. 의도한 성질이다. 5코스트를 특별히 밀고 싶다면
        // 그건 새 규칙이고 11단계에서 근거와 함께 넣을 일이다.
        assertEquals(
            BotScoring.levelFitnessTerm(oneCost, 9),
            BotScoring.levelFitnessTerm(fiveCost, 9),
            1e-9,
        )
    }

    // --- 2항: 스타업 임박 ---

    @Test
    fun `같은 유닛을 가질수록 스타업 항이 커진다`() {
        val def = MasterData.units.first()

        assertEquals(0.0, BotScoring.starUpTerm(def, player()), 1e-9)
        assertEquals(
            BotWeights.STAR_UP_PER_COPY,
            BotScoring.starUpTerm(def, player(bench = listOf(unit(def, "a")))),
            1e-9,
        )
        assertEquals(
            "2장이면 다음 한 장이 2성을 만든다",
            BotWeights.STAR_UP_PER_COPY * 2,
            BotScoring.starUpTerm(def, player(bench = listOf(unit(def, "a"), unit(def, "b")))),
            1e-9,
        )
    }

    @Test
    fun `벤치와 보드를 함께 세고 다른 유닛은 세지 않는다`() {
        val def = MasterData.units[0]
        val other = MasterData.units[1]
        val state = player(
            bench = listOf(unit(def, "a"), unit(other, "x")),
            board = listOf(unit(def, "b", onBoardAt = 0)),
        )
        assertEquals(BotWeights.STAR_UP_PER_COPY * 2, BotScoring.starUpTerm(def, state), 1e-9)
        assertEquals(BotWeights.STAR_UP_PER_COPY * 1, BotScoring.starUpTerm(other, state), 1e-9)
    }

    @Test
    fun `이미 올라간 2성은 스타업 항에 세지 않는다`() {
        // 상점 카드는 항상 1성이고 1성끼리만 합쳐진다. 2성을 세면 오지 않을 합성을 기대하게 된다.
        val def = MasterData.units.first()
        val state = player(bench = listOf(unit(def, "a", starLevel = 2), unit(def, "b", starLevel = 2)))
        assertEquals(0.0, BotScoring.starUpTerm(def, state), 1e-9)
    }

    // --- 3항: 태그 겹침 ---

    @Test
    fun `보드에 같은 태그가 많을수록 겹침 점수가 커진다`() {
        val mecha = defsOf(Origin.MECHA, 3)
        val candidate = mecha[2]
        val empty = player()
        val oneMember = player(board = listOf(unit(mecha[0], "a", onBoardAt = 0)))
        val twoMembers = player(board = listOf(unit(mecha[0], "a", 0), unit(mecha[1], "b", 1)))

        val scores = listOf(empty, oneMember, twoMembers)
            .map { BotScoring.synergyOverlapTerm(candidate, it, noPlan) }

        assertEquals("빈 보드는 겹칠 것이 없다", 0.0, scores[0], 1e-9)
        assertTrue("겹침이 늘면 점수도 는다: $scores", scores.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `우선 태그는 가중치를 더 받는다`() {
        // 명세서 4-7: "그 태그에 해당하는 유닛의 가중치를 높인다".
        val mecha = defsOf(Origin.MECHA, 2)
        val state = player(board = listOf(unit(mecha[0], "a", onBoardAt = 0)))
        val plan = BotPlan(priorityTraitIds = listOf(Origin.MECHA.traitId), isEarlyGame = false)

        val plain = BotScoring.synergyOverlapTerm(mecha[1], state, noPlan)
        val prioritised = BotScoring.synergyOverlapTerm(mecha[1], state, plan)

        assertTrue("우선 태그가 아니어도 겹침 점수는 있다", plain > 0.0)
        assertTrue("우선 태그면 더 받는다: $plain -> $prioritised", prioritised > plain)
    }

    @Test
    fun `계열과 직업을 따로 센다`() {
        // 한 유닛이 두 태그를 가지므로 양쪽에서 점수를 받을 수 있어야 한다.
        val mechaWarden = MasterData.units.first { it.origin == Origin.MECHA && it.unitClass == UnitClass.WARDEN }
        val otherMecha = MasterData.units.first { it.origin == Origin.MECHA && it.id != mechaWarden.id }
        val otherWarden = MasterData.units.first { it.unitClass == UnitClass.WARDEN && it.id != mechaWarden.id }

        val onlyOrigin = player(board = listOf(unit(otherMecha, "a", onBoardAt = 0)))
        val bothTags = player(board = listOf(unit(otherMecha, "a", 0), unit(otherWarden, "b", 1)))

        val one = BotScoring.synergyOverlapTerm(mechaWarden, onlyOrigin, noPlan)
        val two = BotScoring.synergyOverlapTerm(mechaWarden, bothTags, noPlan)
        assertTrue("계열만 겹칠 때보다 계열+직업이 겹칠 때가 커야 한다: $one -> $two", two > one)
    }

    // --- 4항: 임계값 돌파 ---

    @Test
    fun `임계값을 새로 넘기는 유닛에만 보너스가 붙는다`() {
        val mecha = defsOf(Origin.MECHA, 3)
        val threshold = MasterData.trait(Origin.MECHA).thresholds.first()
        assertEquals("이 테스트는 첫 임계값이 2라는 전제에서 쓰였다", 2, threshold)

        val oneOnBoard = player(board = listOf(unit(mecha[0], "a", onBoardAt = 0)))
        val twoOnBoard = player(board = listOf(unit(mecha[0], "a", 0), unit(mecha[1], "b", 1)))

        assertTrue("2번째 기계공학자는 시너지를 켠다", BotScoring.thresholdBreakTerm(mecha[1], oneOnBoard) > 0.0)
        assertEquals(
            "3번째는 아직 다음 임계값(4)에 못 미쳐 보너스가 없다",
            0.0,
            BotScoring.thresholdBreakTerm(mecha[2], twoOnBoard),
            1e-9,
        )
    }

    @Test
    fun `보드에 이미 있는 종은 임계값을 넘기지 못한다`() {
        // 5단계가 정한 "서로 다른 유닛 종만 센다"가 여기서도 지켜져야 한다.
        // 복제본의 가치는 2항이 이미 값을 매겼으므로 4항까지 주면 이중으로 세는 것이다.
        val mecha = defsOf(Origin.MECHA, 2)
        val state = player(board = listOf(unit(mecha[0], "a", onBoardAt = 0)))
        assertEquals(0.0, BotScoring.thresholdBreakTerm(mecha[0], state), 1e-9)
        assertTrue(BotScoring.thresholdBreakTerm(mecha[1], state) > 0.0)
    }

    // --- 5항: 골드 ---

    @Test
    fun `사고 나서 최소 골드가 남지 않으면 감당 불가다`() {
        // 명세서 4-7: "항상 최소 4골드는 남기고(이자 확보)".
        val oneCost = MasterData.units.first { it.cost == 1 }
        assertTrue(BotScoring.canAfford(oneCost, player(gold = BotWeights.GOLD_RESERVE + 1)))
        assertFalse(BotScoring.canAfford(oneCost, player(gold = BotWeights.GOLD_RESERVE)))
        assertFalse(BotScoring.canAfford(oneCost, player(gold = 0)))
    }

    @Test
    fun `감당 불가는 다른 항이 아무리 커도 덮을 수 없다`() {
        val def = MasterData.units.first { it.cost == 1 }
        val rich = player(gold = 50, bench = listOf(unit(def, "a"), unit(def, "b")))
        val broke = rich.copy(gold = BotWeights.GOLD_RESERVE)

        val good = BotScoring.score(def, rich, noPlan)
        val unaffordable = BotScoring.score(def, broke, noPlan)

        assertTrue("2성 임박이라 점수가 높아야 의미가 있는 테스트다", good.total > 0.0)
        assertEquals(
            "명세서가 큰 음수가 아니라 -무한대라고 적었다",
            Double.NEGATIVE_INFINITY,
            unaffordable.total,
            0.0,
        )
        assertFalse(unaffordable.worthBuying)
    }

    // --- 항들 사이의 서열 ---

    @Test
    fun `스타업 임박이 다른 어떤 항보다 우선한다`() {
        // 명세서 4-7: "2성 완성 임박 시 최우선". 이 서열이 깨지면 봇이 2성을 놓친다.
        val mecha = defsOf(Origin.MECHA, 3)
        val target = MasterData.units.first { it.id !in mecha.map { m -> m.id } }

        // 보드는 기계공학자 1명. 후보 A = 시너지를 켜는 기계공학자, 후보 B = 2장 들고 있는 남
        val state = player(
            board = listOf(unit(mecha[0], "on-board", onBoardAt = 0)),
            bench = listOf(unit(target, "x"), unit(target, "y")),
        )
        val plan = BotPlan(priorityTraitIds = listOf(Origin.MECHA.traitId), isEarlyGame = false)

        val synergyPick = BotScoring.score(mecha[1], state, plan)
        val starUpPick = BotScoring.score(target, state, plan)

        assertTrue("시너지 후보가 임계값을 실제로 넘겨야 비교에 의미가 있다", synergyPick.thresholdBreak > 0.0)
        assertTrue(
            "2성 임박이 시너지 돌파를 이겨야 한다: 스타업 ${starUpPick.total} vs 시너지 ${synergyPick.total}",
            starUpPick.total > synergyPick.total,
        )
    }

    // --- 초반 예외 ---

    @Test
    fun `초반에는 태그를 보지 않고 코스트 대비 스탯만 본다`() {
        // 명세서 4-7: "보드가 비어있는 초반(1~3라운드)은 태그 무관하게 코스트 대비 스탯이 좋은 유닛".
        val mecha = defsOf(Origin.MECHA, 2)
        val early = BotPlan(priorityTraitIds = listOf(Origin.MECHA.traitId), isEarlyGame = true)
        val state = player(board = listOf(unit(mecha[0], "a", onBoardAt = 0)))

        val score = BotScoring.score(mecha[1], state, early)
        assertTrue("겹침 항 자체는 계산된다", score.synergyOverlap > 0.0)
        assertEquals("그래도 합계는 코스트 대비 스탯이다", score.costEfficiency, score.total, 1e-9)
    }

    @Test
    fun `코스트 대비 스탯은 싼 유닛을 유리하게 본다`() {
        val oneCost = MasterData.units.first { it.cost == 1 }
        val fiveCost = MasterData.units.first { it.cost == 5 }
        assertTrue("5코스트가 절대 전투력은 더 높다", BotScoring.power(fiveCost) > BotScoring.power(oneCost))
        assertTrue(
            "골드 1당으로 나누면 1코스트가 낫다",
            BotScoring.costEfficiency(oneCost) > BotScoring.costEfficiency(fiveCost),
        )
    }

    // --- 아이템 배분이 쓰는 보유 스탯 ---

    @Test
    fun `보유 스탯은 성 등급과 장착 아이템을 반영한다`() {
        val def = MasterData.units.first()
        val bare = unit(def, "a")
        val twoStar = bare.copy(starLevel = 2)
        val geared = bare.copy(items = listOf(MasterData.item("comp_might"), MasterData.item("comp_vitality")))

        assertTrue("2성이 1성보다 강하다", BotScoring.instancePower(twoStar) > BotScoring.instancePower(bare))
        assertTrue("아이템을 끼면 강해진다", BotScoring.instancePower(geared) > BotScoring.instancePower(bare))
    }
}
