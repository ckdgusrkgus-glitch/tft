package com.leechanghyun.autobattler.core.augment

import com.leechanghyun.autobattler.core.masterdata.AUGMENT_CHOICES_PER_ROUND
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 로드맵 9단계. 증강 후보 3개를 뽑는 규칙. 명세서 4-8: 3개 중 1개, 영구 지속. */
class AugmentRollerTest {

    private val player = PlayerState(playerId = "p", displayName = "나", isBot = false)

    @Test
    fun `후보는 서로 다른 세 개다`() {
        val rolled = AugmentRoller(random = Random(1)).roll(player)

        assertEquals(AUGMENT_CHOICES_PER_ROUND, rolled.size)
        assertEquals("같은 증강이 두 번 제시됐다", rolled.size, rolled.map { it.id }.toSet().size)
    }

    @Test
    fun `이미 고른 증강은 다시 제시되지 않는다`() {
        // 명세서 4-8 의 "영구 지속" 은 같은 증강을 두 번 쌓는다는 뜻이 아니다.
        val taken = MasterData.augments.take(4)
        val holder = player.copy(augments = taken)

        repeat(20) { seed ->
            val rolled = AugmentRoller(random = Random(seed)).roll(holder)
            assertTrue(
                "이미 보유한 ${rolled.firstOrNull { it in taken }?.name} 가 또 나왔다",
                rolled.none { it in taken },
            )
        }
    }

    @Test
    fun `섞인 목록으로 만들어도 같은 씨앗이면 같은 후보가 나온다`() {
        // 앱은 Room 에서 ORDER BY id 로 읽고 테스트는 명세서 표 순서를 쓴다. 뽑기 직전의
        // sortedBy { it.id } 를 지우면 같은 씨앗이 앱과 테스트에서 다른 카드를 내는데,
        // 그 상태로도 다른 테스트는 전부 통과한다.
        val spec = AugmentRoller(catalog = MasterData.augments, random = Random(9)).roll(player)
        val byId = AugmentRoller(catalog = MasterData.augments.sortedBy { it.id }, random = Random(9)).roll(player)
        val reversed = AugmentRoller(catalog = MasterData.augments.reversed(), random = Random(9)).roll(player)

        assertEquals("표 순서와 id 순서가 다른 후보를 낸다", spec, byId)
        assertEquals("목록 순서가 결과를 바꾼다", spec, reversed)
    }

    @Test
    fun `후보가 모자라면 조용히 줄이지 않고 터진다`() {
        val roller = AugmentRoller(catalog = MasterData.augments.take(2), random = Random(1))

        assertThrows(IllegalArgumentException::class.java) { roller.roll(player) }
    }

    @Test
    fun `세 번의 증강 라운드를 다 지나도 후보는 남는다`() {
        // 10종에서 3라운드 동안 1개씩 고르므로 마지막 제시 시점의 후보 풀은 8종이다.
        var holder = player
        repeat(3) {
            val rolled = AugmentRoller(random = Random(it)).roll(holder)
            holder = holder.copy(augments = holder.augments + rolled.first())
        }
        assertEquals(3, holder.augments.size)
        assertTrue(
            "마지막 라운드 뒤에도 제시 가능한 후보가 남아야 한다",
            AugmentRules.offerable(holder).size >= AUGMENT_CHOICES_PER_ROUND,
        )
    }
}
