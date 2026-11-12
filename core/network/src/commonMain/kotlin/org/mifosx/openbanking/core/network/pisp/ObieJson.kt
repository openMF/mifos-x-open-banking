/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.pisp

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The serializer used for PISP request bodies.
 *
 * Mirrors the content-negotiation config of the sandbox client, with `explicitNulls = false` so the
 * all-nullable OBIE DTOs emit only the fields that were set — which is what keeps a consumer payment
 * free of the merchant `Risk` fields rather than sending them as explicit nulls.
 *
 * PISP bodies are encoded here rather than handed to content negotiation because the Worker computes
 * the detached JWS over the exact bytes it receives and refuses any body that is not compact JSON
 * (`400 body_not_compact`). `JsonObject.toString()` is compact; content negotiation might not be.
 */
internal val obieJson: Json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** Encodes [value] to the [JsonObject] whose compact form is sent (and signed by the Worker). */
internal fun <T> obieBody(serializer: SerializationStrategy<T>, value: T): JsonObject =
    obieJson.encodeToJsonElement(serializer, value) as JsonObject
