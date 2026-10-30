package dev.frost819.newbv.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.frost819.newbv.data.db.AppDatabase
import dev.frost819.newbv.data.db.AppDatabaseMigrations
import dev.frost819.newbv.data.db.dao.SearchHistoryDao
import dev.frost819.newbv.data.db.dao.UserDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * 数据层 Hilt 模块。
 *
 * 提供 Room 数据库、DAO、DataStore 的单例绑定。
 * 所有绑定注册到 [SingletonComponent]，全应用生命周期共享。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * 提供 [AppDatabase] 单例。
     *
     * 使用应用上下文构建，数据库名由 [AppDatabase.DATABASE_NAME] 定义。
     * 不使用手动单例（区别于原版 BV），由 Hilt 管理生命周期。
     *
     * 显式注册 [AppDatabaseMigrations.ALL]：Room 只认注册过的迁移，漏注册会在
     * 版本变更时直接抛异常。**不启用 destructive migration**——`user` 表存着
     * 登录凭证，销毁重建等于把存量用户全部踢下线。
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase {
        val builder = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
        // 逐条注册而非 `addMigrations(*ALL)`：展开运算符会先整体复制一次数组，
        // detekt 的 SpreadOperator 规则会拦（迁移只有个位数，但没必要开这个口子）。
        AppDatabaseMigrations.ALL.forEach { builder.addMigrations(it) }
        return builder.build()
    }

    /** 提供搜索历史 DAO。 */
    @Provides
    fun provideSearchHistoryDao(database: AppDatabase): SearchHistoryDao = database.searchHistoryDao()

    /** 提供账户 DAO。 */
    @Provides
    fun provideUserDao(database: AppDatabase): UserDao = database.userDao()

    /**
     * 提供 DataStore 单例。
     *
     * 使用 [PreferenceDataStoreFactory] 创建，持久化文件名为 `Settings.preferences_pb`。
     * 协程作用域使用 IO 调度器 + SupervisorJob。
     */
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { java.io.File(context.filesDir, "datastore/Settings.preferences_pb") },
        )
}
