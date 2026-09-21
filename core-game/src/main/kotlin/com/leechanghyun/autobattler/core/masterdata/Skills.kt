package com.leechanghyun.autobattler.core.masterdata

import com.leechanghyun.autobattler.core.model.SkillDef

/**
 * 유닛 스킬 14종.
 *
 * 명세서 4-6 은 "스킬 수치는 1단계에서 임의 초기값으로 채우고 11단계 밸런스 튜닝에서 조정한다"고
 * 명시했다. 따라서 아래 manaCost / startingMana / basePower / areaRadius 는 **전부 임의 초기값**이며,
 * 명세서에서 가져온 확정 수치가 아니다. 스킬 이름과 컨셉만 명세서 4-4 로스터 표의 "스킬 컨셉" 열에서 왔다.
 */
/** 명세서 원본 로스터 14종의 스킬. */
private val SPEC_SKILL_DEFS: List<SkillDef> = listOf(
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

/** 확장 로스터 10종([EXPANSION_UNIT_DEFS])의 스킬. 수치는 전부 임의 초기값이다. */
private val EXPANSION_SKILL_DEFS: List<SkillDef> = listOf(
    SkillDef("skill_winding_strike", "태엽 연격", "태엽을 감아 빠르게 연속 베기를 날린다", 50, 10, 210, 0),
    SkillDef("skill_gale_barrier", "돌풍 장막", "주변 아군에게 바람 방벽을 씌워 피해를 흡수한다", 60, 15, 240, 1),
    SkillDef("skill_void_volley", "공허 사격", "허공에서 화살을 불러내 한 줄의 적을 꿰뚫는다", 60, 15, 235, 0),
    SkillDef("skill_coin_flurry", "금화 난무", "금화를 흩뿌리며 주변 적을 난타한다", 55, 15, 225, 1),
    SkillDef("skill_shadow_wall", "그림자 장벽", "자신 앞에 그림자 벽을 세워 적의 진입을 막는다", 80, 20, 300, 1),
    SkillDef("skill_transmute", "황금 변성", "적 하나를 잠시 황금으로 굳히고 피해를 준다", 80, 20, 295, 0),
    SkillDef("skill_overclock", "과부하 연산", "아군 전체의 공격속도를 끌어올린다", 90, 25, 380, 2),
    SkillDef("skill_storm_bastion", "뇌운 요새", "자신을 중심으로 번개 장막을 펼쳐 주변 적을 감전시킨다", 90, 25, 360, 2),
    SkillDef("skill_siege_barrage", "포격 지원", "가장 넓은 적 무리에 포탄을 연달아 떨어뜨린다", 110, 30, 480, 2),
    SkillDef("skill_bounty_shot", "현상금 사격", "체력이 가장 높은 적을 조준해 관통 저격한다", 110, 30, 490, 0),
)

/** 원본 14종 + 확장 10종의 스킬 전체. 같은 파일 안에서는 위에서 아래로 초기화되므로 맨 뒤에 둔다. */
internal val SKILL_DEFS: List<SkillDef> = SPEC_SKILL_DEFS + EXPANSION_SKILL_DEFS
