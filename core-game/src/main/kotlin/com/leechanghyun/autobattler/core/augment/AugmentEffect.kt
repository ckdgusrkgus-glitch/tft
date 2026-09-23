package com.leechanghyun.autobattler.core.augment

import com.leechanghyun.autobattler.core.model.AugmentDef

/**
 * 증강 효과 10종. 명세서 4-8 표의 [AugmentDef.effectId] 문자열을 타입으로 옮긴 것이다.
 *
 * [AugmentDef] 는 Room 에 들어가야 해서 [AugmentDef.effectId] 가 `String` 이다. 문자열은 오타를
 * 컴파일 시점에 잡지 못하고, 표에만 있고 처리 분기가 없는 효과를 조용히 무시한다. 그래서 경계
 * 한 곳([of])에서 한 번만 열거형으로 바꾸고, 그 뒤로는 엔진 전체가 이 타입만 쓴다.
 * 효과가 늘면 `else` 없는 `when` 들이 전부 컴파일 에러를 낸다.
 *
 * [AugmentDef] 자체는 한 글자도 바꾸지 않았다. 필드를 더하면 Room 스키마가 바뀌고, 이 환경에서는
 * KSP 를 돌릴 수 없어 검증할 방법이 없다.
 */
enum class AugmentEffect {
    /** 황금손길: 매 라운드 종료 시 +2골드. */
    GOLD_PER_ROUND,

    /** 속성연마: 아이템 컴포넌트 1개 무료 지급. */
    GRANT_COMPONENT,

    /** 급속성장: 즉시 경험치 +8. */
    GRANT_EXP,

    /** 전열강화: 수호자 시너지 임계값 요구 인원 -1. */
    TRAIT_THRESHOLD_DISCOUNT,

    /** 폭풍의가호: 폭풍의 부족 유닛 공격속도 +15%. */
    ORIGIN_ATTACK_SPEED,

    /** 심연의계약: 체력을 대가로 매 라운드 골드 +1. */
    HP_FOR_GOLD,

    /** 재고정리: 매 라운드 리롤 1회 무료. */
    FREE_REROLL,

    /** 강철의의지: 아군 전체 방어력 +10. */
    TEAM_ARMOR,

    /** 사수의감각: 사수 유닛 치명타 확률 +20%. */
    CLASS_CRIT_CHANCE,

    /** 대장장이의축복: 완성 아이템 1개 무료 지급. */
    GRANT_COMPLETED_ITEM,

    ;

    companion object {
        private val byName: Map<String, AugmentEffect> = entries.associateBy { it.name }

        /** 마스터 데이터의 [AugmentDef.effectId] 를 해석한다. 표에 없는 값이면 즉시 터진다. */
        fun of(effectId: String): AugmentEffect = requireNotNull(byName[effectId]) {
            "알 수 없는 증강 효과 id: $effectId"
        }
    }
}

/** [AugmentEffect] 로 해석한 효과. */
val AugmentDef.effect: AugmentEffect get() = AugmentEffect.of(effectId)

/**
 * 증강이 바꾸는 면. "10종 전부 실제로 무언가를 바꾼다"를 테스트가 기계적으로 확인하는 데 쓴다.
 *
 * 시너지 쪽 [com.leechanghyun.autobattler.core.synergy.SynergyTables.SCOPE] 와 같은 장치다.
 * 표에만 올리고 처리 분기를 빼먹으면, 그 면을 실제로 읽어 보는 테스트가 값이 그대로인 것을 잡는다.
 */
enum class AugmentScope {
    /** [com.leechanghyun.autobattler.core.economy.Economy.roundIncome] 가 달라진다. */
    ECONOMY,

    /** 고르는 즉시 경험치와 레벨이 달라진다. */
    EXP,

    /** 고르는 즉시 [com.leechanghyun.autobattler.core.model.PlayerState.itemInventory] 가 늘어난다. */
    ITEM,

    /** 시너지 발동 단계가 달라진다. */
    SYNERGY,

    /** 전투 보정([com.leechanghyun.autobattler.core.synergy.TeamBuffs])이 달라진다. */
    COMBAT,

    /** 상점 조작 비용이 달라진다. */
    SHOP,
}
