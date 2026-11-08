package com.baraa.masroof.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * One recognisable color per bank, used as a thin stripe and icon tint on account and card tiles
 * so the user can tell banks apart at a glance. Approximations of each bank's own color; the same
 * in light and dark themes. Keyed by the bank id (`BANK_SNB`, ...).
 */
object MasroofBankColors {
    private val byBank = mapOf(
        "BANK_SNB" to Color(0xFF00875A),
        "BANK_SAB" to Color(0xFFD7141A),
        "BANK_ALRAJHI" to Color(0xFF2747A3),
        "BANK_ALINMA" to Color(0xFFA0662B),
        "BANK_ALJAZIRA" to Color(0xFF0F7C8C),
        "BANK_RIYAD" to Color(0xFF1B4F9C),
        "BANK_ANB" to Color(0xFF2E9A47),
        "BANK_BSF" to Color(0xFF8A1538),
        "BANK_ALBILAD" to Color(0xFFC8102E),
        "BANK_BARQ" to Color(0xFF6C2BD9),
        "BANK_STC" to Color(0xFF5B2C83),
        "BANK_D360" to Color(0xFF1F3A5F),
        "BANK_EMIRATES_NBD" to Color(0xFF0B3A82),
    )

    fun of(bankId: String): Color? = byBank[bankId]

    val stripe = 4.dp
}
