package dev.frost819.newbv.data.db

import androidx.room.migration.Migration

/**
 * 数据库迁移集合。
 *
 * Room 在 `AppDatabase` 的 `version` 与已安装库的版本不一致时，必须能找到一条
 * 从旧版本逐级升到新版本的 [Migration] 链，否则打开数据库会抛
 * `IllegalStateException: A migration from N to M was necessary but not found`，
 * 表现为**老用户升级后一开 App 就崩**。
 *
 * 因此这里的规则是：
 *
 * 1. 每次把 [AppDatabase] 的 `version` +1，必须同时往 [ALL] 里加一条
 *    `Migration(旧版本, 新版本)`，并跑一次 `:data:kspDebugKotlin` 导出新的
 *    `data/schemas/dev.frost819.newbv.data.db.AppDatabase/<新版本>.json`。
 * 2. 不允许用 `fallbackToDestructiveMigration()` 蒙混过关——`user` 表存着登录凭证
 *    与用户锁密码，销毁重建等于把用户全部踢下线。
 *
 * [BASELINE_VERSION] 是首个随正式版发布的 schema 版本（`schemas/4.json`）。
 * 比它更老的库来自原版 BV（`dev.aaa1115910.biliapi` 时期），不在兼容范围内；
 * 若将来需要兼容，请在这里补对应的迁移并下调该常量。
 *
 * 当前没有待迁移的版本（[BASELINE_VERSION] 即最新版本），[ALL] 为空是预期状态。
 */
object AppDatabaseMigrations {
    /** 首个随正式版发布的 schema 版本，见 `data/schemas/…/4.json`。 */
    const val BASELINE_VERSION: Int = 4

    /**
     * 全部迁移，按起始版本从低到高排列。
     *
     * 新增迁移时**追加**到数组末尾，不要修改或删除已有条目——它们要服务于
     * 仍停留在旧版本上的存量用户。
     */
    val ALL: Array<Migration> = arrayOf()
}
