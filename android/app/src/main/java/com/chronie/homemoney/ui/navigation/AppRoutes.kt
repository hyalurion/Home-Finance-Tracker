package com.chronie.homemoney.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for the app's root [androidx.navigation3.ui.NavDisplay].
 *
 * Every destination implements [NavKey] and is `@Serializable` so the back stack can be saved
 * across configuration changes and process death (Navigation 3 relies on this for restoration).
 *
 * Replacements for the previous Navigation 2 string routes:
 * - "welcome"            -> [Welcome]
 * - "main"               -> [Main]
 * - "membership"         -> [Membership]
 * - "settings"           -> [Settings]
 * - "lan_sync"           -> [LanSync]
 * - "ai_expense"         -> [AIChat] (former one-shot scan, now a full chat)
 * - "database_test"      -> [DatabaseTest]
 * - "open_source_licenses" -> [OpenSourceLicenses]
 * - "add_expense?expenseId=..." -> [AddExpense]
 * - "weekday_detail?..." -> [WeekdayDetail]
 */
@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Welcome : AppRoute

    @Serializable
    data object Main : AppRoute

    @Serializable
    data object Membership : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object LanSync : AppRoute

    /** Streaming AI chat with tool calling, attachments and resumable history. */
    @Serializable
    data object AIChat : AppRoute

    @Serializable
    data object DatabaseTest : AppRoute

    @Serializable
    data object OpenSourceLicenses : AppRoute

    /** Add/expense-edit form. [expenseId] is null when creating a new expense. */
    @Serializable
    data class AddExpense(val expenseId: String? = null) : AppRoute

    /** Per-weekday expense breakdown. [startDate]/[endDate] carry the chart's selected range. */
    @Serializable
    data class WeekdayDetail(
        val dayOfWeek: Int,
        val amount: Double,
        val count: Int,
        val percentage: Float,
        val startDate: String,
        val endDate: String
    ) : AppRoute
}
