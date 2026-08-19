/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpconsents.consentDetail

/**
 * Node tags for one standing payment.
 *
 * Append-only: renaming or removing one a suite references makes that suite silently stop asserting
 * what it claims to.
 */
internal object VrpConsentDetailTestTags {
    const val LOADING_SKELETON = "vrpConsentDetail:loadingSkeleton"
    const val CONTENT = "vrpConsentDetail:content"
    const val HEADER = "vrpConsentDetail:header"
    const val STATUS = "vrpConsentDetail:status"
    const val VALIDITY = "vrpConsentDetail:validity"
    const val SYNCED_AT = "vrpConsentDetail:syncedAt"
    const val PER_PAYMENT_LIMIT = "vrpConsentDetail:perPaymentLimit"
    const val PERIODIC_LIMIT = "vrpConsentDetail:periodicLimit"
    const val CONSUMED = "vrpConsentDetail:consumed"
    const val REMAINING = "vrpConsentDetail:remaining"
    const val PAYER = "vrpConsentDetail:payer"
    const val PAY_BUTTON = "vrpConsentDetail:payButton"
    const val HISTORY = "vrpConsentDetail:history"
    const val HISTORY_EMPTY = "vrpConsentDetail:historyEmpty"
    const val REVOKE_BUTTON = "vrpConsentDetail:revokeButton"
    const val REVOKE_DIALOG = "vrpConsentDetail:revokeDialog"
    const val REVOKE_CONFIRM = "vrpConsentDetail:revokeConfirm"
    const val REVOKE_CANCEL = "vrpConsentDetail:revokeCancel"
    const val REVOKE_IRREVERSIBLE_NOTICE = "vrpConsentDetail:revokeIrreversibleNotice"
    const val REVOKE_ERROR = "vrpConsentDetail:revokeError"
    const val UNUSABLE_STATE = "vrpConsentDetail:unusableState"
    const val UNUSABLE_BODY = "vrpConsentDetail:unusableBody"
    const val ENDED_STATE = "vrpConsentDetail:endedState"
    const val NOT_FOUND_STATE = "vrpConsentDetail:notFoundState"
    const val REFRESH_BUTTON = "vrpConsentDetail:refreshButton"

    fun paymentRow(localId: String): String = "vrpConsentDetail:paymentRow:$localId"

    fun paymentStatus(localId: String): String = "vrpConsentDetail:paymentStatus:$localId"

    fun paymentAmount(localId: String): String = "vrpConsentDetail:paymentAmount:$localId"
}
