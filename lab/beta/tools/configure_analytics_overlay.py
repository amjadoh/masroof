"""Reviewable additive hooks. Production inputs remain byte-for-byte unchanged."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[3]
base = 'kotlin/com/baraa/masroof/'
spec = {}
def hook(path, old, new, count=1):
    spec.setdefault(base + path, []).append(dict(old=old, new=new, count=count))

hook('application/AppContainer.kt', 'private val database: MasroofDatabase', 'internal val database: MasroofDatabase')
hook('application/AppContainer.kt', '    fun runStartupMaintenance() {', '''    private val analyticsDelegate = lazy { com.baraa.masroof.lab.replica.application.analytics.LabAnalyticsWorkflow(this, appContext) }
    val labAnalytics get() = analyticsDelegate.value
    fun stopLabAnalytics() { if (analyticsDelegate.isInitialized()) labAnalytics.close() }
    fun runStartupMaintenance() {
        labAnalytics.start()''')
hook('application/AppContainer.kt', '        applicationScope.cancel()\n', '        stopLabAnalytics()\n        applicationScope.cancel()\n')
hook('application/sms/HistoricalSmsScanner.kt', '        scanOnDispatcher(receivedAfter)', '        com.baraa.masroof.lab.replica.application.analytics.AnalyticsImportGate.during { scanOnDispatcher(receivedAfter) }')
hook('application/dashboard/DashboardService.kt', '    val isCurrentPeriod: Boolean,', '    val isCurrentPeriod: Boolean,\n    val labSarEquivalents: Map<String, com.baraa.masroof.core.money.Money> = emptyMap(),')
hook('application/dashboard/DashboardProjection.kt', '    val meta: DashboardMeta,', '    val meta: DashboardMeta,\n    val labSarEquivalents: Map<String, com.baraa.masroof.core.money.Money> = emptyMap(),')
hook('application/dashboard/DashboardProjection.kt', '            isCurrentPeriod = isCurrentPeriod,', '            isCurrentPeriod = isCurrentPeriod,\n            labSarEquivalents = labSarEquivalents,')
hook('application/dashboard/DashboardProjectionBuilder.kt', '            transactions = context.transactions,\n            transactionFacts', '            transactions = context.transactions,\n            labSarEquivalents = context.sarEquivalents,\n            transactionFacts')

hook('application/backup/BackupPackageFormat.kt', '    val themeMode: String,\n)', '    val themeMode: String,\n    val labAnalysis: String? = null,\n)')
hook('application/backup/BackupPackageCodec.kt', '        json.decodeFromString(BackupPreferencesSnapshot.serializer(), raw)', '        json.decodeFromString(BackupPreferencesSnapshot.serializer(), raw).also { com.baraa.masroof.lab.replica.application.analytics.LabAnalyticsBackup.validate(it.labAnalysis) }')
hook('application/backup/DatabaseBackupService.kt', '        return BackupPreferencesSnapshot(', '        return BackupPreferencesSnapshot(\n            labAnalysis = com.baraa.masroof.lab.replica.application.analytics.LabAnalyticsBackup.capture(appContext),')
hook('application/backup/DatabaseBackupService.kt', '                    writeEncryptedPackage(staging, destination, secret)', '                    com.baraa.masroof.lab.replica.application.analytics.LabAnalyticsBackup.validateExportSize(staging, archiveLimits)\n                    writeEncryptedPackage(staging, destination, secret)')
hook('application/backup/DatabaseBackupService.kt', '    private fun restorePreferences(snapshot: BackupPreferencesSnapshot) {', '    private fun restorePreferences(snapshot: BackupPreferencesSnapshot) {\n        com.baraa.masroof.lab.replica.application.analytics.LabAnalyticsBackup.restore(appContext, snapshot.labAnalysis)')
recovery = 'application/backup/DatabaseRestoreRecovery.kt'
hook(recovery, '        val transferIntegrityRepairVersion: Int?,\n', '        val transferIntegrityRepairVersion: Int?,\n        val labAnalysis: String? = null,\n')
hook(recovery, '        return PreferenceSnapshot(\n            onboardingStarted = onboarding.getBoolean', '        return PreferenceSnapshot(\n            labAnalysis = com.baraa.masroof.lab.replica.application.analytics.LabAnalyticsBackup.capture(context),\n            onboardingStarted = onboarding.getBoolean')
hook(recovery, '            transferIntegrityRepairVersion = null,', '            transferIntegrityRepairVersion = null,\n            labAnalysis = snapshot.labAnalysis,')
hook(recovery, '    private fun applyPreferences(context: Context, snapshot: PreferenceSnapshot) {', '    private fun applyPreferences(context: Context, snapshot: PreferenceSnapshot) {\n        com.baraa.masroof.lab.replica.application.analytics.LabAnalyticsBackup.restore(context, snapshot.labAnalysis)')
hook(recovery, '            transferIntegrityRepairVersion = values["transferIntegrityRepairVersion"]?.toIntOrNull(),', '            transferIntegrityRepairVersion = values["transferIntegrityRepairVersion"]?.toIntOrNull(),\n            labAnalysis = values["labAnalysis"]?.takeIf { it.isNotEmpty() }?.let { String(java.util.Base64.getDecoder().decode(it), Charsets.UTF_8) },')
hook(recovery, '        appendLine("onboardingStarted=${snapshot.onboardingStarted}")', '        appendLine("labAnalysis=${snapshot.labAnalysis?.let { java.util.Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) } ?: ""}")\n        appendLine("onboardingStarted=${snapshot.onboardingStarted}")')

# Keep the existing route, header, transaction navigation, and editor.
dashboard = 'presentation/dashboard/DashboardScreen.kt'
hook(dashboard, '    var flowDetailMode by rememberSaveable', '''    var labAnalysisOpen by rememberSaveable { mutableStateOf(false) }
    val labDestination by com.baraa.masroof.lab.replica.application.analytics.AnalyticsNavigation.target.collectAsState()
    LaunchedEffect(labDestination) { if (labDestination != null) labAnalysisOpen = true }
    if (labAnalysisOpen) {
        com.baraa.masroof.lab.replica.presentation.analytics.LabAnalyticsRoute(
            onBack = { labAnalysisOpen = false; com.baraa.masroof.lab.replica.application.analytics.AnalyticsNavigation.clear() },
            onOpenTransaction = onOpenTransaction,
        )
        return
    }
    var flowDetailMode by rememberSaveable''')
hook(dashboard, '                                onRescan = onRescan,', '                                onRescan = onRescan,\n                                onOpenAnalytics = { labAnalysisOpen = true },')
hook(dashboard, '    onRescan: () -> Unit,\n) {\n    state.currentAccount', '    onRescan: () -> Unit,\n    onOpenAnalytics: () -> Unit,\n) {\n    state.currentAccount')
hook(dashboard, '                        showIncome = layout.quickIncomeVisible,\n                    )', '                        showIncome = layout.quickIncomeVisible,\n                    )\n                    if (state.isCurrentPeriod) {\n                        androidx.compose.foundation.layout.Spacer(Modifier.height(MasroofSpacing.sectionGap))\n                        com.baraa.masroof.lab.replica.presentation.analytics.LabForecastCard(onOpenAnalytics)\n                    }')
hook('presentation/dashboard/DashboardViewModel.kt', ') : ViewModel() {', '    val labAnalytics: com.baraa.masroof.lab.replica.application.analytics.LabAnalyticsWorkflow? = null,\n) : ViewModel() {')
hook('presentation/dashboard/DashboardViewModelFactory.kt', '            overviewLoader = container.dashboardService,', '            overviewLoader = container.dashboardService,\n            labAnalytics = container.labAnalytics,')
nav = 'presentation/navigation/MasroofRoot.kt'
hook(nav, 'LocalChartDataWorkflow provides dashboardViewModel.chartDataWorkflow) {', 'LocalChartDataWorkflow provides dashboardViewModel.chartDataWorkflow,\n        com.baraa.masroof.lab.replica.presentation.analytics.LocalLabAnalyticsWorkflow provides dashboardViewModel.labAnalytics) {')
hook(nav, 'reviewCount = notifications.unreadCount,', 'reviewCount = notifications.unreadCount + com.baraa.masroof.lab.replica.presentation.analytics.labUnreadAlerts(dashboardViewModel.labAnalytics),')
hook(nav, '    var consumedNavigationGeneration by rememberSaveable', '''    val labTarget by com.baraa.masroof.lab.replica.application.analytics.AnalyticsNavigation.target.collectAsState()
    LaunchedEffect(labTarget) { if (labTarget != null) homeDestination = HomeDestination.Dashboard }
    var consumedNavigationGeneration by rememberSaveable''')
notifications = 'presentation/notification/NotificationCenterScreen.kt'
hook(notifications, '    ) { contentModifier ->\n        when {', '''    ) { originalModifier ->
        Column(originalModifier) {
        com.baraa.masroof.lab.replica.presentation.analytics.LabAlertsCard()
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) {
        val contentModifier = androidx.compose.ui.Modifier.fillMaxSize()
        when {''')
hook(notifications, '\n    }\n}\n\n@Composable\nprivate fun NotificationCenterRow', '\n        }\n        }\n    }\n}\n\n@Composable\nprivate fun NotificationCenterRow')

# New draft has Save/Cancel only; existing editors retain every action.
editor = 'presentation/settings/SettingsCommitmentEditorScreen.kt'
hook(editor, '    onDelete: () -> Unit,\n)', '    onDelete: () -> Unit,\n    newSuggestion: Boolean = false,\n)')
hook(editor, '            OutlinedButton(\n                onClick = onToggleActive,', '            if (!newSuggestion) OutlinedButton(\n                onClick = onToggleActive,')
hook(editor, '            OutlinedButton(\n                onClick = { showDeleteConfirm = true },', '            if (!newSuggestion) OutlinedButton(\n                onClick = { showDeleteConfirm = true },')
hook('MainActivity.kt', '        super.onCreate(savedInstanceState)\n', '        super.onCreate(savedInstanceState)\n        intent?.getStringExtra("lab_analysis_alert")?.let { com.baraa.masroof.lab.replica.application.analytics.AnalyticsNavigation.open(it) }\n')

(root / 'lab/beta/replica-overlays/analytics.json').write_text(json.dumps(spec, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
