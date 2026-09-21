package com.leechanghyun.autobattler.core.combat

import com.leechanghyun.autobattler.core.model.HexCoord

/**
 * 전투 중 일어난 일. 테스트에서 진행을 확인하고, 10단계에서 애니메이션을 입히는 데 쓴다.
 *
 * 시뮬레이션은 화면과 무관하게 끝까지 돌아간 뒤 이 목록을 남긴다.
 */
sealed interface CombatEvent {
    val tick: Int

    data class Moved(
        override val tick: Int,
        val unitId: String,
        val from: HexCoord,
        val to: HexCoord,
    ) : CombatEvent

    data class Attacked(
        override val tick: Int,
        val attackerId: String,
        val targetId: String,
        val damage: Int,
    ) : CombatEvent

    data class SkillCast(
        override val tick: Int,
        val casterId: String,
        val skillId: String,
        val targetIds: List<String>,
        val damagePerTarget: Int,
    ) : CombatEvent

    data class Died(
        override val tick: Int,
        val unitId: String,
    ) : CombatEvent
}

/** 전투 결과. */
enum class CombatWinner { PLAYER, ENEMY, DRAW }

/**
 * 전투가 끝난 뒤의 요약.
 *
 * @param ticks 전투에 걸린 틱 수. 실제 시간은 `ticks * CombatRules.TICK_MS` 밀리초다.
 * @param timedOut [CombatRules.MAX_TICKS] 에 걸려 강제로 끝났는지.
 * @param survivors 살아남은 유닛의 id 와 남은 체력.
 */
data class CombatOutcome(
    val winner: CombatWinner,
    val ticks: Int,
    val timedOut: Boolean,
    val survivors: Map<String, Int>,
    val events: List<CombatEvent>,
) {
    val durationMillis: Int get() = ticks * CombatRules.TICK_MS

    /** 패배한 쪽이 입는 체력 피해. 살아남은 적 유닛 수에 비례한다. 수치는 임의 초기값이다. */
    fun playerHpLoss(): Int = when (winner) {
        CombatWinner.PLAYER, CombatWinner.DRAW -> 0
        CombatWinner.ENEMY -> BASE_HP_LOSS + survivors.size
    }

    companion object {
        /** 패배 시 기본 체력 감소량. 명세서에 수치가 없어 임의 초기값으로 두었다. */
        const val BASE_HP_LOSS = 2
    }
}
