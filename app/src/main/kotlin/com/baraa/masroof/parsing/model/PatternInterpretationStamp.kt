package com.baraa.masroof.parsing.model

/** Existing confidence-reason wire format. Keep bytes stable across mapper and write guard. */
data class PatternInterpretationStamp(val fingerprint: String, val patternId: String, val revision: Int,
    val roleProfile: Boolean) {
    val engine: String get() = if (roleProfile) ROLE_ENGINE else FACT_ENGINE
    val rules: String get() = if (roleProfile) "$fingerprint:$patternId:$revision" else fingerprint
    fun definitionReasons(): List<String> = if (roleProfile) emptyList() else
        listOf("pattern_config:$fingerprint", "pattern_revision:$patternId:$revision")

    companion object {
        const val FACT_ENGINE = "message-pattern-facts-v1"
        const val ROLE_ENGINE = "message-pattern-roles-v1"
        fun isPatternDerived(reasons: List<String>): Boolean = reasons.any {
            it in setOf("engine:$FACT_ENGINE", "engine:$ROLE_ENGINE") || it.startsWith("pattern_")
        }

        /** Malformed, duplicate or contradictory metadata cannot authorize an automatic write. */
        fun decode(reasons: List<String>): PatternInterpretationStamp? {
            val roles = reasons.count { it == "engine:$ROLE_ENGINE" } == 1
            if (reasons.count { it.startsWith("engine:") } != 1 || (!roles && "engine:$FACT_ENGINE" !in reasons)) return null
            val roleStamp = if (roles) reasons.filter { it.startsWith("rules:") }.singleOrNull()
                ?.removePrefix("rules:")?.split(':') ?: return null else null
            if (roleStamp != null && roleStamp.size != 3) return null
            val fingerprint = roleStamp?.first() ?: reasons.filter { it.startsWith("pattern_config:") }.singleOrNull()
                ?.removePrefix("pattern_config:") ?: return null
            val identity = roleStamp?.let { listOf("pattern_revision", it[1], it[2]) }
                ?: reasons.filter { it.startsWith("pattern_revision:") }.singleOrNull()?.split(':') ?: return null
            if (!fingerprint.matches(Regex("[0-9a-f]{64}")) || identity.size != 3) return null
            if (!roles && reasons.count { it == "rules:$fingerprint" } != 1) return null
            val revision = identity[2].toIntOrNull()?.takeIf { it > 0 && it.toString() == identity[2] } ?: return null
            return PatternInterpretationStamp(fingerprint, identity[1], revision, roles)
        }
    }
}
