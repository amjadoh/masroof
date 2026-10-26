package com.baraa.masroof.application.maintenance

enum class StartupMaintenanceOutcome {
    READY,
    BLOCKED,
}

/** Financial screens stay blocked until current-data integrity repair succeeds. */
class StartupMaintenance(private val transferIntegrityRepair: TransferIntegrityRepairCoordinator? = null) {
    suspend fun runBlockingPhase(): StartupMaintenanceOutcome = when (transferIntegrityRepair?.runIfNeeded()) {
        BackfillOutcome.INCOMPLETE -> StartupMaintenanceOutcome.BLOCKED
        else -> StartupMaintenanceOutcome.READY
    }
}
