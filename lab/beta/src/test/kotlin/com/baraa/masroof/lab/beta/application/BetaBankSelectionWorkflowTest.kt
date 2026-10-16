package com.baraa.masroof.lab.beta.application

import com.baraa.masroof.lab.bank.BankCatalog
import com.baraa.masroof.lab.bank.BankDefinition
import com.baraa.masroof.lab.finance.value.BankId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BetaBankSelectionWorkflowTest {
    private val a = BankId("fixture_a")
    private val b = BankId("fixture_b")
    private val c = BankId("fixture_c")
    private val catalog = BetaBankCatalog(listOf(a, b, c).map(::SupportedBetaBank))
    private class MemoryStore : BetaBankSelectionStore {
        val rows = linkedMapOf<BankId, UserBankSelection>()
        override suspend fun all() = rows.values.toList()
        override suspend fun save(selection: UserBankSelection) { rows[selection.bankId] = selection }
    }
    @Test fun threeBankFixtureSeparatesSupportSelectionAndHistoryScope() = runTest {
        val store = MemoryStore()
        val workflow = BetaBankSelectionWorkflow(store, catalog)
        assertTrue(workflow.choices().all { it.state == null })
        workflow.enable(a)
        val policy = BetaBankImportScopePolicy()
        val routing = BankCatalog(listOf(BankDefinition(a, setOf("A")), BankDefinition(b, setOf("B")), BankDefinition(c, setOf("C"))))
        val captured = listOf("A", "B", "C").map { routing.candidates(it) }.map {
            policy.evaluate(it, store.all())
        }.filterIsInstance<BetaBankImportScopePolicy.Decision.Accept>()
        assertEquals(listOf(a), captured.map { it.bank })
        workflow.enable(b)
        assertEquals(BankSelectionState.ENABLED, store.rows[a]?.state)
        assertEquals(BetaBankImportScopePolicy.Decision.Accept(b), policy.evaluate(setOf(b), store.all(), setOf(b)))
        assertEquals(BetaBankImportScopePolicy.Decision.Reject(BetaBankImportScopePolicy.Rejection.OUTSIDE_REQUESTED_HISTORY),
            policy.evaluate(setOf(a), store.all(), setOf(b)))
        workflow.disable(a)
        assertEquals(BetaBankImportScopePolicy.Decision.Reject(BetaBankImportScopePolicy.Rejection.NOT_ENABLED), policy.evaluate(setOf(a), store.all()))
        workflow.enable(a)
        assertEquals(BetaBankImportScopePolicy.Decision.Accept(a), policy.evaluate(setOf(a), store.all()))
        assertEquals(2, store.rows.size)
    }
    @Test fun collisionCannotBeResolvedBySelectionOrCatalogOrder() = runTest {
        val store = MemoryStore()
        BetaBankSelectionWorkflow(store, catalog).enable(a)
        listOf(listOf(a, b, c), listOf(c, b, a)).forEach { banks ->
            val router = BankCatalog(banks.map { BankDefinition(it, setOf("shared")) })
            assertEquals(BetaBankImportScopePolicy.Decision.Reject(BetaBankImportScopePolicy.Rejection.AMBIGUOUS),
                BetaBankImportScopePolicy().evaluate(router.candidates("shared"), store.all()))
        }
        assertEquals(BetaBankImportScopePolicy.Decision.Reject(BetaBankImportScopePolicy.Rejection.UNKNOWN),
            BetaBankImportScopePolicy().evaluate(emptySet(), store.all()))
    }
    @Test fun mentionedTransferBankDoesNotBecomeSelectedOrOwned() = runTest {
        val store = MemoryStore()
        BetaBankSelectionWorkflow(store, catalog).enable(a)
        // Counterparty facts deliberately are not parameters to selection or scope policy.
        assertEquals(BetaBankImportScopePolicy.Decision.Accept(a), BetaBankImportScopePolicy().evaluate(setOf(a), store.all()))
        assertEquals(setOf(a), store.rows.keys)
        assertFalse(BetaBankCatalog.current.supported.any { it.id in setOf(a, b, c) })
    }
    @Test fun unsupportedBankAndDisableBeforeAddAreRejected() = runTest {
        val workflow = BetaBankSelectionWorkflow(MemoryStore(), catalog)
        try { workflow.enable(BankId("unsupported")); fail("Unsupported bank accepted") } catch (_: IllegalArgumentException) { }
        try { workflow.disable(a); fail("Unselected bank disabled") } catch (_: IllegalArgumentException) { }
    }
}
