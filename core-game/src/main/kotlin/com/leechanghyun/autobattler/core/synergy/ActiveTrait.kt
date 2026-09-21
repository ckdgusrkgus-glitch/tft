package com.leechanghyun.autobattler.core.synergy

import com.leechanghyun.autobattler.core.model.TraitDef

/**
 * 시너지 버프가 걸리는 범위.
 *
 * 명세서 4-4 표는 계열 2종에만 "아군 전체"라고 적고 나머지 6종은 범위를 적지 않는다.
 * 그래서 아래 배정은 **확정값이 아니라 해석**이며, 근거는 증강 표(4-8)의 어휘 대비다.
 * 태그 한정일 때는 "폭풍의 부족 유닛", "사수 시너지 유닛"이라 쓰고,
 * 팀 전체일 때는 "아군 전체 방어력 +10"이라 쓴다.
 */
enum class TraitScope {
    /** 아군 전원에게 걸린다. 기계공학자, 심연의 아이들. */
    TEAM,

    /** 그 태그를 가진 유닛에게만 걸린다. 폭풍의 부족, 검사, 마법사, 사수, 수호자. */
    TAGGED,

    /** 전투가 아니라 경제에 걸린다. 황금가문뿐이다. */
    ECONOMY,
}

/**
 * 시너지 1종의 현재 상태. 화면에 그대로 그릴 수 있는 값까지 들고 있다.
 *
 * [TraitDef.activeThreshold] 가 못 하는 것을 여기서 채운다. 그 함수는 인원을 세지 않고,
 * 증강 할인을 모르고, 단계 번호 대신 임계값 "값"을 돌려주며, 최고 단계인지도 알려주지 않는다.
 *
 * @param effectiveThresholds 증강 할인(4-8 전열강화)이 반영된 임계값. 각 항은 최소 1이다.
 * @param tier 0 이면 미발동, 1..`thresholds.size` 면 발동 단계. 수치표의 색인은 `tier - 1` 이다.
 */
data class ActiveTrait(
    val trait: TraitDef,
    val memberCount: Int,
    val effectiveThresholds: List<Int>,
    val tier: Int,
) {
    val traitId: String get() = trait.id
    val name: String get() = trait.name
    val isActive: Boolean get() = tier > 0

    /** 할인이 반영된 실제 발동 인원. 미발동이면 null. */
    val activeThreshold: Int? get() = effectiveThresholds.getOrNull(tier - 1)

    /** 다음 단계에 필요한 인원. 최고 단계면 null. 화면의 회색 "수호자 1/2" 표기가 이 값을 쓴다. */
    val nextThreshold: Int? get() = effectiveThresholds.getOrNull(tier)

    /**
     * 명세서 효과 문구. 미발동이면 null.
     *
     * **반드시 할인 전 원래 임계값을 키로 찾는다.** `effectsByThreshold` 의 키는 기본 임계값이고
     * [TraitDef] 의 init 가 그것을 강제하므로, 전열강화로 2/4 가 1/3 이 되어도
     * `trait.thresholds[tier - 1]` 로 찾아야 한다. 할인된 값으로 찾으면 키가 없어 null 이 된다.
     */
    val effectText: String?
        get() = if (!isActive) null else trait.effectsByThreshold[trait.thresholds[tier - 1]]

    companion object {
        /**
         * 할인이 반영된 임계값. 0명으로 발동하는 시너지가 생기지 않도록 1 미만으로 내려가지 않는다.
         *
         * 명세서 4-8 의 전열강화("수호자 시너지 임계값 요구 인원 -1")가 유일한 소비자이고,
         * 9단계 전까지 할인은 항상 0이다.
         */
        fun effectiveThresholds(trait: TraitDef, discount: Int): List<Int> =
            if (discount <= 0) trait.thresholds else trait.thresholds.map { (it - discount).coerceAtLeast(1) }

        /**
         * 인원수로 단계를 구한다.
         *
         * `lastOrNull { count >= it }` 이 아니라 `count { count >= it }` 인 이유는 두 가지다.
         * 결과가 임계값 값(2/4/6)이 아니라 수치표를 바로 색인할 수 있는 단계 번호이고,
         * 할인으로 임계값이 `[1, 1]` 처럼 붕괴해도 올바른 단계를 고른다.
         */
        fun of(trait: TraitDef, memberCount: Int, thresholdDiscount: Int = 0): ActiveTrait {
            val effective = effectiveThresholds(trait, thresholdDiscount)
            return ActiveTrait(
                trait = trait,
                memberCount = memberCount,
                effectiveThresholds = effective,
                tier = effective.count { memberCount >= it },
            )
        }
    }
}
