package com.baraa.masroof.lab.beta.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.finance.posting.FinancialType
import com.baraa.masroof.lab.finance.asset.OwnedAsset
import com.baraa.masroof.ui.components.*
import com.baraa.masroof.ui.dashboard.DashboardSectionHeading
import com.baraa.masroof.ui.theme.*

@Composable
internal fun BetaReviewResolutionActions(detail: BetaReviewDetail,busy: Boolean,
    onApprove: (BetaFinancialApproval)->Unit,onRetryPending: ()->Unit,onCancelPending: ()->Unit) {
    val preview=detail.preview ?: return
    if(preview.pending!=null) {
        MasroofCard {Text(stringResource(R.string.beta_fix_saved_decision));Text(stringResource(financialTypeLabel(preview.pending.type)))}
        OutlinedButton(enabled=!busy && preview.blockedReason==null,onClick=onRetryPending,modifier=Modifier.fillMaxWidth().testTag("review-resume-decision")) {Text(stringResource(R.string.beta_retry))}
        TextButton(enabled=!busy,onClick=onCancelPending,modifier=Modifier.testTag("review-cancel-decision")) {Text(stringResource(R.string.beta_approval_cancel_pending))}
        return
    }
    if(preview.blockedReason!=null || preview.correctionOwned) return
    fun choice(asset: com.baraa.masroof.lab.finance.asset.AssetId?)=asset?.let {betaAssetChoiceKey(it)}
    val incoming=detail.row.family in setOf("TRANSFER_IN","REFUND")
    val destinationNeeded=detail.row.family in setOf("FINANCING_INSTALLMENT","CARD_PAYMENT")
    var source by rememberSaveable(detail.row.id,preview.registryRevision) {mutableStateOf(choice(detail.source) ?: "external".takeIf {incoming})}
    var destination by rememberSaveable(detail.row.id,preview.registryRevision) {mutableStateOf(choice(detail.destination) ?: "external".takeIf {!incoming && !destinationNeeded})}
    var selectedType by rememberSaveable(detail.row.id) {mutableStateOf<String?>(null)}
    var selector by remember {mutableStateOf<String?>(null)}
    var draftChoice by remember {mutableStateOf<String?>(null)}
    fun request(type: String)=betaApprovalDraft(preview,type,source,destination,detail.income?.name)
    if(detail.actions.isNotEmpty()) DashboardSectionHeading(stringResource(R.string.beta_complete_review_actions),MasroofOutlineIcons.ShieldCheck)
    detail.actions.forEach {type ->
        OutlinedButton(enabled=!busy,onClick={val ready=request(type.name);if(ready!=null) onApprove(ready) else selectedType=type.name},
            modifier=Modifier.fillMaxWidth().testTag("review-resolve-${type.name}")) {
            Icon(reviewTypeIcon(type),null,Modifier.size(MasroofIconSizes.sm));Spacer(Modifier.width(MasroofSpacing.sectionHeaderGap))
            Text(stringResource(R.string.beta_complete_review_as,stringResource(financialTypeLabel(type))))
        }
    }
    if(selectedType!=null) MasroofCard {
        Text(stringResource(R.string.beta_complete_review_choose_asset),style=MaterialTheme.typography.titleMedium)
        fun label(value: String?)=if(value=="external") null else preview.assets.singleOrNull {betaAssetChoiceKey(it.id)==value}
        @Composable fun endpoint(value: String?,field: String,title: Int) {
            OutlinedButton(enabled=!busy,onClick={selector=field;draftChoice=value},modifier=Modifier.fillMaxWidth().testTag("review-select-$field")) {
                val asset=label(value)
                Text(stringResource(title)+": "+if(asset!=null) reviewAssetLabel(asset,detail) else stringResource(if(value=="external") R.string.beta_review_external else R.string.beta_complete_review_choose_asset))
            }
        }
        endpoint(source,"source",R.string.beta_review_source);endpoint(destination,"destination",R.string.beta_review_destination)
        val ready=request(checkNotNull(selectedType))
        Button(enabled=!busy && ready!=null,onClick={ready?.let(onApprove)},modifier=Modifier.fillMaxWidth().testTag("review-confirm-resolution")) {Text(stringResource(R.string.beta_complete_review_save))}
    }
    if(selector!=null) BetaBottomSheet(stringResource(R.string.beta_complete_review_choose_asset),busy,
        onCancel={selector=null},onApply={if(selector=="source") source=draftChoice else destination=draftChoice;selector=null},
        canApply=draftChoice!=null,applyTag="review-asset-apply",cancelTag="review-asset-cancel") {
        val type=selectedType?.let(FinancialType::valueOf)
        val sourceField=selector=="source"
        val incomingType=type in setOf(FinancialType.INCOME,FinancialType.REFUND,FinancialType.EXTERNAL_TRANSFER_IN)
        val externalAllowed=if(sourceField) incomingType else !incomingType && type !in setOf(FinancialType.LOAN_REPAYMENT,FinancialType.CREDIT_CARD_PAYMENT)
        val kinds=when {
            sourceField && incomingType -> emptySet()
            !sourceField && type==FinancialType.LOAN_REPAYMENT -> setOf(com.baraa.masroof.lab.finance.asset.AssetKind.LOAN)
            !sourceField && type==FinancialType.CREDIT_CARD_PAYMENT -> setOf(com.baraa.masroof.lab.finance.asset.AssetKind.CREDIT_CARD)
            (!sourceField && !incomingType) -> emptySet()
            type in setOf(FinancialType.LOAN_REPAYMENT,FinancialType.CREDIT_CARD_PAYMENT,FinancialType.INCOME,FinancialType.EXTERNAL_TRANSFER_IN,FinancialType.EXTERNAL_TRANSFER_OUT) -> setOf(com.baraa.masroof.lab.finance.asset.AssetKind.ACCOUNT)
            else -> setOf(com.baraa.masroof.lab.finance.asset.AssetKind.ACCOUNT,com.baraa.masroof.lab.finance.asset.AssetKind.CREDIT_CARD)
        }
        val choices=(if(externalAllowed) listOf("external") else emptyList())+preview.assets.filter {it.kind in kinds}.map {betaAssetChoiceKey(it.id)}
        MasroofSheetChoices(choices,setOfNotNull(draftChoice),{key ->
            if(key=="external") stringResource(R.string.beta_review_external) else reviewAssetLabel(preview.assets.single {betaAssetChoiceKey(it.id)==key},detail)
        },{draftChoice=it},{"review-endpoint-$it"},enabled=!busy)
    }
}

@Composable
private fun reviewAssetLabel(asset: OwnedAsset,detail: BetaReviewDetail): String =
    (detail.assetMetadata[asset.id]?.displayName ?: stringResource(assetKindLabel(asset.kind))) +
        asset.last4?.let {" · •• $it"}.orEmpty()+" · "+stringResource(bankName(asset.id.bank))

@Composable
internal fun BetaReviewMeta(icon: ImageVector,label: String) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
        Icon(icon,null,Modifier.size(MasroofIconSizes.sm),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label,style=MaterialTheme.typography.bodyMedium)
    }
}
internal fun reviewFamilyLabel(family: String?): Int = when(family) {
    "PURCHASE" -> financialTypeLabel(FinancialType.EXPENSE); "REFUND" -> financialTypeLabel(FinancialType.REFUND)
    "FINANCING_INSTALLMENT" -> financialTypeLabel(FinancialType.LOAN_REPAYMENT); "BILL_PAYMENT" -> financialTypeLabel(FinancialType.BILL_PAYMENT)
    "TRANSFER_IN" -> financialTypeLabel(FinancialType.EXTERNAL_TRANSFER_IN); "TRANSFER_OUT" -> financialTypeLabel(FinancialType.EXTERNAL_TRANSFER_OUT)
    "CARD_PAYMENT" -> financialTypeLabel(FinancialType.CREDIT_CARD_PAYMENT); "WITHDRAWAL" -> financialTypeLabel(FinancialType.CASH_WITHDRAWAL)
    "FEE" -> financialTypeLabel(FinancialType.FEE); else -> R.string.beta_review_required
}
internal fun reviewFamilyIcon(family: String?): ImageVector = when(family) {
    "FINANCING_INSTALLMENT" -> MasroofOutlineIcons.HandCoins; "CARD_PAYMENT","PURCHASE","REFUND" -> MasroofOutlineIcons.CreditCard
    "TRANSFER_IN" -> MasroofOutlineIcons.ArrowDownLeft; "TRANSFER_OUT" -> MasroofOutlineIcons.ArrowUpRight
    "BILL_PAYMENT" -> MasroofOutlineIcons.Receipt; else -> MasroofOutlineIcons.MessageSquare
}
internal fun reviewTypeIcon(type: FinancialType): ImageVector = when(type) {
    FinancialType.LOAN_REPAYMENT -> MasroofOutlineIcons.HandCoins; FinancialType.INCOME,FinancialType.EXTERNAL_TRANSFER_IN -> MasroofOutlineIcons.ArrowDownLeft
    FinancialType.EXTERNAL_TRANSFER_OUT -> MasroofOutlineIcons.ArrowUpRight; else -> MasroofOutlineIcons.Receipt
}
internal fun reviewReasonLabel(detail: BetaReviewDetail): Int = when {
    detail.row.reason=="CONFIRM_LOAN" -> R.string.beta_complete_review_loan_reason
    detail.reasons.any {it in setOf("MISSING_TRANSACTION_TIME","INVALID_TIME","UNBOUND_TIME_ROLE")} -> R.string.beta_complete_review_time_reason
    detail.reasons.any {it.contains("AMOUNT") || it.contains("CURRENCY") || it=="MISSING_POSITIVE_PRINCIPAL"} -> R.string.beta_complete_review_amount_reason
    detail.reasons.any {it.contains("CONFLICT")} -> R.string.beta_complete_review_conflict_reason
    detail.row.reason==null || detail.row.family=="UNKNOWN" -> R.string.beta_complete_review_unknown_reason
    else -> approvalReason(detail.row.reason)
}
