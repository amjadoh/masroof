package com.baraa.masroof.lab.copy.domain.model

/**
 * Known financial institution in the user's Masroof profile.
 */
data class BankRegistryEntry(
    val bank: Bank,
    val displayName: String? = null,
)
