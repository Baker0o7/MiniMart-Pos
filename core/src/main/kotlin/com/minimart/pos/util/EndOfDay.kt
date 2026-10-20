package com.minimart.pos.util

enum class StepState { DONE, TODO, BLOCKED }

data class EndOfDayStep(
    val id: String, val title: String, val state: StepState, val detail: String,
    val action: HealthAction = HealthAction.NONE
)

data class EndOfDayInputs(
    val openShifts: List<String> = emptyList(),
    val heldCarts: Int = 0,
    val pendingSync: Int = 0,
    val syncConfigured: Boolean = false,
    val unclaimedMpesaToday: Int = 0,
    val backupDoneToday: Boolean = false,
    val salesToday: Int = 0,
    val expensesRecordedToday: Boolean = false,
    val lowStock: Int = 0
)

/** What to do before closing the shop. [canFinish] is false while any step is BLOCKED. */
object EndOfDay {
    fun steps(i: EndOfDayInputs): List<EndOfDayStep> = buildList {
        add(
            if (i.openShifts.isEmpty()) EndOfDayStep("shifts", "All shifts closed", StepState.DONE, "No cashier is still clocked in.")
            else EndOfDayStep("shifts", "Close open shifts", StepState.BLOCKED,
                "Still open: ${i.openShifts.joinToString(", ")}. Count the drawer and clock out so the cash total is right.", HealthAction.SHIFTS)
        )
        add(
            if (i.heldCarts == 0) EndOfDayStep("held", "No held sales", StepState.DONE, "Nothing is parked.")
            else EndOfDayStep("held", "Finish or clear held sales", StepState.TODO,
                "${i.heldCarts} sale(s) are parked. Complete them or clear them so tomorrow starts clean.")
        )
        if (i.unclaimedMpesaToday > 0) add(EndOfDayStep("mpesa", "Match M-Pesa payments", StepState.TODO,
            "${i.unclaimedMpesaToday} payment(s) today are not tied to a sale.", HealthAction.MPESA))
        else add(EndOfDayStep("mpesa", "M-Pesa matched", StepState.DONE, "Every payment received today is accounted for."))
        if (i.syncConfigured) add(
            if (i.pendingSync == 0) EndOfDayStep("sync", "Tills synced", StepState.DONE, "Everything has reached the main phone.")
            else EndOfDayStep("sync", "Sync the tills", StepState.TODO, "${i.pendingSync} sale(s) have not reached the main phone yet.", HealthAction.SYNC)
        )
        if (!i.expensesRecordedToday && i.salesToday > 0) add(EndOfDayStep("expenses", "Any expenses today?", StepState.TODO,
            "No expense was recorded today. Add stock bought, transport or wages so profit is right."))
        if (i.lowStock > 0) add(EndOfDayStep("stock", "Plan tomorrow's restock", StepState.TODO,
            "${i.lowStock} product(s) are low.", HealthAction.LOW_STOCK))
        add(
            if (i.backupDoneToday) EndOfDayStep("backup", "Backed up today", StepState.DONE, "Today's sales are saved in a backup.")
            else EndOfDayStep("backup", "Back up today's sales", StepState.TODO, "Make a backup before you leave.", HealthAction.BACKUP)
        )
    }

    fun canFinish(steps: List<EndOfDayStep>) = steps.none { it.state == StepState.BLOCKED }
    fun remaining(steps: List<EndOfDayStep>) = steps.count { it.state != StepState.DONE }
}
