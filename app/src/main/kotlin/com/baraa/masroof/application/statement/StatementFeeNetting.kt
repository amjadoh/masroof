package com.baraa.masroof.application.statement

import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.matching.BankTransferFeePolicy
import com.baraa.masroof.domain.statement.BankStatementEntry
import com.baraa.masroof.domain.statement.ParsedBankStatement
import com.baraa.masroof.domain.statement.StatementDirection
import java.math.BigDecimal

/**
 * SNB prints one transfer fee as four lines: twice the fee, its VAT, the reversal of one fee,
 * and the reversal of its VAT (-0.50, -0.08, +0.25, +0.04). The app holds that fee as one
 * movement, so the four lines become one debit of what the bank kept (0.29), dated and
 * numbered as the first line. Lines that do not form exactly that shape stay as printed.
 */
object StatementFeeNetting {
    private const val LINES = 4

    fun net(statement: ParsedBankStatement): ParsedBankStatement {
        val entries = statement.entries
        val netted = ArrayList<BankStatementEntry>(entries.size)
        var i = 0
        while (i < entries.size) {
            val group = entries.subList(i, minOf(i + LINES, entries.size))
            val kept = keptFee(group)
            if (kept != null) {
                netted += group.first().copy(amount = kept, direction = StatementDirection.DEBIT)
                i += LINES
            } else {
                netted += entries[i]
                i += 1
            }
        }
        return if (netted.size == entries.size) statement else statement.copy(entries = netted)
    }

    private fun keptFee(group: List<BankStatementEntry>): Money? {
        if (group.size != LINES) return null
        val first = group.first()
        if (first.bank != BankTransferFeePolicy.BANK_SNB) return null
        if (group.any { it.bank != first.bank || it.accountMasked != first.accountMasked || it.bookedDate != first.bookedDate }) {
            return null
        }
        if (group.any { it.amount.currency != first.amount.currency }) return null
        val (charge, chargeVat, reversal, reversalVat) = group
        val expected = listOf(StatementDirection.DEBIT, StatementDirection.DEBIT, StatementDirection.CREDIT, StatementDirection.CREDIT)
        if (group.map { it.direction } != expected) return null
        val fee = reversal.amount.amount
        if (fee.signum() <= 0 || charge.amount.amount.compareTo(fee.multiply(BigDecimal(2))) != 0) return null
        if (chargeVat.amount.amount.compareTo(BankTransferFeePolicy.vat(charge.amount.amount)) != 0) return null
        if (reversalVat.amount.amount.compareTo(BankTransferFeePolicy.vat(fee)) != 0) return null
        val kept = BankTransferFeePolicy.chargedFee(first.bank, reversal.amount)
        return kept.takeIf { it.amount.signum() > 0 }
    }
}
