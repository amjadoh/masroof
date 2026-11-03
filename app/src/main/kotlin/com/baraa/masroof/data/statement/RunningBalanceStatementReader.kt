package com.baraa.masroof.data.statement

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.statement.BankStatementEntry
import com.baraa.masroof.domain.statement.CanonicalStatementFormat
import com.baraa.masroof.domain.statement.ParsedBankStatement
import com.baraa.masroof.domain.statement.StatementAccountBalance
import com.baraa.masroof.domain.statement.StatementCoverage
import com.baraa.masroof.domain.statement.StatementDirection
import com.baraa.masroof.domain.statement.StatementMatchPolicy
import com.baraa.masroof.domain.statement.StatementParseResult
import com.baraa.masroof.domain.statement.StatementRejection
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Reads the movements of one account from the text lines of a bank statement, whatever the
 * bank's layout, as long as every movement line shows a date, its amount and the running
 * balance after it.
 *
 * The running balance is the proof: an interpretation is accepted only when every
 * consecutive pair of balances differs by exactly one amount shown on the later line. A
 * layout that cannot be proven this way is rejected, never guessed. The first line's
 * direction comes from an explicit minus, or from an opening balance printed elsewhere.
 */
object RunningBalanceStatementReader {
    private val MONEY = Regex("(?<![\\d.,])-?(?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2}(?![\\d.])")
    private val DMY = Regex("(?<!\\d)(\\d{2})/(\\d{2})/(\\d{4})(?!\\d)")
    private val YMD = Regex("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)")

    private data class Line(val number: Int, val date: LocalDate, val values: List<BigDecimal>, val text: String)

    private data class Movement(val line: Line, val signed: BigDecimal, val balance: BigDecimal)

    fun read(lines: List<String>, account: StatementMatchPolicy.QualifiedAccount): StatementParseResult {
        val candidates = joinWrapped(lines).mapNotNull { (number, text) -> line(number, text) }
        if (candidates.size < 2 || candidates.size > CanonicalStatementFormat.MAX_DATA_ROWS) {
            return StatementParseResult.Rejected(StatementRejection.UNRECOGNIZED)
        }
        val printed = lines.flatMap { text -> MONEY.findAll(text).map { it.value.toAmount() }.toList() }
        val movements = listOf(true, false).firstNotNullOfOrNull { balanceLast ->
            listOf(candidates, candidates.reversed()).firstNotNullOfOrNull { ordered -> chain(ordered, balanceLast, printed) }
        } ?: return StatementParseResult.Rejected(StatementRejection.UNRECOGNIZED)
        val first = movements.first()
        val entries = movements.map { movement ->
            BankStatementEntry(
                lineNumber = movement.line.number,
                bank = account.bank,
                accountMasked = account.accountMasked,
                bookedDate = movement.line.date,
                bookedAtTime = null,
                bookedAtRaw = movement.line.date.toString(),
                direction = if (movement.signed.signum() < 0) StatementDirection.DEBIT else StatementDirection.CREDIT,
                amount = Money(movement.signed.abs(), Currency.SAR),
                description = movement.line.text.trim().take(CanonicalStatementFormat.MAX_DESCRIPTION_LENGTH),
                reference = null,
            )
        }
        return StatementParseResult.Accepted(
            ParsedBankStatement(
                formatVersion = CanonicalStatementFormat.VERSION,
                coverage = StatementCoverage(movements.minOf { it.line.date }, movements.maxOf { it.line.date }),
                accounts = listOf(account),
                entries = entries,
                balances = listOf(
                    StatementAccountBalance(
                        bank = account.bank,
                        accountMasked = account.accountMasked,
                        // An overdrawn balance is not a Money amount; it is left out rather than misread.
                        opening = (first.balance - first.signed).takeIf { it.signum() >= 0 }?.let { Money(it, Currency.SAR) },
                        closing = movements.last().balance.takeIf { it.signum() >= 0 }?.let { Money(it, Currency.SAR) },
                    ),
                ),
            ),
        )
    }

    /** Movements oldest first, or null when this reading of the layout does not chain. */
    private fun chain(ordered: List<Line>, balanceLast: Boolean, printed: List<BigDecimal>): List<Movement>? {
        if (ordered.zipWithNext().any { (a, b) -> b.date.isBefore(a.date) }) return null
        fun balance(line: Line) = if (balanceLast) line.values.last() else line.values.first()
        fun amounts(line: Line) = if (balanceLast) line.values.dropLast(1) else line.values.drop(1)
        val movements = ArrayList<Movement>(ordered.size)
        for (i in 1 until ordered.size) {
            val delta = balance(ordered[i]) - balance(ordered[i - 1])
            if (delta.signum() == 0) return null
            // An explicitly signed amount must agree with the balance; an unsigned one takes its sign.
            amounts(ordered[i]).firstOrNull { it.abs().compareTo(delta.abs()) == 0 && (it.signum() > 0 || delta.signum() < 0) }
                ?: return null
            movements += Movement(ordered[i], delta, balance(ordered[i]))
        }
        val head = ordered.first()
        val signed = firstSigned(head, amounts(head), balance(head), printed, ordered.any { line -> amounts(line).any { it.signum() < 0 } })
            ?: return null
        return listOf(Movement(head, signed, balance(head))) + movements
    }

    private fun firstSigned(head: Line, amounts: List<BigDecimal>, balance: BigDecimal, printed: List<BigDecimal>, signedLayout: Boolean): BigDecimal? {
        val nonZero = amounts.filter { it.signum() != 0 }
        if (signedLayout) return nonZero.lastOrNull()
        // Unsigned layout: the statement prints its opening balance somewhere; it settles the direction.
        val choices = nonZero.flatMap { listOf(it, it.negate()) }.filter { signed ->
            val opening = balance - signed
            printed.count { it.compareTo(opening) == 0 } > 0 && head.values.none { it.compareTo(opening) == 0 }
        }
        return choices.distinctBy { it.toPlainString() }.singleOrNull()
    }

    /**
     * Some layouts print a movement's date on one line and its amount and balance on the
     * next. A dated line without two amounts takes the following undated line that has them.
     */
    private fun joinWrapped(lines: List<String>): List<Pair<Int, String>> {
        val joined = ArrayList<Pair<Int, String>>(lines.size)
        var i = 0
        while (i < lines.size) {
            val text = lines[i]
            val next = lines.getOrNull(i + 1)
            if (next != null && date(text) != null && MONEY.findAll(text).count() < 2 &&
                !hasDate(next) && MONEY.findAll(next).count() >= 2
            ) {
                joined += (i + 1) to "$text $next"
                i += 2
            } else {
                joined += (i + 1) to text
                i += 1
            }
        }
        return joined
    }

    private fun hasDate(text: String): Boolean = DMY.containsMatchIn(text) || YMD.containsMatchIn(text)

    private fun line(number: Int, text: String): Line? {
        val date = date(text) ?: return null
        val values = MONEY.findAll(text).map { it.value.toAmount() }.toList()
        if (values.size < 2) return null
        return Line(number, date, values, text)
    }

    private fun date(text: String): LocalDate? {
        val found = DMY.findAll(text).mapNotNull { m -> civil(m.groupValues[3], m.groupValues[2], m.groupValues[1]) } +
            YMD.findAll(text).mapNotNull { m -> civil(m.groupValues[1], m.groupValues[2], m.groupValues[3]) }
        return found.distinct().singleOrNull()
    }

    /** Hijri years (14xx) on the same line are not Gregorian dates. */
    private fun civil(year: String, month: String, day: String): LocalDate? =
        runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()?.takeIf { it.year in 2000..2100 }

    private fun String.toAmount(): BigDecimal = BigDecimal(replace(",", ""))
}
