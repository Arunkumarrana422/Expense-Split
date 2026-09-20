package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ExpenseEntity
import com.example.data.local.GroupEntity
import com.example.data.local.GroupMemberEntity
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

    fun login(email: String, password: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repository.login(email, password)
            if (res.isSuccess) {
                loadUserData()
            }
            onResult(res)
        }
    }

    fun register(fullName: String, email: String, password: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repository.register(fullName, email, password)
            if (res.isSuccess) {
                loadUserData()
            }
            onResult(res)
        }
    }

    fun logout() {
        repository.logout()
        loadUserData()
    }

    fun updateProfile(newName: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repository.updateProfile(newName)
            onResult(res)
        }
    }

    val currentUserId: String get() = repository.currentUserId
    val currentUserEmail: String get() = repository.currentUserEmail
    val currentUserName: String get() = repository.currentUserName
    val isLoggedIn: Boolean get() = repository.isLoggedIn

    private val _allGroups = MutableStateFlow<List<GroupEntity>>(emptyList())
    val allGroups: StateFlow<List<GroupEntity>> = _allGroups.asStateFlow()

    private val _allGroupExpenses = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    val allGroupExpenses: StateFlow<List<ExpenseEntity>> = _allGroupExpenses.asStateFlow()

    private val _personalExpenses = MutableStateFlow<List<PersonalExpenseEntity>>(emptyList())
    val personalExpenses: StateFlow<List<PersonalExpenseEntity>> = _personalExpenses.asStateFlow()

    private var groupsJob: Job? = null
    private var groupExpensesJob: Job? = null
    private var personalExpensesJob: Job? = null

    init {
        loadUserData()
    }

    private fun loadUserData() {
        val userId = repository.currentUserId
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

    fun addExpense(groupId: String, title: String, amount: Long, currency: String, category: String, splitMethod: String, notes: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.addExpense(groupId, title, amount, currency, category, splitMethod, notes)
            onComplete()
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            repository.deleteExpense(expenseId)
        }
    }

    fun addSettlement(groupId: String, receiverId: String, receiverName: String, amount: Long, paymentMethod: String, notes: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.addSettlement(groupId, receiverId, receiverName, amount, paymentMethod, notes)
            onComplete()
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
}
