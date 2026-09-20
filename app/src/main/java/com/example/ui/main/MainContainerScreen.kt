package com.example.ui.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.GroupEntity
import com.example.data.local.PersonalExpenseEntity
import com.example.ui.home.HomeScreen
import com.example.ui.personal.PersonalExpensesScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.reports.ReportsScreen
import com.example.viewmodel.ExpenseViewModel
import kotlinx.coroutines.launch

@Composable
fun MainContainerScreen(
    viewModel: ExpenseViewModel,
    groups: List<GroupEntity>,
    personalExpenses: List<PersonalExpenseEntity>,
    themeMode: String,
    currency: String,
    isSyncing: Boolean,
    onNavigateToCreateRoom: () -> Unit,
    onNavigateToJoinRoom: () -> Unit,
    onNavigateToGroupDetails: (String) -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToAddPersonalExpense: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    val coroutineScope = rememberCoroutineScope()

    val allGroupExpenses by viewModel.allGroupExpenses.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(0)
                        }
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Personal") },
                    label = { Text("Personal") }
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 2,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(2)
                        }
                    },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Reports") },
                    label = { Text("Reports") }
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 3,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(3)
                        }
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Profile") },
                    label = { Text("Profile") }
                )
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    userName = viewModel.currentUserName,
                    currentUserId = viewModel.currentUserId,
                    groups = groups,
                    isSyncing = isSyncing,
                    onRefresh = { viewModel.refreshData() },
                    onCreateRoom = onNavigateToCreateRoom,
                    onJoinRoom = onNavigateToJoinRoom,
                    onGroupClick = onNavigateToGroupDetails,
                    onEditGroup = { groupId, name, desc, currency, max, onComplete ->
                        viewModel.updateGroup(groupId, name, desc, currency, max, onComplete)
                    },
                    onDeleteGroup = { groupId, name, onComplete ->
                        viewModel.requestDeleteGroup(groupId, name, onComplete)
                    },
                    onNotificationsClick = onNavigateToNotifications,
                    onSettingsClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(3)
                        }
                    }
                )
                1 -> PersonalExpensesScreen(
                    personalExpenses = personalExpenses,
                    onNavigateToAddExpense = onNavigateToAddPersonalExpense,
                    onDeleteExpense = { viewModel.deletePersonalExpense(it) }
                )
                2 -> ReportsScreen(
                    groupExpenses = allGroupExpenses,
                    personalExpenses = personalExpenses
                )
                3 -> ProfileScreen(
                    userName = viewModel.currentUserName,
                    userEmail = viewModel.currentUserEmail,
                    currentTheme = themeMode,
                    currentCurrency = currency,
                    onThemeChange = { newTheme ->
                        viewModel.setThemeMode(newTheme)
                    },
                    onCurrencyChange = { newCurrency ->
                        viewModel.setCurrency(newCurrency)
                    },
                    onUpdateProfile = { newName, cb ->
                        viewModel.updateProfile(newName, cb)
                    },
                    onLogout = {
                        viewModel.logout()
                        onNavigateToLogin()
                    }
                )
            }
        }
    }
}
