package com.baraa.masroof.application.maintenance

enum class StartupMaintenanceOutcome {
    READY,
    BLOCKED,
}

/** Financial screens stay blocked until current-data integrity repair succeeds. */
class StartupMaintenance(private val transferIntegrityRepair: TransferIntegrityRepairCoordinator? = null) {
    suspend fun runBlockingPhase(): StartupMaintenanceOutcome {
        val repair = transferIntegrityRepair ?: return StartupMaintenanceOutcome.READY
        return when (repair.pendingRequirement()) {
            null -> StartupMaintenanceOutcome.READY
            MaintenanceRequirement.NOT_REQUIRED,
            MaintenanceRequirement.BACKGROUND,
            -> StartupMaintenanceOutcome.READY
            MaintenanceRequirement.BLOCKING ->
                when (repair.runIfNeeded()) {
                    BackfillOutcome.UP_TO_DATE,
                    BackfillOutcome.COMPLETED,
                    -> StartupMaintenanceOutcome.READY

                    BackfillOutcome.INCOMPLETE -> StartupMaintenanceOutcome.BLOCKED
                }
        }
    }
}
