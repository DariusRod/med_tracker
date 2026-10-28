package app.doselog.ui.navigation

import androidx.annotation.DrawableRes
import app.doselog.R
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
data object LogRoute

@Serializable
data object ScoreRoute

@Serializable
data object ReportsRoute

@Serializable
data object ManageRoute

/** [itemId] of [NEW_ITEM] opens the editor for a new item. */
@Serializable
data class ItemEditRoute(val itemId: Long = NEW_ITEM) {
    companion object {
        const val NEW_ITEM = 0L
    }
}

@Serializable
data object ExportRoute

enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
) {
    LOG(LogRoute, LogRoute::class, "Log", R.drawable.ic_medication, R.drawable.ic_medication_filled),
    SCORE(ScoreRoute, ScoreRoute::class, "Score", R.drawable.ic_mood, R.drawable.ic_mood_filled),
    REPORTS(ReportsRoute, ReportsRoute::class, "Reports", R.drawable.ic_analytics, R.drawable.ic_analytics_filled),
    MANAGE(ManageRoute, ManageRoute::class, "Manage", R.drawable.ic_list, R.drawable.ic_list_filled),
}
