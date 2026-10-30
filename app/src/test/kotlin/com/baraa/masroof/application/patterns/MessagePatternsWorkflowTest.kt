package com.baraa.masroof.application.patterns

import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.data.room.DatabaseAccessGate
import com.baraa.masroof.data.room.DatabaseRestartRequiredException
import com.baraa.masroof.engine.bank.patterns.BankMessagePatterns
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class MessagePatternsWorkflowTest {
    @Test fun deletingABundledPatternDoesNotReinstallItOnCatalogLoadOrRestart() = runBlocking {
        val directory = Files.createTempDirectory("masroof-delete-bundled-").toFile()
        try {
            val store = MessagePatternStore(directory.resolve("patterns.json"))
            val body = "Payment\nAmount:10SAR"
            val seed = com.baraa.masroof.engine.bank.patterns.SavedMessagePattern.fromChoices("bundled", "BANK_TEST", "Sender",
                "PURCHASE", body, listOf(com.baraa.masroof.engine.bank.patterns.FieldChoice("amount", 15, body.length)))
            val bank = BankMessagePatterns("BANK_TEST", listOf("Sender"), patterns = listOf(seed))
            fun workflow() = MessagePatternsWorkflow(store, DatabaseAccessGate(), bundledBanks = listOf(bank), requiredBundledBankIds = setOf(bank.bankId))
            val w = workflow()
            val installed = w.load()
            assertTrue(runCatching { w.deletePattern(installed.generation, bank.bankId, seed.id) }.isFailure)
            w.setEnabled(installed.generation, bank.bankId, seed.id, false)
            w.deletePattern(installed.generation + 1, bank.bankId, seed.id)
            val deleted = w.load()
            assertTrue(deleted.banks.single().patterns.isEmpty())
            assertEquals(setOf(seed.id), deleted.banks.single().deletedPatternIds)
            assertEquals(deleted, workflow().load())
            assertTrue(runCatching { w.restoreRevision(deleted.generation, bank.bankId, seed.id, 1) }.isFailure)
            assertEquals(deleted, w.load())
        } finally { directory.deleteRecursively() }
    }

    @Test fun editingKeepsPatternIdentityAndHistoryButRequiresFreshFinancialApproval() = runBlocking {
        val directory = Files.createTempDirectory("masroof-edit-pattern-").toFile()
        try {
            val store = MessagePatternStore(directory.resolve("patterns.json"))
            val w = MessagePatternsWorkflow(store, DatabaseAccessGate())
            w.saveBank(0, BankMessagePatterns("STC", listOf("STC Bank")))
            val body = "Payment\nAmount:10SAR\nTo:SHOP"
            val amount = PatternBinding("amount", 15, 20)
            w.saveTeaching(w.teach(1, "STC", "STC Bank", PatternOperation.PURCHASE, body, listOf(amount)))
            val original = w.load().banks.single().patterns.single()
            val approved = original.copy(revision = 2, factContract = com.baraa.masroof.engine.bank.patterns.PatternFactContract(
                com.baraa.masroof.engine.bank.MessageFamily.PURCHASE, com.baraa.masroof.engine.bank.MoneyDirection.OUTGOING))
            store.saveBank(2, BankMessagePatterns("STC", listOf("STC Bank"), patterns = listOf(approved)))
            val before = store.load()
            assertEquals(listOf(amount), w.editBindings(before.generation, "STC", original.id, body))
            val edit = w.teach(before.generation, "STC", "STC Bank", PatternOperation.TRANSFER_OUT, body,
                listOf(amount, PatternBinding("counterparty", body.indexOf("SHOP"), body.length)), patternId = original.id)
            assertEquals(before, store.load())
            assertEquals("OTHER", w.tryPreview(edit, "STC Bank", body.replace("SHOP", "OTHER"))!!.getValue("counterparty").text)
            val saved = w.saveTeaching(edit)
            val bank = saved.banks.single()
            assertEquals(listOf(1, 2, 3), bank.patterns.map { it.revision })
            val current = bank.currentPatterns.single()
            assertEquals(original.id, current.id)
            assertEquals("TRANSFER_OUT", current.label)
            assertNull(current.factContract)
            assertEquals(approved, bank.patterns.single { it.revision == 2 })
            assertTrue(runCatching { w.saveTeaching(edit) }.isFailure)
            val stale = w.teach(saved.generation, "STC", "STC Bank", PatternOperation.PURCHASE, body, listOf(amount), patternId = original.id)
            w.setEnabled(saved.generation, "STC", original.id, false)
            w.deletePattern(saved.generation + 1, "STC", original.id)
            assertTrue(runCatching { w.saveTeaching(stale) }.isFailure)
            assertTrue(w.load().banks.single().patterns.isEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test fun bankEditsKeepIdentityAndRejectCrossBankSendersWithoutChangingTheCatalog() = runBlocking {
        val directory = Files.createTempDirectory("masroof-banks-").toFile()
        try {
            val workflow = MessagePatternsWorkflow(MessagePatternStore(directory.resolve("patterns.json")), DatabaseAccessGate())
            workflow.addBank(0, "First bank", "FIRST", "FIRST_BANK")
            workflow.addBank(1, "Second bank", "SECOND", "SECOND_BANK")
            workflow.updateBank(2, "FIRST_BANK", "Renamed bank", listOf("FIRST", "FIRST_ALIAS"))
            val saved = workflow.catalog()
            assertEquals("Renamed bank", saved.banks.single { it.bankId == "FIRST_BANK" }.displayName)
            assertEquals(listOf("FIRST", "FIRST_ALIAS"), saved.banks.single { it.bankId == "FIRST_BANK" }.senders)
            assertTrue(runCatching { workflow.updateBank(3, "FIRST_BANK", "Conflict", listOf("SECOND")) }.isFailure)
            assertTrue(runCatching { workflow.addBank(3, "Duplicate", "FIRST_ALIAS") }.isFailure)
            assertEquals(saved, workflow.catalog())
        } finally { directory.deleteRecursively() }
    }

    @Test fun teachingRequiresPreviewAndPreservesRevisionsWithoutFinancialWrites() = runBlocking {
        val directory = Files.createTempDirectory("masroof-teaching-").toFile()
        try {
            val workflow = MessagePatternsWorkflow(MessagePatternStore(directory.resolve("patterns.json")), DatabaseAccessGate())
            workflow.saveBank(0, BankMessagePatterns("STC", listOf("STC Bank")))
            val body = "Payment\nAmount:10SAR\nTo:SHOP"
            val bindings = listOf(PatternBinding("amount", body.indexOf("10SAR"), body.indexOf("10SAR") + 5),
                PatternBinding("merchant", body.indexOf("SHOP"), body.length))
            val preview = workflow.teach(1, "STC", "STC Bank", PatternOperation.PURCHASE, body, bindings)
            assertEquals("10SAR", preview.fields.getValue("amount").text)
            assertTrue(workflow.load().banks.single().patterns.isEmpty())
            assertEquals("250SAR", workflow.tryPreview(preview, "STC Bank", body.replace("10SAR", "250SAR"))!!.getValue("amount").text)
            assertNull(workflow.tryPreview(preview, "OTHER", body))
            val saved = workflow.saveTeaching(preview)
            val id = saved.banks.single().currentPatterns.single().id
            // A taught pattern starts off: it matches nothing until the user switches it on.
            val stored = saved.banks.single().currentPatterns.single()
            assertFalse(stored.enabled)
            assertNull(stored.preview("STC", "STC Bank", body))
            workflow.setEnabled(2, "STC", id, true)
            val active = workflow.load().banks.single().currentPatterns.single()
            assertNotNull(active.preview("STC", "STC Bank", body))
            assertNull(active.preview("OTHER", "STC Bank", body))
            workflow.setEnabled(3, "STC", id, false)
            assertNull(workflow.load().banks.single().currentPatterns.single().preview("STC", "STC Bank", body))
            assertTrue(runCatching { workflow.saveTeaching(preview) }.isFailure)
            workflow.restoreRevision(4, "STC", id, 2)
            val restored = workflow.load().banks.single()
            val versions = restored.patterns
            assertEquals(listOf(1, 2, 3, 4), versions.map { it.revision })
            assertNotNull(restored.currentPatterns.single().preview("STC", "STC Bank", body))
            // Editing keeps the pattern's current on/off state.
            assertTrue(workflow.teach(5, "STC", "STC Bank", PatternOperation.PURCHASE, body, bindings, patternId = id).pattern.enabled)
            assertTrue(runCatching { workflow.teach(5, "STC", "STC Bank", PatternOperation.PURCHASE, body,
                listOf(PatternBinding("merchant", body.indexOf("SHOP"), body.length))) }.isFailure)
            assertTrue(runCatching { workflow.teach(5, "STC", "STC Bank", PatternOperation.PURCHASE, body,
                bindings + PatternBinding("source_account", 0, 7)) }.isFailure)
        } finally { directory.deleteRecursively() }
    }

    @Test fun editsWaitForAdmittedFinancialWorkAndRetiredInstancesCannotEdit() = runBlocking {
        val directory = Files.createTempDirectory("masroof-pattern-gate-").toFile()
        try {
            val store = MessagePatternStore(directory.resolve("patterns.json"))
            val gate = DatabaseAccessGate()
            val workflow = MessagePatternsWorkflow(store, gate, Dispatchers.Unconfined)
            val release = CompletableDeferred<Unit>()
            val reader = async(start = CoroutineStart.UNDISPATCHED) { gate.withAccess { release.await() } }
            val saving = async(start = CoroutineStart.UNDISPATCHED) {
                workflow.saveBank(0, BankMessagePatterns("STC", listOf("STC Bank")))
            }
            assertFalse(saving.isCompleted)
            assertEquals(0L, store.load().generation)
            release.complete(Unit)
            reader.await()
            assertEquals(1L, saving.await().generation)
            gate.retire()
            assertTrue(runCatching { workflow.saveBank(1, BankMessagePatterns("OTHER", listOf("Other"))) }
                .exceptionOrNull() is DatabaseRestartRequiredException)
            assertEquals(listOf("STC"), store.load().banks.map { it.bankId })
        } finally { directory.deleteRecursively() }
    }
}
