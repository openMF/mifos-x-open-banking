/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
@file:Suppress("MatchingDeclarationName")

package org.mifosx.openbanking.feature.vrpconsents.consentList

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.mifosx.openbanking.core.common.formatIsoDate
import org.mifosx.openbanking.core.data.vrp.VrpConsentRepository
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.feature.vrpconsents.formatLimitAmount
import template.core.base.ui.viewmodel.BaseViewModel

/** Screen state for the standing-payment list. */
data class VrpConsentListState(
    val uiState: VrpConsentListUiState = VrpConsentListUiState.Loading,
)

/**
 * The four rendered states.
 *
 * There is no `NoNetwork` state: the list is read from storage, so there is nothing for one to mean.
 */
sealed interface VrpConsentListUiState {

    data object Loading : VrpConsentListUiState

    data class Content(val consents: List<ConsentRowUi>) : VrpConsentListUiState

    /** No standing payments yet. Carries the create call-to-action, not an error tone. */
    data object Empty : VrpConsentListUiState

    data class Error(val kind: VrpConsentListErrorKind) : VrpConsentListUiState
}

/**
 * One display-ready row.
 *
 * Money and dates arrive finished. [period] and [status] stay typed, so the enum-to-copy mapping
 * lives beside the composable that renders it, matching the beneficiaries row.
 *
 * @property consentId The row key, and the argument the detail route takes.
 * @property payeeName Who the money goes to, e.g. `Sarah Chen`.
 * @property limitAmountLabel The headline ceiling, e.g. `£500`.
 * @property period The window that ceiling covers.
 * @property status The consent's status at [VrpConsent.syncedAt].
 * @property validUntil When it stops, e.g. `18 Mar 2027`. Null when it runs indefinitely.
 * @property isRevoked Whether the app has ended this authority.
 */
data class ConsentRowUi(
    val consentId: String,
    val payeeName: String,
    val limitAmountLabel: String,
    val period: PeriodType?,
    val status: ConsentStatus,
    val validUntil: String?,
    val isRevoked: Boolean,
)

/** The one failure this screen distinguishes. Reading local storage is all it does. */
enum class VrpConsentListErrorKind {
    StorageUnavailable,
}

/**
 * Actions the view model owns.
 *
 * Opening a consent and creating one are navigation, carried by the screen's lambdas.
 */
sealed interface VrpConsentListAction {

    data object RetryLoad : VrpConsentListAction

    /** Results the view model raises for itself. Never applied where they arrive. */
    sealed interface Internal : VrpConsentListAction {

        data class ReceiveConsents(val consents: List<VrpConsent>) : Internal

        data class ReceiveFailure(val error: Throwable) : Internal
    }
}

/**
 * Drives the standing-payment list.
 *
 * Reads only local storage: no endpoint lists a customer's standing payments, so this screen renders
 * from the database, or it does not render.
 */
class VrpConsentListViewModel(
    private val repository: VrpConsentRepository,
) : BaseViewModel<VrpConsentListState, Nothing, VrpConsentListAction>(
    initialState = VrpConsentListState(),
) {

    init {
        observeConsents()
    }

    override fun handleAction(action: VrpConsentListAction) {
        when (action) {
            VrpConsentListAction.RetryLoad -> {
                updateState { copy(uiState = VrpConsentListUiState.Loading) }
                observeConsents()
            }

            is VrpConsentListAction.Internal.ReceiveConsents -> applyConsents(action.consents)
            is VrpConsentListAction.Internal.ReceiveFailure -> applyFailure()
        }
    }

    private fun observeConsents() {
        repository.observeActive()
            .onEach { consents -> sendAction(VrpConsentListAction.Internal.ReceiveConsents(consents)) }
            .catch { error -> sendAction(VrpConsentListAction.Internal.ReceiveFailure(error)) }
            .launchIn(viewModelScope)
    }

    private fun applyConsents(consents: List<VrpConsent>) {
        val uiState = if (consents.isEmpty()) {
            VrpConsentListUiState.Empty
        } else {
            VrpConsentListUiState.Content(consents.map { it.toRowUi() })
        }
        updateState { copy(uiState = uiState) }
    }

    private fun applyFailure() {
        updateState {
            copy(uiState = VrpConsentListUiState.Error(VrpConsentListErrorKind.StorageUnavailable))
        }
    }
}

/**
 * The row this consent renders as.
 *
 * The headline ceiling is the largest periodic limit, because that is the figure that describes the
 * authority as a whole. A consent carrying no periodic limit cannot exist — the bank refuses one —
 * so the per-payment ceiling stands in only for a row read back from a corrupted store.
 */
private fun VrpConsent.toRowUi(): ConsentRowUi {
    val headline = controlParameters.periodicLimits.maxByOrNull { it.amount.minorUnits }

    return ConsentRowUi(
        consentId = consentId,
        payeeName = payee.name,
        limitAmountLabel = formatLimitAmount(
            headline?.amount ?: controlParameters.maximumIndividualAmount,
        ),
        period = headline?.periodType,
        status = status,
        validUntil = validity?.validTo?.let { formatIsoDate(it.toString()) },
        isRevoked = revokedAt != null,
    )
}
