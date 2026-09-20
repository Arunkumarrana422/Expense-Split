package com.example.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ExpenseEntity
import com.example.data.local.PersonalExpenseEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    groupExpenses: List<ExpenseEntity>,
    personalExpenses: List<PersonalExpenseEntity>
) {
    data class ReportItem(val title: String, val amount: Long, val category: String, val date: Long)

    val allItems = remember(groupExpenses, personalExpenses) {
        val groupItems = groupExpenses.map { ReportItem(it.title, it.totalAmountInMinorUnits, it.categoryName, it.expenseDate) }
        val personalItems = personalExpenses.map { ReportItem(it.title, it.amount, it.category, it.date) }
        (groupItems + personalItems).sortedByDescending { it.date }
    }

    val totalSpending = allItems.sumOf { it.amount }
    val categoryBreakdown = allItems.groupBy { it.category }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(top = 8.dp, bottom = 0.dp),
                title = { Text("Reports & Analytics") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Total Analytics Spending", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹$totalSpending", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Category Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (allItems.isEmpty()) {
                        Text("No data available for charts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        categoryBreakdown.forEach { (cat, list) ->
                            val catTotal = list.sumOf { it.amount }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(cat, fontWeight = FontWeight.Medium)
                                Text("₹$catTotal", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
