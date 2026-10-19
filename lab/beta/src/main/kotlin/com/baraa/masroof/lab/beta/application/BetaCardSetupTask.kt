package com.baraa.masroof.lab.beta.application

import com.baraa.masroof.lab.finance.asset.AssetId
import com.baraa.masroof.lab.finance.value.BankId

/** One task per owned card, never one task per SMS. */
data class BetaCardSetupTask(val bank: BankId,val suffix: String,val assetId: AssetId?)
