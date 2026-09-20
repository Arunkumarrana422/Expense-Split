package com.example.ui.settlement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ExpenseEntity
import com.example.data.local.GroupMemberEntity
import com.example.data.local.SettlementEntity
import com.example.domain.calculator.ExpenseCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementScreen(
    currentUserId: String,
    currentUserName: String,
    members: List<GroupMemberEntity>,
    expenses: List<ExpenseEntity>,
    settlements: List<SettlementEntity>,
    initialPayerId: String? = null,
    initialReceiverId: String? = null,
    initialAmount: Long? = null,
    onRecordSettlement: (payerId: String, payerName: String, receiverId: String, receiverName: String, amount: Long, paymentMethod: String, notes: String) -> Unit,
    onBack: () -> Unit
) {
    val distinctMembers = remember(members) {
        members.distinctBy { it.userId.ifBlank { it.membershipId } }
    }

    // Compute net balances for each member
    val memberBalances = remember(distinctMembers, expenses, settlements) {
        val paidMap = mutableMapOf<String, Long>()
        val shareMap = mutableMapOf<String, Long>()
        val settlementsPaidMap = mutableMapOf<String, Long>()
        val settlementsReceivedMap = mutableMapOf<String, Long>()

        distinctMembers.forEach { m ->
            paidMap[m.userId] = 0L
            shareMap[m.userId] = 0L
            settlementsPaidMap[m.userId] = 0L
            settlementsReceivedMap[m.userId] = 0L
        }

        expenses.forEach { exp ->
            paidMap[exp.paidByUserId] = (paidMap[exp.paidByUserId] ?: 0L) + exp.totalAmountInMinorUnits
            if (distinctMembers.isNotEmpty()) {
                val split = exp.totalAmountInMinorUnits / distinctMembers.size
                distinctMembers.forEach { m ->
                    shareMap[m.userId] = (shareMap[m.userId] ?: 0L) + split
                }
            }
        }

        settlements.forEach { set ->
            settlementsPaidMap[set.payerUserId] = (settlementsPaidMap[set.payerUserId] ?: 0L) + set.amountInMinorUnits
            settlementsReceivedMap[set.receiverUserId] = (settlementsReceivedMap[set.receiverUserId] ?: 0L) + set.amountInMinorUnits
        }

        val netMap = mutableMapOf<String, Long>()
        distinctMembers.forEach { m ->
            val p = paidMap[m.userId] ?: 0L
            val sp = settlementsPaidMap[m.userId] ?: 0L
            val s = shareMap[m.userId] ?: 0L
            val sr = settlementsReceivedMap[m.userId] ?: 0L
            netMap[m.userId] = (p + sp) - (s + sr)
        }
        netMap
    }

    // Simplified debts
    val suggestedDebts = remember(memberBalances) {
        ExpenseCalculator.simplifyDebts(memberBalances)
    }

    // Initial deduction logic:
    // If initial params provided, use them.
    // Otherwise, check if current user is debtor or if there are suggested debts.
    val firstSuggestedDebt = remember(suggestedDebts, currentUserId) {
        suggestedDebts.firstOrNull { it.fromUserId == currentUserId }
            ?: suggestedDebts.firstOrNull { it.toUserId == currentUserId }
            ?: suggestedDebts.firstOrNull()
    }

    var selectedPayerId by remember {
        mutableStateOf(
            initialPayerId
                ?: firstSuggestedDebt?.fromUserId
                ?: currentUserId.ifBlank { distinctMembers.firstOrNull()?.userId ?: "" }
        )
    }

    var selectedReceiverId by remember {
        mutableStateOf(
            initialReceiverId
                ?: firstSuggestedDebt?.toUserId
                ?: distinctMembers.firstOrNull { it.userId != selectedPayerId }?.userId
                ?: ""
        )
    }

    var amountStr by remember {
        mutableStateOf(
            initialAmount?.toString()
                ?: firstSuggestedDebt?.amount?.toString()
                ?: ""
        )
    }

    var paymentMethod by remember { mutableStateOf("UPI (Google Pay / PhonePe / Paytm)") }
    var notes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var payerDropdownExpanded by remember { mutableStateOf(false) }
    var receiverDropdownExpanded by remember { mutableStateOf(false) }
    var paymentMethodDropdownExpanded by remember { mutableStateOf(false) }

    val paymentMethods = listOf(
        "UPI (Google Pay / PhonePe / Paytm)",
        "Cash",
        "Bank Transfer (IMPS / NEFT)",
        "Net Banking",
        "Credit / Debit Card",
        "Other"
    )

    val quickNoteChips = listOf(
        "Full Balance Settled",
        "Room Rent Split",
        "Groceries & Food",
        "Utilities & Bills",
        "Monthly Settlement",
        "UPI Transfer"
    )

    val selectedPayer = distinctMembers.find { it.userId == selectedPayerId }
        ?: GroupMemberEntity(userId = currentUserId, userName = currentUserName.ifBlank { "You" })
    val selectedReceiver = distinctMembers.find { it.userId == selectedReceiverId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Record Settlement",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Suggested Auto-fill section if debts exist
            if (suggestedDebts.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Automatic Suggested Settlements",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = "Tap any suggestion below to instantly auto-fill who pays whom and the exact amount:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        suggestedDebts.forEach { debt ->
                            val debtorName = distinctMembers.find { it.userId == debt.fromUserId }?.userName ?: "Debtor"
                            val creditorName = distinctMembers.find { it.userId == debt.toUserId }?.userName ?: "Creditor"
                            val isCurrentSelected = selectedPayerId == debt.fromUserId && selectedReceiverId == debt.toUserId

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedPayerId = debt.fromUserId
                                        selectedReceiverId = debt.toUserId
                                        amountStr = debt.amount.toString()
                                        if (notes.isBlank()) {
                                            notes = "Settled $debtorName to $creditorName balance"
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrentSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCurrentSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = debtorName.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "$debtorName ➔ $creditorName",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Suggested to balance to ₹0",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "₹${debt.amount}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = if (isCurrentSelected) Icons.Default.CheckCircle else Icons.Default.TouchApp,
                                            contentDescription = null,
                                            tint = if (isCurrentSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Visual Summary Card
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
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Settlement Flow",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Payer and Receiver interactive flow
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Payer Box
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Who Paid?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            com.example.ui.common.UserAvatar(
                                userName = selectedPayer?.userName ?: "P",
                                base64Photo = selectedPayer?.profileImage ?: "",
                                size = 46.dp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = selectedPayer?.userName ?: "Payer",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            val payerNet = memberBalances[selectedPayerId] ?: 0L
                            Text(
                                text = if (payerNet < 0) "Owes ₹${-payerNet}" else if (payerNet > 0) "Gets ₹$payerNet" else "₹0",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (payerNet < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            )
                        }

                        // Arrow & Amount
                        Column(
                            modifier = Modifier.weight(1.2f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "₹${amountStr.ifBlank { "0" }}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "via ${paymentMethod.substringBefore(" (")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Receiver Box
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Who Received?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            com.example.ui.common.UserAvatar(
                                userName = selectedReceiver?.userName ?: "R",
                                base64Photo = selectedReceiver?.profileImage ?: "",
                                size = 46.dp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = selectedReceiver?.userName ?: "Receiver",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            val receiverNet = memberBalances[selectedReceiverId] ?: 0L
                            Text(
                                text = if (receiverNet > 0) "Gets ₹$receiverNet" else if (receiverNet < 0) "Owes ₹${-receiverNet}" else "₹0",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (receiverNet > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Recording this settlement will automatically decrease the debt and adjust both members' net balances toward ₹0.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Payer Dropdown Selector
            ExposedDropdownMenuBox(
                expanded = payerDropdownExpanded,
                onExpandedChange = { payerDropdownExpanded = !payerDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedPayer?.userName ?: "Select Payer",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Payer (Who Paid the Money)") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = payerDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(14.dp)
                )
                ExposedDropdownMenu(
                    expanded = payerDropdownExpanded,
                    onDismissRequest = { payerDropdownExpanded = false }
                ) {
                    distinctMembers.forEach { member ->
                        val net = memberBalances[member.userId] ?: 0L
                        val status = if (net < 0) " (Owes ₹${-net})" else if (net > 0) " (Gets back ₹$net)" else " (Settled)"
                        DropdownMenuItem(
                            text = {
                                Text("${member.userName}$status", fontWeight = if (member.userId == selectedPayerId) FontWeight.Bold else FontWeight.Normal)
                            },
                            onClick = {
                                selectedPayerId = member.userId
                                payerDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Receiver Dropdown Selector
            ExposedDropdownMenuBox(
                expanded = receiverDropdownExpanded,
                onExpandedChange = { receiverDropdownExpanded = !receiverDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedReceiver?.userName ?: "Select Receiver",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Receiver (Who Received the Money)") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = receiverDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(14.dp)
                )
                ExposedDropdownMenu(
                    expanded = receiverDropdownExpanded,
                    onDismissRequest = { receiverDropdownExpanded = false }
                ) {
                    distinctMembers.filter { it.userId != selectedPayerId }.forEach { member ->
                        val net = memberBalances[member.userId] ?: 0L
                        val status = if (net > 0) " (Gets back ₹$net)" else if (net < 0) " (Owes ₹${-net})" else " (Settled)"
                        DropdownMenuItem(
                            text = {
                                Text("${member.userName}$status", fontWeight = if (member.userId == selectedReceiverId) FontWeight.Bold else FontWeight.Normal)
                            },
                            onClick = {
                                selectedReceiverId = member.userId
                                receiverDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Settlement Amount Field
            OutlinedTextField(
                value = amountStr,
                onValueChange = {
                    if (it.isEmpty() || it.all { char -> char.isDigit() || char == '.' }) {
                        amountStr = it
                    }
                },
                label = { Text("Settlement Amount (₹)") },
                leadingIcon = {
                    Text(
                        text = "₹",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            // Payment Method Dropdown
            ExposedDropdownMenuBox(
                expanded = paymentMethodDropdownExpanded,
                onExpandedChange = { paymentMethodDropdownExpanded = !paymentMethodDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = paymentMethod,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Payment Method") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Payment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paymentMethodDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(14.dp)
                )
                ExposedDropdownMenu(
                    expanded = paymentMethodDropdownExpanded,
                    onDismissRequest = { paymentMethodDropdownExpanded = false }
                ) {
                    paymentMethods.forEach { method ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val icon = when {
                                        method.contains("UPI") -> Icons.Default.QrCode
                                        method.contains("Cash") -> Icons.Default.Payments
                                        method.contains("Bank") -> Icons.Default.AccountBalance
                                        else -> Icons.Default.CreditCard
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = method,
                                        fontWeight = if (paymentMethod == method) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            },
                            onClick = {
                                paymentMethod = method
                                paymentMethodDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Notes / Comments
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes / Comments (Optional)") },
                placeholder = { Text("e.g. Paid via Google Pay ref #4982") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            // Quick Note Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Quick Comments:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickNoteChips.take(3).forEach { chip ->
                        SuggestionChip(
                            onClick = { notes = chip },
                            label = { Text(chip, fontSize = 12.sp) }
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Confirm Button
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()?.toLong() ?: 0L
                    if (selectedPayerId.isBlank()) {
                        errorMessage = "Please select a payer."
                        return@Button
                    }
                    if (selectedReceiverId.isBlank()) {
                        errorMessage = "Please select a receiver."
                        return@Button
                    }
                    if (selectedPayerId == selectedReceiverId) {
                        errorMessage = "Payer and receiver cannot be the same person."
                        return@Button
                    }
                    if (amt <= 0L) {
                        errorMessage = "Please enter a valid settlement amount greater than 0."
                        return@Button
                    }

                    errorMessage = null
                    isSubmitting = true
                    val pName = selectedPayer?.userName ?: currentUserName.ifBlank { "Member" }
                    val rName = selectedReceiver?.userName ?: "Member"
                    onRecordSettlement(
                        selectedPayerId,
                        pName,
                        selectedReceiverId,
                        rName,
                        amt,
                        paymentMethod,
                        notes
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                        Text("Confirm & Record Settlement", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
