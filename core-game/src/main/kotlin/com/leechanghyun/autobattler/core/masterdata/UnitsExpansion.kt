package com.leechanghyun.autobattler.core.masterdata

import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.PrimaryStat
import com.leechanghyun.autobattler.core.model.UnitClass
import com.leechanghyun.autobattler.core.model.UnitDef

/**
 * 시너지 임계값을 채우기 위해 추가한 유닛 10종.
 *
 * 명세서 4-4 의 원본 로스터 14종으로는 계열 임계값 6을 어느 계열도 달성할 수 없고,
 * 기계공학자/황금가문은 4도 달성할 수 없었다. 직업도 수호자/사수가 3종뿐이라 임계값 4가 막혀 있었다.
 * 그래서 로스터를 24종으로 늘려 **계열 6종 / 직업 6종**을 모두 맞췄다.
 *
 * 계열 × 직업 분포 (각 행과 열의 합이 6):
 * ```
 *              검사  마법사  사수  수호자
 * 기계공학자     2      1     2      1
 * 심연의아이들   2      2     1      1
 * 황금가문       1      1     2      2
 * 폭풍의부족     1      2     1      2
 * ```
 *
 * 이 10종의 스탯은 전부 **임의 초기값**이다. 명세서에 없는 유닛이므로 확정 수치가 존재하지 않는다.
 * 원본 14종의 코스트별 스탯 폭에 맞춰 잡았고, 11단계 밸런스 튜닝에서 조정한다.
 */
internal val EXPANSION_UNIT_DEFS: List<UnitDef> = listOf(
    // --- 1코스트 ---
    UnitDef(
        id = "clockwork_swordsman",
        name = "태엽검병",
        cost = 1,
        origin = Origin.MECHA,
        unitClass = UnitClass.BLADE,
        baseHp = 600,
        baseAttack = 52,
        primaryStat = PrimaryStat.AD,
        attackRange = 1,
        attackSpeed = 0.72f,
        skillId = "skill_winding_strike",
    ),
    UnitDef(
        id = "wind_keeper",
        name = "바람지기",
        cost = 1,
        origin = Origin.STORM_TRIBE,
        unitClass = UnitClass.WARDEN,
        baseHp = 720,
        baseAttack = 45,
        primaryStat = PrimaryStat.AD,
        attackRange = 1,
        attackSpeed = 0.58f,
        skillId = "skill_gale_barrier",
    ),

    // --- 2코스트 ---
    UnitDef(
        id = "abyss_shooter",
        name = "심연사수",
        cost = 2,
        origin = Origin.ABYSSAL,
        unitClass = UnitClass.MARKSMAN,
        baseHp = 560,
        baseAttack = 56,
        primaryStat = PrimaryStat.AD,
        attackRange = 4,
        attackSpeed = 0.72f,
        skillId = "skill_void_volley",
    ),
    UnitDef(
        id = "gilded_blade",
        name = "금빛검사",
        cost = 2,
        origin = Origin.GOLDEN_HOUSE,
        unitClass = UnitClass.BLADE,
        baseHp = 660,
        baseAttack = 58,
        primaryStat = PrimaryStat.AD,
        attackRange = 1,
        attackSpeed = 0.76f,
        skillId = "skill_coin_flurry",
    ),

    // --- 3코스트 ---
    UnitDef(
        id = "dark_bulwark",
        name = "어둠방벽",
        cost = 3,
        origin = Origin.ABYSSAL,
        unitClass = UnitClass.WARDEN,
        baseHp = 900,
        baseAttack = 55,
        primaryStat = PrimaryStat.AD,
        attackRange = 1,
        attackSpeed = 0.60f,
        skillId = "skill_shadow_wall",
    ),
    UnitDef(
        id = "golden_alchemist",
        name = "황금연금술사",
        cost = 3,
        origin = Origin.GOLDEN_HOUSE,
        unitClass = UnitClass.ARCANIST,
        baseHp = 680,
        baseAttack = 72,
        primaryStat = PrimaryStat.AP,
        attackRange = 3,
        attackSpeed = 0.58f,
        skillId = "skill_transmute",
    ),

    // --- 4코스트 ---
    UnitDef(
        id = "calculus_savant",
        name = "연산술사",
        cost = 4,
        origin = Origin.MECHA,
        unitClass = UnitClass.ARCANIST,
        baseHp = 800,
        baseAttack = 95,
        primaryStat = PrimaryStat.AP,
        attackRange = 3,
        attackSpeed = 0.60f,
        skillId = "skill_overclock",
    ),
    UnitDef(
        id = "thunder_rampart",
        name = "천둥성벽",
        cost = 4,
        origin = Origin.STORM_TRIBE,
        unitClass = UnitClass.WARDEN,
        baseHp = 1150,
        baseAttack = 65,
        primaryStat = PrimaryStat.AD,
        attackRange = 1,
        attackSpeed = 0.58f,
        skillId = "skill_storm_bastion",
    ),

    // --- 5코스트 ---
    UnitDef(
        id = "siege_tank",
        name = "강철포격전차",
        cost = 5,
        origin = Origin.MECHA,
        unitClass = UnitClass.MARKSMAN,
        baseHp = 950,
        baseAttack = 105,
        primaryStat = PrimaryStat.AD,
        attackRange = 4,
        attackSpeed = 0.70f,
        skillId = "skill_siege_barrage",
    ),
    UnitDef(
        id = "golden_sniper",
        name = "황금저격수",
        cost = 5,
        origin = Origin.GOLDEN_HOUSE,
        unitClass = UnitClass.MARKSMAN,
        baseHp = 800,
        baseAttack = 115,
        primaryStat = PrimaryStat.AD,
        attackRange = 4,
        attackSpeed = 0.75f,
        skillId = "skill_bounty_shot",
    ),
)
