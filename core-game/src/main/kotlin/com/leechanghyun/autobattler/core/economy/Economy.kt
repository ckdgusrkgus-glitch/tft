package com.leechanghyun.autobattler.core.economy

import com.leechanghyun.autobattler.core.augment.AugmentRules
import com.leechanghyun.autobattler.core.masterdata.EXP_TO_NEXT_LEVEL
import com.leechanghyun.autobattler.core.masterdata.EconomyRules
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.synergy.SynergyEngine

/**
 * 골드/경험치 계산 규칙. 명세서 4-1, 4-2.
 *
 * 전부 순수 함수라 [PlayerState] 를 받아 새 [PlayerState] 를 돌려준다.
 * 상태를 직접 고치지 않으므로 "어디선가 골드가 새는" 버그를 추적하기 쉽다.
 */
object Economy {

    /**
     * 유닛 판매가.
     *
     * 명세서에 판매가 규칙이 없어 원작 관행을 따랐다. 11단계에서 조정한다.
     * - 1코스트는 성 등급과 무관하게 산 값 그대로 돌려받는다. (1 / 3 / 9골드)
     * - 2코스트 이상은 2성부터 1골드씩 손해를 본다. (cost x 3 - 1, cost x 9 - 1)
     */
    fun sellPrice(unit: BoardUnit): Int {
        val cost = unit.unitDef.cost
        val fullValue = cost * unit.copiesConsumed
        return if (cost == 1 || unit.starLevel == 1) fullValue else fullValue - 1
    }

    /**
     * 라운드 종료 시 지급되는 골드. 명세서 4-1, 4-4(황금가문), 4-8(황금손길·심연의계약).
     *
     * 기본 수입 + 보유 골드 이자(10당 1, 최대 5) + 연승 또는 연패 보너스 + 황금가문 시너지 + 증강.
     *
     * ### 지급 시점, 그리고 9단계에 endRound 훅을 만들지 않은 이유
     * 명세서 4-4 는 황금가문을, 4-8 은 황금손길을 "라운드 종료 시"라고 적지만 이 엔진은 라운드
     * **시작**에 수입을 준다([startRound]). 전투가 끝난 뒤 다음 라운드 시작 사이에 보드도 증강도
     * 건드리는 코드가 없어 읽히는 값이 같고 지급액도 같으므로, 이 함수를 "직전 라운드 종료분을
     * 다음 시작에 정산하는 것"으로 규정한다.
     *
     * 5단계는 여기에 "진짜 `endRound` 훅은 9단계에 만든다" 고 적어 두었지만 만들지 않았다.
     * 훅이 필요한 줄 알았던 이유는 증강이 고른 **그 라운드부터** 골드를 준다고 보았기 때문인데,
     * 위 규정대로면 2-1 에 고른 황금손길은 2-2 시작에 첫 2골드를 받고 그것이 곧 "2-1 종료분"이다.
     * 지급 시점이 이미 맞으므로 훅은 같은 일을 하는 두 번째 경로가 될 뿐이다.
     *
     * ### 이자와 섞이지 않는다
     * 시너지 골드를 `state.gold` 에 먼저 넣으면 10골드당 1 구간이 밀려 이번 라운드 이자에 복리로
     * 섞인다. 별도 항으로 더해 그것을 막는다. 다음 라운드의 이자 원금에는 당연히 포함된다.
     * 증강 골드도 같은 이유로 별도 항이고, 시너지 골드와도 **합치지 않는다.**
     * [com.leechanghyun.autobattler.core.synergy.SynergyState.goldPerRound] 는 황금가문만의
     * 값으로 남아야 5단계가 세운 `황금가문 골드는 이자와 섞이지 않는다` 가 계속 그것을 검증한다.
     */
    fun roundIncome(state: PlayerState): Int {
        val streak = maxOf(state.winStreak, state.loseStreak)
        return EconomyRules.BASE_INCOME +
            EconomyRules.interestFor(state.gold) +
            EconomyRules.streakBonusFor(streak) +
            SynergyEngine.resolve(state).goldPerRound +
            AugmentRules.goldPerRound(state.augments)
    }

    /** 라운드 수입을 지급하고 라운드당 자동 경험치를 더한 상태를 돌려준다. */
    fun startRound(state: PlayerState): PlayerState =
        grantExp(
            state.copy(gold = state.gold + roundIncome(state)),
            EconomyRules.PASSIVE_EXP_PER_ROUND,
        )

    /**
     * 경험치를 주고 올릴 수 있는 만큼 레벨을 올린다. 명세서 4-2.
     *
     * 최대 레벨에 도달하면 남은 경험치는 버린다.
     */
    fun grantExp(state: PlayerState, amount: Int): PlayerState {
        require(amount >= 0) { "경험치는 음수가 될 수 없다" }
        var level = state.level
        var exp = state.exp + amount

        while (level < PlayerState.MAX_LEVEL) {
            val needed = EXP_TO_NEXT_LEVEL[level] ?: break
            if (exp < needed) break
            exp -= needed
            level++
        }
        if (level >= PlayerState.MAX_LEVEL) exp = 0

        return state.copy(level = level, exp = exp)
    }

    /** 다음 레벨까지 남은 경험치. 최대 레벨이면 null. */
    fun expToNextLevel(state: PlayerState): Int? {
        if (state.level >= PlayerState.MAX_LEVEL) return null
        val needed = EXP_TO_NEXT_LEVEL[state.level] ?: return null
        return needed - state.exp
    }

    /** 전투 결과를 반영해 연승/연패와 체력을 갱신한다. 패배 시 체력 감소량은 [hpLoss] 다. */
    fun recordResult(state: PlayerState, won: Boolean, hpLoss: Int = 0): PlayerState =
        if (won) {
            state.copy(winStreak = state.winStreak + 1, loseStreak = 0)
        } else {
            state.copy(
                winStreak = 0,
                loseStreak = state.loseStreak + 1,
                hp = (state.hp - hpLoss).coerceAtLeast(0),
            )
        }
}
