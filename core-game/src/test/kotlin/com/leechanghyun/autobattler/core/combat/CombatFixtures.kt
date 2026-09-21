package com.leechanghyun.autobattler.core.combat

import com.leechanghyun.autobattler.core.board.OffsetCoord
import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.PrimaryStat
import com.leechanghyun.autobattler.core.model.SkillDef
import com.leechanghyun.autobattler.core.model.UnitClass
import com.leechanghyun.autobattler.core.model.UnitDef
import com.leechanghyun.autobattler.core.synergy.UnitBuffs

/**
 * 전투 테스트용 유닛 생성기.
 *
 * 마스터 데이터의 수치는 11단계에서 전부 바뀔 임의 초기값이므로, 전투 규칙을 검증하는 테스트가
 * 그 수치에 기대면 밸런스를 만질 때마다 깨진다. 그래서 테스트는 여기서 직접 만든 유닛만 쓴다.
 */
internal object CombatFixtures {

    /** 마나가 절대 차지 않는 값. 스킬을 배제하고 기본 공격만 보고 싶을 때 쓴다. */
    const val UNREACHABLE_MANA = 9999

    fun skill(
        id: String = "test_skill",
        manaCost: Int = 30,
        startingMana: Int = 0,
        basePower: Int = 100,
        areaRadius: Int = 0,
    ) = SkillDef(
        id = id,
        name = "테스트 스킬",
        description = "테스트 전용",
        manaCost = manaCost,
        startingMana = startingMana,
        basePower = basePower,
        areaRadius = areaRadius,
    )

    /** 스킬을 쓰지 않는 유닛용 스킬. 마나가 차지 않고 위력도 0이다. */
    fun noSkill() = skill(manaCost = UNREACHABLE_MANA, startingMana = 0, basePower = 0)

    fun def(
        id: String = "test_unit",
        baseHp: Int = 1000,
        baseAttack: Int = 100,
        attackRange: Int = 1,
        attackSpeed: Float = 1.0f,
    ) = UnitDef(
        id = id,
        name = id,
        cost = 1,
        origin = Origin.MECHA,
        unitClass = UnitClass.WARDEN,
        baseHp = baseHp,
        baseAttack = baseAttack,
        primaryStat = PrimaryStat.AD,
        attackRange = attackRange,
        attackSpeed = attackSpeed,
        skillId = "test_skill",
    )

    /**
     * 전장 오프셋(행, 열)에 유닛 1기를 세운다.
     *
     * 전장 줄 번호는 위가 0(적 후열), 아래가 7(플레이어 후열)이다.
     */
    fun unit(
        id: String,
        team: CombatTeam,
        row: Int,
        col: Int,
        def: UnitDef = def(),
        skill: SkillDef = noSkill(),
        starLevel: Int = 1,
        buffs: UnitBuffs = UnitBuffs.NONE,
    ) = CombatUnit(
        id = id,
        team = team,
        def = def,
        skill = skill,
        starLevel = starLevel,
        position = CombatField.grid.fromOffset(OffsetCoord(row, col)),
        buffs = buffs,
    )
}
