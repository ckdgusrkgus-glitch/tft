package com.leechanghyun.autobattler.core.masterdata

import com.leechanghyun.autobattler.core.model.AugmentDef
import com.leechanghyun.autobattler.core.model.ItemDef
import com.leechanghyun.autobattler.core.model.MonsterDef
import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.SkillDef
import com.leechanghyun.autobattler.core.model.TraitDef
import com.leechanghyun.autobattler.core.model.UnitClass
import com.leechanghyun.autobattler.core.model.UnitDef

/**
 * 게임 마스터 데이터의 단일 진입점. 명세서 4장 표 전체가 여기에 모인다.
 *
 * Room DB 를 처음 만들 때 이 값들을 그대로 시드(seed)로 넣고, 이후에는 DB 에서 읽는다.
 * 순수 Kotlin 이라 안드로이드 없이도 단위테스트로 검증할 수 있다.
 */
object MasterData {

    val units: List<UnitDef> = UNIT_DEFS
    val skills: List<SkillDef> = SKILL_DEFS
    val traits: List<TraitDef> = TRAIT_DEFS
    val itemComponents: List<ItemDef> = ITEM_COMPONENT_DEFS
    val augments: List<AugmentDef> = AUGMENT_DEFS
    val monsters: List<MonsterDef> = MONSTER_DEFS

    private val unitsById: Map<String, UnitDef> = units.associateBy { it.id }
    private val skillsById: Map<String, SkillDef> = skills.associateBy { it.id }
    private val traitsById: Map<String, TraitDef> = traits.associateBy { it.id }

    /** 코스트별로 묶은 유닛 목록. 상점 롤에서 등급이 정해진 뒤 이 목록에서 하나를 뽑는다. */
    val unitsByCost: Map<Int, List<UnitDef>> = units.groupBy { it.cost }

    fun unit(id: String): UnitDef = requireNotNull(unitsById[id]) { "알 수 없는 유닛 id: $id" }

    fun skill(id: String): SkillDef = requireNotNull(skillsById[id]) { "알 수 없는 스킬 id: $id" }

    fun trait(id: String): TraitDef = requireNotNull(traitsById[id]) { "알 수 없는 시너지 id: $id" }

    fun trait(origin: Origin): TraitDef = trait(origin.traitId)

    fun trait(unitClass: UnitClass): TraitDef = trait(unitClass.traitId)

    /** 스테이지 [stage] 의 크립 라운드 몬스터. 크립 라운드가 없는 스테이지면 null. */
    fun monsterForStage(stage: Int): MonsterDef? = monsters.firstOrNull { it.stage == stage }

    init {
        // 마스터 데이터가 서로 어긋난 채로 앱이 뜨는 일을 막는다.
        require(unitsById.size == units.size) { "유닛 id 가 중복됐다" }
        require(skillsById.size == skills.size) { "스킬 id 가 중복됐다" }
        require(traitsById.size == traits.size) { "시너지 id 가 중복됐다" }
        units.forEach { unit ->
            require(skillsById.containsKey(unit.skillId)) {
                "유닛 ${unit.id} 가 존재하지 않는 스킬 ${unit.skillId} 를 가리킨다"
            }
        }
        require(unitsByCost.keys.all { POOL_SIZE_BY_COST.containsKey(it) }) {
            "풀 크기표에 없는 코스트의 유닛이 있다"
        }
    }
}
