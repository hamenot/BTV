package dev.frost819.newbv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.frost819.newbv.data.db.dao.SearchHistoryDao
import dev.frost819.newbv.data.db.dao.UserDao
import dev.frost819.newbv.data.db.entity.SearchHistoryEntity
import dev.frost819.newbv.data.db.entity.UserEntity

/**
 * 应用主数据库。
 *
 * 持久化搜索历史与多账户信息，由 Hilt 注入，禁止手动构建单例。
 * 数据库版本 4（相对原版 BV 的版本 3 新增了 newBV 的 schema 起始）。
 *
 * 实体列表：
 * - [SearchHistoryEntity]：搜索历史
 * - [UserEntity]：账户信息
 *
 * Schema 导出位置：`data/schemas/`（由 KSP `room.schemaLocation` 配置）。
 *
 * **改表结构时的固定动作**（缺一步就会让存量用户崩溃或丢登录态）：
 * 1. 实体改动 + 把 `version` 加 1；
 * 2. 在 [AppDatabaseMigrations.ALL] 里追加 `Migration(旧版本, 新版本)`；
 * 3. 跑 `./gradlew :data:kspDebugKotlin` 导出新的 schema json 并提交；
 * 4. 加一条迁移测试（`MigrationTestHelper`）。
 *
 * 不要用 `fallbackToDestructiveMigration()` 绕过——`user` 表里是登录凭证与用户锁密码。
 *
 * **关于索引（已评估，暂不添加）**：
 * `search_history` 的 `WHERE keyword = ?` / `ORDER BY search_date DESC`、
 * `user` 的 `WHERE uid = ?` 目前都是全表扫描。但这两张表的量级极小
 * （账户几条、搜索历史几十条，且写入路径已按 uid 去重），扫描成本不可测量；
 * 而改索引必须升到 version 5，所有存量用户都要跑一次迁移 —— 迁移写错就是
 * 全员登录异常。收益与风险不成比例，故保持 version 4。
 * 若将来搜索历史改为无上限存储、或账户数量上百，再按上面的固定动作补索引。
 */
@Database(
    entities = [SearchHistoryEntity::class, UserEntity::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    /** 搜索历史 DAO。 */
    abstract fun searchHistoryDao(): SearchHistoryDao

    /** 账户 DAO。 */
    abstract fun userDao(): UserDao

    companion object {
        /** 数据库文件名。 */
        const val DATABASE_NAME = "AppDatabase.db"
    }
}
