package com.baraa.masroof.lab.bank.experiment

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.IOException

class PatternPersistenceTest {
    @Test fun explicitUserRangesWorkWhenDiscoveryDidNotOfferTheValue() {
        val body = "Synthetic event\nField:😀"
        val start = body.indexOf("😀")
        val pattern = SavedMessagePattern.fromChoices("direct", "EXAMPLE_BANK", "Sender", "USER_CHOICE", body,
            listOf(FieldChoice("counterparty", start, start + 2)))
        assertEquals("🚀", requireNotNull(pattern.preview("EXAMPLE_BANK", "Sender", body.replace("😀", "🚀")))
            .fields.getValue("counterparty").raw)
    }

    @Test fun timeFirstAndStandaloneDatesBecomeTypedSlotsWithoutUserSuppliedKinds() {
        val dates = listOf("19:05 26-07-2026" to "TIME_DMY_LONG", "19:05 2026-07-26" to "TIME_YMD",
            "27/08/2026" to "DATE_DMY_ONLY", "2026-08-27" to "DATE_YMD_ONLY")
        for ((date, kind) in dates) {
            val body = "Synthetic event\nDate:$date"
            val pattern = SavedMessagePattern.fromChoices("dates", "EXAMPLE_BANK", "Sender", "USER_CHOICE", body,
                listOf(FieldChoice("date", body.indexOf(date), body.length)))
            assertEquals(kind, pattern.parts.filterIsInstance<FieldSlot>().single().kind.name)
            val directory = Files.createTempDirectory("masroof-date-pattern-").toFile()
            try {
                val file = directory.resolve("patterns.json").toPath()
                PatternStore.save(file, listOf(pattern))
                val changed = body.replace("2026", "2027")
                assertEquals(date.replace("2026", "2027"), requireNotNull(PatternStore.load(file).single()
                    .preview("EXAMPLE_BANK", "Sender", changed)).fields.getValue("date").raw)
                val invalid = body.replace("26-07-2026", "31-02-2026").replace("2026-07-26", "2026-02-31")
                    .replace("27/08/2026", "31/02/2026").replace("2026-08-27", "2026-02-31")
                assertNull(pattern.preview("EXAMPLE_BANK", "Sender", invalid))
            } finally { directory.deleteRecursively() }
        }
    }

    @Test fun humanChoicesNeedNoPrimitiveTypesOrManuallyAuthoredTemplate() {
        val example = syntheticExamples().first()
        val pattern = SavedMessagePattern.fromChoices("automatic", "EXAMPLE_BANK", "STC Bank", example.label,
            example.body, example.selections.map { FieldChoice(it.field, it.start, it.end) })
        assertEquals(example.selections.map { it.kind }, pattern.parts.filterIsInstance<FieldSlot>().map { it.kind })
        val (body, expected) = example.variant(1)
        assertEquals(expected, requireNotNull(pattern.preview("EXAMPLE_BANK", "STC Bank", body)).fields.mapValues { it.value.raw })
    }

    @Test fun selectedCandidatesBecomePersistedSlotsInsteadOfRememberedValues() {
        val example = syntheticExamples().first()
        val pattern = SavedMessagePattern.fromBindings("incoming", "EXAMPLE_BANK", "STC Bank", example.label,
            example.body, example.selections.map { UserFieldBinding(it) })
        val directory = Files.createTempDirectory("masroof-pattern-store-").toFile()
        try {
            val file = directory.resolve("patterns.json").toPath()
            PatternStore.save(file, listOf(pattern))
            val json = Files.readString(file)
            assertFalse(json.contains("137.28")); assertFalse(json.contains("SYNTHETIC PERSON"))
            assertFalse(json.contains("14/10/26")); assertFalse(json.contains("\"007\""))
            val loaded = PatternStore.load(file)
            val (body, expected) = example.variant(1)
            val match = requireNotNull(SavedMessagePattern.unique(loaded, "EXAMPLE_BANK", "STC Bank", body))
            assertEquals(expected, match.second.fields.mapValues { it.value.raw })
            assertFalse(match.second.financialPostingAllowed)
        } finally { directory.deleteRecursively() }
    }

    @Test fun scopesDisableRevisionsAndConflictsSurviveReload() = withStore { file, pattern ->
        val body = syntheticExamples().first().variant(1).first
        assertNull(SavedMessagePattern.unique(PatternStore.load(file), "OTHER_BANK", "STC Bank", body))
        assertNull(SavedMessagePattern.unique(PatternStore.load(file), "EXAMPLE_BANK", "OtherSender", body))
        PatternStore.save(file, listOf(pattern.copy(enabled = false, revision = 2)))
        assertNull(SavedMessagePattern.unique(PatternStore.load(file), "EXAMPLE_BANK", "STC Bank", body))
        PatternStore.save(file, listOf(pattern.copy(revision = 3)))
        assertEquals(3, requireNotNull(SavedMessagePattern.unique(PatternStore.load(file), "EXAMPLE_BANK", "STC Bank", body)).first.revision)
        PatternStore.save(file, listOf(pattern, pattern.copy(id = "conflicting", label = "DIFFERENT_LABEL")))
        val loaded = PatternStore.load(file)
        assertNull(SavedMessagePattern.unique(loaded, "EXAMPLE_BANK", "STC Bank", body))
        assertNull(SavedMessagePattern.unique(loaded.reversed(), "EXAMPLE_BANK", "STC Bank", body))
    }

    @Test fun failedWriteKeepsPriorDefinitionAndLeavesNoPartialFile() = withStore { file, pattern ->
        val prior = Files.readAllBytes(file)
        assertThrows(IOException::class.java) {
            PatternStore.save(file, listOf(pattern.copy(revision = 2))) { throw IOException("Injected write interruption") }
        }
        assertArrayEquals(prior, Files.readAllBytes(file))
        assertEquals(1, PatternStore.load(file).single().revision)
        Files.list(file.parent).use { assertEquals(1L, it.count()) }
    }

    @Test fun unknownSchemaCorruptFileAndExecutablePartAreRejected() = withStore { file, _ ->
        val valid = Files.readString(file)
        Files.writeString(file, valid.replace("\"schema\": 1", "\"schema\": 99"))
        assertThrows(IllegalArgumentException::class.java) { PatternStore.load(file) }
        Files.writeString(file, "{incomplete")
        assertThrows(Exception::class.java) { PatternStore.load(file) }
        Files.writeString(file, valid.replaceFirst("\"literal\":", "\"regex\":"))
        assertThrows(IllegalArgumentException::class.java) { PatternStore.load(file) }
    }

    @Test fun ignoredInlineValueChangesWithoutBeingFrozenOrReturned() {
        val example = markedExample("PURCHASE", "Purchase Card:([[card|DIGITS|4567]]) ref:([[unused|DIGITS|001]]) Amount:[[amount|MONEY|SAR 20.00]] at [[merchant|TEXT|SYNTHETIC SHOP]] on:[[time|DATE_YMD|2026-10-05 19:40:25]]")
        val pattern = SavedMessagePattern.fromBindings("inline", "EXAMPLE_BANK", "ExampleSender", example.label, example.body,
            example.selections.map { UserFieldBinding(it, ignored = it.field == "unused") })
        val changed = example.body.replace("001", "009").replace("SAR 20.00", "USD 85.00").replace("SYNTHETIC SHOP", "OTHER SHOP")
        val result = requireNotNull(pattern.preview("EXAMPLE_BANK", "ExampleSender", changed))
        assertEquals("USD 85.00", result.fields.getValue("amount").raw)
        assertEquals("OTHER SHOP", result.fields.getValue("merchant").raw)
        assertFalse(result.fields.containsKey("unused"))
    }

    @Test fun fileSnapshotCanBeRestoredWithoutOriginalTeachingMessage() = withStore { file, pattern ->
        val snapshot = file.resolveSibling("snapshot.json")
        Files.copy(file, snapshot)
        PatternStore.save(file, listOf(pattern.copy(enabled = false)))
        Files.copy(snapshot, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        assertTrue(PatternStore.load(file).single().enabled)
    }

    private fun withStore(check: (java.nio.file.Path, SavedMessagePattern) -> Unit) {
        val directory = Files.createTempDirectory("masroof-pattern-store-").toFile()
        try {
            val example = syntheticExamples().first()
            val pattern = SavedMessagePattern.fromBindings("incoming", "EXAMPLE_BANK", "STC Bank", example.label,
                example.body, example.selections.map { UserFieldBinding(it) })
            val file = directory.resolve("patterns.json").toPath()
            PatternStore.save(file, listOf(pattern))
            check(file, pattern)
        } finally { directory.deleteRecursively() }
    }
}
