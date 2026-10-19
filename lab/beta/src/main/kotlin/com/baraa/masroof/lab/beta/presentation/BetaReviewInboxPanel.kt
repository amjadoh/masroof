package com.baraa.masroof.lab.beta.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.ui.components.*
import com.baraa.masroof.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
internal fun BetaReviewInboxPanel(container: BetaContainer, onClose: () -> Unit,
    onBusyChanged: (Boolean) -> Unit, onApprove: (String) -> Unit, onConfirmAccounts: () -> Unit) {
    val scope=rememberBetaUiScope()
    var busy by remember {mutableStateOf(false)}
    var confirmDismiss by remember {mutableStateOf(false)}
    SideEffect {onBusyChanged(busy)}
    DisposableEffect(Unit) {onDispose {onBusyChanged(false)}}
    var ignored by rememberSaveable { mutableStateOf(false) }
    var after by rememberSaveable { mutableStateOf("") }
    var generation by remember {mutableIntStateOf(0)}
    var page by remember {mutableStateOf<BetaInboxPage?>(null)}
    var selected by remember {mutableStateOf<BetaInboxRow?>(null)}
    var failed by remember {mutableStateOf(false)}
    var corrections by rememberSaveable {mutableStateOf(false)}
    var learning by rememberSaveable {mutableStateOf(false)}
    BackHandler {if(busy) return@BackHandler; if(selected!=null) {selected=null;learning=false} else onClose()}
    BetaLaunchedEffect(after,ignored,generation) {
        failed=false;page=null
        try {page=withContext(BetaIo.dispatcher) {container.reviewInboxPage(after,ignored)}}
        catch(c: CancellationException) {throw c}
        catch(_: Exception) {failed=true}
    }
    fun resolve(restore: Boolean) {
        val row=selected ?: return
        if(busy) return
        busy=true;failed=false
        scope.launch {
            try {withContext(BetaIo.dispatcher) {container.resolveReviewAsNonFinancial(row.id,restore);container.processAutomaticEvidence()}; selected=null;generation++}
            catch(c: CancellationException) {throw c}
            catch(_: Exception) {failed=true}
            finally {busy=false;confirmDismiss=false}
        }
    }
    if(confirmDismiss) AlertDialog(onDismissRequest={if(!busy) confirmDismiss=false},
        title={Text(stringResource(R.string.beta_fix_dismiss))},text={Text(stringResource(R.string.beta_fix_dismiss_help))},
        confirmButton={TextButton(onClick={resolve(false)},enabled=!busy) {Text(stringResource(R.string.beta_fix_dismiss))}},
        dismissButton={TextButton(onClick={confirmDismiss=false},enabled=!busy) {Text(stringResource(R.string.beta_asset_cancel))}})
    if(corrections) {BetaReviewPanel(container,{corrections=false},onBusyChanged);return}
    if(learning && selected!=null) {BetaInterpretationReviewPanel(container,selected!!.id,{learning=false},onBusyChanged);return}
    Column(verticalArrangement=Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
        selected?.let { row ->
            Text(row.description ?: row.sender,style=MaterialTheme.typography.titleLarge)
            row.amount?.let {Text(betaMoney(it,row.currency),style=MaterialTheme.typography.headlineSmall)}
            Text(betaDate(row.receivedAt),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(approvalReason(row.reason.orEmpty())),color=MaterialTheme.colorScheme.onSurfaceVariant)
            MasroofCard {Text(row.body)}
            if(row.canApprove) Button(enabled=!busy,onClick={onApprove(row.id)},modifier=Modifier.fillMaxWidth().testTag("history-review-0")) {
                Text(stringResource(R.string.beta_history_review_action))
            } else if(!ignored) {
                Text(stringResource(R.string.beta_fix_unknown_message))
                OutlinedButton(enabled=!busy,onClick=onConfirmAccounts,modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.beta_setup_assets_action))}
            }
            if(failed) Text(stringResource(R.string.beta_review_failed),color=MaterialTheme.colorScheme.error)
            if(row.dismissed) OutlinedButton(onClick={resolve(true)},enabled=!busy,modifier=Modifier.testTag("review-restore")) {Text(stringResource(R.string.beta_fix_restore))}
            else if(!ignored && row.reason!="FINANCIAL_APPROVAL_PENDING") TextButton(onClick={confirmDismiss=true},enabled=!busy,modifier=Modifier.testTag("review-dismiss")) {Text(stringResource(R.string.beta_fix_dismiss))}
            // Learning remains available explicitly, never as the default financial review action.
            TextButton(enabled=!busy,onClick={learning=true}) {Text(stringResource(R.string.beta_fix_advanced_learning))}
            OutlinedButton(enabled=!busy,onClick={selected=null;generation++}) {Text(stringResource(R.string.beta_dash_back))}
            return@Column
        }
        Row(horizontalArrangement=Arrangement.spacedBy(MasroofSpacing.smallGap)) {
            BetaChoice(modifier=Modifier.testTag("review-filter-pending"),enabled=!busy,selected=!ignored,onClick={ignored=false;after=""},label={Text(stringResource(R.string.beta_fix_review_pending))})
            BetaChoice(modifier=Modifier.testTag("review-filter-ignored"),enabled=!busy,selected=ignored,onClick={ignored=true;after=""},label={Text(stringResource(R.string.beta_fix_review_informational))})
        }
        if(failed) {
            Text(stringResource(R.string.beta_review_failed),color=MaterialTheme.colorScheme.error)
            OutlinedButton(enabled=!busy,onClick={generation++}) {Text(stringResource(R.string.beta_retry))}
        } else if(page==null) LinearProgressIndicator(Modifier.fillMaxWidth())
        page?.let { current ->
            if(current.processing>0) MasroofCard {
                Text(stringResource(R.string.beta_fix_processing))
                OutlinedButton(enabled=!busy,onClick={
                    busy=true
                    scope.launch {
                        try {withContext(BetaIo.dispatcher) {container.retryEvidence(100);container.processAutomaticEvidence()};generation++;failed=false}
                        catch(c: CancellationException) {throw c}
                        catch(_: Exception) {failed=true}
                        finally {busy=false}
                    }
                },modifier=Modifier.testTag("review-retry-processing")) {Text(stringResource(R.string.beta_retry))}
            }
            if(current.rows.isEmpty()) Text(stringResource(R.string.beta_review_empty),Modifier.testTag("review-empty"))
            current.rows.forEachIndexed { index,row ->
                MasroofCard(Modifier.testTag("review-item-$index")) {
                    Text(row.description ?: row.sender,style=MaterialTheme.typography.titleMedium)
                    row.amount?.let {Text(betaMoney(it,row.currency),style=MaterialTheme.typography.titleLarge)}
                    Text(betaDate(row.receivedAt),style=MaterialTheme.typography.bodySmall)
                    if(!ignored) Text(stringResource(approvalReason(row.reason.orEmpty())),style=MaterialTheme.typography.bodySmall)
                    MasroofTextButton(enabled=!busy,onClick={selected=row},modifier=Modifier.testTag("history-review-$index")) {Text(stringResource(R.string.beta_dash_details))}
                }
            }
            if(current.next!=null) OutlinedButton(enabled=!busy,onClick={after=current.next}) {Text(stringResource(R.string.beta_history_next))}
            if(after.isNotEmpty()) TextButton(enabled=!busy,onClick={after=""}) {Text(stringResource(R.string.beta_history_first))}
        }
        OutlinedButton(enabled=!busy,onClick={corrections=true},modifier=Modifier.testTag("review-existing-transactions")) {Text(stringResource(R.string.beta_setup_edit_transactions))}
    }
}
