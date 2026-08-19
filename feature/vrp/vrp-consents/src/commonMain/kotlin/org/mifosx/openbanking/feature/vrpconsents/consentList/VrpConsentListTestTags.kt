/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpconsents.consentList

/**
 * Node tags for the standing-payment list.
 *
 * Append-only: renaming or removing one a suite references makes that suite silently stop asserting
 * what it claims to.
 */
internal object VrpConsentListTestTags {
    const val LOADING_SKELETON = "vrpConsentList:loadingSkeleton"
    const val CONTENT = "vrpConsentList:content"
    const val EMPTY_STATE = "vrpConsentList:emptyState"
    const val EMPTY_TITLE = "vrpConsentList:emptyTitle"
    const val EMPTY_BODY = "vrpConsentList:emptyBody"
    const val CREATE_BUTTON = "vrpConsentList:createButton"
    const val ERROR_STATE = "vrpConsentList:errorState"
    const val ERROR_BODY = "vrpConsentList:errorBody"
    const val RETRY_BUTTON = "vrpConsentList:retryButton"

    fun row(consentId: String): String = "vrpConsentList:row:$consentId"

    fun rowLimit(consentId: String): String = "vrpConsentList:rowLimit:$consentId"

    fun rowStatus(consentId: String): String = "vrpConsentList:rowStatus:$consentId"
}
