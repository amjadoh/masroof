package com.baraa.masroof.lab.beta.application

/** Persisted Beta-only progress. An existing acknowledged installation never restarts onboarding. */
enum class BetaOnboardingStep { BANK, IMPORT, ASSETS, REVIEW, COMPLETE }
