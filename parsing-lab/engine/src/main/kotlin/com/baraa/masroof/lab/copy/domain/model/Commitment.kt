package com.baraa.masroof.lab.copy.domain.model

import com.baraa.masroof.lab.copy.core.money.Money
import java.time.Instant
import java.time.LocalDate

data class Commitment(
    val id: String,
    val name: String,
    val amount: Money,
    val transactionDate: LocalDate,
    val recurrence: CommitmentRecurrence?,
    val dueDate: LocalDate?,
    val active: Boolean,
    val pauseIntervals: List<CommitmentPauseInterval> = emptyList(),
    val sourceTransactionId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
