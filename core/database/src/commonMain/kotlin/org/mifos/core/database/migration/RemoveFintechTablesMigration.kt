/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.mifos.core.database.migration

import androidx.room3.DeleteTable
import androidx.room3.migration.AutoMigrationSpec

/**
 * Auto-migration spec for v4 → v5 that drops the four template-demo tables
 * (`exchange_rates`, `coin_markets`, `coin_detail`, `rate_history`) which
 * backed the Frankfurter (currency-rates) and CoinGecko (crypto) feature
 * modules — both removed as template residue (2026-05-22).
 *
 * Without this spec, Room rejects the v4 → v5 AutoMigration because tables
 * present in v4 are missing from v5 with no explicit deletion declared.
 *
 * Empty-class — Room only reads the annotation metadata.
 */
@DeleteTable.Entries(
    DeleteTable(tableName = "exchange_rates"),
    DeleteTable(tableName = "coin_markets"),
    DeleteTable(tableName = "coin_detail"),
    DeleteTable(tableName = "rate_history"),
)
class RemoveFintechTablesMigration : AutoMigrationSpec
