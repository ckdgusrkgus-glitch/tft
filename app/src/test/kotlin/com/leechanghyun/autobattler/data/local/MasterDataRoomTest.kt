package com.leechanghyun.autobattler.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.leechanghyun.autobattler.core.masterdata.MasterData
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Room 저장/조회가 마스터 데이터를 손실 없이 왕복하는지 검증한다.
 *
 * Robolectric 으로 JVM 위에서 실제 SQLite 를 띄우므로 에뮬레이터나 기기가 없어도 돌아간다.
 * 실행: `./gradlew :app:testDebugUnitTest`
 */
@RunWith(RobolectricTestRunner::class)
class MasterDataRoomTest {

    private lateinit var database: AutoBattlerDatabase
    private lateinit var dao: MasterDataDao
    private lateinit var seeder: MasterDataSeeder

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AutoBattlerDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.masterDataDao()
        seeder = MasterDataSeeder(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `시딩하면 명세서 4장 표가 모두 들어간다`() = runTest {
        assertTrue("빈 DB 는 시딩된다", seeder.seedIfEmpty())

        assertEquals(14, dao.getUnits().size)
        assertEquals(14, dao.getSkills().size)
        assertEquals(8, dao.getTraits().size)
        assertEquals(9, dao.getItems().size)
        assertEquals(10, dao.getAugments().size)
        assertEquals(4, dao.getMonsters().size)
    }

    @Test
    fun `이미 시딩된 DB 는 다시 시딩하지 않는다`() = runTest {
        seeder.seedIfEmpty()
        assertFalse("두 번째 호출은 건너뛴다", seeder.seedIfEmpty())
        assertEquals(14, dao.countUnits())
    }

    @Test
    fun `유닛을 저장했다 읽으면 원본과 같다`() = runTest {
        seeder.seed()
        val restored = dao.getUnits().map(MasterDataMapper::toDomain).associateBy { it.id }
        MasterData.units.forEach { original ->
            assertEquals("유닛 ${original.name}", original, restored[original.id])
        }
    }

    @Test
    fun `시너지와 아이템의 리스트-맵 필드도 그대로 복원된다`() = runTest {
        seeder.seed()

        val traits = dao.getTraits().map(MasterDataMapper::toDomain).associateBy { it.id }
        MasterData.traits.forEach { original ->
            assertEquals("시너지 ${original.name}", original, traits[original.id])
        }

        val items = dao.getItems().map(MasterDataMapper::toDomain).associateBy { it.id }
        MasterData.itemComponents.forEach { original ->
            assertEquals("아이템 ${original.name}", original, items[original.id])
        }
    }

    @Test
    fun `미니언이 있는 몬스터와 없는 몬스터가 모두 복원된다`() = runTest {
        seeder.seed()
        val monsters = dao.getMonsters().map(MasterDataMapper::toDomain).associateBy { it.id }

        MasterData.monsters.forEach { original ->
            assertEquals("몬스터 ${original.name}", original, monsters[original.id])
        }
        assertEquals(2, monsters.getValue("creep_rock_golem").minions?.count)
        assertEquals(null, monsters.getValue("creep_wild_dogs").minions)
    }

    @Test
    fun `코스트로 유닛을 조회할 수 있다`() = runTest {
        seeder.seed()
        assertEquals(4, dao.getUnitsByCost(1).size)
        assertEquals(1, dao.getUnitsByCost(5).size)
        assertEquals("종말의 사도", dao.getUnitsByCost(5).single().name)
    }
}
