package com.baraa.masroof.application.maintenance

/** Whether the launch UI must wait for a maintenance task before showing financial data. */
enum class MaintenanceRequirement {
    /**
     * Financial display is unsafe until the current-data repair finishes. Startup waits for it.
     */
    BLOCKING,

    /**
     * Stored data is already correct to display; the task only refreshes it. Runs as
     * retryable background work, and open screens reload when it completes.
     */
    BACKGROUND,

    /**
     * The maintenance task does not require work.
     */
    NOT_REQUIRED,
}

/** Completion of a current-data repair; incomplete work remains retryable. */
enum class BackfillOutcome { UP_TO_DATE, COMPLETED, INCOMPLETE }
