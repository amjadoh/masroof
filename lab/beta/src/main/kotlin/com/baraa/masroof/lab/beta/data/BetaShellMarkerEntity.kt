package com.baraa.masroof.lab.beta.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Minimal shell row for B0 isolation probes.
 *
 * Full RawEvidence and finance tables arrive with later Beta foundation work.
 * This marker is not a production ledger row.
 */
@Entity(tableName = "beta_shell_marker")
data class BetaShellMarkerEntity(
    @PrimaryKey val id: String,
    val note: String,
)
