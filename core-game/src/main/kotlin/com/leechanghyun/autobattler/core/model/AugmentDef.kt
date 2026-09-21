package com.leechanghyun.autobattler.core.model

/**
 * 증강 마스터 데이터. 명세서 4-8.
 *
 * 명세서 5장 초안은 `applyEffect: (PlayerState) -> PlayerState` 람다를 들고 있었지만,
 * 람다는 Room 에 저장할 수 없고 equals 비교도 되지 않아 마스터 데이터로 쓰기 어렵다.
 * 그래서 여기서는 [effectId] 문자열만 저장하고, 실제 효과 적용은 로드맵 9단계에서
 * effectId 를 분기하는 별도 핸들러로 붙인다.
 */
data class AugmentDef(
    val id: String,
    val name: String,
    val description: String,
    val effectId: String,
)
