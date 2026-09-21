package com.leechanghyun.autobattler.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room 에 저장하는 마스터 데이터 테이블들. 명세서 4장 표에 1:1 대응한다.
 *
 * 도메인 모델(`core-game` 모듈)과 엔티티를 따로 두는 이유는, 도메인 쪽을 안드로이드 의존성 없는
 * 순수 Kotlin 으로 유지해 단위테스트로 검증하기 위해서다. 변환은 [MasterDataMapper] 가 담당한다.
 */
@Entity(tableName = "units")
data class UnitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val cost: Int,
    val origin: String,
    val unitClass: String,
    val baseHp: Int,
    val baseAttack: Int,
    val primaryStat: String,
    val attackRange: Int,
    val attackSpeed: Float,
    val skillId: String,
)

@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val manaCost: Int,
    val startingMana: Int,
    val basePower: Int,
    val areaRadius: Int,
)

@Entity(tableName = "traits")
data class TraitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    /** [com.leechanghyun.autobattler.core.persistence.ValueCodec] 로 인코딩된 정수 목록. */
    val thresholds: String,
    /** 인코딩된 Map<임계값, 효과 설명>. */
    val effectsByThreshold: String,
)

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** 인코딩된 Map<StatType, Float>. */
    val statModifiers: String,
    val isComponent: Boolean,
    /** 인코딩된 컴포넌트 id 목록. 컴포넌트면 빈 문자열. */
    val recipe: String,
)

@Entity(tableName = "augments")
data class AugmentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val effectId: String,
)

@Entity(tableName = "monsters")
data class MonsterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val stage: Int,
    val count: Int,
    val hp: Int,
    val attack: Int,
    @Embedded(prefix = "minion_") val minions: MinionEmbedded?,
    val rewardComponents: Int,
    val rewardCompletedItem: Boolean,
    val rewardGold: Int,
)

/** 몬스터에 딸린 보조 개체. null 이면 미니언이 없다. */
data class MinionEmbedded(
    val count: Int,
    val hp: Int,
    val attack: Int,
)
