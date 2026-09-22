package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ExpenseEntity
import com.example.data.local.GroupEntity
import com.example.data.local.GroupMemberEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.PersonalExpenseEntity
import com.example.data.local.SettlementEntity
import com.example.data.repository.ExpenseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ExpenseRepository(application)

    private val _themeMode = MutableStateFlow("System Default")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
    }

    private val _currency = MutableStateFlow("INR (₹)")
    val currency: StateFlow<String> = _currency.asStateFlow()

    fun setCurrency(curr: String) {
        _currency.value = curr
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun login(email: String, password: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = repository.login(email, password)
            if (res.isSuccess) {
                // Fully await synchronization so all previous groups, expenses & data are loaded
                val uid = repository.currentUserId
                if (uid.isNotBlank()) {
                    repository.syncDataFromFirebase(uid)
                }
                loadUserData()
            }
            _isSyncing.value = false
            onResult(res)
        }
    }

    fun register(fullName: String, email: String, password: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = repository.register(fullName, email, password)
            if (res.isSuccess) {
                val uid = repository.currentUserId
                if (uid.isNotBlank()) {
                    repository.syncDataFromFirebase(uid)
                }
                loadUserData()
            }
            _isSyncing.value = false
            onResult(res)
        }
    }

    fun refreshData(onComplete: (() -> Unit)? = null) {
        val uid = repository.currentUserId
        if (uid.isNotBlank()) {
            viewModelScope.launch {
                _isSyncing.value = true
                repository.syncDataFromFirebase(uid)
                _isSyncing.value = false
                onComplete?.invoke()
            }
        } else {
            onComplete?.invoke()
        }
    }

    fun logout() {
        repository.logout()
        _allGroups.value = emptyList()
        _allGroupExpenses.value = emptyList()
        _personalExpenses.value = emptyList()
        _notifications.value = emptyList()
        loadUserData()
    }

    fun updateProfile(newName: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repository.updateProfile(newName)
            onResult(res)
        }
    }

    fun updatePassword(oldPassword: String, newPassword: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repository.updatePassword(oldPassword, newPassword)
            onResult(res)
        }
    }

    fun sendPasswordResetEmail(email: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repository.sendPasswordResetEmail(email)
            onResult(res)
        }
    }

    val currentUserId: String get() = repository.currentUserId
    val currentUserEmail: String get() = repository.currentUserEmail
    val currentUserName: String get() = repository.currentUserName
    val userName: StateFlow<String> = repository.userNameFlow.asStateFlow()
    val isLoggedIn: Boolean get() = repository.isLoggedIn

    private val _allGroups = MutableStateFlow<List<GroupEntity>>(emptyList())
    val allGroups: StateFlow<List<GroupEntity>> = _allGroups.asStateFlow()

    private val _allGroupExpenses = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    val allGroupExpenses: StateFlow<List<ExpenseEntity>> = _allGroupExpenses.asStateFlow()

    private val _personalExpenses = MutableStateFlow<List<PersonalExpenseEntity>>(emptyList())
    val personalExpenses: StateFlow<List<PersonalExpenseEntity>> = _personalExpenses.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationEntity>>(emptyList())
    val notifications: StateFlow<List<NotificationEntity>> = _notifications.asStateFlow()

    private var groupsJob: Job? = null
    private var groupExpensesJob: Job? = null
    private var personalExpensesJob: Job? = null
    private var notificationsJob: Job? = null

    init {
        loadUserData()
    }

    private fun loadUserData() {
        val userId = repository.currentUserId
        if (userId.isNotBlank()) {
            viewModelScope.launch {
                repository.syncDataFromFirebase(userId)
            }
            repository.startNotificationListener(userId)
        }
        groupsJob?.cancel()
        groupsJob = viewModelScope.launch {
            repository.getGroupsForUser(userId).collect { groups ->
                _allGroups.value = groups
            }
        }

        groupExpensesJob?.cancel()
        groupExpensesJob = viewModelScope.launch {
            repository.getExpensesForUser(userId).collect { expenses ->
                _allGroupExpenses.value = expenses
            }
        }

        personalExpensesJob?.cancel()
        personalExpensesJob = viewModelScope.launch {
            repository.getPersonalExpenses(userId).collect { expenses ->
                _personalExpenses.value = expenses
            }
        }

        notificationsJob?.cancel()
        notificationsJob = viewModelScope.launch {
            repository.getNotificationsForUser(userId).collect { notifs ->
                _notifications.value = notifs
            }
        }
    }

    fun createGroup(name: String, description: String, currency: String, maxMembers: Int, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val roomCode = repository.createGroup(name, description, currency, maxMembers)
            onResult(roomCode)
        }
    }

    fun joinGroup(roomCode: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val res = repository.joinGroup(roomCode)
            onResult(res)
        }
    }

    fun getExpensesForGroup(groupId: String): Flow<List<ExpenseEntity>> {
        return repository.getExpenses(groupId)
    }

    fun getMembersForGroup(groupId: String): Flow<List<GroupMemberEntity>> {
        return repository.getMembers(groupId)
    }

    fun getSettlementsForGroup(groupId: String): Flow<List<SettlementEntity>> {
        return repository.getSettlements(groupId)
    }

    fun addExpense(
        groupId: String,
        title: String,
        amount: Long,
        currency: String,
        category: String,
        splitMethod: String,
        notes: String,
        groupName: String = "",
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            repository.addExpense(groupId, title, amount, currency, category, splitMethod, notes, groupName)
            onComplete()
        }
    }

    fun deleteExpense(expense: ExpenseEntity, groupName: String = "") {
        viewModelScope.launch {
            repository.deleteExpense(expense, groupName)
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            repository.deleteExpense(expenseId)
        }
    }

    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            repository.deleteNotification(notificationId)
        }
    }

    fun clearNotifications() {
        viewModelScope.launch {
            repository.clearNotifications(currentUserId)
        }
    }

    fun addSettlement(
        groupId: String,
        receiverId: String,
        receiverName: String,
        amount: Long,
        paymentMethod: String,
        notes: String,
        payerId: String = "",
        payerName: String = "",
        groupName: String = "",
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            repository.addSettlement(
                groupId = groupId,
                receiverId = receiverId,
                receiverName = receiverName,
                amount = amount,
                paymentMethod = paymentMethod,
                notes = notes,
                payerId = payerId,
                payerName = payerName,
                groupName = groupName
            )
            onComplete()
        }
    }

    fun deleteSettlement(settlement: SettlementEntity) {
        viewModelScope.launch {
            repository.deleteSettlement(settlement)
        }
    }

    fun addPersonalExpense(title: String, amount: Long, category: String, notes: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.addPersonalExpense(title, amount, category, notes)
            onComplete()
        }
    }

    fun deletePersonalExpense(id: String) {
        viewModelScope.launch {
            repository.deletePersonalExpense(id)
        }
    }

    fun updateGroup(groupId: String, name: String, description: String, currency: String, maxMembers: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.updateGroup(groupId, name, description, currency, maxMembers)
            onComplete()
        }
    }

    fun requestDeleteGroup(groupId: String, groupName: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.requestDeleteGroup(groupId, groupName)
            onComplete()
        }
    }

    val profilePhoto = repository.profilePhotoFlow.asStateFlow()

    fun updateProfilePhoto(base64Str: String) {
        viewModelScope.launch {
            repository.updateProfilePhoto(base64Str)
        }
    }

    fun approveGroupDeletion(groupId: String) {
        viewModelScope.launch {
            repository.approveGroupDeletion(groupId)
        }
    }

    fun rejectGroupDeletion(groupId: String) {
        viewModelScope.launch {
            repository.rejectGroupDeletion(groupId)
        }
    }

    fun clearExpensesForGroup(groupId: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearExpensesForGroup(groupId)
            onComplete()
        }
    }

    fun requestResetCycle(groupId: String, groupName: String, onCodeGenerated: (String) -> Unit) {
        viewModelScope.launch {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            val code = (1..6).map { chars.random() }.joinToString("")
            repository.requestResetCycle(groupId, groupName, code)
            onCodeGenerated(code)
        }
    }

    fun verifyAndClearExpenses(groupId: String, enteredCode: String, expectedCode: String, onSuccess: () -> Unit, onError: () -> Unit) {
        if (enteredCode.trim() == expectedCode.trim() && expectedCode.isNotBlank()) {
            viewModelScope.launch {
                repository.clearExpensesForGroup(groupId)
                onSuccess()
            }
        } else {
            onError()
        }
    }

    fun resetAllUserData(onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = repository.resetAllUserData(currentUserId)
            onComplete(result)
        }
    }

    fun deleteAccount(onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteAccount(currentUserId)
            onComplete(result)
        }
    }
}
