package com.example.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ExpenseEntity
import com.example.data.local.GroupEntity
import com.example.data.local.GroupMemberEntity
import com.example.data.local.SettlementEntity
import com.example.domain.calculator.ExpenseCalculator
import com.example.util.FilterChipBar
import com.example.util.TimeFilter
import com.example.util.isTimestampInFilter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailsScreen(
    group: GroupEntity?,
    expenses: List<ExpenseEntity>,
    members: List<GroupMemberEntity>,
    settlements: List<SettlementEntity>,
    onAddExpense: () -> Unit,
    onAddSettlement: (payerId: String?, receiverId: String?, amount: Long?) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit,
    onDeleteSettlement: (SettlementEntity) -> Unit,
    onRefresh: (onComplete: () -> Unit) -> Unit = { it() },
    onBack: () -> Unit
) {
    val tabs = listOf("Overview", "Expenses", "Members", "Balances", "Activity", "Settlements")
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val coroutineScope = rememberCoroutineScope()
    var expenseToDelete by remember { mutableStateOf<ExpenseEntity?>(null) }
    var settlementToDelete by remember { mutableStateOf<SettlementEntity?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.groupName ?: "Group Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(2.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        IconButton(onClick = {
                            isRefreshing = true
                            onRefresh {
                                isRefreshing = false
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Sync Group Data")
                        }
                    }
                    IconButton(onClick = { onAddSettlement(null, null, null) }) {
                        Icon(imageVector = Icons.Default.Payment, contentDescription = "Settle Up")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (pagerState.currentPage == 5) {
                        onAddSettlement(null, null, null)
                    } else {
                        onAddExpense()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = if (pagerState.currentPage == 5) Icons.Default.Handshake else Icons.Default.Add,
                    contentDescription = if (pagerState.currentPage == 5) "Record Settlement" else "Add Expense"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {},
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[pagerState.currentPage])
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            height = 3.dp
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    val selected = pagerState.currentPage == index
                    Tab(
                        selected = selected,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        modifier = Modifier
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            // Always ensure ADMIN members appear at the top of member lists
            val distinctMembers = remember(members) {
                members.distinctBy { it.userId.ifBlank { it.membershipId } }
                    .sortedWith(
                        compareByDescending<GroupMemberEntity> {
                            it.role.equals("ADMIN", ignoreCase = true) || it.role.equals("CREATOR", ignoreCase = true)
                        }.thenBy { it.userName.lowercase() }
                    )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> OverviewTab(
                        group = group,
                        expenses = expenses,
                        members = distinctMembers,
                        settlements = settlements,
                        onDelete = { expenseToDelete = it },
                        onNavigateToSettlements = {
                            coroutineScope.launch { pagerState.animateScrollToPage(5) }
                        },
                        onNavigateToBalances = {
                            coroutineScope.launch { pagerState.animateScrollToPage(3) }
                        }
                    )
                    1 -> ExpensesTab(
                        expenses = expenses,
                        onDelete = { expenseToDelete = it }
                    )
                    2 -> MembersTab(distinctMembers)
                    3 -> BalancesTab(
                        expenses = expenses,
                        members = distinctMembers,
                        settlements = settlements,
                        onSettleUp = { pId, rId, amt ->
                            onAddSettlement(pId, rId, amt)
                        }
                    )
                    4 -> ActivityTab(
                        expenses = expenses,
                        settlements = settlements
                    )
                    5 -> SettlementsTab(
                        settlements = settlements,
                        onDeleteSettlement = { settlementToDelete = it },
                        onRecordSettlement = { onAddSettlement(null, null, null) }
                    )
                }
            }
        }
    }

    // Delete Expense Dialog
    if (expenseToDelete != null) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) expenseToDelete = null },
            title = { Text("Delete Expense?") },
            text = {
                val exp = expenseToDelete
                if (exp != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Are you sure you want to delete '${exp.title}' (₹${exp.totalAmountInMinorUnits})?")
                        Text(
                            "Paid by: ${exp.paidByName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "⚠️ Notice: If you delete another member's or admin's expense, a personal warning notification with your name and details will be sent directly to their phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text("Are you sure you want to delete this expense?")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        expenseToDelete?.let { exp ->
                            isDeleting = true
                            onDeleteExpense(exp)
                            isDeleting = false
                            expenseToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Delete Expense")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { expenseToDelete = null },
                    enabled = !isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Settlement Dialog
    if (settlementToDelete != null) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) settlementToDelete = null },
            title = { Text("Delete Settlement Record?") },
            text = {
                val set = settlementToDelete
                if (set != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Are you sure you want to delete this settlement of ₹${set.amountInMinorUnits}?")
                        Text(
                            "From ${set.payerName} to ${set.receiverName} via ${set.paymentMethod}.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Deleting this will restore the previous pending debt between both members.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text("Are you sure you want to delete this settlement?")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        settlementToDelete?.let { set ->
                            isDeleting = true
                            onDeleteSettlement(set)
                            isDeleting = false
                            settlementToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Delete Settlement")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { settlementToDelete = null },
                    enabled = !isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun OverviewTab(
    group: GroupEntity?,
    expenses: List<ExpenseEntity>,
    members: List<GroupMemberEntity>,
    settlements: List<SettlementEntity>,
    onDelete: (ExpenseEntity) -> Unit,
    onNavigateToSettlements: () -> Unit,
    onNavigateToBalances: () -> Unit
) {
    val totalSpending = expenses.sumOf { it.totalAmountInMinorUnits }
    val totalSettled = settlements.sumOf { it.amountInMinorUnits }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "Total Group Spending", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(text = "₹$totalSpending", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Room Code: ${group?.roomCode ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(text = "Members: ${members.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        // Quick Stats row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Settlements stat card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onNavigateToSettlements() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Handshake, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            Text("Settled", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text("₹$totalSettled", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        Text("${settlements.size} payments", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Balances stat card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onNavigateToBalances() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("Balances", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text("${members.size} Members", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("View debt summary", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item {
            Text(text = "Recent Expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        if (expenses.isEmpty()) {
            item {
                Text("No expenses recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(expenses.take(5)) { expense ->
                ExpenseCardItem(expense = expense, onDelete = { onDelete(expense) })
            }
        }
    }
}

@Composable
fun ExpensesTab(expenses: List<ExpenseEntity>, onDelete: (ExpenseEntity) -> Unit) {
    var selectedFilter by remember { mutableStateOf(TimeFilter.ALL) }

    val filteredExpenses = remember(expenses, selectedFilter) {
        expenses.filter { exp ->
            isTimestampInFilter(exp.expenseDate, selectedFilter)
        }.sortedWith(compareByDescending<ExpenseEntity> { it.expenseDate }.thenByDescending { it.createdAt })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FilterChipBar(selectedFilter = selectedFilter, onFilterSelected = { selectedFilter = it })
        }

        if (filteredExpenses.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No expenses found for ${selectedFilter.label.lowercase()}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(filteredExpenses, key = { it.expenseId }) { expense ->
                ExpenseCardItem(expense = expense, onDelete = { onDelete(expense) })
            }
        }
    }
}

@Composable
fun ExpenseCardItem(expense: ExpenseEntity, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val formattedDate = remember(expense.expenseDate) {
        dateFormat.format(Date(expense.expenseDate))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                }
                Column {
                    Text(text = expense.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Paid by ${expense.paidByName} • ${expense.categoryName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "₹${expense.totalAmountInMinorUnits}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun MembersTab(members: List<GroupMemberEntity>) {
    val sortedMembers = remember(members) {
        members.sortedWith(
            compareByDescending<GroupMemberEntity> {
                it.role.equals("ADMIN", ignoreCase = true) || it.role.equals("CREATOR", ignoreCase = true)
            }.thenBy { it.userName.lowercase() }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sortedMembers, key = { it.userId.ifBlank { it.membershipId } }) { member ->
            val isAdmin = member.role.equals("ADMIN", ignoreCase = true) || member.role.equals("CREATOR", ignoreCase = true)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isAdmin) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isAdmin) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.secondaryContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = member.userName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = if (isAdmin) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 16.sp
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = member.userName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isAdmin) {
                                    Icon(
                                        imageVector = Icons.Default.AdminPanelSettings,
                                        contentDescription = "Admin",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Role: ${if (isAdmin) "ADMIN" else member.role.uppercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isAdmin) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }

                    if (isAdmin) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Admin",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BalancesTab(
    expenses: List<ExpenseEntity>,
    members: List<GroupMemberEntity>,
    settlements: List<SettlementEntity>,
    onSettleUp: (payerId: String, receiverId: String, amount: Long) -> Unit
) {
    // Calculate net balances per member taking expenses AND settlements into account
    val paidMap = mutableMapOf<String, Long>()
    val shareMap = mutableMapOf<String, Long>()
    val settlementsPaidMap = mutableMapOf<String, Long>()
    val settlementsReceivedMap = mutableMapOf<String, Long>()

    members.forEach { m ->
        paidMap[m.userId] = 0L
        shareMap[m.userId] = 0L
        settlementsPaidMap[m.userId] = 0L
        settlementsReceivedMap[m.userId] = 0L
    }

    expenses.forEach { exp ->
        val paid = paidMap[exp.paidByUserId] ?: 0L
        paidMap[exp.paidByUserId] = paid + exp.totalAmountInMinorUnits

        // Equal split among members
        if (members.isNotEmpty()) {
            val splitShare = exp.totalAmountInMinorUnits / members.size
            members.forEach { m ->
                val s = shareMap[m.userId] ?: 0L
                shareMap[m.userId] = s + splitShare
            }
        }
    }

    settlements.forEach { set ->
        val sp = settlementsPaidMap[set.payerUserId] ?: 0L
        settlementsPaidMap[set.payerUserId] = sp + set.amountInMinorUnits

        val sr = settlementsReceivedMap[set.receiverUserId] ?: 0L
        settlementsReceivedMap[set.receiverUserId] = sr + set.amountInMinorUnits
    }

    val netBalancesMap = remember(members, expenses, settlements) {
        val map = mutableMapOf<String, Long>()
        members.forEach { m ->
            val p = paidMap[m.userId] ?: 0L
            val sp = settlementsPaidMap[m.userId] ?: 0L
            val s = shareMap[m.userId] ?: 0L
            val sr = settlementsReceivedMap[m.userId] ?: 0L
            map[m.userId] = (p + sp) - (s + sr)
        }
        map
    }

    val simplifiedDebts = remember(netBalancesMap) {
        ExpenseCalculator.simplifyDebts(netBalancesMap)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Suggested Settlements Section (Who pays whom)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (simplifiedDebts.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Handshake,
                            contentDescription = null,
                            tint = if (simplifiedDebts.isEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (simplifiedDebts.isEmpty()) "All Settled Up!" else "Suggested Settlements",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (simplifiedDebts.isEmpty()) {
                        Text(
                            text = "🎉 Every member's balance is currently at ₹0. No pending debts in this room.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Tap 'Settle Up' to record payment and bring balances to ₹0 automatically:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        simplifiedDebts.forEach { debt ->
                            val debtor = members.find { it.userId == debt.fromUserId }?.userName ?: "Member"
                            val creditor = members.find { it.userId == debt.toUserId }?.userName ?: "Member"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "$debtor owes $creditor",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Amount: ₹${debt.amount}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Button(
                                    onClick = { onSettleUp(debt.fromUserId, debt.toUserId, debt.amount) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text("Settle Up", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(text = "Net Balances", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        items(members, key = { it.userId.ifBlank { it.membershipId } }) { member ->
            val net = netBalancesMap[member.userId] ?: 0L
            val paid = paidMap[member.userId] ?: 0L
            val share = shareMap[member.userId] ?: 0L
            val setPaid = settlementsPaidMap[member.userId] ?: 0L
            val setRecv = settlementsReceivedMap[member.userId] ?: 0L

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (net == 0L) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                        else if (net > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = member.userName.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = if (net == 0L) MaterialTheme.colorScheme.secondary
                                            else if (net > 0) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error
                                )
                            }
                            Text(text = member.userName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = when {
                                net == 0L -> "₹0 (Settled Up)"
                                net > 0L -> "+₹$net (Gets back)"
                                else -> "-₹${-net} (Owes)"
                            },
                            fontWeight = FontWeight.Bold,
                            color = when {
                                net == 0L -> MaterialTheme.colorScheme.secondary
                                net > 0L -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }

                    // Breakdown details
                    Text(
                        text = "Expense Paid: ₹$paid • Share: ₹$share",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

sealed class GroupActivityItem {
    abstract val timestamp: Long
    abstract val id: String

    data class ExpenseActivity(val expense: ExpenseEntity) : GroupActivityItem() {
        override val timestamp: Long get() = expense.expenseDate
        override val id: String get() = expense.expenseId
    }

    data class SettlementActivity(val settlement: SettlementEntity) : GroupActivityItem() {
        override val timestamp: Long get() = settlement.settlementDate
        override val id: String get() = settlement.settlementId
    }
}

@Composable
fun ActivityTab(
    expenses: List<ExpenseEntity>,
    settlements: List<SettlementEntity>
) {
    var selectedFilter by remember { mutableStateOf(TimeFilter.ALL) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    val allActivities = remember(expenses, settlements, selectedFilter) {
        val list = mutableListOf<GroupActivityItem>()
        expenses.forEach { list.add(GroupActivityItem.ExpenseActivity(it)) }
        settlements.forEach { list.add(GroupActivityItem.SettlementActivity(it)) }

        list.filter { isTimestampInFilter(it.timestamp, selectedFilter) }
            .sortedByDescending { it.timestamp }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FilterChipBar(selectedFilter = selectedFilter, onFilterSelected = { selectedFilter = it })
        }

        item {
            Text(text = "Activity Timeline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        if (allActivities.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No activities recorded for ${selectedFilter.label.lowercase()}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(allActivities, key = { it.id }) { activity ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (activity) {
                            is GroupActivityItem.ExpenseActivity -> {
                                val exp = activity.expense
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Receipt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Expense: ${exp.title}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "₹${exp.totalAmountInMinorUnits} paid by ${exp.paidByName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = dateFormat.format(Date(exp.expenseDate)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            is GroupActivityItem.SettlementActivity -> {
                                val set = activity.settlement
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Handshake,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Settlement: ${set.payerName} ➔ ${set.receiverName}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "₹${set.amountInMinorUnits} paid via ${set.paymentMethod}" +
                                                (if (set.notes.isNotBlank()) " • \"${set.notes}\"" else ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = dateFormat.format(Date(set.settlementDate)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettlementsTab(
    settlements: List<SettlementEntity>,
    onDeleteSettlement: (SettlementEntity) -> Unit,
    onRecordSettlement: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(TimeFilter.ALL) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    val filteredSettlements = remember(settlements, searchQuery, selectedFilter) {
        settlements.filter { set ->
            val matchesFilter = isTimestampInFilter(set.settlementDate, selectedFilter)
            val query = searchQuery.trim().lowercase()
            val matchesSearch = query.isEmpty() ||
                set.payerName.lowercase().contains(query) ||
                set.receiverName.lowercase().contains(query) ||
                set.paymentMethod.lowercase().contains(query) ||
                set.notes.lowercase().contains(query) ||
                set.amountInMinorUnits.toString().contains(query)

            matchesFilter && matchesSearch
        }.sortedByDescending { it.settlementDate }
    }

    val totalSettledAmount = remember(filteredSettlements) {
        filteredSettlements.sumOf { it.amountInMinorUnits }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search settlements...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
        }

        // Time Filters
        item {
            FilterChipBar(selectedFilter = selectedFilter, onFilterSelected = { selectedFilter = it })
        }

        // Summary Stats Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Handshake,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (selectedFilter == TimeFilter.ALL) "Total Settled History" else "Settled History (${selectedFilter.label})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₹$totalSettledAmount",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${filteredSettlements.size} Completed",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        if (filteredSettlements.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Handshake,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No settlements matching \"$searchQuery\""
                               else "No settlement history recorded yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = onRecordSettlement,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Record First Settlement")
                    }
                }
            }
        } else {
            items(filteredSettlements, key = { it.settlementId }) { settlement ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header: Transfer Flow & Amount
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.errorContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = settlement.payerName.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Text(
                                    text = settlement.payerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = settlement.receiverName.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = settlement.receiverName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "₹${settlement.amountInMinorUnits}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        // Method Badge, Date, Comment
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Payment,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = settlement.paymentMethod,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Text(
                                    text = dateFormat.format(Date(settlement.settlementDate)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { onDeleteSettlement(settlement) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Settlement",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Comments / Notes if available
                        if (settlement.notes.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notes,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = settlement.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
