/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.di

import com.russhwolf.settings.Settings
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.mifosx.openbanking.core.network.HSBCUKSandboxConfig
import org.mifosx.openbanking.core.network.TOKEN_ENDPOINT
import org.mifosx.openbanking.core.network.api.Aisp
import org.mifosx.openbanking.core.network.api.OAuth
import org.mifosx.openbanking.core.network.api.Pisp
import org.mifosx.openbanking.core.network.api.Vrp
import org.mifosx.openbanking.core.network.config.HsbcConfig
import org.mifosx.openbanking.core.network.getBaseUrl
import org.mifosx.openbanking.core.network.hsbcSandboxHttpClient

/**
 * Wires the HSBC sandbox networking graph. The mTLS [org.mifosx.openbanking.core.network.mtls.MtlsIdentity]
 * and the `named("hsbcSigningKey")` signing key come from [networkPlatformModule] (loaded synchronously
 * from platform resources), so the whole graph is plain synchronous singles — no suspend, no
 * `runBlocking`, no `Deferred`.
 */
val NetworkModule = module {
    includes(networkPlatformModule)

    single {
        hsbcSandboxHttpClient(
            settings = get<Settings>(named("secure")),
            identity = get(),
            signingKeyPem = get(named("hsbcSigningKey")),
        )
    }

    single {
        OAuth(
            httpClient = get(),
            tokenUrl = getBaseUrl(HSBCUKSandboxConfig.UKPersonal) + TOKEN_ENDPOINT,
            clientId = HsbcConfig.CLIENT_ID,
            kid = HsbcConfig.KID,
            signingKeyPem = get(named("hsbcSigningKey")),
        )
    }

    single { Aisp(get()) }

    single {
        Pisp(
            httpClient = get(),
            kid = HsbcConfig.KID,
            signingKeyPem = get(named("hsbcSigningKey")),
            financialId = get(named("hsbcFinancialId")),
            signingIssuer = get(named("hsbcSigningIssuer")),
        )
    }

    single {
        Vrp(
            httpClient = get(),
            kid = HsbcConfig.KID,
            signingKeyPem = get(named("hsbcSigningKey")),
            financialId = get(named("hsbcFinancialId")),
            signingIssuer = get(named("hsbcSigningIssuer")),
        )
    }

    single(named("hsbcClientId")) { HsbcConfig.CLIENT_ID }
    single(named("hsbcKid")) { HsbcConfig.KID }
    single(named("hsbcBankHost")) { HsbcConfig.BANK_HOST }
    single(named("hsbcAuthorizeHost")) { HsbcConfig.AUTHORIZE_HOST }
    single(named("hsbcRedirectUri")) { HsbcConfig.REDIRECT_URI }

    /**
     * The ASPSP's Open Banking organisation id, sent as `x-fapi-financial-id` on PISP calls.
     *
     * Blank because HSBC publishes no value for it: the sandbox's own Postman collection references
     * an undefined variable for this header, and the AIS calls this app already makes are accepted
     * without it. [org.mifosx.openbanking.core.network.pisp.fapiHeaders] omits the header entirely
     * while this is blank, which is what the sandbox accepts; supply a value here if a live call
     * ever rejects its absence.
     */
    single(named("hsbcFinancialId")) { "" }

    /**
     * The `http://openbanking.org.uk/iss` claim in the detached JWS header: the organisation and
     * software-statement pair the signing certificate was issued to. HSBC verifies it against the
     * certificate, so it is not free text.
     */
    single(named("hsbcSigningIssuer")) { HsbcConfig.SIGNING_ISSUER }
}
