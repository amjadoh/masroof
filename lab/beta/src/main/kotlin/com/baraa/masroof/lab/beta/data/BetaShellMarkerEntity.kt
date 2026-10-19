package com.baraa.masroof.lab.beta.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Minimal shell row for B0 isolation probes.
 *
 * Full RawEvidence and finance tables arrive with later Beta foundation work.
 * This marker is not a production ledger row.
 */
data class BetaShellMarkerEntity(
    val id: String,
    val note: String,
)
