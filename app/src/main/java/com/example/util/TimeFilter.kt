package com.example.util

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

enum class TimeFilter(val label: String) {
    ALL("All"),
    DAILY("Day"),
    WEEKLY("Week"),
    MONTHLY("Month")
}

fun isTimestampInFilter(timestamp: Long, filter: TimeFilter): Boolean {
    if (filter == TimeFilter.ALL) return true
    val itemCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val nowCal = Calendar.getInstance()

    return when (filter) {
        TimeFilter.ALL -> true
        TimeFilter.DAILY -> {
            itemCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
            itemCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
        }
        TimeFilter.WEEKLY -> {
            val diff = nowCal.timeInMillis - timestamp
            diff in 0..(7 * 24 * 60 * 60 * 1000L)
        }
        TimeFilter.MONTHLY -> {
            itemCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
            itemCal.get(Calendar.MONTH) == nowCal.get(Calendar.MONTH)
        }
    }
}

@Composable
fun FilterChipBar(
    selectedFilter: TimeFilter,
    onFilterSelected: (TimeFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(TimeFilter.values()) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.label, fontSize = 13.sp) },
                leadingIcon = if (selectedFilter == filter) {
                    { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}
