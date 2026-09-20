package com.example.data.repository

import android.content.Context
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.local.ExpenseEntity
import com.example.data.local.GroupEntity
import com.example.data.local.GroupMemberEntity
import com.example.data.local.PersonalExpenseEntity
import com.example.data.local.SettlementEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ExpenseRepository(context: Context) {
    private val database = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "expense_splitter.db"
    ).fallbackToDestructiveMigration().build()

    private val prefs = context.getSharedPreferences("expense_auth_prefs", Context.MODE_PRIVATE)

    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    init {
        try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
        } catch (e: Exception) {
            // Already initialized
        }
        // If not logged in via Firebase Auth, ensure local prefs are completely clean
        if (auth?.currentUser == null) {
            prefs.edit().clear().apply()
        }
    }

    val currentUserId: String
        get() = auth?.currentUser?.uid ?: ""

    val isLoggedIn: Boolean
        get() = auth?.currentUser != null

    val currentUserEmail: String
        get() = auth?.currentUser?.email ?: ""

    val currentUserName: String
        get() {
            val user = auth?.currentUser
            return user?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: "User"
        }

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            val authInstance = auth ?: return Result.failure(Exception("Firebase Auth is not available"))
            authInstance.signInWithEmailAndPassword(email, password).await()
            val uid = authInstance.currentUser?.uid
            if (uid != null) {
                // Try fetching user name from Firestore if display name is empty
                try {
                    val userDoc = firestore?.collection("users")?.document(uid)?.get()?.await()
                    val savedName = userDoc?.getString("fullName")
                    if (!savedName.isNullOrBlank() && authInstance.currentUser?.displayName.isNullOrBlank()) {
                        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(savedName)
                            .build()
                        authInstance.currentUser?.updateProfile(profileUpdates)?.await()
                    }
                } catch (e: Exception) {
                    // Non-fatal
                }
                syncDataFromFirestore(uid)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(fullName: String, email: String, password: String): Result<Unit> {
        return try {
            val authInstance = auth ?: return Result.failure(Exception("Firebase Auth is not available"))
            val result = authInstance.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(fullName)
                .build()
            user?.updateProfile(profileUpdates)?.await()

            val uid = user?.uid
            if (uid != null) {
                val userData = hashMapOf(
                    "userId" to uid,
                    "fullName" to fullName,
                    "email" to email,
                    "createdAt" to System.currentTimeMillis()
                )
                try {
                    firestore?.collection("users")?.document(uid)?.set(userData)?.await()
                } catch (e: Exception) {
                    // Non-fatal
                }
                syncDataFromFirestore(uid)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        try {
            auth?.signOut()
        } catch (e: Exception) {}
        prefs.edit().clear().apply()
    }

    suspend fun updateProfile(newName: String): Result<Unit> {
        return try {
            val user = auth?.currentUser ?: return Result.failure(Exception("User not logged in"))
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build()
            user.updateProfile(profileUpdates).await()
            try {
                firestore?.collection("users")?.document(user.uid)?.update("fullName", newName)?.await()
            } catch (e: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncDataFromFirestore(userId: String) {
        if (userId.isBlank()) return
        try {
            val fs = firestore ?: return
            val memberQuery = fs.collection("groupMembers")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            for (memberDoc in memberQuery.documents) {
                val member = memberDoc.toObject(GroupMemberEntity::class.java)
                if (member != null) {
                    database.groupDao().insertMember(member)
                    val groupDoc = fs.collection("groups").document(member.groupId).get().await()
                    val group = groupDoc.toObject(GroupEntity::class.java)
                    if (group != null) {
                        database.groupDao().insertGroup(group)
                    }
                    val expenseQuery = fs.collection("expenses")
                        .whereEqualTo("groupId", member.groupId)
                        .get()
                        .await()
                    for (expDoc in expenseQuery.documents) {
                        val exp = expDoc.toObject(ExpenseEntity::class.java)
                        if (exp != null) {
                            database.expenseDao().insertExpense(exp)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Non-fatal sync error
        }
    }

    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode = _isDemoMode.asStateFlow()

    fun setDemoMode(enabled: Boolean) {
        _isDemoMode.value = enabled
    }

    // Groups
    val allGroups: Flow<List<GroupEntity>> = database.groupDao().getAllGroups()
    val allExpenses: Flow<List<ExpenseEntity>> = database.expenseDao().getAllExpenses()

    fun getGroupsForUser(userId: String): Flow<List<GroupEntity>> {
        return database.groupDao().getGroupsForUser(userId)
    }

    fun getExpensesForUser(userId: String): Flow<List<ExpenseEntity>> {
        return database.expenseDao().getExpensesForUser(userId)
    }

    suspend fun createGroup(
        name: String,
        description: String,
        currency: String,
        maxMembers: Int
    ): String {
        val groupId = UUID.randomUUID().toString()
        val roomCode = (100000..999999).random().toString()
        val group = GroupEntity(
            groupId = groupId,
            groupName = name,
            description = description,
            roomCode = roomCode,
            createdBy = currentUserId,
            currency = currency,
            maximumMembers = maxMembers,
            currentMemberCount = 1
        )
        database.groupDao().insertGroup(group)

        // Add creator as admin
        val membershipId = UUID.randomUUID().toString()
        val member = GroupMemberEntity(
            membershipId = membershipId,
            groupId = groupId,
            userId = currentUserId,
            userName = currentUserName,
            role = "ADMIN"
        )
        database.groupDao().insertMember(member)

        try {
            firestore?.let { fs ->
                fs.collection("groups").document(groupId).set(group).await()
                fs.collection("groupMembers").document(membershipId).set(member).await()
            }
        } catch (e: Exception) {
            // Offline fallback
        }
        return roomCode
    }

    suspend fun joinGroup(roomCode: String): Result<String> {
        val group = database.groupDao().getGroupByRoomCode(roomCode)
            ?: try {
                val fs = firestore ?: return Result.failure(Exception("Offline and room not found locally"))
                val query = fs.collection("groups").whereEqualTo("roomCode", roomCode).get().await()
                if (query.isEmpty) return Result.failure(Exception("Room not found with code: $roomCode"))
                val doc = query.documents[0]
                val g = doc.toObject(GroupEntity::class.java) ?: return Result.failure(Exception("Invalid room data"))
                database.groupDao().insertGroup(g)
                g
            } catch (e: Exception) {
                return Result.failure(Exception("Failed to find room: ${e.message}"))
            }

        if (group.currentMemberCount >= group.maximumMembers) {
            return Result.failure(Exception("Room is full (Max: ${group.maximumMembers} members)"))
        }

        val membershipId = UUID.randomUUID().toString()
        val member = GroupMemberEntity(
            membershipId = membershipId,
            groupId = group.groupId,
            userId = currentUserId,
            userName = currentUserName,
            role = "MEMBER"
        )
        database.groupDao().insertMember(member)

        // Update count
        val updatedGroup = group.copy(currentMemberCount = group.currentMemberCount + 1)
        database.groupDao().insertGroup(updatedGroup)

        try {
            firestore?.let { fs ->
                fs.collection("groups").document(group.groupId).set(updatedGroup).await()
                fs.collection("groupMembers").document(membershipId).set(member).await()
            }
        } catch (e: Exception) {
            // Offline fallback
        }

        return Result.success(group.groupId)
    }

    fun getExpenses(groupId: String): Flow<List<ExpenseEntity>> {
        return database.expenseDao().getExpensesForGroup(groupId)
    }

    suspend fun addExpense(
        groupId: String,
        title: String,
        amount: Long,
        currency: String,
        category: String,
        splitMethod: String,
        notes: String
    ) {
        val expenseId = UUID.randomUUID().toString()
        val expense = ExpenseEntity(
            expenseId = expenseId,
            groupId = groupId,
            title = title,
            totalAmountInMinorUnits = amount,
            currency = currency,
            categoryName = category,
            paidByUserId = currentUserId,
            paidByName = currentUserName,
            splitMethod = splitMethod,
            notes = notes
        )
        database.expenseDao().insertExpense(expense)
        try {
            firestore?.collection("expenses")?.document(expenseId)?.set(expense)?.await()
        } catch (e: Exception) {
            // Offline
        }
    }

    suspend fun deleteExpense(expenseId: String) {
        database.expenseDao().deleteExpense(expenseId)
        try {
            firestore?.collection("expenses")?.document(expenseId)?.delete()?.await()
        } catch (e: Exception) {}
    }

    fun getMembers(groupId: String): Flow<List<GroupMemberEntity>> {
        return database.groupDao().getMembersForGroup(groupId)
    }

    fun getSettlements(groupId: String): Flow<List<SettlementEntity>> {
        return database.settlementDao().getSettlementsForGroup(groupId)
    }

    suspend fun addSettlement(
        groupId: String,
        receiverId: String,
        receiverName: String,
        amount: Long,
        paymentMethod: String,
        notes: String
    ) {
        val settlementId = UUID.randomUUID().toString()
        val settlement = SettlementEntity(
            settlementId = settlementId,
            groupId = groupId,
            payerUserId = currentUserId,
            payerName = currentUserName,
            receiverUserId = receiverId,
            receiverName = receiverName,
            amountInMinorUnits = amount,
            paymentMethod = paymentMethod,
            notes = notes
        )
        database.settlementDao().insertSettlement(settlement)
        try {
            firestore?.collection("settlements")?.document(settlementId)?.set(settlement)?.await()
        } catch (e: Exception) {}
    }

    fun getPersonalExpenses(userId: String): Flow<List<PersonalExpenseEntity>> {
        return database.personalExpenseDao().getPersonalExpenses(userId)
    }

    suspend fun addPersonalExpense(title: String, amount: Long, category: String, notes: String) {
        val id = UUID.randomUUID().toString()
        val expense = PersonalExpenseEntity(
            expenseId = id,
            userId = currentUserId,
            title = title,
            amount = amount,
            category = category,
            notes = notes
        )
        database.personalExpenseDao().insertPersonalExpense(expense)
    }

    suspend fun deletePersonalExpense(id: String) {
        database.personalExpenseDao().deletePersonalExpense(id)
    }
}
