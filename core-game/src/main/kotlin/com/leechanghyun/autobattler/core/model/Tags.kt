package com.leechanghyun.autobattler.core.model

/**
 * 계열(Origin) 4종. 명세서 4-4.
 *
 * [displayName] 은 UI 노출용 한글명, enum 이름은 DB/코드에서 쓰는 안정적인 키다.
 */
enum class Origin(val displayName: String) {
    MECHA("기계공학자"),
    ABYSSAL("심연의 아이들"),
    GOLDEN_HOUSE("황금가문"),
    STORM_TRIBE("폭풍의 부족"),
}

/** 직업(Class) 4종. 명세서 4-4. */
enum class UnitClass(val displayName: String) {
    BLADE("검사"),
    ARCANIST("마법사"),
    MARKSMAN("사수"),
    WARDEN("수호자"),
}

/**
 * 유닛의 주력 스탯. 명세서 4-4 로스터 표의 "AD/AP" 열을 코드로 옮긴 것이다.
 *
 * AD 유닛은 기본 공격 위주, AP 유닛은 스킬 위주로 피해를 낸다.
 */
enum class PrimaryStat { AD, AP }

/** 시너지 종류. 계열과 직업을 하나의 시너지 목록으로 함께 다루기 위한 구분값이다. */
enum class TraitKind { ORIGIN, CLASS }
