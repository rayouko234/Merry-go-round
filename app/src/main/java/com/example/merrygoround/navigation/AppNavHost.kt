package com.example.merrygoround.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.merrygoround.di.AppContainer
import com.example.merrygoround.di.ViewModels
import com.example.merrygoround.ui.activity.ActivityScreen
import com.example.merrygoround.ui.auth.LoginScreen
import com.example.merrygoround.ui.auth.RegisterScreen
import com.example.merrygoround.ui.contributions.ContributeScreen
import com.example.merrygoround.ui.dashboard.DashboardScreen
import com.example.merrygoround.ui.groups.CreateGroupScreen
import com.example.merrygoround.ui.groups.GroupDetailScreen
import com.example.merrygoround.ui.groups.GroupsScreen
import com.example.merrygoround.ui.groups.JoinGroupScreen
import com.example.merrygoround.ui.payouts.PayoutScheduleScreen
import com.example.merrygoround.ui.profile.NotificationsScreen
import com.example.merrygoround.ui.profile.ProfileScreen
import com.example.merrygoround.ui.welcome.WelcomeScreen

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "Home", Icons.Filled.Home),
    BottomTab(Routes.GROUPS, "Groups", Icons.Filled.Savings),
    BottomTab(Routes.ACTIVITY, "Activity", Icons.Filled.List),
    BottomTab(Routes.PROFILE, "Profile", Icons.Filled.Person)
)

/**
 * Single navigation graph (brief §7). The start destination is decided once from
 * the persisted session; authenticated destinations get the bottom navigation bar.
 */
@Composable
fun AppNavHost(container: AppContainer, loggedIn: Boolean) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.bottomBarDestinations

    fun navigateToTop(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun logoutToWelcome() {
        navController.navigate(Routes.WELCOME) {
            popUpTo(0) { inclusive = true }
        }
    }

    val startDestination = if (loggedIn) Routes.HOME else Routes.WELCOME

    Scaffold(
        // Screens own their insets; the root only positions the bottom bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navigateToTop(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.WELCOME) {
                WelcomeScreen(
                    onGetStarted = { navController.navigate(Routes.REGISTER) },
                    onLogin = { navController.navigate(Routes.LOGIN) }
                )
            }

            composable(Routes.REGISTER) {
                val vm: com.example.merrygoround.ui.auth.AuthViewModel =
                    viewModel(factory = ViewModels.auth(container))
                val state by vm.uiState.collectAsStateWithLifecycle()
                RegisterScreen(
                    state = state,
                    onRegister = vm::register,
                    onBack = { navController.popBackStack() },
                    onGoToLogin = {
                        navController.navigate(Routes.LOGIN) { launchSingleTop = true }
                    },
                    onRegistered = {
                        vm.resetSuccess()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.LOGIN) {
                val vm: com.example.merrygoround.ui.auth.AuthViewModel =
                    viewModel(factory = ViewModels.auth(container))
                val state by vm.uiState.collectAsStateWithLifecycle()
                LoginScreen(
                    state = state,
                    onLogin = vm::login,
                    onBack = { navController.popBackStack() },
                    onGoToRegister = {
                        navController.navigate(Routes.REGISTER) { launchSingleTop = true }
                    },
                    onLoggedIn = {
                        vm.resetSuccess()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.HOME) {
                val vm: com.example.merrygoround.ui.dashboard.DashboardViewModel =
                    viewModel(factory = ViewModels.dashboard(container))
                val state by vm.uiState.collectAsStateWithLifecycle()
                DashboardScreen(
                    state = state,
                    onRefresh = vm::refresh,
                    onSelectGroup = vm::selectGroup,
                    onContribute = { navController.navigate(Routes.contribute(it)) },
                    onOpenGroup = { navController.navigate(Routes.groupDetail(it)) },
                    onCreateGroup = { navController.navigate(Routes.CREATE_GROUP) },
                    onJoinGroup = { navController.navigate(Routes.JOIN_GROUP) },
                    onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) }
                )
            }

            composable(Routes.GROUPS) {
                val vm: com.example.merrygoround.ui.groups.GroupsViewModel =
                    viewModel(factory = ViewModels.groups(container))
                val state by vm.uiState.collectAsStateWithLifecycle()
                GroupsScreen(
                    state = state,
                    onRefresh = vm::refresh,
                    onOpenGroup = { navController.navigate(Routes.groupDetail(it)) },
                    onCreateGroup = { navController.navigate(Routes.CREATE_GROUP) },
                    onJoinGroup = { navController.navigate(Routes.JOIN_GROUP) }
                )
            }

            composable(Routes.ACTIVITY) {
                val vm: com.example.merrygoround.ui.activity.ActivityViewModel =
                    viewModel(factory = ViewModels.activity(container))
                val state by vm.uiState.collectAsStateWithLifecycle()
                ActivityScreen(
                    state = state,
                    onRefresh = vm::refresh,
                    onSetTab = vm::setTab,
                    onSetGroupFilter = vm::setGroupFilter,
                    onSetStatusFilter = vm::setStatusFilter
                )
            }

            composable(Routes.PROFILE) {
                val vm: com.example.merrygoround.ui.profile.ProfileViewModel =
                    viewModel(factory = ViewModels.profile(container))
                val state by vm.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.loggedOut) {
                    if (state.loggedOut) {
                        vm.consumeLogout()
                        logoutToWelcome()
                    }
                }
                ProfileScreen(
                    state = state,
                    onRefresh = vm::refresh,
                    onSave = vm::saveProfile,
                    onToggleNotifications = vm::toggleNotifications,
                    onLogout = vm::logout,
                    onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) }
                )
            }
            composable(Routes.CREATE_GROUP) {
                val vm: com.example.merrygoround.ui.groups.CreateGroupViewModel =
                    viewModel(factory = ViewModels.createGroup(container))
                val state by vm.uiState.collectAsStateWithLifecycle()
                CreateGroupScreen(
                    state = state,
                    onCreate = vm::create,
                    onBack = { navController.popBackStack() },
                    onCreated = { groupId ->
                        vm.consumeCreated()
                        navController.navigate(Routes.groupDetail(groupId)) {
                            popUpTo(Routes.GROUPS)
                        }
                    }
                )
            }

            composable(Routes.JOIN_GROUP) {
                val vm: com.example.merrygoround.ui.groups.JoinGroupViewModel =
                    viewModel(factory = ViewModels.joinGroup(container))
                val state by vm.uiState.collectAsStateWithLifecycle()
                JoinGroupScreen(
                    state = state,
                    onJoin = vm::join,
                    onBack = { navController.popBackStack() },
                    onJoined = { groupId ->
                        vm.consumeJoined()
                        navController.navigate(Routes.groupDetail(groupId)) {
                            popUpTo(Routes.GROUPS)
                        }
                    }
                )
            }

            composable(
                route = Routes.GROUP_DETAIL,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) { entry ->
                val groupId = entry.arguments?.getString("groupId") ?: return@composable
                val vm: com.example.merrygoround.ui.groups.GroupDetailViewModel =
                    viewModel(factory = ViewModels.groupDetail(container))
                val state by vm.uiState.collectAsStateWithLifecycle()
                val myUserId by container.sessionStore.currentUserId
                    .collectAsStateWithLifecycle(initialValue = null)
                androidx.compose.runtime.DisposableEffect(groupId, myUserId) {
                    vm.load(groupId, myUserId = myUserId)
                    onDispose { }
                }
                GroupDetailScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onRefresh = vm::refresh,
                    onDecide = vm::decide,
                    onRemove = vm::removeMember,
                    onStartGroup = vm::startGroup,
                    onMoveMember = vm::moveMember,
                    onOpenSchedule = { navController.navigate(Routes.payoutSchedule(groupId)) },
                    onOpenContribute = { navController.navigate(Routes.contribute(groupId)) }
                )
            }
            composable(
                route = Routes.CONTRIBUTE,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) { entry ->
                val groupId = entry.arguments?.getString("groupId") ?: return@composable
                val vm: com.example.merrygoround.ui.contributions.ContributeViewModel =
                    viewModel(factory = ViewModels.contribute(container))
                val state by vm.uiState.collectAsStateWithLifecycle()
                androidx.compose.runtime.DisposableEffect(groupId) {
                    vm.load(groupId)
                    onDispose { }
                }
                ContributeScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onRefresh = vm::refresh,
                    onAmountChange = vm::onAmountChange,
                    onNoteChange = vm::onNoteChange,
                    onSubmit = vm::submit
                )
            }

            composable(
                route = Routes.PAYOUT_SCHEDULE,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) { entry ->
                val groupId = entry.arguments?.getString("groupId") ?: return@composable
                val vm: com.example.merrygoround.ui.payouts.PayoutScheduleViewModel =
                    viewModel(factory = ViewModels.payoutSchedule(container))
                val state by vm.uiState.collectAsStateWithLifecycle()
                val myUserId by container.sessionStore.currentUserId
                    .collectAsStateWithLifecycle(initialValue = null)
                androidx.compose.runtime.DisposableEffect(groupId, myUserId) {
                    vm.load(groupId, myUserId)
                    onDispose { }
                }
                PayoutScheduleScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onRefresh = vm::refresh,
                    onCompletePayout = vm::completePayout
                )
            }

            composable(Routes.NOTIFICATIONS) {
                val vm: com.example.merrygoround.ui.profile.NotificationsViewModel =
                    viewModel(factory = ViewModels.notifications(container))
                val state by vm.uiState.collectAsStateWithLifecycle()
                NotificationsScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onRefresh = vm::refresh
                )
            }
        }
    }
}
