package com.leechanghyun.autobattler.core.masterdata

import com.leechanghyun.autobattler.core.model.AugmentDef
import com.leechanghyun.autobattler.core.model.StageRound

/**
 * 증강 10종. 명세서 4-8 표 그대로.
 *
 * [AugmentDef.effectId] 는 9단계에서 효과 핸들러를 분기할 때 쓰는 키다.
 * 발동 라운드(2-1, 3-2, 4-2)는 [AUGMENT_ROUNDS] 에 있다.
 */
internal val AUGMENT_DEFS: List<AugmentDef> = listOf(
    AugmentDef("aug_golden_touch", "황금손길", "매 라운드 종료 시 +2골드", "GOLD_PER_ROUND"),
    AugmentDef("aug_attribute_polish", "속성연마", "보유 아이템 컴포넌트 1개 무료 지급", "GRANT_COMPONENT"),
    AugmentDef("aug_rapid_growth", "급속성장", "즉시 경험치 +8", "GRANT_EXP"),
    AugmentDef("aug_front_line", "전열강화", "수호자 시너지 임계값 요구 인원 -1", "TRAIT_THRESHOLD_DISCOUNT"),
    AugmentDef("aug_storm_blessing", "폭풍의가호", "폭풍의 부족 유닛 공격속도 +15%", "ORIGIN_ATTACK_SPEED"),
    AugmentDef("aug_abyss_pact", "심연의계약", "체력 -100 대신 매 라운드 골드 +1", "HP_FOR_GOLD"),
    AugmentDef("aug_clearance", "재고정리", "상점 리롤 비용 1턴 동안 무료", "FREE_REROLL"),
    AugmentDef("aug_iron_will", "강철의의지", "아군 전체 방어력 +10", "TEAM_ARMOR"),
    AugmentDef("aug_marksman_sense", "사수의감각", "사수 시너지 유닛 치명타 확률 +20%", "CLASS_CRIT_CHANCE"),
    AugmentDef("aug_smith_blessing", "대장장이의축복", "완성 아이템 1개 무료 지급", "GRANT_COMPLETED_ITEM"),
)

/**
 * 증강을 고르는 라운드. 명세서 4-8: 2-1, 3-2, 4-2.
 *
 * Pair 의 첫 값이 스테이지, 둘째 값이 스테이지 내 라운드 번호다.
 */
val AUGMENT_ROUNDS: List<Pair<Int, Int>> = listOf(2 to 1, 3 to 2, 4 to 2)

/** 증강 선택 시 제시되는 후보 개수. 명세서 4-8: 3개 중 1개. */
const val AUGMENT_CHOICES_PER_ROUND = 3

/**
 * [AUGMENT_ROUNDS] 를 라운드 번호로 옮긴 것. 평면 번호로는 5, 10, 14 라운드다.
 *
 * 스테이지 표기 그대로 비교하면 두 값을 짝지어 맞춰야 해서 한쪽만 보는 실수가 난다.
 */
val AUGMENT_STAGE_ROUNDS: List<StageRound> =
    AUGMENT_ROUNDS.map { (stage, roundInStage) -> StageRound.of(stage, roundInStage) }

/** 이 라운드가 증강 선택 라운드인지. 명세서 4-8: 2-1, 3-2, 4-2. */
val StageRound.isAugmentRound: Boolean get() = this in AUGMENT_STAGE_ROUNDS
