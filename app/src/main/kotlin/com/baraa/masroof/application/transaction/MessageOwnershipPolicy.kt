package com.baraa.masroof.application.transaction

import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.OwnershipStatus

/** Receiving an SMS does not establish ownership of its local account or card. */
internal object MessageOwnershipPolicy {
    fun isConfirmedExternal(family: MessageFamily, source: OwnershipStatus, destination: OwnershipStatus,
        card: OwnershipStatus, hasCard: Boolean): Boolean {
        if (reviewReason(family, source, destination, card, hasCard) != "message_on_external_container") return false
        val localAccount = if (family in setOf(MessageFamily.TRANSFER_IN, MessageFamily.REFUND)) destination else source
        val cardIsLocal = family in setOf(MessageFamily.PURCHASE, MessageFamily.REFUND,
            MessageFamily.WITHDRAWAL, MessageFamily.BILL_PAYMENT, MessageFamily.FEE)
        return localAccount != OwnershipStatus.OWNED && (!cardIsLocal || card != OwnershipStatus.OWNED)
    }

    fun reviewReason(family: MessageFamily, source: OwnershipStatus, destination: OwnershipStatus,
        card: OwnershipStatus, hasCard: Boolean): String? {
        val localAccount = when (family) {
            MessageFamily.TRANSFER_IN, MessageFamily.REFUND -> destination
            MessageFamily.TRANSFER_OUT, MessageFamily.PURCHASE, MessageFamily.WITHDRAWAL,
            MessageFamily.BILL_PAYMENT, MessageFamily.FEE, MessageFamily.CARD_PAYMENT, MessageFamily.CARD_TOP_UP,
            MessageFamily.FINANCING_INSTALLMENT -> source
            else -> return null
        }
        val localCard = family in setOf(MessageFamily.PURCHASE, MessageFamily.REFUND,
            MessageFamily.WITHDRAWAL, MessageFamily.BILL_PAYMENT, MessageFamily.FEE)
        if (localAccount == OwnershipStatus.EXTERNAL || localCard && card == OwnershipStatus.EXTERNAL)
            return "message_on_external_container"
        if (family == MessageFamily.PURCHASE && source != OwnershipStatus.OWNED && card != OwnershipStatus.OWNED)
            return "purchase_instrument_ownership_unknown"
        if (localCard && hasCard && card == OwnershipStatus.UNKNOWN &&
            (localAccount != OwnershipStatus.OWNED || family == MessageFamily.WITHDRAWAL))
            return "instrument_ownership_unknown"
        return null
    }
}
