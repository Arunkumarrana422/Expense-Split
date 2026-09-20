package com.example.data.repository

import android.content.Context
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.local.ExpenseEntity
import com.example.data.local.GroupEntity
import com.example.data.local.GroupMemberEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.PersonalExpenseEntity
import com.example.data.local.SettlementEntity
import com.example.util.NotificationHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ExpenseRepository(private val context: Context) {
    private val database = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "expense_splitter.db"
    ).fallbackToDestructiveMigration().build()

    private val prefs = context.getSharedPreferences("expense_auth_prefs", Context.MODE_PRIVATE)

    private fun ensureFirebase(): FirebaseApp {
        val apps = FirebaseApp.getApps(context)
        return if (apps.isNotEmpty()) {
            FirebaseApp.getInstance()
        } else {
            val options = FirebaseOptions.Builder()
                .setApplicationId("1:52643587669:android:55f7580c6b52c35c1a96dc")
                .setApiKey("AIzaSyA9JFTO-kzwvccCvydATL-gqGgaqxo33wA")
                .setProjectId("student-patnar")
                .setDatabaseUrl("https://student-patnar-default-rtdb.firebaseio.com")
                .setStorageBucket("student-patnar.firebasestorage.app")
                .setGcmSenderId("52643587669")
                .build()
            FirebaseApp.initializeApp(context.applicationContext, options)
        }
    }

    private fun getAuth(): FirebaseAuth {
        return try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            val app = ensureFirebase()
            FirebaseAuth.getInstance(app)
        }
    }

    private fun getFirestore(): FirebaseFirestore {
        return try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            val app = ensureFirebase()
            FirebaseFirestore.getInstance(app)
        }
    }

    init {
        try {
            ensureFirebase()
        } catch (e: Exception) {
            // Already initialized
        }
        // If not logged in via Firebase Auth, ensure local prefs are completely clean
        try {
            if (getAuth().currentUser == null) {
                prefs.edit().clear().apply()
            }
        } catch (e: Exception) {
            prefs.edit().clear().apply()
        }
    }

    val currentUserId: String
        get() = try { getAuth().currentUser?.uid ?: "" } catch (e: Exception) { "" }

    val isLoggedIn: Boolean
        get() = try { getAuth().currentUser != null } catch (e: Exception) { false }

    val currentUserEmail: String
        get() = try { getAuth().currentUser?.email ?: "" } catch (e: Exception) { "" }

    val currentUserName: String
        get() {
            return try {
                val user = getAuth().currentUser
                user?.displayName?.takeIf { it.isNotBlank() }
                    ?: user?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    ?: "User"
            } catch (e: Exception) {
                "User"
            }
        }

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            val authInstance = getAuth()
            authInstance.signInWithEmailAndPassword(email, password).await()
            val uid = authInstance.currentUser?.uid
            if (uid != null) {
                // Try fetching user name from Firestore if display name is empty
                try {
                    val userDoc = getFirestore().collection("users").document(uid).get().await()
                    val savedName = userDoc.getString("fullName")
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
            val authInstance = getAuth()
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
                    getFirestore().collection("users").document(uid).set(userData).await()
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
            getAuth().signOut()
        } catch (e: Exception) {}
        prefs.edit().clear().apply()
    }

    suspend fun updateProfile(newName: String): Result<Unit> {
        return try {
            val user = getAuth().currentUser ?: return Result.failure(Exception("User not logged in"))
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build()
            user.updateProfile(profileUpdates).await()
            try {
                getFirestore().collection("users").document(user.uid).update("fullName", newName).await()
            } catch (e: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncDataFromFirestore(userId: String) {
        if (userId.isBlank()) return
        try {
            val fs = getFirestore()
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

            // Sync notifications for this user
            try {
                val notifQuery = fs.collection("notifications")
                    .whereIn("recipientUserId", listOf(userId, "ALL", ""))
                    .get()
                    .await()
                for (doc in notifQuery.documents) {
                    val notif = doc.toObject(NotificationEntity::class.java)
                    if (notif != null) {
                        database.notificationDao().insertNotification(notif)
                    }
                }
            } catch (e: Exception) {
                // Non-fatal
            }
        } catch (e: Exception) {
            // Non-fatal sync error
        }
    }

    private var notificationListener: ListenerRegistration? = null

    fun startNotificationListener(userId: String) {
        notificationListener?.remove()
        if (userId.isBlank()) return
        try {
            val fs = getFirestore()
            notificationListener = fs.collection("notifications")
                .whereIn("recipientUserId", listOf(userId, "ALL", ""))
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    for (change in snapshot.documentChanges) {
                        if (change.type == DocumentChange.Type.ADDED) {
                            val notif = change.document.toObject(NotificationEntity::class.java)
                            CoroutineScope(Dispatchers.IO).launch {
                                database.notificationDao().insertNotification(notif)
                            }
                            // If generated by another user and recent, alert the device immediately
                            val isOtherSender = notif.senderUserId.isNotBlank() && notif.senderUserId != currentUserId
                            val isRecent = (System.currentTimeMillis() - notif.timestamp) < 300_000 // 5 minutes
                            if (isOtherSender && isRecent) {
                                NotificationHelper.showDeviceNotification(
                                    context = context,
                                    title = notif.title,
                                    message = notif.message,
                                    isWarning = notif.type == "WARNING"
                                )
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            // Non-fatal
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
            val fs = getFirestore()
            fs.collection("groups").document(groupId).set(group).await()
            fs.collection("groupMembers").document(membershipId).set(member).await()
        } catch (e: Exception) {
            // Offline fallback
        }
        return roomCode
    }

    suspend fun joinGroup(roomCode: String): Result<String> {
        val group = database.groupDao().getGroupByRoomCode(roomCode)
            ?: try {
                val fs = getFirestore()
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
            val fs = getFirestore()
            fs.collection("groups").document(group.groupId).set(updatedGroup).await()
            fs.collection("groupMembers").document(membershipId).set(member).await()
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
        notes: String,
        groupName: String = ""
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
            getFirestore().collection("expenses").document(expenseId).set(expense).await()
        } catch (e: Exception) {
            // Offline
        }

        // Notification: Show in notifications and trigger phone notification
        val notifId = UUID.randomUUID().toString()
        val amountFormatted = amount / 100.0
        val resolvedGroupName = groupName.ifBlank {
            database.groupDao().getGroupById(groupId)?.groupName ?: ""
        }
        val groupSuffix = if (resolvedGroupName.isNotBlank()) " in $resolvedGroupName" else ""
        val notif = NotificationEntity(
            id = notifId,
            recipientUserId = "ALL", // broadcast to group
            senderUserId = currentUserId,
            senderUserName = currentUserName,
            groupId = groupId,
            groupName = resolvedGroupName,
            title = "💸 New Expense Added",
            message = "$currentUserName added '$title' (₹$amountFormatted)$groupSuffix.",
            type = "INFO",
            expenseTitle = title,
            amount = amountFormatted,
            timestamp = System.currentTimeMillis()
        )
        database.notificationDao().insertNotification(notif)
        try {
            getFirestore().collection("notifications").document(notifId).set(notif).await()
        } catch (e: Exception) {}

        // Trigger mobile phone system notification
        NotificationHelper.showDeviceNotification(
            context = context,
            title = "New Expense Added",
            message = "$currentUserName added '$title' of ₹$amountFormatted$groupSuffix",
            isWarning = false
        )
    }

    suspend fun deleteExpense(expense: ExpenseEntity, groupName: String = "") {
        database.expenseDao().deleteExpense(expense.expenseId)
        try {
            getFirestore().collection("expenses").document(expense.expenseId).delete().await()
        } catch (e: Exception) {}

        val notifId = UUID.randomUUID().toString()
        val deleter = currentUserName.ifBlank { "A group member" }
        val amountFormatted = expense.totalAmountInMinorUnits / 100.0
        val resolvedGroupName = groupName.ifBlank {
            database.groupDao().getGroupById(expense.groupId)?.groupName ?: ""
        }
        val groupSuffix = if (resolvedGroupName.isNotBlank()) " in $resolvedGroupName" else ""

        val isOtherUserExpense = expense.paidByUserId.isNotBlank() && expense.paidByUserId != currentUserId

        if (isOtherUserExpense) {
            // WARNING notification personally addressed ONLY to the person whose expense was deleted!
            val warningNotif = NotificationEntity(
                id = notifId,
                recipientUserId = expense.paidByUserId, // Strictly visible to the user whose expense was deleted!
                senderUserId = currentUserId,
                senderUserName = deleter,
                groupId = expense.groupId,
                groupName = resolvedGroupName,
                title = "⚠️ Warning: Expense Deleted",
                message = "Warning: $deleter deleted your expense '${expense.title}' (₹$amountFormatted)$groupSuffix.",
                type = "WARNING",
                expenseTitle = expense.title,
                amount = amountFormatted,
                timestamp = System.currentTimeMillis()
            )
            database.notificationDao().insertNotification(warningNotif)
            try {
                getFirestore().collection("notifications").document(notifId).set(warningNotif).await()
            } catch (e: Exception) {}
        } else {
            val selfNotif = NotificationEntity(
                id = notifId,
                recipientUserId = currentUserId,
                senderUserId = currentUserId,
                senderUserName = deleter,
                groupId = expense.groupId,
                groupName = resolvedGroupName,
                title = "Expense Deleted",
                message = "You deleted expense '${expense.title}' (₹$amountFormatted)$groupSuffix.",
                type = "INFO",
                expenseTitle = expense.title,
                amount = amountFormatted,
                timestamp = System.currentTimeMillis()
            )
            database.notificationDao().insertNotification(selfNotif)
            try {
                getFirestore().collection("notifications").document(notifId).set(selfNotif).await()
            } catch (e: Exception) {}
        }
    }

    suspend fun deleteExpense(expenseId: String) {
        database.expenseDao().deleteExpense(expenseId)
        try {
            getFirestore().collection("expenses").document(expenseId).delete().await()
        } catch (e: Exception) {}
    }

    fun getNotificationsForUser(userId: String): Flow<List<NotificationEntity>> {
        return database.notificationDao().getNotificationsForUser(userId)
    }

    suspend fun clearNotifications() {
        database.notificationDao().clearAll()
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
            getFirestore().collection("settlements").document(settlementId).set(settlement).await()
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
