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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.mifosx.openbanking.core.ui.components.MifosFilledPillButton
import org.mifosx.openbanking.core.ui.scaffold.KptScaffold
import org.mifosx.openbanking.feature.vrpconsents.consentStatusLabel
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.Res
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_list_accessibility
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_list_create
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_list_empty_body
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_list_empty_title
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_list_error_storage
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_list_error_title
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_list_limit_format
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_list_no_end_date
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_list_retry
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_list_title
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_list_valid_until
import org.mifosx.openbanking.feature.vrpconsents.periodLabel
import template.core.base.designsystem.component.KptTopAppBar
import template.core.base.designsystem.theme.KptTheme

@Composable
internal fun VrpConsentListScreen(
    onBack: () -> Unit,
    onOpenConsent: (String) -> Unit,
    onNavigateToSetup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VrpConsentListViewModel = koinViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val onAction = remember(viewModel) {
        { action: VrpConsentListAction -> viewModel.trySendAction(action) }
    }

    VrpConsentListScreenContent(
        state = state,
        onAction = onAction,
        onBack = onBack,
        onOpenConsent = onOpenConsent,
        onNavigateToSetup = onNavigateToSetup,
        modifier = modifier,
    )
}

@Composable
internal fun VrpConsentListScreenContent(
    state: VrpConsentListState,
    onAction: (VrpConsentListAction) -> Unit,
    onBack: () -> Unit,
    onOpenConsent: (String) -> Unit,
    onNavigateToSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KptScaffold(
        modifier = modifier,
        topBar = {
            KptTopAppBar(
                title = stringResource(Res.string.feature_vrp_consents_list_title),
                onNavigationIconClick = onBack,
            )
        },
        bottomBar = {
            if (state.uiState !is VrpConsentListUiState.Error) {
                CreateAction(onClick = onNavigateToSetup)
            }
        },
    ) {
        when (val uiState = state.uiState) {
            VrpConsentListUiState.Loading -> LoadingState()
            is VrpConsentListUiState.Content -> ConsentList(uiState.consents, onOpenConsent)
            VrpConsentListUiState.Empty -> EmptyState()
            is VrpConsentListUiState.Error -> ErrorState(
                onRetry = { onAction(VrpConsentListAction.RetryLoad) },
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize().testTag(VrpConsentListTestTags.LOADING_SKELETON),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ConsentList(
    consents: List<ConsentRowUi>,
    onOpenConsent: (String) -> Unit,
) {
    val listDescription = stringResource(Res.string.feature_vrp_consents_list_accessibility)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(VrpConsentListTestTags.CONTENT)
            .semantics { contentDescription = listDescription },
        contentPadding = PaddingValues(KptTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(KptTheme.spacing.md),
    ) {
        items(consents, key = { it.consentId }) { consent ->
            ConsentCard(consent = consent, onClick = { onOpenConsent(consent.consentId) })
        }
    }
}

@Composable
private fun ConsentCard(
    consent: ConsentRowUi,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(VrpConsentListTestTags.row(consent.consentId))
            .clickable(onClick = onClick),
        shape = KptTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = KptTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = KptTheme.elevation.level1),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(KptTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = consent.payeeName,
                        style = KptTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = KptTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    StatusChip(consent)
                }

                Row(
                    modifier = Modifier.padding(top = KptTheme.spacing.sm),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = consent.limitAmountLabel,
                        style = KptTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Light,
                        color = KptTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            Res.string.feature_vrp_consents_list_limit_format,
                            periodLabel(consent.period),
                        ),
                        style = KptTheme.typography.bodyMedium,
                        color = KptTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = KptTheme.spacing.sm,
                            bottom = KptTheme.spacing.xs,
                        ),
                    )
                }

                Text(
                    text = consent.validUntil
                        ?.let { stringResource(Res.string.feature_vrp_consents_list_valid_until, it) }
                        ?: stringResource(Res.string.feature_vrp_consents_list_no_end_date),
                    style = KptTheme.typography.bodyMedium,
                    color = KptTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = KptTheme.spacing.sm),
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = KptTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun StatusChip(consent: ConsentRowUi) {
    Surface(
        shape = KptTheme.shapes.extraLarge,
        color = KptTheme.colorScheme.surfaceVariant,
        modifier = Modifier.testTag(VrpConsentListTestTags.rowStatus(consent.consentId)),
    ) {
        Text(
            text = consentStatusLabel(consent.status),
            style = KptTheme.typography.labelMedium,
            color = KptTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = KptTheme.spacing.sm,
                vertical = KptTheme.spacing.xs,
            ),
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(KptTheme.spacing.md)
            .testTag(VrpConsentListTestTags.EMPTY_STATE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.feature_vrp_consents_list_empty_title),
            style = KptTheme.typography.headlineSmall,
            color = KptTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(VrpConsentListTestTags.EMPTY_TITLE),
        )
        Text(
            text = stringResource(Res.string.feature_vrp_consents_list_empty_body),
            style = KptTheme.typography.bodyMedium,
            color = KptTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = KptTheme.spacing.sm)
                .testTag(VrpConsentListTestTags.EMPTY_BODY),
        )
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(KptTheme.spacing.md)
            .testTag(VrpConsentListTestTags.ERROR_STATE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.feature_vrp_consents_list_error_title),
            style = KptTheme.typography.headlineSmall,
            color = KptTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.feature_vrp_consents_list_error_storage),
            style = KptTheme.typography.bodyMedium,
            color = KptTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = KptTheme.spacing.sm)
                .testTag(VrpConsentListTestTags.ERROR_BODY),
        )
        MifosFilledPillButton(
            label = stringResource(Res.string.feature_vrp_consents_list_retry),
            onClick = onRetry,
            testTag = VrpConsentListTestTags.RETRY_BUTTON,
            modifier = Modifier.padding(top = KptTheme.spacing.lg),
        )
    }
}

@Composable
private fun CreateAction(onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(KptTheme.spacing.md)) {
        MifosFilledPillButton(
            label = stringResource(Res.string.feature_vrp_consents_list_create),
            onClick = onClick,
            testTag = VrpConsentListTestTags.CREATE_BUTTON,
        )
    }
}
