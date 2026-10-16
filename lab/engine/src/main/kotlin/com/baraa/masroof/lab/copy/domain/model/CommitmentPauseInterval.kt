package com.baraa.masroof.lab.copy.domain.model

import java.time.Instant

data class CommitmentPauseInterval(
    val pausedAt: Instant,
    val resumedAt: Instant? = null,
)
