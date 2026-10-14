package de.klimakompendium.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.klimakompendium.ui.helper.HelperScreen
import de.klimakompendium.ui.home.HomeScreen
import de.klimakompendium.ui.plants.ControllerEditScreen
import de.klimakompendium.ui.plants.PlantDetailScreen
import de.klimakompendium.ui.plants.PlantEditScreen
import de.klimakompendium.ui.plants.PlantsScreen
import de.klimakompendium.ui.settings.SettingsScreen
import de.klimakompendium.ui.terms.TermDetailScreen
import de.klimakompendium.ui.terms.TermEditScreen
import de.klimakompendium.ui.terms.TermsScreen

object Routes {
    const val HOME = "home"
    const val TERMS = "terms"
    const val HELPER = "helper"
    const val PLANTS = "plants"
    const val SETTINGS = "settings"

    const val HELPER_PATTERN = "helper?tab={tab}&ref={ref}"
    const val TERM_DETAIL = "term/{id}"
    const val TERM_EDIT = "termEdit/{id}"
    const val PLANT_DETAIL = "plant/{id}"
    const val PLANT_EDIT = "plantEdit/{id}"
    const val CONTROLLER_EDIT = "controllerEdit/{plantId}/{id}"

    fun helper(tab: String = "explain", ref: String = "") = "helper?tab=$tab&ref=$ref"
    fun termDetail(id: Long) = "term/$id"
    fun termEdit(id: Long) = "termEdit/$id"
    fun plantDetail(id: Long) = "plant/$id"
    fun plantEdit(id: Long) = "plantEdit/$id"
    fun controllerEdit(plantId: Long, id: Long) = "controllerEdit/$plantId/$id"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, "Start", Icons.Default.Home),
    TopLevel(Routes.TERMS, "Begriffe", Icons.Default.Menu),
    TopLevel(Routes.HELPER, "Druck/Temp", Icons.Default.Thermostat),
    TopLevel(Routes.PLANTS, "Anlagen", Icons.Default.Build),
)

@Composable
fun AppNav(nav: NavHostController = rememberNavController()) {
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = destination?.route?.let { r -> topLevel.any { r == it.route || r.startsWith("${it.route}?") } } ?: true

    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar {
                topLevel.forEach { item ->
                    val selected = destination?.hierarchy?.any { it.route == item.route || it.route?.startsWith("${item.route}?") == true } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(item.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenTerm = { nav.navigate(Routes.termDetail(it)) },
                    onOpenPlant = { nav.navigate(Routes.plantDetail(it)) },
                    onOpenRefrigerant = { nav.navigate(Routes.helper("pt", it)) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.TERMS) {
                TermsScreen(onOpenTerm = { nav.navigate(Routes.termDetail(it)) }, onNewTerm = { nav.navigate(Routes.termEdit(0)) })
            }
            composable(
                Routes.HELPER_PATTERN,
                arguments = listOf(
                    navArgument("tab") { type = NavType.StringType; defaultValue = "explain" },
                    navArgument("ref") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                HelperScreen(e.arguments?.getString("tab") ?: "explain", e.arguments?.getString("ref") ?: "")
            }
            composable(Routes.PLANTS) {
                PlantsScreen(onOpenPlant = { nav.navigate(Routes.plantDetail(it)) }, onNewPlant = { nav.navigate(Routes.plantEdit(0)) })
            }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }

            composable(Routes.TERM_DETAIL, listOf(navArgument("id") { type = NavType.LongType })) { e ->
                TermDetailScreen(
                    id = e.arguments!!.getLong("id"), onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.termEdit(it)) },
                    onOpenRefrigerant = { nav.navigate(Routes.helper("pt", it)) },
                )
            }
            composable(Routes.TERM_EDIT, listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments!!.getLong("id")
                TermEditScreen(
                    id = id, onBack = { nav.popBackStack() },
                    onSaved = { savedId ->
                        if (id == 0L) nav.navigate(Routes.termDetail(savedId)) { popUpTo(Routes.TERM_EDIT) { inclusive = true } }
                        else nav.popBackStack()
                    },
                )
            }
            composable(Routes.PLANT_DETAIL, listOf(navArgument("id") { type = NavType.LongType })) { e ->
                PlantDetailScreen(
                    id = e.arguments!!.getLong("id"), onBack = { nav.popBackStack() },
                    onEditPlant = { nav.navigate(Routes.plantEdit(it)) },
                    onEditController = { pid, cid -> nav.navigate(Routes.controllerEdit(pid, cid)) },
                    onOpenRefrigerant = { nav.navigate(Routes.helper("pt", it)) },
                    onOpenTerm = { nav.navigate(Routes.termDetail(it)) },
                )
            }
            composable(Routes.PLANT_EDIT, listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments!!.getLong("id")
                PlantEditScreen(
                    id = id, onBack = { nav.popBackStack() },
                    onSaved = { savedId ->
                        if (id == 0L) nav.navigate(Routes.plantDetail(savedId)) { popUpTo(Routes.PLANT_EDIT) { inclusive = true } }
                        else nav.popBackStack()
                    },
                )
            }
            composable(
                Routes.CONTROLLER_EDIT,
                listOf(navArgument("plantId") { type = NavType.LongType }, navArgument("id") { type = NavType.LongType }),
            ) { e ->
                ControllerEditScreen(
                    plantId = e.arguments!!.getLong("plantId"), controllerId = e.arguments!!.getLong("id"),
                    onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() },
                )
            }
        }
    }
}
