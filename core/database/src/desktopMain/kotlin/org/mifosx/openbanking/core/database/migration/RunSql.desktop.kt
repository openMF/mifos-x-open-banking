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
import androidx.sqlite.execSQL

/**
 * Resolves androidx.sqlite's non-web `execSQL`, which does not suspend. Declaring the actual
 * `suspend` anyway costs nothing and is what lets one declaration serve the web targets too.
 */
internal actual suspend fun SQLiteConnection.runSql(sql: String) = execSQL(sql)
