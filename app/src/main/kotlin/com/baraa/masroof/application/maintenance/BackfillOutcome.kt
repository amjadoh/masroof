package com.baraa.masroof.application.maintenance

/** Completion of a current-data repair; incomplete work remains retryable. */
enum class BackfillOutcome { UP_TO_DATE, COMPLETED, INCOMPLETE }
