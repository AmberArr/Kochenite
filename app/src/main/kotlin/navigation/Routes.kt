package moe.frost.kochenite.navigation

import kotlinx.serialization.Serializable

@Serializable internal data object OverviewRoute

@Serializable internal data object TransactionsRoute

@Serializable internal data object AccountsRoute

@Serializable internal data object ReportsRoute

@Serializable internal data object SettingsRoute

@Serializable internal data object CategoriesRoute

@Serializable internal data object TagsRoute

@Serializable internal data class TagRoute(
    val id: String? = null,
)

@Serializable internal data object TemplatesRoute

@Serializable internal data class TemplateSaveRoute(
    val entryId: String,
)

@Serializable internal data object GroupsRoute

@Serializable internal data class AccountRoute(
    val id: String? = null,
)

@Serializable internal data class CategoryRoute(
    val id: String? = null,
)

@Serializable internal data class GroupRoute(
    val id: String? = null,
)

@Serializable internal data class AdjustmentRoute(
    val id: String,
)

@Serializable internal data class EntryEditorRoute(
    val id: String,
)

@Serializable internal data class EntryDetailRoute(
    val id: String,
)
