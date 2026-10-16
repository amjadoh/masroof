package com.baraa.masroof.lab.copy.domain.ids

import com.baraa.masroof.lab.copy.domain.model.Bank

object CreditFacilityIdFactory {
    fun facilityId(bank: Bank, primaryLast4: String): String =
        RegistryEntityIdFactory.stableCreditFacilityId(bank.id, primaryLast4.trim())
}
