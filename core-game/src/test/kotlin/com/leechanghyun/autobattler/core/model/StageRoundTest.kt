package com.leechanghyun.autobattler.core.model

import com.leechanghyun.autobattler.core.masterdata.AUGMENT_ROUNDS
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.masterdata.isAugmentRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 로드맵 9단계. 평면 라운드 번호와 "2-1" 표기 사이의 환산을 고정한다. */
class StageRoundTest {

    @Test
    fun `평면 번호와 스테이지 표기는 서로를 되돌린다`() {
        for (flat in 1..40) {
            val round = StageRound.ofFlat(flat)
            assertEquals(
                "$flat 라운드를 ${round.label} 로 읽었다가 되돌리면 달라진다",
                round,
                StageRound.of(round.stage, round.roundInStage),
            )
        }
    }

    @Test
    fun `스테이지는 ROUNDS_PER_STAGE 라운드마다 바뀐다`() {
        val perStage = StageRound.ROUNDS_PER_STAGE
        assertEquals(1, StageRound.ofFlat(1).stage)
        assertEquals(1, StageRound.ofFlat(perStage).stage)
        assertEquals("$perStage 라운드 다음은 2스테이지다", 2, StageRound.ofFlat(perStage + 1).stage)
        assertEquals(1, StageRound.ofFlat(perStage + 1).roundInStage)
    }

    @Test
    fun `1 보다 작은 라운드는 만들 수 없다`() {
        assertThrows(IllegalArgumentException::class.java) { StageRound.ofFlat(0) }
        assertThrows(IllegalArgumentException::class.java) { StageRound.of(0, 1) }
        assertThrows(IllegalArgumentException::class.java) {
            StageRound.of(1, StageRound.ROUNDS_PER_STAGE + 1)
        }
    }

    @Test
    fun `크립 라운드는 언제나 스테이지의 마지막 라운드다`() {
        // 명세서 4-9 의 "스테이지별 4번째 라운드" 가 ROUNDS_PER_STAGE = 4 의 근거다.
        // 둘이 어긋나면 크립이 스테이지 한가운데에 끼거나 존재하지 않는 라운드를 가리킨다.
        assertEquals(
            "명세서 4-9 의 크립 라운드 번호와 스테이지 길이가 어긋났다",
            StageRound.ROUNDS_PER_STAGE,
            MonsterDef.MONSTER_ROUND_IN_STAGE,
        )
        MasterData.monsters.forEach { monster ->
            val round = StageRound.of(monster.stage, MonsterDef.MONSTER_ROUND_IN_STAGE)
            assertEquals("${monster.name} 의 라운드 표기가 다르다", monster.roundLabel, round.label)
            assertEquals(
                "크립은 스테이지의 마지막 라운드여야 한다",
                StageRound.ROUNDS_PER_STAGE,
                round.roundInStage,
            )
        }
    }

    @Test
    fun `증강 라운드는 명세서가 적은 세 번뿐이고 크립과 겹치지 않는다`() {
        val augmentRounds = AUGMENT_ROUNDS.map { (stage, inStage) -> StageRound.of(stage, inStage) }
        assertEquals("명세서 4-8: 2-1, 3-2, 4-2 총 3회", 3, augmentRounds.size)
        assertEquals(listOf("2-1", "3-2", "4-2"), augmentRounds.map { it.label })

        // 명세서 4-9 가 "증강 라운드와 겹치지 않도록 분리" 라고 못박았다.
        augmentRounds.forEach { round ->
            assertTrue("${round.label} 이 증강 라운드로 인식되지 않는다", round.isAugmentRound)
            assertFalse(
                "${round.label} 이 크립 라운드와 겹친다",
                round.roundInStage == MonsterDef.MONSTER_ROUND_IN_STAGE,
            )
        }
        for (flat in 1..20) {
            val round = StageRound.ofFlat(flat)
            assertEquals(round in augmentRounds, round.isAugmentRound)
        }
    }
}
