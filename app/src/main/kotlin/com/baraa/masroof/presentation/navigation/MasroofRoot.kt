package com.baraa.masroof.presentation.navigation

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.MasroofAppBar
import com.baraa.masroof.presentation.common.ReviewNotificationIconButton
import com.baraa.masroof.presentation.common.MasroofIcons

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.platform.testTag
import com.baraa.masroof.application.notification.NotificationAction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.presentation.common.MasroofScreenBackground
import com.baraa.masroof.presentation.dashboard.AccountsSummaryRoute
import com.baraa.masroof.presentation.dashboard.CardOwnershipKey
import com.baraa.masroof.presentation.dashboard.CardsSummaryRoute
import com.baraa.masroof.presentation.dashboard.DashboardRoute
import com.baraa.masroof.presentation.dashboard.DashboardUiState
import com.baraa.masroof.presentation.dashboard.DashboardViewModel
import com.baraa.masroof.presentation.dashboard.LoanOwnershipKey
import com.baraa.masroof.presentation.dashboard.LoansSummaryRoute
import com.baraa.masroof.presentation.dashboard.MerchantSpendingSummaryRoute
import com.baraa.masroof.presentation.dashboard.TransactionDetailScreen
import com.baraa.masroof.presentation.dashboard.TransactionListFilterState
import com.baraa.masroof.presentation.dashboard.TransactionListScreen
import com.baraa.masroof.presentation.notification.NotificationCenterRoute
import com.baraa.masroof.presentation.notification.NotificationCenterViewModel
import com.baraa.masroof.presentation.notification.notificationCenterExternalState
import com.baraa.masroof.presentation.onboarding.OnboardingRoute
import com.baraa.masroof.presentation.onboarding.OnboardingStep
import com.baraa.masroof.presentation.onboarding.OnboardingViewModel
import com.baraa.masroof.presentation.review.ReviewRoute
import com.baraa.masroof.presentation.review.ReviewViewModel
import com.baraa.masroof.presentation.settings.SettingsRoute
import com.baraa.masroof.presentation.settings.SettingsViewModel
import com.baraa.masroof.presentation.statement.StatementReconciliationViewModel

/**
 * Minimal root composition: onboarding while incomplete, dashboard + review after HOME.
 * Home screens live on one back stack ([HomeBackStack.kt]); back always returns to the previous visit.
 */
@Composable
fun MasroofRoot(
    onboardingViewModel: OnboardingViewModel,
    dashboardViewModel: DashboardViewModel,
    reviewViewModel: ReviewViewModel,
    settingsViewModel: SettingsViewModel,
    statementReconciliationViewModel: StatementReconciliationViewModel,
    notificationCenterViewModel: NotificationCenterViewModel,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onLocaleChanged: () -> Unit,
    onRequestExport: () -> Unit,
    onRequestImport: () -> Unit,
    onRequestExportLogs: () -> Unit,
    onRequestRestoreBackup: () -> Unit,
) {
    val onboarding by onboardingViewModel.uiState.collectAsState()
    val notifications by notificationCenterViewModel.uiState.collectAsState()
    var homeStack by rememberSaveable { mutableStateOf(homeRootStack()) }
    val top = homeEntries(homeStack).lastOrNull()?.destination ?: HomeDestination.Dashboard
    val tourTargets = remember { com.baraa.masroof.ui.components.MasroofCoachMarkTargets() }
    val dashboardLoading by remember(dashboardViewModel) {
        dashboardViewModel.uiState.map { it.loading }.distinctUntilChanged()
    }.collectAsState(initial = true)
    androidx.compose.runtime.CompositionLocalProvider(com.baraa.masroof.presentation.dashboard.LocalChartDataWorkflow provides dashboardViewModel.chartDataWorkflow,
        com.baraa.masroof.presentation.analytics.LocalAnalyticsWorkflow provides dashboardViewModel.analytics,
        com.baraa.masroof.ui.components.LocalMasroofCoachMarkTargets provides tourTargets) {
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        run {
            MasroofAppBar(title = stringResource(R.string.app_name)) {
                if (onboarding.step == OnboardingStep.HOME) ReviewNotificationIconButton(reviewCount = notifications.unreadCount + com.baraa.masroof.presentation.analytics.unreadAnalysisAlerts(dashboardViewModel.analytics),
                    onClick = { homeStack = pushHome(homeStack, HomeDestination.NotificationCenter) })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MasroofContent(
                onboardingViewModel = onboardingViewModel,
                dashboardViewModel = dashboardViewModel,
                reviewViewModel = reviewViewModel,
                settingsViewModel = settingsViewModel,
                statementReconciliationViewModel = statementReconciliationViewModel,
                notificationCenterViewModel = notificationCenterViewModel,
                onRequestPermissions = onRequestPermissions,
                onOpenAppSettings = onOpenAppSettings,
                onLocaleChanged = onLocaleChanged,
                onRequestExport = onRequestExport,
                onRequestImport = onRequestImport,
                onRequestExportLogs = onRequestExportLogs,
                onRequestRestoreBackup = onRequestRestoreBackup,
                stack = homeStack,
                onStackChange = { homeStack = it },
            )
        }
        if (onboarding.step == OnboardingStep.HOME) {
            val entries = listOf(
                Triple(HomeDestination.Dashboard, R.string.approved_nav_home, MasroofIcons.dashboard),
                Triple(HomeDestination.AllTransactions, R.string.approved_nav_transactions, MasroofIcons.recentTransactions),
                Triple(HomeDestination.Review, R.string.approved_nav_review, MasroofIcons.reviewQueue),
                Triple(HomeDestination.Settings, R.string.approved_nav_settings, MasroofIcons.settings),
            )
            com.baraa.masroof.presentation.common.MasroofBottomNavigation(entries.map { (destination, label, icon) ->
                com.baraa.masroof.presentation.common.MasroofNavigationItem(stringResource(label), icon,
                    top == destination,
                    stringResource(if (destination == HomeDestination.Settings) R.string.dashboard_open_settings else label),
                    tourId = HomeTour.tabId(destination))
            }) { index ->
                if (settingsViewModel.uiState.value.importingSms) return@MasroofBottomNavigation
                val tab = entries[index].first
                // Home and a re-tapped tab start fresh. Another tab is pushed and keeps a transaction detail behind it,
                // but Review always opens on its list because every Review visit shares one ViewModel.
                val fresh = tab == HomeDestination.Dashboard || tab == top
                if (fresh) dashboardViewModel.closeTransactionDetail()
                if (fresh || tab == HomeDestination.Review) reviewViewModel.closeDetail()
                homeStack = switchHomeTab(homeStack, tab)
            }

        }
    }
    if (HomeTour.autoStart && onboarding.step == OnboardingStep.HOME && !onboarding.homeGuideSeen &&
        top == HomeDestination.Dashboard && !dashboardLoading) {
        val finish = { onboardingViewModel.setHomeGuideSeen(true) }
        BackHandler(onBack = finish)
        HomeTour(tourTargets, finish)
    }
    }
}

}

@Composable
private fun MasroofContent(
    onboardingViewModel: OnboardingViewModel,
    dashboardViewModel: DashboardViewModel,
    reviewViewModel: ReviewViewModel,
    settingsViewModel: SettingsViewModel,
    statementReconciliationViewModel: StatementReconciliationViewModel,
    notificationCenterViewModel: NotificationCenterViewModel,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onLocaleChanged: () -> Unit,
    onRequestExport: () -> Unit,
    onRequestImport: () -> Unit,
    onRequestExportLogs: () -> Unit,
    onRequestRestoreBackup: () -> Unit,
    stack: List<String>,
    onStackChange: (List<String>) -> Unit,
) {
    val onboardingState by onboardingViewModel.uiState.collectAsState()
    val reviewState by reviewViewModel.uiState.collectAsState()
    val dashboardState by dashboardViewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val entries = homeEntries(stack)
    val top = entries.lastOrNull() ?: HomeBackStackEntry(0, HomeDestination.Dashboard)
    // Each visit keeps its own saved state (settings stack, selected card, scroll) while covered by another screen.
    val destinationStateHolder = rememberSaveableStateHolder()
    var liveEntryIds by rememberSaveable { mutableStateOf(entries.map { it.id }) }
    // The visit that opened the transaction detail; the detail shows only while that visit is on top.
    var transactionDetailOwner by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingSettingsLaunch by remember { mutableStateOf<SettingsLaunchRequest?>(null) }
    LaunchedEffect(stack) {
        val ids = entries.map { it.id }
        (liveEntryIds - ids.toSet()).forEach(destinationStateHolder::removeState)
        liveEntryIds = ids
        if (transactionDetailOwner != null && transactionDetailOwner !in ids) {
            transactionDetailOwner = null
            dashboardViewModel.closeTransactionDetail()
        }
        // A launch still waiting for Settings to load must not land in a later, unrelated Settings visit.
        if (top.destination != HomeDestination.Settings) pendingSettingsLaunch = null
    }
    var transactionListSeedFilter by remember { mutableStateOf<TransactionListFilterState?>(null) }
    var transactionListOpenGeneration by remember { mutableStateOf(0) }
    var pendingLoansSummaryLoanKey by rememberSaveable { mutableStateOf<String?>(null) }
    var loansSummaryDetailExitsToHome by rememberSaveable { mutableStateOf(false) }
    var pendingCardsSummaryCardKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCardsSummaryDebitKey by rememberSaveable { mutableStateOf<String?>(null) }
    var cardsSummaryDetailExitsToHome by rememberSaveable { mutableStateOf(false) }

    fun push(destination: HomeDestination) {
        val next = pushHome(stack, destination)
        // The review ViewModel is shared, so a new Review visit must not open on an older visit's message.
        if (next !== stack && destination == HomeDestination.Review) reviewViewModel.closeDetail()
        onStackChange(next)
    }

    fun pop() {
        val popped = popHome(stack) ?: return
        // Settings and Review change transactions; whatever is underneath re-reads them.
        if (top.destination == HomeDestination.Settings || top.destination == HomeDestination.Review) dashboardViewModel.refresh()
        onStackChange(popped)
    }

    fun openTransaction(id: String) {
        transactionDetailOwner = top.id
        dashboardViewModel.openTransactionDetail(id)
    }

    val labTarget by AnalyticsNavigation.target.collectAsState()
    LaunchedEffect(labTarget) {
        if (labTarget == null) return@LaunchedEffect
        // A full-screen detail hides the dashboard, which is what opens the alert.
        if (top.destination == HomeDestination.Dashboard) dashboardViewModel.closeTransactionDetail() else push(HomeDestination.Dashboard)
    }
    val labCenterReturns by AnalyticsNavigation.centerReturns.collectAsState()
    var consumedLabCenterReturn by rememberSaveable { mutableStateOf(labCenterReturns) }
    LaunchedEffect(labCenterReturns) {
        if (labCenterReturns > consumedLabCenterReturn) pop()
        consumedLabCenterReturn = labCenterReturns
    }

    // [launch] replaces the Settings stack of this fresh visit so the first back returns to the caller.
    fun openSettings(launch: SettingsLaunchRequest? = null) {
        pendingSettingsLaunch = launch
        push(HomeDestination.Settings)
    }

    fun openTransactionList(filter: TransactionListFilterState? = null) {
        transactionListSeedFilter = filter
        transactionListOpenGeneration++
        push(HomeDestination.AllTransactions)
    }

    val notificationExternalState = notificationCenterExternalState(
        dashboardState = dashboardState,
        settingsState = settingsState,
    )

    LaunchedEffect(notificationExternalState) {
        notificationCenterViewModel.refresh(notificationExternalState)
    }

    if (onboardingState.step == OnboardingStep.HOME) {
        // A closed detail has no owner; a detail opened without one (deep link, direct ViewModel call) belongs to the visible screen.
        LaunchedEffect(dashboardState.selectedTransactionId) { if (dashboardState.selectedTransactionId == null) transactionDetailOwner = null }
        val detailOnTop = dashboardState.selectedTransactionId != null && (transactionDetailOwner ?: top.id) == top.id
        BackHandler {
            when {
                detailOnTop -> dashboardViewModel.closeTransactionDetail()
                top.destination == HomeDestination.Review && reviewState.selectedDetail != null -> reviewViewModel.closeDetail()
                // Consume system back on the home screen — do not exit the app.
                else -> pop()
            }
        }

        val detailOpenedFromAllTransactions = top.destination == HomeDestination.AllTransactions && detailOnTop

        val labAnalysisVisible by AnalyticsNavigation.analysisVisible.collectAsState()
        val labOverlayDetail = top.destination == HomeDestination.Dashboard && labAnalysisVisible && detailOnTop
        if (detailOnTop && !detailOpenedFromAllTransactions && !labOverlayDetail) {
            if (showTransactionDetail(
                    dashboardState = dashboardState,
                    onBack = dashboardViewModel::closeTransactionDetail,
                    onReclassify = dashboardViewModel::reclassifySelectedTransaction,
                    onIgnore = dashboardViewModel::ignoreSelectedTransaction,
                    onMarkAsCommitment = dashboardViewModel::markSelectedTransactionAsCommitment,
                )
            ) {
                return
            }
        }

        // Bank display names come from the pattern catalog, so home screens never show raw ids (BANK_STC).
        LaunchedEffect(Unit) { settingsViewModel.loadBankCatalog() }
        androidx.compose.runtime.CompositionLocalProvider(com.baraa.masroof.presentation.settings.LocalSettingsBankNames provides
            settingsState.bankCatalog?.banks.orEmpty().mapNotNull { bank -> bank.displayName?.let { bank.bankId to it } }.toMap()) {
        destinationStateHolder.SaveableStateProvider(top.id) {
        when (top.destination) {
            HomeDestination.Dashboard -> Box {
                DashboardRoute(
                viewModel = dashboardViewModel,
                onOpenAllTransactions = { openTransactionList() },
                onOpenTransaction = ::openTransaction,
                onOpenSettings = { openSettings() },
                onImportSms = { openSettings(launch = SettingsLaunchRequest(SettingsDestination.SmsImport())) },
                onOpenReview = { push(HomeDestination.Review) },
                onOpenCommitments = { push(HomeDestination.CommitmentsSummary) },
                onManageCardsInSettings = {
                    openSettings(
                        launch = resolveManageSettingsLaunch(
                            state = settingsState,
                            target = ManageSettingsTarget.Cards,
                        ),
                    )
                },
                onOpenAccountsSummary = { push(HomeDestination.AccountsSummary) },
                onOpenCardsSummary = {
                    pendingCardsSummaryCardKey = null
                    pendingCardsSummaryDebitKey = null
                    cardsSummaryDetailExitsToHome = false
                    push(HomeDestination.CardsSummary)
                },
                onOpenLoansSummary = {
                    pendingLoansSummaryLoanKey = null
                    loansSummaryDetailExitsToHome = false
                    push(HomeDestination.LoansSummary)
                },
                onOpenMerchantsSummary = { push(HomeDestination.MerchantsSummary) },
                onOpenLoanDetail = { loan ->
                    pendingLoansSummaryLoanKey = LoanOwnershipKey.of(loan)
                    loansSummaryDetailExitsToHome = true
                    push(HomeDestination.LoansSummary)
                },
                onOpenCardDetail = { card ->
                    pendingCardsSummaryCardKey = CardOwnershipKey.of(card)
                    pendingCardsSummaryDebitKey = null
                    cardsSummaryDetailExitsToHome = true
                    push(HomeDestination.CardsSummary)
                },
                onOpenDebitDetail = { debit ->
                    pendingCardsSummaryDebitKey = CardOwnershipKey.of(debit)
                    pendingCardsSummaryCardKey = null
                    cardsSummaryDetailExitsToHome = true
                    push(HomeDestination.CardsSummary)
                },
                onRequestSmsPermission = onRequestPermissions,
                onOpenAppSettings = onOpenAppSettings,
            )
                if (labOverlayDetail) Box(Modifier.fillMaxSize().testTag("lab-analytics-transaction-detail")) {
                    val closeLabDetail = { if (!dashboardState.reclassifying && !dashboardState.ignoring && !dashboardState.markingCommitment) dashboardViewModel.closeTransactionDetail() }
                    BackHandler(onBack = closeLabDetail)
                    showTransactionDetail(dashboardState, closeLabDetail,
                        dashboardViewModel::reclassifySelectedTransaction, dashboardViewModel::ignoreSelectedTransaction,
                        dashboardViewModel::markSelectedTransactionAsCommitment, overlayOnList = true)
                }
            }
            HomeDestination.AccountsSummary -> AccountsSummaryRoute(
                viewModel = dashboardViewModel,
                onBack = ::pop,
                onOpenTransaction = ::openTransaction,
            )
            HomeDestination.CardsSummary -> CardsSummaryRoute(
                viewModel = dashboardViewModel,
                initialSelectedCardKey = pendingCardsSummaryCardKey,
                initialSelectedDebitKey = pendingCardsSummaryDebitKey,
                detailBackExitsSummary = cardsSummaryDetailExitsToHome,
                onInitialSelectionConsumed = {
                    pendingCardsSummaryCardKey = null
                    pendingCardsSummaryDebitKey = null
                },
                onBack = ::pop,
                onOpenTransaction = ::openTransaction,
                onManageCard = { bank ->
                    openSettings(launch = SettingsLaunchRequest(com.baraa.masroof.presentation.navigation.SettingsDestination.BankCards(bank.id)))
                },
            )
            HomeDestination.LoansSummary -> LoansSummaryRoute(
                viewModel = dashboardViewModel,
                initialSelectedLoanKey = pendingLoansSummaryLoanKey,
                detailBackExitsSummary = loansSummaryDetailExitsToHome,
                onInitialSelectionConsumed = { pendingLoansSummaryLoanKey = null },
                onBack = ::pop,
                onOpenTransaction = ::openTransaction,
            )
            HomeDestination.MerchantsSummary -> MerchantSpendingSummaryRoute(
                viewModel = dashboardViewModel,
                onBack = ::pop,
                onOpenDaily = { push(HomeDestination.DailyExpenses) },
                onOpenTransaction = ::openTransaction,
            )
            HomeDestination.CommitmentsSummary -> com.baraa.masroof.presentation.dashboard.CommitmentsSummaryScreen(
                dashboardState, ::pop, { row ->
                    when (row.source) {
                        com.baraa.masroof.application.dashboard.CommitmentDashboardSource.CREDIT_CARD -> {
                            pendingCardsSummaryCardKey = row.creditFacilityKey
                            cardsSummaryDetailExitsToHome = true
                            push(HomeDestination.CardsSummary)
                        }
                        com.baraa.masroof.application.dashboard.CommitmentDashboardSource.LOAN -> {
                            pendingLoansSummaryLoanKey = row.loanKey
                            loansSummaryDetailExitsToHome = true
                            push(HomeDestination.LoansSummary)
                        }
                        com.baraa.masroof.application.dashboard.CommitmentDashboardSource.USER -> row.userCommitmentId?.let { id ->
                            openSettings(launch = SettingsLaunchRequest(SettingsDestination.CommitmentDetail(id)))
                        }
                    }
                }, onOpenPayment = ::openTransaction)
            HomeDestination.DailyExpenses -> com.baraa.masroof.presentation.dashboard.DailyExpensesScreen(
                state = dashboardState, onBack = ::pop,
                onOpenTransaction = ::openTransaction)
            HomeDestination.NotificationCenter -> NotificationCenterRoute(
                viewModel = notificationCenterViewModel,
                externalState = notificationExternalState,
                onBack = ::pop,
                onNavigate = { action ->
                    handleNotificationAction(
                        action = action,
                        settingsState = settingsState,
                        onRequestPermissions = onRequestPermissions,
                        onOpenAppSettings = onOpenAppSettings,
                        onOpenReview = { push(HomeDestination.Review) },
                        onOpenSettings = { launch -> openSettings(launch = launch) },
                    )
                },
            )
            HomeDestination.Review -> ReviewRoute(
                viewModel = reviewViewModel,
                onBack = ::pop,
            )
            HomeDestination.AllTransactions -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    TransactionListScreen(
                        periodLabel = dashboardState.periodLabel,
                        transactions = dashboardState.allTransactions,
                        projection = dashboardState.transactionListProjection,
                        onFilterChanged = dashboardViewModel::setTransactionListQuery,
                        onBack = ::pop,
                        onOpenTransaction = ::openTransaction,
                        seedFilter = transactionListSeedFilter,
                        onSeedFilterApplied = { transactionListSeedFilter = null },
                        openGeneration = transactionListOpenGeneration,
                        ownedCards = dashboardState.ownedCards,
                        ownedAccounts = dashboardState.ownedAccounts,
                    )
                    if (detailOnTop) showTransactionDetail(
                        dashboardState = dashboardState,
                        onBack = dashboardViewModel::closeTransactionDetail,
                        onReclassify = dashboardViewModel::reclassifySelectedTransaction,
                        onIgnore = dashboardViewModel::ignoreSelectedTransaction,
                        onMarkAsCommitment = dashboardViewModel::markSelectedTransactionAsCommitment,
                        overlayOnList = true,
                    )
                }
            }
            HomeDestination.Settings -> SettingsRoute(
                viewModel = settingsViewModel,
                // The home guide only shows on the dashboard, so this clears history like the Home tab.
                onShowGuideAgain = { onboardingViewModel.setHomeGuideSeen(false); dashboardViewModel.refresh(); onStackChange(switchHomeTab(stack, HomeDestination.Dashboard)) },
                statementReconciliationViewModel = statementReconciliationViewModel,
                reviewRequiredCount = dashboardState.summary?.reviewRequiredCount ?: 0,
                pendingLaunch = pendingSettingsLaunch,
                onPendingLaunchConsumed = { pendingSettingsLaunch = null },
                onBack = ::pop,
                onOpenReview = { push(HomeDestination.Review) },
                onOpenAccounts = { dashboardViewModel.refresh(); push(HomeDestination.AccountsSummary) },
                onOpenReviewExample = { id ->
                    push(HomeDestination.Review)
                    reviewViewModel.setListMode(com.baraa.masroof.presentation.review.ReviewListMode.PENDING)
                    reviewViewModel.openDetail(id)
                },
                onLocaleChanged = onLocaleChanged,
                onRequestExport = onRequestExport,
                onRequestImport = onRequestImport,
                onRequestExportLogs = onRequestExportLogs,
                onRequestSmsPermission = onRequestPermissions,
                onOpenAppSettings = onOpenAppSettings,
            )
        }
        }
        }
    } else {
        OnboardingRoute(
            viewModel = onboardingViewModel,
            settingsViewModel = settingsViewModel,
            onRequestPermissions = onRequestPermissions,
            onOpenAppSettings = onOpenAppSettings,
            onRequestRestoreBackup = onRequestRestoreBackup,
        )
    }
}

/**
 * Shows transaction detail when a selection exists.
 *
 * Returns true when the caller should stop composing sibling destinations (full-screen detail).
 * Returns false when nothing is shown, or when detail is rendered as an overlay sibling.
 */
@Composable
private fun showTransactionDetail(
    dashboardState: DashboardUiState,
    onBack: () -> Unit,
    onReclassify: (FinancialTransactionType) -> Unit,
    onIgnore: () -> Unit,
    onMarkAsCommitment: () -> Unit,
    overlayOnList: Boolean = false,
): Boolean {
    val selectedId = dashboardState.selectedTransactionId ?: return false
    val selected = dashboardState.allTransactions.find { it.id == selectedId }
        ?: dashboardState.recentTransactions.find { it.id == selectedId }
        ?: dashboardState.labSelectedTransaction
    if (selected != null) {
        val detailContent: @Composable () -> Unit = {
            TransactionDetailScreen(
                transaction = selected,
                smsEvidence = dashboardState.selectedTransactionSms,
                smsLoading = dashboardState.selectedTransactionSmsLoading,
                reclassifying = dashboardState.reclassifying,
                reclassifySuccess = dashboardState.reclassifySuccess,
                ignoring = dashboardState.ignoring,
                markingCommitment = dashboardState.markingCommitment,
                isCommitted = selected.id in dashboardState.committedSourceTransactionIds,
                markCommitmentSuccess = dashboardState.markCommitmentSuccess,
                error = dashboardState.reclassifyError ?: dashboardState.ignoreError ?: dashboardState.markCommitmentError,
                onBack = onBack,
                onReclassify = onReclassify,
                onIgnore = onIgnore,
                onMarkAsCommitment = onMarkAsCommitment,
                ownedCards = dashboardState.ownedCards,
            )
        }
        if (overlayOnList) {
            MasroofScreenBackground(modifier = Modifier.fillMaxSize(), content = detailContent)
        } else {
            detailContent()
        }
        return true
    }
    if (overlayOnList) {
        com.baraa.masroof.presentation.analytics.AnalysisEvidenceDetailFallback(dashboardState.selectedTransactionSmsLoading, onBack)
        return true
    }
    // Keep the detail destination while a refresh reloads rows for the same period.
    return dashboardState.loading
}

private fun handleNotificationAction(
    action: NotificationAction,
    settingsState: com.baraa.masroof.presentation.settings.SettingsUiState,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenSettings: (SettingsLaunchRequest) -> Unit,
) {
    when (action) {
        NotificationAction.REQUEST_SMS_PERMISSION -> onRequestPermissions()
        NotificationAction.OPEN_APP_SETTINGS -> onOpenAppSettings()
        NotificationAction.OPEN_REVIEW -> onOpenReview()
        NotificationAction.OPEN_SETTINGS_CARDS,
        NotificationAction.OPEN_SETTINGS_ACCOUNTS,
        NotificationAction.OPEN_SETTINGS_ABOUT,
        -> resolveNotificationSettingsLaunch(action, settingsState)?.let(onOpenSettings)

        NotificationAction.DISMISS_RESCAN,
        NotificationAction.MARK_READ_ONLY -> Unit
    }
}
