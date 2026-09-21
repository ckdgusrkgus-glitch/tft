package com.leechanghyun.autobattler.core.model

/**
 * 유닛 스킬 마스터 데이터. 명세서 4-6.
 *
 * 명세서가 수치를 고정하지 않았으므로 [com.leechanghyun.autobattler.core.masterdata.MasterData] 의
 * 값은 전부 임의 초기값이고, 로드맵 11단계 밸런스 튜닝에서 조정한다.
 *
 * @param manaCost 스킬 발동에 필요한 마나 최대치
 * @param startingMana 라운드 시작 시 보유 마나
 * @param basePower 1성 기준 스킬 계수 적용 전 기본 피해/회복량
 * @param areaRadius 광역 스킬의 반경(헥스 칸 수). 0 이면 단일 대상
 */
data class SkillDef(
    val id: String,
    val name: String,
    val description: String,
    val manaCost: Int,
    val startingMana: Int,
    val basePower: Int,
    val areaRadius: Int,
) {
    init {
        require(manaCost > 0) { "스킬 $id 의 마나 비용은 0보다 커야 한다" }
        require(startingMana in 0..manaCost) { "스킬 $id 의 시작 마나는 0..$manaCost 범위여야 한다" }
        require(areaRadius >= 0) { "스킬 $id 의 반경은 0 이상이어야 한다" }
    }
}
