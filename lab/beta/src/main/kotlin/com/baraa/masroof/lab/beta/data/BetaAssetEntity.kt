package com.baraa.masroof.lab.beta.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Registry identity is independent of a suffix. Parent references cannot cross bank or environment. */
@androidx.room.DatabaseView(viewName="beta_asset", value="SELECT environment,bankId,localId,'ACCOUNT' AS kind,last4,NULL AS parentLocalId,NULL AS creditCardRole,displayName,network,accountType,loanType,metadataRevision FROM beta_account_registry UNION ALL SELECT environment,bankId,localId,kind,last4,parentLocalId,creditCardRole,displayName,network,accountType,loanType,metadataRevision FROM beta_card_registry UNION ALL SELECT environment,bankId,localId,'CREDIT_FACILITY' AS kind,last4,NULL AS parentLocalId,NULL AS creditCardRole,displayName,network,accountType,loanType,metadataRevision FROM beta_credit_facility UNION ALL SELECT environment,bankId,localId,'LOAN' AS kind,last4,NULL AS parentLocalId,NULL AS creditCardRole,displayName,network,accountType,loanType,metadataRevision FROM beta_loan_registry")
data class BetaAssetEntity(
    val environment: String,
    val bankId: String,
    val localId: String,
    val kind: String,
    val last4: String?,
    val parentLocalId: String?,
    val creditCardRole: String?,
    val displayName: String?=null,val network: String?=null,val accountType: String?=null,val loanType: String?=null,val metadataRevision: Long=0,
)

/** Aliases are evidence, deliberately non-unique across assets (including within one bank). */
data class BetaAssetAliasEntity(
    val environment: String,
    val bankId: String,
    val assetLocalId: String,
    val kind: String,
    val value: String,
    val evidenceId: String,
)
