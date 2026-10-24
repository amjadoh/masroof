package com.baraa.masroof.testsupport

import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.AccountType
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.repository.AccountRegistryRepository

/** Shared registry behavior; tests override writes relevant to their scenario. */
internal open class MutableCardRegistryRepository(
    vararg initial: CardRegistryEntry,
) : NoOpCardRegistryRepository() {
    val entries = initial.toMutableList()

    override suspend fun setOwnership(reference: CardReference, status: OwnershipStatus) {
        val index = entries.indexOfFirst { it.bank == reference.bank && it.last4 == reference.last4 }
        if (index >= 0) entries[index] = entries[index].copy(ownership = status)
    }

    override suspend fun resolve(reference: CardReference): OwnershipStatus =
        get(reference)?.ownership ?: OwnershipStatus.UNKNOWN

    override suspend fun get(reference: CardReference): CardRegistryEntry? =
        entries.find { it.bank == reference.bank && it.last4 == reference.last4 }

    override suspend fun listAll(): List<CardRegistryEntry> = entries.toList()
}

internal open class StubAccountRegistryRepository(
    vararg initial: AccountRegistryEntry,
) : AccountRegistryRepository {
    val entries = initial.toMutableList()

    override suspend fun observe(reference: AccountReference, rawSmsId: String) = Unit
    override suspend fun setOwnership(reference: AccountReference, status: OwnershipStatus) = Unit
    override suspend fun updateDisplayName(reference: AccountReference, displayName: String?) = Unit
    override suspend fun updateAccountType(reference: AccountReference, accountType: AccountType) = Unit

    override suspend fun resolve(reference: AccountReference): OwnershipStatus =
        get(reference)?.ownership ?: OwnershipStatus.UNKNOWN

    override suspend fun get(reference: AccountReference): AccountRegistryEntry? =
        entries.find { it.bank == reference.bank && it.maskedNumber == reference.maskedNumber }

    override suspend fun listAll(): List<AccountRegistryEntry> = entries.toList()
}
