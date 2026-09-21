package com.leechanghyun.autobattler.core.masterdata

import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.PrimaryStat
import com.leechanghyun.autobattler.core.model.UnitClass
import com.leechanghyun.autobattler.core.model.UnitDef

/**
 * 명세서 4-4 표의 원본 로스터 14종. 표를 그대로 옮긴 것이다.
 *
 * HP / 공격력 / 사거리 / 계열 / 직업 / 코스트는 명세서 확정값이므로 **여기 값은 수정하지 않는다.**
 * `attackSpeed` 만 명세서 표에 없어 임의 초기값으로 채웠고, 11단계에서 조정한다.
 * (사거리가 길수록 공격속도를 낮게, 근접 검사 계열을 높게 잡는 관행을 따랐다.)
 *
 * 시너지 임계값을 채우기 위해 추가한 유닛은 [EXPANSION_UNIT_DEFS] 에 따로 있고,
 * 실제로 쓰이는 전체 로스터는 [UNIT_DEFS] 다.
 */
internal val SPEC_UNIT_DEFS: List<UnitDef> = listOf(
    // --- 1코스트 ---
    UnitDef(
        id = "steel_guard",
        name = "강철수호병",
        cost = 1,
        origin = Origin.MECHA,
        unitClass = UnitClass.WARDEN,
        baseHp = 700,
        baseAttack = 50,
        primaryStat = PrimaryStat.AD,
        attackRange = 1,
        attackSpeed = 0.60f,
        skillId = "skill_taunt_shield",
    ),
    UnitDef(
        id = "flame_apprentice",
        name = "화염견습생",
        cost = 1,
        origin = Origin.STORM_TRIBE,
        unitClass = UnitClass.ARCANIST,
        baseHp = 500,
        baseAttack = 40,
        primaryStat = PrimaryStat.AP,
        attackRange = 3,
        attackSpeed = 0.55f,
        skillId = "skill_fireball",
    ),
    UnitDef(
        id = "shadow_pilferer",
        name = "그림자좀도둑",
        cost = 1,
        origin = Origin.ABYSSAL,
        unitClass = UnitClass.BLADE,
        baseHp = 550,
        baseAttack = 55,
        primaryStat = PrimaryStat.AD,
        attackRange = 1,
        attackSpeed = 0.75f,
        skillId = "skill_ambush",
    ),
    UnitDef(
        id = "golden_archer",
        name = "황금궁수",
        cost = 1,
        origin = Origin.GOLDEN_HOUSE,
        unitClass = UnitClass.MARKSMAN,
        baseHp = 500,
        baseAttack = 45,
        primaryStat = PrimaryStat.AD,
        attackRange = 4,
        attackSpeed = 0.70f,
        skillId = "skill_multishot",
    ),

    // --- 2코스트 ---
    UnitDef(
        id = "precision_sight",
        name = "정밀조준기",
        cost = 2,
        origin = Origin.MECHA,
        unitClass = UnitClass.MARKSMAN,
        baseHp = 600,
        baseAttack = 55,
        primaryStat = PrimaryStat.AD,
        attackRange = 4,
        attackSpeed = 0.70f,
        skillId = "skill_piercing_shot",
    ),
    UnitDef(
        id = "storm_mage",
        name = "폭풍마도사",
        cost = 2,
        origin = Origin.STORM_TRIBE,
        unitClass = UnitClass.ARCANIST,
        baseHp = 550,
        baseAttack = 60,
        primaryStat = PrimaryStat.AP,
        attackRange = 3,
        attackSpeed = 0.55f,
        skillId = "skill_chain_lightning",
    ),
    UnitDef(
        id = "dark_blade",
        name = "어둠칼날",
        cost = 2,
        origin = Origin.ABYSSAL,
        unitClass = UnitClass.BLADE,
        baseHp = 650,
        baseAttack = 60,
        primaryStat = PrimaryStat.AD,
        attackRange = 1,
        attackSpeed = 0.75f,
        skillId = "skill_execute",
    ),
    UnitDef(
        id = "gilded_priest",
        name = "금빛사제",
        cost = 2,
        origin = Origin.GOLDEN_HOUSE,
        unitClass = UnitClass.WARDEN,
        baseHp = 750,
        baseAttack = 40,
        primaryStat = PrimaryStat.AP,
        attackRange = 2,
        attackSpeed = 0.60f,
        skillId = "skill_heal_ally",
    ),

    // --- 3코스트 ---
    UnitDef(
        id = "steel_commander",
        name = "강철기사단장",
        cost = 3,
        origin = Origin.MECHA,
        unitClass = UnitClass.BLADE,
        baseHp = 800,
        baseAttack = 70,
        primaryStat = PrimaryStat.AD,
        attackRange = 1,
        attackSpeed = 0.70f,
        skillId = "skill_charge_slam",
    ),
    UnitDef(
        id = "abyss_devourer",
        name = "심연포식자",
        cost = 3,
        origin = Origin.ABYSSAL,
        unitClass = UnitClass.ARCANIST,
        baseHp = 700,
        baseAttack = 75,
        primaryStat = PrimaryStat.AP,
        attackRange = 2,
        attackSpeed = 0.60f,
        skillId = "skill_terror",
    ),
    UnitDef(
        id = "storm_bow_king",
        name = "폭풍궁왕",
        cost = 3,
        origin = Origin.STORM_TRIBE,
        unitClass = UnitClass.MARKSMAN,
        baseHp = 650,
        baseAttack = 65,
        primaryStat = PrimaryStat.AD,
        attackRange = 4,
        attackSpeed = 0.75f,
        skillId = "skill_storm_arrow",
    ),

    // --- 4코스트 ---
    UnitDef(
        id = "golden_giant",
        name = "황금거인",
        cost = 4,
        origin = Origin.GOLDEN_HOUSE,
        unitClass = UnitClass.WARDEN,
        baseHp = 1100,
        baseAttack = 60,
        primaryStat = PrimaryStat.AD,
        attackRange = 1,
        attackSpeed = 0.60f,
        skillId = "skill_empower_smash",
    ),
    UnitDef(
        id = "thunder_lord",
        name = "뇌전군주",
        cost = 4,
        origin = Origin.STORM_TRIBE,
        unitClass = UnitClass.BLADE,
        baseHp = 900,
        baseAttack = 90,
        primaryStat = PrimaryStat.AD,
        attackRange = 1,
        attackSpeed = 0.80f,
        skillId = "skill_thunder_slash",
    ),

    // --- 5코스트 ---
    UnitDef(
        id = "apostle_of_end",
        name = "종말의 사도",
        cost = 5,
        origin = Origin.ABYSSAL,
        unitClass = UnitClass.ARCANIST,
        baseHp = 850,
        baseAttack = 110,
        primaryStat = PrimaryStat.AP,
        attackRange = 3,
        attackSpeed = 0.60f,
        skillId = "skill_doomsday",
    ),
)

/** 명세서 원본 14종 + 확장 10종을 합친 실제 로스터 24종. */
internal val UNIT_DEFS: List<UnitDef> = SPEC_UNIT_DEFS + EXPANSION_UNIT_DEFS
