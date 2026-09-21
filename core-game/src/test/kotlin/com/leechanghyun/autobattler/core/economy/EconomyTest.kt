package com.leechanghyun.autobattler.core.economy

import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 골드/경험치 계산 규칙을 검증한다. 명세서 4-1, 4-2. */
class EconomyTest {

    private fun player(
        gold: Int = 0,
        level: Int = 1,
        exp: Int = 0,
        winStreak: Int = 0,
        loseStreak: Int = 0,
    ) = PlayerState(
        playerId = "p1",
        displayName = "나",
        isBot = false,
        gold = gold,
        level = level,
        exp = exp,
        winStreak = winStreak,
        loseStreak = loseStreak,
    )

    private fun unit(unitId: String, star: Int = 1) =
        BoardUnit(instanceId = "x", unitDef = MasterData.unit(unitId), starLevel = star)

    @Test
    fun `기본 수입은 5골드다`() {
        assertEquals(5, Economy.roundIncome(player(gold = 0)))
    }

    @Test
    fun `이자가 수입에 더해진다`() {
        assertEquals("30골드면 이자 3", 8, Economy.roundIncome(player(gold = 30)))
        assertEquals("이자는 5에서 멈춘다", 10, Economy.roundIncome(player(gold = 90)))
    }

    @Test
    fun `연승과 연패 모두 보너스를 받는다`() {
        assertEquals("3연승 보너스 1", 6, Economy.roundIncome(player(winStreak = 3)))
        assertEquals("5연패 보너스 3", 8, Economy.roundIncome(player(loseStreak = 5)))
    }

    @Test
    fun `라운드를 시작하면 수입과 자동 경험치를 함께 받는다`() {
        val after = Economy.startRound(player(gold = 10, level = 3))
        assertEquals("5 기본 + 1 이자", 16, after.gold)
        assertEquals(2, after.exp)
        assertEquals(3, after.level)
    }

    @Test
    fun `경험치가 차면 레벨이 오른다`() {
        val after = Economy.grantExp(player(level = 1), 2)
        assertEquals(2, after.level)
        assertEquals(0, after.exp)
    }

    @Test
    fun `경험치를 한 번에 많이 주면 여러 레벨이 오른다`() {
        // 레벨 1에서 2로 2, 2에서 3으로 6. 합쳐서 8이면 레벨 3이 된다.
        val after = Economy.grantExp(player(level = 1), 8)
        assertEquals(3, after.level)
        assertEquals(0, after.exp)
    }

    @Test
    fun `남은 경험치는 다음 레벨로 이월된다`() {
        val after = Economy.grantExp(player(level = 1), 5)
        assertEquals(2, after.level)
        assertEquals("2를 쓰고 3이 남는다", 3, after.exp)
    }

    @Test
    fun `최대 레벨에서는 경험치가 쌓이지 않는다`() {
        val after = Economy.grantExp(player(level = PlayerState.MAX_LEVEL), 100)
        assertEquals(PlayerState.MAX_LEVEL, after.level)
        assertEquals(0, after.exp)
        assertNull(Economy.expToNextLevel(after))
    }

    @Test
    fun `다음 레벨까지 남은 경험치를 알려준다`() {
        assertEquals(2, Economy.expToNextLevel(player(level = 1)))
        assertEquals("레벨 3은 12 필요, 5 보유", 7, Economy.expToNextLevel(player(level = 3, exp = 5)))
    }

    @Test
    fun `1성 판매가는 코스트와 같다`() {
        assertEquals(1, Economy.sellPrice(unit("steel_guard")))
        assertEquals(5, Economy.sellPrice(unit("apostle_of_end")))
    }

    @Test
    fun `1코스트는 성이 올라도 손해가 없다`() {
        assertEquals(3, Economy.sellPrice(unit("steel_guard", star = 2)))
        assertEquals(9, Economy.sellPrice(unit("steel_guard", star = 3)))
    }

    @Test
    fun `2코스트 이상은 성이 오르면 1골드 손해를 본다`() {
        assertEquals("2코스트 2성 = 2x3-1", 5, Economy.sellPrice(unit("dark_blade", star = 2)))
        assertEquals("2코스트 3성 = 2x9-1", 17, Economy.sellPrice(unit("dark_blade", star = 3)))
        assertEquals("3코스트 2성 = 3x3-1", 8, Economy.sellPrice(unit("abyss_devourer", star = 2)))
    }

    @Test
    fun `승리하면 연승이 쌓이고 연패는 초기화된다`() {
        val after = Economy.recordResult(player(loseStreak = 3), won = true)
        assertEquals(1, after.winStreak)
        assertEquals(0, after.loseStreak)
    }

    @Test
    fun `패배하면 연패가 쌓이고 체력이 준다`() {
        val after = Economy.recordResult(player(winStreak = 2), won = false, hpLoss = 8)
        assertEquals(0, after.winStreak)
        assertEquals(1, after.loseStreak)
        assertEquals(PlayerState.STARTING_HP - 8, after.hp)
    }

    @Test
    fun `체력은 0 아래로 내려가지 않는다`() {
        val after = Economy.recordResult(player(), won = false, hpLoss = 999)
        assertEquals(0, after.hp)
        assertEquals(true, after.isEliminated)
    }
}
