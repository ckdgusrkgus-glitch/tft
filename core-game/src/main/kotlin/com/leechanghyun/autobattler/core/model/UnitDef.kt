package com.leechanghyun.autobattler.core.model

/**
 * 유닛 마스터 데이터 1건. 명세서 4-4 로스터 표 + 5장 데이터 모델 초안.
 *
 * 이 클래스는 "설계도"이고, 실제 전장에 올라간 개체는 [BoardUnit] 이다.
 *
 * @param attackSpeed 초당 공격 횟수. 명세서 표에 없어 임의로 정한 초기값이며 11단계에서 조정한다.
 */
data class UnitDef(
    val id: String,
    val name: String,
    val cost: Int,
    val origin: Origin,
    val unitClass: UnitClass,
    val baseHp: Int,
    val baseAttack: Int,
    val primaryStat: PrimaryStat,
    val attackRange: Int,
    val attackSpeed: Float,
    val skillId: String,
) {
    init {
        require(cost in MIN_COST..MAX_COST) { "유닛 $id 의 코스트 $cost 는 $MIN_COST..$MAX_COST 범위를 벗어난다" }
        require(baseHp > 0) { "유닛 $id 의 체력은 0보다 커야 한다" }
        require(attackRange >= 1) { "유닛 $id 의 사거리는 1 이상이어야 한다" }
        require(attackSpeed > 0f) { "유닛 $id 의 공격속도는 0보다 커야 한다" }
    }

    companion object {
        const val MIN_COST = 1
        const val MAX_COST = 5
    }
}
