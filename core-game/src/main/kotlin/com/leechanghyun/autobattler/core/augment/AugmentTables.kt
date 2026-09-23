package com.leechanghyun.autobattler.core.augment

import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.masterdata.traitId
import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.UnitClass

/**
 * 증강 효과의 수치표. 명세서 4-8 이 글로 적은 값을 한 파일에 모은 것이다.
 *
 * 시너지의 [com.leechanghyun.autobattler.core.synergy.SynergyTables] 와 같은 자리다. 수치를
 * 규칙 코드 안에 흩어 두면 11단계 밸런스 튜닝이 열 군데를 찾아다녀야 한다.
 *
 * ### 어디까지가 명세서 값인가
 * 아래 값 중 **명세서에 글로 적힌 것**은 골드 +2, 경험치 +8, 임계값 -1, 공격속도 +15%,
 * 체력 -100, 골드 +1, 방어력 +10, 치명타 확률 +20% 다. 명세서에 없어 이 단계에서 정한 값은
 * [FREE_REROLLS_PER_ROUND] 하나뿐이고, 그 근거는 [FREE_REROLLS_PER_ROUND] KDoc 에 있다.
 * 치명타가 **몇 배**로 때리는지는 명세서에 없어 전투 쪽
 * [com.leechanghyun.autobattler.core.combat.CombatRules.CRIT_BONUS_PERCENT] 에 있다.
 */
object AugmentTables {

    /** 효과가 바꾸는 면. 10종 전부 있어야 한다. */
    val SCOPE: Map<AugmentEffect, AugmentScope> = mapOf(
        AugmentEffect.GOLD_PER_ROUND to AugmentScope.ECONOMY,
        AugmentEffect.HP_FOR_GOLD to AugmentScope.ECONOMY,
        AugmentEffect.GRANT_EXP to AugmentScope.EXP,
        AugmentEffect.GRANT_COMPONENT to AugmentScope.ITEM,
        AugmentEffect.GRANT_COMPLETED_ITEM to AugmentScope.ITEM,
        AugmentEffect.TRAIT_THRESHOLD_DISCOUNT to AugmentScope.SYNERGY,
        AugmentEffect.ORIGIN_ATTACK_SPEED to AugmentScope.COMBAT,
        AugmentEffect.TEAM_ARMOR to AugmentScope.COMBAT,
        AugmentEffect.CLASS_CRIT_CHANCE to AugmentScope.COMBAT,
        AugmentEffect.FREE_REROLL to AugmentScope.SHOP,
    )

    /** 매 라운드 더 들어오는 골드. */
    val GOLD_PER_ROUND: Map<AugmentEffect, Int> = mapOf(
        AugmentEffect.GOLD_PER_ROUND to 2,
        AugmentEffect.HP_FOR_GOLD to 1,
    )

    /**
     * 고르는 순간 한 번 내는 체력 값.
     *
     * 명세서의 "체력 -100" 과 이 엔진의 시작 체력 100([com.leechanghyun.autobattler.core.model.PlayerState.STARTING_HP])
     * 이 정면으로 부딪친다. 그대로 빼면 `hp <= 0` 이 되어 고르는 즉시 탈락한다.
     * 어느 쪽도 고치지 않는다. 명세서 값은 명세서 값이고, 시작 체력 100 은 README 가 임의
     * 초기값으로 이미 공표한 수다. 대신 [AugmentRules.hpAfterCost] 가 **최소 1 로 막는다.**
     * 탈락 방지는 밸런스가 아니라 안전 규칙이라 수치를 만들어 내지 않는다.
     *
     * 그 결과 시작 체력 100 에서 심연의계약은 "체력 1 로 떨어지고 매 라운드 +1골드" 가 된다.
     * 사실상 아무도 고르지 않을 선택지이며, 두 수 중 어느 쪽을 움직일지는 11단계 밸런스의 일이다.
     */
    val HP_COST: Map<AugmentEffect, Int> = mapOf(
        AugmentEffect.HP_FOR_GOLD to 100,
    )

    /** 고르는 순간 한 번 받는 경험치. */
    val GRANT_EXP: Map<AugmentEffect, Int> = mapOf(
        AugmentEffect.GRANT_EXP to 8,
    )

    /** 요구 인원을 깎아 줄 시너지와 깎는 양. */
    val TRAIT_DISCOUNT: Map<AugmentEffect, Pair<String, Int>> = mapOf(
        AugmentEffect.TRAIT_THRESHOLD_DISCOUNT to (UnitClass.WARDEN.traitId to 1),
    )

    /** 계열 한정 공격속도 보정. */
    val ORIGIN_ATTACK_SPEED: Map<AugmentEffect, Pair<Origin, Float>> = mapOf(
        AugmentEffect.ORIGIN_ATTACK_SPEED to (Origin.STORM_TRIBE to 0.15f),
    )

    /** 아군 전체 방어력 가산. */
    val TEAM_ARMOR: Map<AugmentEffect, Int> = mapOf(
        AugmentEffect.TEAM_ARMOR to 10,
    )

    /**
     * 직업 한정 치명타 충전량. 기본 공격 1회당 이만큼 쌓이고
     * [com.leechanghyun.autobattler.core.combat.CombatRules.CRIT_CHARGE_FULL] 에서 터진다.
     *
     * 20 은 명세서의 "치명타 확률 +20%" 를 그대로 옮긴 값이다. 상한이 100 이라 5번에 1번,
     * 즉 공격의 20% 가 치명타가 된다. 확률을 난수 없이 표현하는 방식은 폭풍의 부족 연쇄 번개와
     * 같다([com.leechanghyun.autobattler.core.combat.CombatUnit.chargeChain]).
     */
    val CLASS_CRIT_CHARGE: Map<AugmentEffect, Pair<UnitClass, Int>> = mapOf(
        AugmentEffect.CLASS_CRIT_CHANCE to (UnitClass.MARKSMAN to 20),
    )

    /**
     * 재고정리가 매 라운드 주는 무료 리롤 횟수. **이 단계에서 정한 값이다.**
     *
     * 명세서는 "상점 리롤 비용 1턴 동안 무료" 라고 적지만 같은 절이 증강을 "영구 지속" 이라고도
     * 한다. 두 문장을 동시에 만족시키는 읽기는 하나뿐이다. 효과는 영구히 남고, 그 효과가 매
     * 라운드 주는 것이 "무료 리롤" 이다. 횟수 제한이 없으면 골드가 무의미해져 4-1 경제가 통째로
     * 무너지므로 1회로 둔다. 11단계 재검토 대상이다.
     */
    const val FREE_REROLLS_PER_ROUND = 1

    init {
        require(SCOPE.keys == AugmentEffect.entries.toSet()) {
            "증강 효과 ${AugmentEffect.entries.toSet() - SCOPE.keys} 의 적용 면이 표에 없다"
        }
        // 마스터 데이터의 effectId 문자열이 전부 해석되는지. 표에만 있고 타입에 없는 효과는
        // 여기서 앱이 뜨기 전에 터진다.
        MasterData.augments.forEach { AugmentEffect.of(it.effectId) }
    }
}
