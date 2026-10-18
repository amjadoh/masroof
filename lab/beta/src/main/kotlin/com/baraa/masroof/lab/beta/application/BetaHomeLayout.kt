package com.baraa.masroof.lab.beta.application

enum class BetaHomeSection { HERO, SHORTCUTS, COMMITMENTS, METRICS, TREND, MERCHANTS, TRANSACTIONS }
data class BetaHomeSectionEntry(val section: BetaHomeSection, val visible: Boolean = true)
data class BetaHomeLayout(val sections: List<BetaHomeSectionEntry> = BetaHomeSection.entries.map(::BetaHomeSectionEntry),
    val expensesVisible: Boolean = true, val averageVisible: Boolean = true) {
    init { require(sections.map { it.section }.toSet() == BetaHomeSection.entries.toSet() && sections.size == BetaHomeSection.entries.size) }
    fun toggle(section: BetaHomeSection) = copy(sections = sections.map { if (it.section == section) it.copy(visible = !it.visible) else it })
    fun move(section: BetaHomeSection, direction: Int): BetaHomeLayout {
        require(direction == -1 || direction == 1)
        val index = sections.indexOfFirst { it.section == section }
        val destination = index + direction
        if (destination !in sections.indices) return this
        return copy(sections = sections.toMutableList().apply { add(destination, removeAt(index)) })
    }
    internal fun encode() = (listOf("v1", expensesVisible.toString(), averageVisible.toString()) +
        sections.map { "${it.section.name}:${it.visible}" }).joinToString("|")
    companion object {
        internal fun decode(value: String?): BetaHomeLayout {
            if (value == null) return BetaHomeLayout()
            val parts = value.split('|')
            require(parts.size == 3 + BetaHomeSection.entries.size && parts[0] == "v1")
            return BetaHomeLayout(parts.drop(3).map {
                val row = it.split(':'); require(row.size == 2)
                BetaHomeSectionEntry(BetaHomeSection.valueOf(row[0]), row[1].toBooleanStrict())
            }, parts[1].toBooleanStrict(), parts[2].toBooleanStrict())
        }
    }
}
