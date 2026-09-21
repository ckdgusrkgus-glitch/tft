package com.leechanghyun.autobattler.core.masterdata

import com.leechanghyun.autobattler.core.model.SkillDef

/**
 * 유닛 스킬 14종.
 *
 * 명세서 4-6 은 "스킬 수치는 1단계에서 임의 초기값으로 채우고 11단계 밸런스 튜닝에서 조정한다"고
 * 명시했다. 따라서 아래 manaCost / startingMana / basePower / areaRadius 는 **전부 임의 초기값**이며,
 * 명세서에서 가져온 확정 수치가 아니다. 스킬 이름과 컨셉만 명세서 4-4 로스터 표의 "스킬 컨셉" 열에서 왔다.
 */
internal val SKILL_DEFS: List<SkillDef> = listOf(
    SkillDef("skill_taunt_shield", "강철 방벽", "주변 적을 도발하고 자신에게 쉴드를 얻는다", 60, 15, 250, 1),
    SkillDef("skill_fireball", "화염구", "가장 먼 적 주변에 광역 마법 피해를 준다", 60, 10, 200, 1),
    SkillDef("skill_ambush", "기습", "은신 후 가장 체력이 낮은 적을 기습한다", 50, 10, 220, 0),
    SkillDef("skill_multishot", "다단히트 사격", "현재 대상에게 3회 연속 사격한다", 50, 10, 180, 0),
    SkillDef("skill_piercing_shot", "관통사격", "직선상의 적 전체를 관통 공격한다", 70, 20, 240, 0),
    SkillDef("skill_chain_lightning", "연쇄 번개", "적 3명에게 튀는 번개를 날린다", 70, 20, 230, 0),
    SkillDef("skill_execute", "처형", "체력이 낮은 적에게 추가 피해를 준다", 60, 15, 260, 0),
    SkillDef("skill_heal_ally", "성역", "체력이 가장 낮은 아군을 회복시킨다", 70, 25, 280, 0),
    SkillDef("skill_charge_slam", "돌진 강타", "가장 먼 적에게 돌진해 광역 피해를 준다", 80, 20, 320, 1),
    SkillDef("skill_terror", "공포", "적 2명을 잠시 행동 불가로 만들고 피해를 준다", 80, 20, 300, 1),
    SkillDef("skill_storm_arrow", "폭풍 화살", "지정 지역에 광역 화살비를 내린다", 80, 20, 310, 2),
    SkillDef("skill_empower_smash", "거인의 강타", "자신을 강화한 뒤 전방을 내려친다", 90, 25, 400, 1),
    SkillDef("skill_thunder_slash", "뇌전 검격", "연쇄 번개를 두른 검격을 휘두른다", 90, 30, 430, 1),
    SkillDef("skill_doomsday", "종말", "전장의 모든 적에게 고정 피해를 준다", 120, 30, 500, 99),
)
