package com.leechanghyun.autobattler.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 앱 로컬 DB. 현재는 마스터 데이터만 담는다.
 *
 * 전적 저장 테이블은 로드맵 11단계에서 추가하면서 버전을 올리고 마이그레이션을 붙인다.
 */
@Database(
    entities = [
        UnitEntity::class,
        SkillEntity::class,
        TraitEntity::class,
        ItemEntity::class,
        AugmentEntity::class,
        MonsterEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AutoBattlerDatabase : RoomDatabase() {
    abstract fun masterDataDao(): MasterDataDao

    companion object {
        const val NAME = "autobattler.db"
    }
}
