package com.baraa.masroof.application.patterns

/** Actionable approval failures, without exposing parser internals or message contents. */
enum class PatternApprovalProblem {
    MESSAGE_MISMATCH, ACCOUNT_REQUIRED, CARD_MISMATCH, REFERENCE_ROLE, REFERENCE_MISMATCH,
    CURRENCY_REQUIRED, CURRENCY_CONFLICT, INVALID_AMOUNT, INVALID_TIME, PATTERN_CONFLICT, MESSAGE_CONTEXT_CONFLICT,
    NON_FINANCIAL, CONFIGURATION_CHANGED, VALIDATION,
}

class PatternApprovalException(val problem: PatternApprovalProblem) : IllegalArgumentException(problem.name)
