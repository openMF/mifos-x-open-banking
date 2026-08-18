/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.model.callback

/**
 * The state of a consent resource, shared by every consent-based API.
 *
 * The wire carries either a four-letter code or the long name depending on the endpoint;
 * [fromString] accepts both.
 */
enum class ConsentStatus {
    /** Staged, not yet approved by the customer. */
    AwaitingAuthorisation,

    /** Approved. The consent may be used. */
    Authorised,

    /** The customer declined at the approval step. */
    Rejected,

    /** Withdrawn by the customer after approval. */
    Revoked,

    /** Cancelled by the bank. */
    Cancelled,

    /** Past its validity date. */
    Expired,

    /** A one-off consent that has been used. */

    Consumed,
    ;

    companion object {
        fun fromString(raw: String): ConsentStatus = when (raw) {
            "AWAU", "AwaitingAuthorisation", "Awaiting authorisation" -> AwaitingAuthorisation
            "AUTH", "Authorised" -> Authorised
            "RJCT", "Rejected" -> Rejected
            "Revoked" -> Revoked
            "CANC", "Cancelled" -> Cancelled
            "EXPD", "Expired" -> Expired
            "Consumed" -> Consumed
            else -> Rejected
        }
    }
}
