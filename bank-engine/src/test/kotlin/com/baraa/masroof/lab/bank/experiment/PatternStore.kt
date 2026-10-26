package com.baraa.masroof.lab.bank.experiment

import com.baraa.masroof.lab.bank.patterns.*
import kotlinx.serialization.json.*
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

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
