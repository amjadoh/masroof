package com.baraa.masroof.lab.copy.parsing.validator

import com.baraa.masroof.lab.copy.parsing.model.ParsedEventDraft

/**
 * Validates structured parse drafts without guessing missing values.
 */
interface ParsedEventValidator {
    fun validate(draft: ParsedEventDraft): ValidationResult
}
