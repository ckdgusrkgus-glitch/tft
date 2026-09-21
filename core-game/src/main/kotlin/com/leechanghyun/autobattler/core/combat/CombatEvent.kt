package com.leechanghyun.autobattler.core.combat

import com.leechanghyun.autobattler.core.model.HexCoord
import com.leechanghyun.autobattler.core.synergy.SynergyState

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
        /** 실제로 깎인 체력. 의미는 5단계 이전과 같다. */
        val damage: Int,
        /** 쉴드가 먹은 양. 10단계 애니메이션의 쉴드 연출이 쓴다. */
        val absorbed: Int = 0,
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

    /** 쉴드가 채워졌다. 심연의 아이들. 주기를 틱 단위로 단언하기 위한 이벤트다. */
    data class Shielded(
        override val tick: Int,
        val unitId: String,
        val amount: Int,
    ) : CombatEvent

    /**
     * 연쇄 번개가 터졌다. 폭풍의 부족.
     *
     * @param targetIds 주 대상을 제외한, 튄 순서대로의 대상 id. 순서가 결정적이라 단언할 수 있다.
     */
    data class ChainLightning(
        override val tick: Int,
        val sourceId: String,
        val primaryTargetId: String,
        val targetIds: List<String>,
        val damagePerTarget: Int,
    ) : CombatEvent

    /**
     * 전투 시작 시 발동해 있던 시너지. [tick] 은 항상 0 이다. 시너지는 전투 내내 바뀌지 않는다.
     *
     * 10단계 전투 HUD 가 "수호자 2 발동"을 그리는 데 쓴다.
     */
    data class TraitActivated(
        override val tick: Int,
        val team: CombatTeam,
        val traitId: String,
        val tier: Int,
        val threshold: Int,
        val memberCount: Int,
    ) : CombatEvent
}

/** 전투 결과. */
enum class CombatWinner { PLAYER, ENEMY, DRAW }

/**
 * 전투가 끝난 뒤의 요약.
 *
 * @param ticks 전투에 걸린 틱 수. 실제 시간은 `ticks * CombatRules.TICK_MS` 밀리초다.
 * @param timedOut [CombatRules.MAX_TICKS] 에 걸려 강제로 끝났는지.
 * @param survivors 살아남은 유닛의 id 와 남은 체력. 쉴드는 포함하지 않는다.
 * @param synergyByTeam 양 진영의 시너지 상태. 시너지 없이 돌린 전투면 비어 있다.
 */
data class CombatOutcome(
    val winner: CombatWinner,
    val ticks: Int,
    val timedOut: Boolean,
    val survivors: Map<String, Int>,
    val events: List<CombatEvent>,
    val synergyByTeam: Map<CombatTeam, SynergyState> = emptyMap(),
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
