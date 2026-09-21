package com.leechanghyun.autobattler.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** 마스터 데이터 읽기/시딩용 DAO. 마스터 데이터는 앱이 수정하지 않으므로 update/delete 가 없다. */
@Dao
interface MasterDataDao {

    @Query("SELECT * FROM units ORDER BY cost, id")
    suspend fun getUnits(): List<UnitEntity>

    @Query("SELECT * FROM units WHERE cost = :cost ORDER BY id")
    suspend fun getUnitsByCost(cost: Int): List<UnitEntity>

    @Query("SELECT * FROM skills ORDER BY id")
    suspend fun getSkills(): List<SkillEntity>

    @Query("SELECT * FROM traits ORDER BY id")
    suspend fun getTraits(): List<TraitEntity>

    @Query("SELECT * FROM items ORDER BY id")
    suspend fun getItems(): List<ItemEntity>

    @Query("SELECT * FROM augments ORDER BY id")
    suspend fun getAugments(): List<AugmentEntity>

    @Query("SELECT * FROM monsters ORDER BY stage")
    suspend fun getMonsters(): List<MonsterEntity>

    @Query("SELECT COUNT(*) FROM units")
    suspend fun countUnits(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnits(units: List<UnitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkills(skills: List<SkillEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTraits(traits: List<TraitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAugments(augments: List<AugmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMonsters(monsters: List<MonsterEntity>)
}
