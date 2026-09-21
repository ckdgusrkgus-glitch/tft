package com.leechanghyun.autobattler.di

import android.content.Context
import androidx.room.Room
import com.leechanghyun.autobattler.data.local.AutoBattlerDatabase
import com.leechanghyun.autobattler.data.local.MasterDataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Room 관련 의존성 제공. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AutoBattlerDatabase =
        Room.databaseBuilder(context, AutoBattlerDatabase::class.java, AutoBattlerDatabase.NAME)
            // 아직 출시 전이라 스키마가 바뀌면 DB 를 지우고 다시 만든다.
            // 전적 저장이 들어가는 11단계부터는 정식 마이그레이션으로 바꿔야 한다.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideMasterDataDao(database: AutoBattlerDatabase): MasterDataDao =
        database.masterDataDao()
}
