/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.database.migration

import androidx.sqlite.SQLiteConnection

/**
 * Runs one statement that returns nothing — the whole of what a migration needs.
 *
 * Migrations are written once, in `commonMain`, and `androidx.sqlite` offers no way to execute SQL
 * there. Its `execSQL` is declared twice, in two source sets a consumer's common code cannot see:
 *
 * ```
 * nonWebMain/androidx/sqlite/SQLite.nonWeb.kt   public fun SQLiteConnection.execSQL(sql: String)
 * webMain/androidx/sqlite/SQLite.web.kt         public suspend fun SQLiteConnection.execSQL(sql: String)
 * ```
 *
 * One suspends and one does not, so there is no common declaration to reconcile them, and the common
 * `SQLiteConnection` is an `expect interface` carrying only `inTransaction` and `close` — not even
 * `prepare`. Room is built the same way, with its own `nonWebMain` and `webMain`, which is the shape
 * saying that SQL belongs in platform code.
 *
 * This is the smallest thing that puts it back in common. Every actual is the same one-line call; the
 * only difference is which `execSQL` each platform resolves, and declaring this one `suspend` covers
 * both — [androidx.room3.migration.Migration.migrate] is itself `suspend`, so nothing is forced to
 * block, and migrations stay in one file.
 *
 * The constraint is compile-time, not a claim that migrations run in a browser. This module declares
 * `js(IR)` and `wasmJs` through the KMP convention, so `commonMain` is type-checked against them
 * whether or not web is a target anyone runs — and it is not: `:cmp-web` is in the build but no CI
 * workflow compiles it. Read this as what lets `core/database` build, which stays true either way.
 */
internal expect suspend fun SQLiteConnection.runSql(sql: String)
