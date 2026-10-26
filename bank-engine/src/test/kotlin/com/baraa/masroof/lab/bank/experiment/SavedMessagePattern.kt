package com.baraa.masroof.lab.bank.experiment

import kotlinx.serialization.json.*
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import com.baraa.masroof.lab.bank.interpretation.SourceText

internal data class FieldChoice(val field: String, val start: Int, val end: Int, val ignored: Boolean = false)
internal data class UserFieldBinding(val selection: Selection, val ignored: Boolean = false, val directlySelected: Boolean = false)
internal sealed interface PatternPart
internal data class StaticText(val value: String) : PatternPart
internal data class FieldSlot(val field: String, val kind: FieldKind, val width: Int, val ignored: Boolean) : PatternPart {
    init {
        require(field.matches(Regex("[a-z][a-z0-9_]{0,47}")))
        require(if (kind == FieldKind.DIGITS) width in 1..160 else width == 0)
    }
    fun representative(): String = when (kind) {
        FieldKind.MONEY -> "1SAR"
        FieldKind.TEXT -> "x"
        FieldKind.DIGITS -> "0".repeat(width)
        FieldKind.DATE_DMY_SHORT -> "01/01/00 00:00"
        FieldKind.DATE_DMY_LONG -> "01-01-2000 00:00"
        FieldKind.DATE_YMD -> "2000-01-01 00:00"
    }
}

/** Only declarative literals and typed slots survive; original selected values are not stored. */
internal data class SavedMessagePattern(val id: String, val bankId: String, val sender: String, val label: String,
    val revision: Int = 1, val enabled: Boolean = true, val parts: List<PatternPart>) {
    init {
        require(id.matches(Regex("[A-Za-z0-9_.-]{1,64}")) && bankId.isNotBlank() && bankId != "UNKNOWN")
        require(sender.length in 1..128 && label.length in 1..64 && revision > 0)
        require(parts.size in 3..33 && parts.size % 2 == 1)
        require(parts.withIndex().all { (index, part) -> if (index % 2 == 0) part is StaticText else part is FieldSlot })
        require(parts.filterIsInstance<StaticText>().sumOf { it.value.length } <= 4096)
        val slots = parts.filterIsInstance<FieldSlot>()
        require(slots.map { it.field }.distinct().size == slots.size)
    }

    private val compiled: ExampleTemplate by lazy {
        val selections = mutableListOf<Selection>()
        val synthetic = buildString {
            parts.forEach { part -> when (part) {
                is StaticText -> append(part.value)
                is FieldSlot -> {
                    val start = length
                    append(part.representative())
                    selections += Selection(part.field, part.kind, start, length)
                }
            } }
        }
        // Reuse the trusted compiler with generated representatives; no original teaching message is needed.
        ExampleTemplate.learn(sender, label, synthetic, selections)
    }

    fun validate() { compiled }

    fun preview(bankId: String, sender: String, body: String): Preview? {
        if (!enabled || bankId != this.bankId) return null
        val result = compiled.preview(sender, body) ?: return null
        val ignored = parts.filterIsInstance<FieldSlot>().filter { it.ignored }.map { it.field }.toSet()
        return result.copy(fields = result.fields.filterKeys { it !in ignored })
    }

    companion object {
        fun fromChoices(id: String, bankId: String, sender: String, label: String, body: String,
            choices: List<FieldChoice>): SavedMessagePattern {
            val bindings = choices.map { choice ->
                require(choice.start in 0 until choice.end && choice.end <= body.length)
                val raw = SourceText(body.substring(choice.start, choice.end)).text
                val kind = when {
                    raw.matches(Regex("[0-9]+")) -> FieldKind.DIGITS
                    raw.matches(Regex(FieldKind.MONEY.pattern(raw))) && FieldKind.MONEY.accepts(raw) -> FieldKind.MONEY
                    else -> FieldKind.values().firstOrNull { it.dateFormat != null && raw.matches(Regex(it.pattern(raw))) && it.accepts(raw) }
                        ?: FieldKind.TEXT
                }
                UserFieldBinding(Selection(choice.field, kind, choice.start, choice.end), ignored = choice.ignored)
            }
            return fromBindings(id, bankId, sender, label, body, bindings)
        }

        fun fromBindings(id: String, bankId: String, sender: String, label: String, body: String,
            bindings: List<UserFieldBinding>): SavedMessagePattern {
            val ordered = bindings.sortedBy { it.selection.start }
            val candidates = GenericCandidateExtractor.extract(body)
            require(ordered.all { binding -> binding.directlySelected || candidates.any {
                it.text.start <= binding.selection.start && it.text.end >= binding.selection.end
            } }) { "A binding must come from a candidate or an explicit direct selection" }
            ExampleTemplate.learn(sender, label, body, ordered.map { it.selection })
            var cursor = 0
            val parts = buildList {
                ordered.forEach { binding ->
                    val selection = binding.selection
                    add(StaticText(body.substring(cursor, selection.start)))
                    add(FieldSlot(selection.field, selection.kind,
                        if (selection.kind == FieldKind.DIGITS) selection.end - selection.start else 0, binding.ignored))
                    cursor = selection.end
                }
                add(StaticText(body.substring(cursor)))
            }
            return SavedMessagePattern(id, bankId, sender, label, parts = parts).also { it.validate() }
        }

        fun unique(patterns: List<SavedMessagePattern>, bankId: String, sender: String, body: String): Pair<SavedMessagePattern, Preview>? =
            patterns.mapNotNull { pattern -> pattern.preview(bankId, sender, body)?.let { pattern to it } }.singleOrNull()
    }
}

internal object PatternStore {
    @Synchronized fun save(path: Path, patterns: List<SavedMessagePattern>, beforeReplace: () -> Unit = {}) {
        require(patterns.size <= 128 && patterns.map { it.id }.distinct().size == patterns.size)
        patterns.forEach { it.validate() }
        val document = buildJsonObject {
            put("schema", 1); put("normalization", 1)
            putJsonArray("patterns") { patterns.forEach { pattern -> add(buildJsonObject {
                put("id", pattern.id); put("bankId", pattern.bankId); put("sender", pattern.sender)
                put("label", pattern.label); put("revision", pattern.revision); put("enabled", pattern.enabled)
                putJsonArray("parts") { pattern.parts.forEach { part -> add(buildJsonObject {
                    when (part) {
                        is StaticText -> put("literal", part.value)
                        is FieldSlot -> {
                            put("field", part.field); put("kind", part.kind.name)
                            put("width", part.width); put("ignored", part.ignored)
                        }
                    }
                }) } }
            }) } }
        }
        val bytes = Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), document).toByteArray(Charsets.UTF_8)
        require(bytes.size <= 1_048_576)
        val target = path.toAbsolutePath()
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, "pattern-", ".new")
        try {
            FileOutputStream(temporary.toFile()).use { it.write(bytes); it.fd.sync() }
            beforeReplace()
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }

    fun load(path: Path): List<SavedMessagePattern> {
        require(Files.size(path) <= 1_048_576)
        val root = Json.parseToJsonElement(Files.readString(path)).jsonObject
        require(root.keys == setOf("schema", "normalization", "patterns"))
        require(root.getValue("schema").jsonPrimitive.int == 1 && root.getValue("normalization").jsonPrimitive.int == 1)
        val rows = root.getValue("patterns").jsonArray
        require(rows.size <= 128)
        val result = rows.map { element ->
            val row = element.jsonObject
            require(row.keys == setOf("id", "bankId", "sender", "label", "revision", "enabled", "parts"))
            val parts = row.getValue("parts").jsonArray.map { value ->
                val part = value.jsonObject
                if (part.keys == setOf("literal")) StaticText(part.getValue("literal").jsonPrimitive.content)
                else {
                    require(part.keys == setOf("field", "kind", "width", "ignored"))
                    FieldSlot(part.getValue("field").jsonPrimitive.content,
                        FieldKind.valueOf(part.getValue("kind").jsonPrimitive.content),
                        part.getValue("width").jsonPrimitive.int, part.getValue("ignored").jsonPrimitive.boolean)
                }
            }
            SavedMessagePattern(row.getValue("id").jsonPrimitive.content, row.getValue("bankId").jsonPrimitive.content,
                row.getValue("sender").jsonPrimitive.content, row.getValue("label").jsonPrimitive.content,
                row.getValue("revision").jsonPrimitive.int, row.getValue("enabled").jsonPrimitive.boolean,
                parts).also { it.validate() }
        }
        require(result.map { it.id }.distinct().size == result.size)
        return result
    }
}
