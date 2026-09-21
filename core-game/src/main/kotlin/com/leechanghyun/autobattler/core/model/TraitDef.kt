package com.leechanghyun.autobattler.core.model

/**
 * 시너지(특성) 마스터 데이터. 명세서 4-4.
 *
 * @param thresholds 발동 인원 임계값. 오름차순이며 보드 위 해당 태그 유닛 수가 이 값 이상일 때 단계가 올라간다.
 * @param effectsByThreshold 임계값별 효과 설명. 실제 수치 적용은 로드맵 5단계 시너지 엔진에서 붙인다.
 */
data class TraitDef(
    val id: String,
    val name: String,
    val kind: TraitKind,
    val thresholds: List<Int>,
    val effectsByThreshold: Map<Int, String>,
) {
    init {
        require(thresholds.isNotEmpty()) { "시너지 $id 는 임계값이 최소 1개 필요하다" }
        require(thresholds == thresholds.sorted()) { "시너지 $id 의 임계값은 오름차순이어야 한다" }
        require(thresholds.toSet().size == thresholds.size) { "시너지 $id 의 임계값에 중복이 있다" }
        require(effectsByThreshold.keys == thresholds.toSet()) {
            "시너지 $id 의 효과 설명이 임계값 목록과 일치하지 않는다"
        }
    }

    /** 보드 위 해당 태그 유닛이 [count] 명일 때 활성화된 임계값. 미달이면 null. */
    fun activeThreshold(count: Int): Int? = thresholds.lastOrNull { count >= it }
}
