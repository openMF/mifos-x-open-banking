/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.callback

/**
 * The Worker refused the redirect on security grounds: the id_token failed verification
 * (signature, iss, aud, exp, nonce, c_hash, s_hash), the state did not match its flow, or the flow
 * was unknown or expired. Shown as the security-error screen, never as a customer decline.
 */
class CallbackSecurityRejected(val code: String) : Exception("Sign-in response failed security checks ($code).")

/** The bank reports the consent as Rejected; the Worker discarded the tokens (`409 consent_rejected`). */
class ConsentRejectedAtBank : Exception("Your bank reports this consent as rejected. Start again to grant access.")

/** Worker error codes on `/v1/auth/complete` that mean "do not trust this redirect". */
internal val SECURITY_REJECTION_CODES = setOf("invalid_id_token", "state_mismatch", "unknown_flow")

/** Extracts `error` from the Worker's `{"error": "..."}` envelope without a JSON dependency. */
internal fun workerErrorCode(body: String?): String? =
    body?.let { Regex(""""error"\s*:\s*"([a-z_]+)"""").find(it)?.groupValues?.get(1) }
