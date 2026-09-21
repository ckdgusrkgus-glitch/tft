package com.leechanghyun.autobattler.core.synergy

import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.UnitClass
import com.leechanghyun.autobattler.core.model.UnitDef

/**
 * 소환 요청. 5단계는 이 데이터를 **내보내기만 하고 실제로 소환하지 않는다.**
 *
 * 기계공학자 6인의 "거대 골렘 소환"(명세서 4-4)은 10단계로 미룬다. 미루는 이유는 전투 코드가
 * 아니라 그 바깥의 파급이다.
 * - 골렘 UnitDef 를 로스터에 넣으면 공용 유닛 풀의 재고가 되어 상점에 뜬다. 마스터 데이터
 *   테스트의 "유닛 24종"도 깨지고, [UnitDef] 는 코스트 1..5 를 강제해 빠져나갈 구멍도 없다.
 * - 골렘이 BoardUnit 이 되면 판매 경로가 골드를 주고 공용 풀에 유령 카드를 밀어 넣는다.
 * - 전장의 어느 빈 칸에 세울지, id 정렬 순서에서 실제 유닛보다 앞에 설지 뒤에 설지,
 *   살아남은 골렘이 패배 시 체력 손실 계산의 생존자 수를 올려도 되는지까지 전부 결정이 필요하다.
 *
 * 결정적으로 10단계 PvE 크립 몬스터가 **똑같은 기능**을 요구한다. 명세서 4-9 가 몬스터를
 * "스킬 없이 기본 공격만 수행하는 단순 개체"라고 적는다. 두 번 만들 이유가 없다.
 *
 * 그래서 5단계는 명세만 내보내고, 테스트가 "소환 명세는 나오지만 전투 유닛 수는 늘지 않는다"를
 * 단언해 이 보류가 조용히 잊히지 않게 한다.
 */
data class SummonSpec(
    val unitDefId: String,
    val count: Int,
    val sourceTraitId: String,
)

/**
 * 한 진영이 받는 전투 보정 전체.
 *
 * 시너지 엔진은 서로 다른 두 질문에 동시에 답해야 한다.
 * 1. 보드 전체에서 어떤 시너지가 몇 단계인가 → [SynergyState.activeTraits]
 * 2. **이 유닛**이 그 태그의 구성원인가 → [byOrigin] / [byClass]
 *
 * 2번이 따로 필요한 이유는 9단계 증강 사수의감각("사수 시너지 유닛 치명타 확률 +20%")이
 * 사수 시너지의 발동 여부와 무관하게 사수 유닛에게 걸리기 때문이다.
 * 팀 단위 배율 하나만 돌려주는 API 였다면 9단계에서 전부 다시 써야 한다.
 */
data class TeamBuffs(
    val teamWide: UnitBuffs = UnitBuffs.NONE,
    val byOrigin: Map<Origin, UnitBuffs> = emptyMap(),
    val byClass: Map<UnitClass, UnitBuffs> = emptyMap(),
    val summons: List<SummonSpec> = emptyList(),
) {
    /** 이 유닛 1기가 최종적으로 받는 보정. */
    fun forUnit(def: UnitDef): UnitBuffs =
        teamWide +
            (byOrigin[def.origin] ?: UnitBuffs.NONE) +
            (byClass[def.unitClass] ?: UnitBuffs.NONE)

    companion object {
        val NONE = TeamBuffs()
    }
}
