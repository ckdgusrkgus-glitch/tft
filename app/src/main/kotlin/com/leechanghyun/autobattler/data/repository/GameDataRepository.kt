package com.leechanghyun.autobattler.data.repository

import com.leechanghyun.autobattler.core.model.AugmentDef
import com.leechanghyun.autobattler.core.model.ItemDef
import com.leechanghyun.autobattler.core.model.MonsterDef
import com.leechanghyun.autobattler.core.model.SkillDef
import com.leechanghyun.autobattler.core.model.TraitDef
import com.leechanghyun.autobattler.core.model.UnitDef
import com.leechanghyun.autobattler.data.local.MasterDataDao
import com.leechanghyun.autobattler.data.local.MasterDataMapper
import com.leechanghyun.autobattler.data.local.MasterDataSeeder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 마스터 데이터 접근 창구. ViewModel 은 Room 을 직접 보지 않고 이 저장소만 쓴다.
 *
 * 마스터 데이터는 게임 중에 바뀌지 않으므로 첫 조회 결과를 메모리에 캐시한다.
 */
@Singleton
class GameDataRepository @Inject constructor(
    private val dao: MasterDataDao,
    private val seeder: MasterDataSeeder,
) {
    private var cachedUnits: List<UnitDef>? = null

    /** 앱 시작 시 한 번 호출한다. DB 가 비어 있으면 마스터 데이터를 넣는다. */
    suspend fun ensureSeeded() {
        seeder.seedIfEmpty()
    }

    suspend fun units(): List<UnitDef> {
        cachedUnits?.let { return it }
        ensureSeeded()
        return dao.getUnits().map(MasterDataMapper::toDomain).also { cachedUnits = it }
    }

    suspend fun skills(): List<SkillDef> = dao.getSkills().map(MasterDataMapper::toDomain)

    suspend fun traits(): List<TraitDef> = dao.getTraits().map(MasterDataMapper::toDomain)

    suspend fun itemComponents(): List<ItemDef> = dao.getItems().map(MasterDataMapper::toDomain)

    suspend fun augments(): List<AugmentDef> = dao.getAugments().map(MasterDataMapper::toDomain)

    suspend fun monsters(): List<MonsterDef> = dao.getMonsters().map(MasterDataMapper::toDomain)
}
