package com.leechanghyun.autobattler.data.local

import com.leechanghyun.autobattler.core.model.AugmentDef
import com.leechanghyun.autobattler.core.model.ItemDef
import com.leechanghyun.autobattler.core.model.MinionGroup
import com.leechanghyun.autobattler.core.model.MonsterDef
import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.PrimaryStat
import com.leechanghyun.autobattler.core.model.SkillDef
import com.leechanghyun.autobattler.core.model.TraitDef
import com.leechanghyun.autobattler.core.model.TraitKind
import com.leechanghyun.autobattler.core.model.UnitClass
import com.leechanghyun.autobattler.core.model.UnitDef
import com.leechanghyun.autobattler.core.persistence.ValueCodec

/**
 * 도메인 모델 ↔ Room 엔티티 변환.
 *
 * enum 은 `name` 문자열로, 리스트/맵은 [ValueCodec] 인코딩 문자열로 저장한다.
 */
object MasterDataMapper {

    fun toEntity(def: UnitDef) = UnitEntity(
        id = def.id,
        name = def.name,
        cost = def.cost,
        origin = def.origin.name,
        unitClass = def.unitClass.name,
        baseHp = def.baseHp,
        baseAttack = def.baseAttack,
        primaryStat = def.primaryStat.name,
        attackRange = def.attackRange,
        attackSpeed = def.attackSpeed,
        skillId = def.skillId,
    )

    fun toDomain(entity: UnitEntity) = UnitDef(
        id = entity.id,
        name = entity.name,
        cost = entity.cost,
        origin = Origin.valueOf(entity.origin),
        unitClass = UnitClass.valueOf(entity.unitClass),
        baseHp = entity.baseHp,
        baseAttack = entity.baseAttack,
        primaryStat = PrimaryStat.valueOf(entity.primaryStat),
        attackRange = entity.attackRange,
        attackSpeed = entity.attackSpeed,
        skillId = entity.skillId,
    )

    fun toEntity(def: SkillDef) = SkillEntity(
        id = def.id,
        name = def.name,
        description = def.description,
        manaCost = def.manaCost,
        startingMana = def.startingMana,
        basePower = def.basePower,
        areaRadius = def.areaRadius,
    )

    fun toDomain(entity: SkillEntity) = SkillDef(
        id = entity.id,
        name = entity.name,
        description = entity.description,
        manaCost = entity.manaCost,
        startingMana = entity.startingMana,
        basePower = entity.basePower,
        areaRadius = entity.areaRadius,
    )

    fun toEntity(def: TraitDef) = TraitEntity(
        id = def.id,
        name = def.name,
        kind = def.kind.name,
        thresholds = ValueCodec.encodeIntList(def.thresholds),
        effectsByThreshold = ValueCodec.encodeIntStringMap(def.effectsByThreshold),
    )

    fun toDomain(entity: TraitEntity) = TraitDef(
        id = entity.id,
        name = entity.name,
        kind = TraitKind.valueOf(entity.kind),
        thresholds = ValueCodec.decodeIntList(entity.thresholds),
        effectsByThreshold = ValueCodec.decodeIntStringMap(entity.effectsByThreshold),
    )

    fun toEntity(def: ItemDef) = ItemEntity(
        id = def.id,
        name = def.name,
        statModifiers = ValueCodec.encodeStatMap(def.statModifiers),
        isComponent = def.isComponent,
        recipe = ValueCodec.encodeStringList(def.recipe),
        description = def.description,
        effectId = def.effectId,
    )

    fun toDomain(entity: ItemEntity) = ItemDef(
        id = entity.id,
        name = entity.name,
        statModifiers = ValueCodec.decodeStatMap(entity.statModifiers),
        isComponent = entity.isComponent,
        recipe = ValueCodec.decodeStringList(entity.recipe),
        description = entity.description,
        effectId = entity.effectId,
    )

    fun toEntity(def: AugmentDef) = AugmentEntity(
        id = def.id,
        name = def.name,
        description = def.description,
        effectId = def.effectId,
    )

    fun toDomain(entity: AugmentEntity) = AugmentDef(
        id = entity.id,
        name = entity.name,
        description = entity.description,
        effectId = entity.effectId,
    )

    fun toEntity(def: MonsterDef) = MonsterEntity(
        id = def.id,
        name = def.name,
        stage = def.stage,
        count = def.count,
        hp = def.hp,
        attack = def.attack,
        minions = def.minions?.let { MinionEmbedded(it.count, it.hp, it.attack) },
        rewardComponents = def.rewardComponents,
        rewardCompletedItem = def.rewardCompletedItem,
        rewardGold = def.rewardGold,
    )

    fun toDomain(entity: MonsterEntity) = MonsterDef(
        id = entity.id,
        name = entity.name,
        stage = entity.stage,
        count = entity.count,
        hp = entity.hp,
        attack = entity.attack,
        minions = entity.minions?.let { MinionGroup(it.count, it.hp, it.attack) },
        rewardComponents = entity.rewardComponents,
        rewardCompletedItem = entity.rewardCompletedItem,
        rewardGold = entity.rewardGold,
    )
}
