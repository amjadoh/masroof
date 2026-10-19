package com.baraa.masroof.lab.beta.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.beta.application.*
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.ui.components.*
import com.baraa.masroof.ui.dashboard.DashboardSectionHeading
import com.baraa.masroof.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Production review list/detail journey, over isolated lab facts and write facades. */
@Composable
internal fun BetaReviewInboxPanel(container: BetaContainer, onClose: () -> Unit,
    onBusyChanged: (Boolean) -> Unit,
    initialEvidenceId: String? = null, onChanged: () -> Unit = {}, onDetailChanged: (Boolean) -> Unit = {}) {
    val scope=rememberBetaUiScope()
    var busy by remember {mutableStateOf(false)}
    var ignored by rememberSaveable {mutableStateOf(false)}
    var after by rememberSaveable {mutableStateOf("")}
    var selected by rememberSaveable {mutableStateOf(initialEvidenceId)}
    var generation by remember {mutableIntStateOf(0)}
    var page by remember {mutableStateOf<BetaInboxPage?>(null)}
    var detail by remember {mutableStateOf<BetaReviewDetail?>(null)}
    var failed by remember {mutableStateOf(false)}
    var failureReason by remember {mutableStateOf<String?>(null)}
    var confirmDismiss by remember {mutableStateOf(false)}
    var learning by rememberSaveable {mutableStateOf(false)}
    var configureReference by remember {mutableStateOf<BetaOwnershipCandidate?>(null)}
    var configureLoan by remember {mutableStateOf(false)}
    SideEffect {onBusyChanged(busy);onDetailChanged(selected!=null)}
    DisposableEffect(Unit) {onDispose {onBusyChanged(false);onDetailChanged(false)}}
    BackHandler {if(!busy) {if(learning) learning=false else if(selected!=null) selected=null else onClose()}}
    BetaLaunchedEffect(after,ignored,selected,generation) {
        failed=false;failureReason=null;page=null;detail=null
        try {withContext(BetaIo.dispatcher) {
            if(selected!=null) null to container.reviewDetail(checkNotNull(selected))
            else container.reviewInboxPage(after,ignored) to null
        }.let {page=it.first;detail=it.second}}
        catch(c: CancellationException) {throw c}
        catch(_: Exception) {failed=true}
    }
    fun perform(closeOnSuccess: Boolean = false, action: ()->Unit) {
        if(busy) return
        busy=true;failed=false
        val selectedId=selected
        scope.launch {
            try {val posted=withContext(BetaIo.dispatcher) {
                    action();container.processAutomaticEvidence()
                    selectedId!=null && container.financialPosting(com.baraa.masroof.lab.finance.posting.PostingKey(com.baraa.masroof.lab.finance.value.Environment.BETA,"evidence:$selectedId"))!=null
                }
                if(closeOnSuccess || posted) selected=null
                configureReference=null;configureLoan=false;confirmDismiss=false;generation++;onChanged()
            } catch(c: CancellationException) {throw c}
            catch(error: Exception) {failed=true;failureReason=error.message?.takeIf {it.matches(Regex("[A-Z_]{1,80}"))}}
            finally {busy=false}
        }
    }
    val row=detail?.row
    if(confirmDismiss && row!=null) AlertDialog(onDismissRequest={if(!busy) confirmDismiss=false},
        title={Text(stringResource(R.string.beta_fix_dismiss))},text={Text(stringResource(R.string.beta_fix_dismiss_help))},
        confirmButton={TextButton(enabled=!busy,onClick={perform(true) {container.resolveReviewAsNonFinancial(row.id)}}) {Text(stringResource(R.string.beta_fix_dismiss))}},
        dismissButton={TextButton(enabled=!busy,onClick={confirmDismiss=false}) {Text(stringResource(R.string.beta_asset_cancel))}})
    val current=detail
    if(current!=null && (configureReference!=null || configureLoan)) {
        val candidate=configureReference
        val existing=candidate?.asset ?: current.preview?.assets?.filter {it.kind==AssetKind.LOAN && it.id.bank.value==current.row.bank}?.singleOrNull().takeIf {configureLoan}
        val kind=existing?.kind ?: if(configureLoan) AssetKind.LOAN else if(candidate?.reference?.kind==BetaOwnershipKind.ACCOUNT) AssetKind.ACCOUNT else AssetKind.DEBIT_CARD
        val bank=existing?.id?.bank ?: candidate?.reference?.bank ?: current.row.bank?.let {com.baraa.masroof.lab.finance.value.BankId(it)}
        if(bank!=null) BetaAssetEditor(bank,existing,current.preview?.assets.orEmpty(),busy,failed,
            initialKind=kind,initialSuffix=candidate?.reference?.suffix.orEmpty(),
            allowedKinds=if(kind in setOf(AssetKind.CREDIT_CARD,AssetKind.DEBIT_CARD)) setOf(AssetKind.CREDIT_CARD,AssetKind.DEBIT_CARD) else setOf(kind),
            metadata=existing?.let {current.assetMetadata[it.id]} ?: BetaAssetMetadata(),
            onCancel={configureReference=null;configureLoan=false},onSave={draft -> perform {
                val saved=container.saveManualAsset(draft)
                candidate?.reference?.let {ref -> container.ownershipSnapshot().candidates.singleOrNull {it.reference==ref}?.let {container.decideOwnership(it,true,saved.id)}}
            }})
    }
    if(learning && selected!=null) {BetaInterpretationReviewPanel(container,checkNotNull(selected),{learning=false},onBusyChanged);return}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
        if(failed) {
            Text(stringResource(failureReason?.let(::approvalReason) ?: R.string.beta_review_failed),color=MaterialTheme.colorScheme.error)
            OutlinedButton(enabled=!busy,onClick={generation++}) {Text(stringResource(R.string.beta_retry))}
        }
        if(page==null && detail==null && !failed) CircularProgressIndicator()
        if(current!=null) {
            val entry=current.row
            MasroofCard {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(MasroofSpacing.carouselGap)) {
                    Icon(reviewFamilyIcon(entry.family),null,Modifier.size(MasroofIconSizes.logo),tint=MaterialTheme.colorScheme.primary)
                    Text(stringResource(reviewFamilyLabel(entry.family)),style=MaterialTheme.typography.titleMedium)
                }
                entry.amount?.let {MasroofReferenceMoney(betaMoney(it,entry.currency))}
            }
            BetaReviewMeta(MasroofOutlineIcons.MessageSquare,stringResource(R.string.beta_complete_review_sender,entry.sender))
            BetaReviewMeta(MasroofOutlineIcons.CalendarDays,stringResource(R.string.beta_complete_review_date,betaDate(entry.receivedAt)))
            entry.description?.let {BetaReviewMeta(MasroofOutlineIcons.Store,it)}
            DashboardSectionHeading(stringResource(R.string.beta_complete_review_reasons),MasroofOutlineIcons.CircleAlert)
            Text(stringResource(reviewReasonLabel(current)))
            if(!entry.dismissed && !ignored) {
                current.ownership.filter {it.status!=BetaOwnershipStatus.OWNED || it.asset==null || (it.asset.kind==AssetKind.DEBIT_CARD && it.asset.parentId==null)}.forEach {candidate ->
                    OwnershipCandidateCard(candidate,busy,{owned,asset -> perform {
                        if(owned==null) container.deferOwnership(candidate) else container.decideOwnership(candidate,owned,asset)
                    }},{configureReference=candidate})
                }
                if(current.loanConfiguration) OutlinedButton(enabled=!busy,onClick={configureLoan=true},modifier=Modifier.fillMaxWidth().testTag("review-configure-loan")) {
                    Text(stringResource(R.string.beta_complete_review_loan))
                }
            }
            DashboardSectionHeading(stringResource(R.string.beta_complete_review_message),MasroofOutlineIcons.MessageSquare)
            MasroofCard {Text(entry.body,textAlign=TextAlign.Start)}
            if(entry.dismissed) OutlinedButton(enabled=!busy,onClick={perform(true) {container.resolveReviewAsNonFinancial(entry.id,true)}},modifier=Modifier.fillMaxWidth().testTag("review-restore")) {Text(stringResource(R.string.beta_fix_restore))}
            else if(!ignored) {
                BetaReviewResolutionActions(current,busy,{request ->
                    perform {
                        val result=container.approveEvidenceFinancial(request)
                        check(result is BetaFinancialAdoptionResult.Posted) {"Financial decision remains retryable"}
                        selected=null
                    }
                },onRetryPending={perform {
                    val request=checkNotNull(current.preview?.pending)
                    check(container.retryFinancialApproval(entry.id,request) is BetaFinancialAdoptionResult.Posted)
                    selected=null
                }},onCancelPending={perform {checkNotNull(current.preview?.pending).let {check(container.cancelFinancialApproval(it))}}})
                OutlinedButton(enabled=!busy,onClick={perform {container.reprocessEvidence(entry.id)}},modifier=Modifier.fillMaxWidth().testTag("review-reinterpret")) {Text(stringResource(R.string.beta_complete_review_retry))}
                if(current.preview?.pending==null) TextButton(enabled=!busy,onClick={confirmDismiss=true},modifier=Modifier.fillMaxWidth().testTag("review-dismiss")) {Text(stringResource(R.string.beta_fix_dismiss))}
            }
            TextButton(enabled=!busy,onClick={learning=true},modifier=Modifier.testTag("review-advanced-learning")) {Text(stringResource(R.string.beta_fix_advanced_learning))}
            return@Column
        }
        Row(horizontalArrangement=Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
            BetaChoice(!ignored,{ignored=false;after=""},Modifier.testTag("review-filter-pending"),enabled=!busy) {Text(stringResource(R.string.beta_fix_review_pending))}
            BetaChoice(ignored,{ignored=true;after=""},Modifier.testTag("review-filter-ignored"),enabled=!busy) {Text(stringResource(R.string.beta_fix_review_informational))}
        }
        page?.let {loaded ->
            if(loaded.processing>0) MasroofCard {
                Text(stringResource(R.string.beta_fix_processing))
                OutlinedButton(enabled=!busy,onClick={perform {container.retryEvidence(100)}},modifier=Modifier.testTag("review-retry-processing")) {Text(stringResource(R.string.beta_retry))}
            }
            if(loaded.rows.isEmpty()) Column(Modifier.fillMaxWidth().testTag("review-empty").padding(MasroofSpacing.screenPaddingLarge),horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(MasroofOutlineIcons.CircleCheck,null,Modifier.size(MasroofIconSizes.hero),tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(MasroofSpacing.sectionGap))
                Text(stringResource(if(ignored) R.string.beta_complete_review_ignored_empty else R.string.beta_complete_review_empty),textAlign=TextAlign.Center)
            } else {
                DashboardSectionHeading(stringResource(R.string.beta_complete_review_count,loaded.rows.size),MasroofOutlineIcons.ShieldCheck)
                loaded.rows.forEachIndexed {index,item -> MasroofCard(Modifier.testTag("review-item-$index")) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(item.description ?: item.sender,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                        Surface(shape=MasroofShapes.small,color=MasroofThemeExtras.extendedColors.liabilitySoft) {
                            Text(stringResource(reviewFamilyLabel(item.family)),Modifier.padding(MasroofSpacing.sectionHeaderGap),style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    item.amount?.let {MasroofReferenceMoney(betaMoney(it,item.currency))}
                    Text(betaDate(item.receivedAt),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(!ignored) Text(stringResource(if(item.family==null || item.family=="UNKNOWN") R.string.beta_complete_review_unknown_reason else approvalReason(item.reason.orEmpty())),style=MaterialTheme.typography.bodySmall)
                    Button(enabled=!busy,onClick={selected=item.id},modifier=Modifier.fillMaxWidth().testTag("history-review-$index"),shape=MasroofShapes.small) {Text(stringResource(R.string.beta_complete_review_evidence))}
                }}
            }
            if(loaded.next!=null) OutlinedButton(enabled=!busy,onClick={after=loaded.next}) {Text(stringResource(R.string.beta_history_next))}
            if(after.isNotEmpty()) TextButton(enabled=!busy,onClick={after=""}) {Text(stringResource(R.string.beta_history_first))}
        }
    }
}
