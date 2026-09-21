package com.leechanghyun.autobattler.data.local

import com.leechanghyun.autobattler.core.masterdata.MasterData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 코드에 들어 있는 [MasterData] 를 Room DB 에 채워 넣는다.
 *
 * Room 의 `onCreate` 콜백을 쓰지 않고 별도 시더로 분리한 이유는,
 * 콜백 안에서는 DAO 를 쓰기가 번거롭고 테스트도 어렵기 때문이다.
 * 대신 앱 시작 시 [seedIfEmpty] 를 한 번 호출한다.
 */
@Singleton
class MasterDataSeeder @Inject constructor(
    private val dao: MasterDataDao,
) {
    /** DB 가 비어 있을 때만 시딩한다. 이미 데이터가 있으면 아무것도 하지 않는다. */
    suspend fun seedIfEmpty(): Boolean {
        if (dao.countUnits() > 0) return false
        seed()
        return true
    }

    /** 마스터 데이터를 덮어쓴다. 표 수정 후 강제로 다시 넣을 때 쓴다. */
    suspend fun seed() {
        dao.insertSkills(MasterData.skills.map(MasterDataMapper::toEntity))
        dao.insertUnits(MasterData.units.map(MasterDataMapper::toEntity))
        dao.insertTraits(MasterData.traits.map(MasterDataMapper::toEntity))
        dao.insertItems(MasterData.itemComponents.map(MasterDataMapper::toEntity))
        dao.insertAugments(MasterData.augments.map(MasterDataMapper::toEntity))
        dao.insertMonsters(MasterData.monsters.map(MasterDataMapper::toEntity))
    }
}
