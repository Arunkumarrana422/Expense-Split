package com.example.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.RegisterScreen
import com.example.ui.auth.SplashOnboardingScreen
import com.example.ui.group.AddExpenseScreen
import com.example.ui.group.GroupDetailsScreen
import com.example.ui.home.HomeScreen
import com.example.ui.notifications.NotificationsScreen
import com.example.ui.personal.AddPersonalExpenseScreen
import com.example.ui.personal.PersonalExpensesScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.reports.ReportsScreen
import com.example.ui.room.CreateRoomScreen
import com.example.ui.room.JoinRoomScreen
import com.example.ui.search.SearchScreen
import com.example.ui.settlement.SettlementScreen
import com.example.viewmodel.ExpenseViewModel

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object CreateRoom : Screen("create_room")
    object JoinRoom : Screen("join_room")
    object GroupDetails : Screen("group_details/{groupId}") {
        fun createRoute(groupId: String) = "group_details/$groupId"
    }
    object AddExpense : Screen("add_expense/{groupId}") {
        fun createRoute(groupId: String) = "add_expense/$groupId"
    }
    object Settlement : Screen("settlement/{groupId}") {
        fun createRoute(groupId: String) = "settlement/$groupId"
    }
    object Personal : Screen("personal")
    object AddPersonalExpense : Screen("add_personal_expense")
    object Reports : Screen("reports")
    object Notifications : Screen("notifications")
    object Profile : Screen("profile")
    object Search : Screen("search")
}

@Composable
fun AppNavGraph(viewModel: ExpenseViewModel) {
    val navController = rememberNavController()
    val groups by viewModel.allGroups.collectAsStateWithLifecycle()
    val personalExpenses by viewModel.personalExpenses.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Personal.route,
        Screen.Reports.route,
        Screen.Profile.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Screen.Home.route,
                        onClick = {
                            if (currentRoute != Screen.Home.route) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Personal.route,
                        onClick = {
                            if (currentRoute != Screen.Personal.route) {
                                navController.navigate(Screen.Personal.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Personal") },
                        label = { Text("Personal") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Reports.route,
                        onClick = {
                            if (currentRoute != Screen.Reports.route) {
                                navController.navigate(Screen.Reports.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = "Reports") },
                        label = { Text("Reports") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Profile.route,
                        onClick = {
                            if (currentRoute != Screen.Profile.route) {
                                navController.navigate(Screen.Profile.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Profile") },
                        label = { Text("Profile") }
                    )
                }
            }
        }
    ) { padding ->
        val startDest = if (viewModel.isLoggedIn) Screen.Home.route else Screen.Splash.route
        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable(Screen.Splash.route) {
                SplashOnboardingScreen(
                    onGetStarted = { navController.navigate(Screen.Register.route) },
                    onLogin = { navController.navigate(Screen.Login.route) }
                )
            }
            composable(Screen.Login.route) {
                LoginScreen(
                    onLogin = { email, password, callback ->
                        viewModel.login(email, password) { result ->
                            callback(result)
                            if (result.isSuccess) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            }
                        }
                    },
                    onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                    onForgotPassword = {}
                )
            }
            composable(Screen.Register.route) {
                RegisterScreen(
                    onRegister = { fullName, email, password, callback ->
                        viewModel.register(fullName, email, password) { result ->
                            callback(result)
                            if (result.isSuccess) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            }
                        }
                    },
                    onBackToLogin = { navController.popBackStack() }
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    userName = viewModel.currentUserName,
                    groups = groups,
                    isSyncing = isSyncing,
                    onRefresh = { viewModel.refreshData() },
                    onCreateRoom = { navController.navigate(Screen.CreateRoom.route) },
                    onJoinRoom = { navController.navigate(Screen.JoinRoom.route) },
                    onGroupClick = { groupId -> navController.navigate(Screen.GroupDetails.createRoute(groupId)) },
                    onNotificationsClick = { navController.navigate(Screen.Notifications.route) },
                    onSettingsClick = { navController.navigate(Screen.Profile.route) }
                )
            }
            composable(Screen.CreateRoom.route) {
                CreateRoomScreen(
                    onCreate = { name, desc, currency, max ->
                        viewModel.createGroup(name, desc, currency, max) { roomCode ->
                            navController.popBackStack()
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.JoinRoom.route) {
                var joinError by remember { mutableStateOf<String?>(null) }
                JoinRoomScreen(
                    errorMessage = joinError,
                    onJoin = { code ->
                        viewModel.joinGroup(code) { result ->
                            if (result.isSuccess) {
                                navController.popBackStack()
                            } else {
                                joinError = result.exceptionOrNull()?.message ?: "Failed to join room"
                            }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.GroupDetails.route) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
                val group = groups.find { it.groupId == groupId }
                val expenses by viewModel.getExpensesForGroup(groupId).collectAsStateWithLifecycle(initialValue = emptyList())
                val members by viewModel.getMembersForGroup(groupId).collectAsStateWithLifecycle(initialValue = emptyList())
                val settlements by viewModel.getSettlementsForGroup(groupId).collectAsStateWithLifecycle(initialValue = emptyList())

                GroupDetailsScreen(
                    group = group,
                    expenses = expenses,
                    members = members,
                    settlements = settlements,
                    onAddExpense = { navController.navigate(Screen.AddExpense.createRoute(groupId)) },
                    onAddSettlement = { navController.navigate(Screen.Settlement.createRoute(groupId)) },
                    onDeleteExpense = { exp -> viewModel.deleteExpense(exp, group?.groupName ?: "") },
                    onRefresh = { viewModel.refreshData() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.AddExpense.route) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
                val group = groups.find { it.groupId == groupId }
                AddExpenseScreen(
                    groupId = groupId,
                    onSave = { title, amount, currency, category, split ->
                        viewModel.addExpense(groupId, title, amount, currency, category, split, "", group?.groupName ?: "") {
                            navController.popBackStack()
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Settlement.route) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
                SettlementScreen(
                    onRecordSettlement = { receiverId, receiverName, amount, method, notes ->
                        viewModel.addSettlement(groupId, receiverId, receiverName, amount, method, notes) {
                            navController.popBackStack()
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Personal.route) {
                PersonalExpensesScreen(
                    personalExpenses = personalExpenses,
                    onNavigateToAddExpense = { navController.navigate(Screen.AddPersonalExpense.route) },
                    onDeleteExpense = { id -> viewModel.deletePersonalExpense(id) }
                )
            }
            composable(Screen.AddPersonalExpense.route) {
                AddPersonalExpenseScreen(
                    onSave = { title, amount, category, notes ->
                        viewModel.addPersonalExpense(title, amount, category, notes) {
                            navController.popBackStack()
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Reports.route) {
                val groupExpenses by viewModel.allGroupExpenses.collectAsStateWithLifecycle()
                val personalExpenses by viewModel.personalExpenses.collectAsStateWithLifecycle()
                ReportsScreen(
                    groupExpenses = groupExpenses,
                    personalExpenses = personalExpenses
                )
            }
            composable(Screen.Notifications.route) {
                val notifications by viewModel.notifications.collectAsStateWithLifecycle()
                NotificationsScreen(
                    notifications = notifications,
                    onClearAll = { viewModel.clearNotifications() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    userName = viewModel.currentUserName,
                    userEmail = viewModel.currentUserEmail,
                    currentTheme = themeMode,
                    currentCurrency = currency,
                    onThemeChange = { viewModel.setThemeMode(it) },
                    onCurrencyChange = { viewModel.setCurrency(it) },
                    onUpdateProfile = { newName, cb ->
                        viewModel.updateProfile(newName, cb)
                    },
                    onLogout = {
                        viewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
