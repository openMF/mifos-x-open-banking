/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.openSourceLicence

import kotlinx.coroutines.CoroutineScope
import template.core.base.store.screen.ScreenDataStream

/** Reads the app's open-source licence text. */
interface OpenSourceLicenceRepo {

    /** The licence text as a screen stream, cached between launches and refreshable. */
    fun getLicence(scope: CoroutineScope): ScreenDataStream<String>
}
