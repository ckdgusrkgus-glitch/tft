package com.leechanghyun.autobattler.core.masterdata

import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.TraitDef
import com.leechanghyun.autobattler.core.model.TraitKind
import com.leechanghyun.autobattler.core.model.UnitClass

/**
 * 시너지 8종(계열 4 + 직업 4). 명세서 4-4 표 그대로.
 *
 * 임계값과 효과 문구는 명세서 확정값이다. 수치화된 실제 버프 적용은 로드맵 5단계에서 붙인다.
 * 시너지 id 는 [Origin] / [UnitClass] enum 이름을 소문자로 쓴 값이라 태그 매칭이 문자열 비교 없이 가능하다.
 */
internal val TRAIT_DEFS: List<TraitDef> = listOf(
    TraitDef(
        id = Origin.MECHA.traitId,
        name = Origin.MECHA.displayName,
        kind = TraitKind.ORIGIN,
        thresholds = listOf(2, 4, 6),
        effectsByThreshold = mapOf(
            2 to "아군 전체 방어력/마법저항력 증가",
            4 to "아군 전체 방어력/마법저항력 대폭 증가",
            6 to "아군 전체 방어력/마법저항력 대폭 증가 + 거대 골렘 소환",
        ),
    ),
    TraitDef(
        id = Origin.ABYSSAL.traitId,
        name = Origin.ABYSSAL.displayName,
        kind = TraitKind.ORIGIN,
        thresholds = listOf(2, 4, 6),
        effectsByThreshold = mapOf(
            2 to "아군 전체가 주기적으로 최대 체력 비례 쉴드 획득",
            4 to "쉴드량 증가",
            6 to "쉴드량 대폭 증가",
        ),
    ),
    TraitDef(
        id = Origin.GOLDEN_HOUSE.traitId,
        name = Origin.GOLDEN_HOUSE.displayName,
        kind = TraitKind.ORIGIN,
        thresholds = listOf(2, 4, 6),
        effectsByThreshold = mapOf(
            2 to "라운드 종료 시 추가 골드 획득",
            4 to "추가 골드 획득량 증가",
            6 to "추가 골드 획득량 대폭 증가",
        ),
    ),
    TraitDef(
        id = Origin.STORM_TRIBE.traitId,
        name = Origin.STORM_TRIBE.displayName,
        kind = TraitKind.ORIGIN,
        thresholds = listOf(2, 4, 6),
        effectsByThreshold = mapOf(
            2 to "공격 시 일정 확률로 연쇄 번개 피해",
            4 to "연쇄 번개 발동 확률/피해 증가",
            6 to "연쇄 번개 발동 확률/피해 대폭 증가",
        ),
    ),
    TraitDef(
        id = UnitClass.BLADE.traitId,
        name = UnitClass.BLADE.displayName,
        kind = TraitKind.CLASS,
        thresholds = listOf(2, 4),
        effectsByThreshold = mapOf(
            2 to "검사 유닛 공격력 증가",
            4 to "검사 유닛 공격력 대폭 증가",
        ),
    ),
    TraitDef(
        id = UnitClass.ARCANIST.traitId,
        name = UnitClass.ARCANIST.displayName,
        kind = TraitKind.CLASS,
        thresholds = listOf(2, 4),
        effectsByThreshold = mapOf(
            2 to "마법사 유닛 주문력 증가",
            4 to "마법사 유닛 주문력 대폭 증가",
        ),
    ),
    TraitDef(
        id = UnitClass.MARKSMAN.traitId,
        name = UnitClass.MARKSMAN.displayName,
        kind = TraitKind.CLASS,
        thresholds = listOf(2, 4),
        effectsByThreshold = mapOf(
            2 to "사수 유닛 공격속도 증가",
            4 to "사수 유닛 공격속도 대폭 증가",
        ),
    ),
    TraitDef(
        id = UnitClass.WARDEN.traitId,
        name = UnitClass.WARDEN.displayName,
        kind = TraitKind.CLASS,
        thresholds = listOf(2, 4),
        effectsByThreshold = mapOf(
            2 to "수호자 유닛 최대 체력 증가",
            4 to "수호자 유닛 최대 체력 대폭 증가",
        ),
    ),
)

/** 계열 enum 을 시너지 id 로 변환한다. */
val Origin.traitId: String get() = name.lowercase()

/** 직업 enum 을 시너지 id 로 변환한다. */
val UnitClass.traitId: String get() = name.lowercase()
