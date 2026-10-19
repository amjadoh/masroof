package com.baraa.masroof.lab.beta.application

/** User-facing registry facts. They never change a financial classification by themselves. */
data class BetaAssetMetadata(val displayName: String?=null,val network: String?=null,
    val accountType: String?=null,val loanType: String?=null,val revision: Long=0)
