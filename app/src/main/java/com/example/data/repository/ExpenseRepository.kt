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
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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

    val profilePhotoFlow = kotlinx.coroutines.flow.MutableStateFlow(prefs.getString("profile_photo", "") ?: "")

    suspend fun updateProfilePhoto(base64Str: String) {
        prefs.edit().putString("profile_photo", base64Str).apply()
        profilePhotoFlow.value = base64Str
        try {
            if (currentUserId.isNotBlank()) {
                dbRef.child("users").child(currentUserId).child("profileImage").setValue(base64Str).await()
                val userGroupsSnap = dbRef.child("user_groups").child(currentUserId).get().await()
                for (gChild in userGroupsSnap.children) {
                    val gId = gChild.key ?: continue
                    val membersSnap = dbRef.child("group_members").child(gId).get().await()
                    for (mChild in membersSnap.children) {
                        val mKey = mChild.key ?: continue
                        val m = mChild.getValue(GroupMemberEntity::class.java)
                        if (m != null && m.userId == currentUserId) {
                            dbRef.child("group_members").child(gId).child(mKey).child("profileImage").setValue(base64Str)
                        }
                    }
                }
            }
        } catch (e: Exception) {}
    }

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

    private fun getDatabase(): FirebaseDatabase {
        val app = ensureFirebase()
        return try {
            FirebaseDatabase.getInstance(app, "https://student-patnar-default-rtdb.firebaseio.com")
        } catch (e: Exception) {
            FirebaseDatabase.getInstance(app)
        }
    }

    private val dbRef: DatabaseReference
        get() = getDatabase().reference

    init {
        try {
            ensureFirebase()
        } catch (e: Exception) {
            // Already initialized
        }
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
                try {
                    val userSnap = dbRef.child("users").child(uid).get().await()
                    val savedName = userSnap.child("fullName").getValue(String::class.java)
                    if (!savedName.isNullOrBlank() && authInstance.currentUser?.displayName.isNullOrBlank()) {
                        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(savedName)
                            .build()
                        authInstance.currentUser?.updateProfile(profileUpdates)?.await()
                    }
                } catch (e: Exception) {
                    // Non-fatal
                }
                syncDataFromFirebase(uid)
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
                    dbRef.child("users").child(uid).setValue(userData).await()
                } catch (e: Exception) {
                    // Non-fatal
                }
                syncDataFromFirebase(uid)
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
                dbRef.child("users").child(user.uid).child("fullName").setValue(newName).await()
            } catch (e: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Comprehensive synchronization from Firebase Realtime Database to local Room DB
     */
    suspend fun syncDataFromFirebase(userId: String) {
        if (userId.isBlank()) return
        try {
            val ref = dbRef

            try {
                val userSnap = ref.child("users").child(userId).get().await()
                val profileImg = userSnap.child("profileImage").getValue(String::class.java) ?: ""
                if (profileImg.isNotBlank()) {
                    prefs.edit().putString("profile_photo", profileImg).apply()
                    profilePhotoFlow.value = profileImg
                }
            } catch (e: Exception) {}

            // 1. Fetch user's registered groups from user_groups node
            val groupIdsToSync = mutableSetOf<String>()

            try {
                val userGroupsSnap = ref.child("user_groups").child(userId).get().await()
                for (child in userGroupsSnap.children) {
                    val gId = child.key
                    if (!gId.isNullOrBlank()) {
                        groupIdsToSync.add(gId)
                    }
                }
            } catch (e: Exception) {}

            // Also check all groups where createdBy == userId as fallback
            try {
                val allGroupsSnap = ref.child("groups").get().await()
                for (gDoc in allGroupsSnap.children) {
                    val group = gDoc.getValue(GroupEntity::class.java)
                    if (group != null && (group.createdBy == userId || groupIdsToSync.contains(group.groupId))) {
                        database.groupDao().insertGroup(group)
                        groupIdsToSync.add(group.groupId)
                        // Make sure user_groups mapping is saved
                        ref.child("user_groups").child(userId).child(group.groupId).setValue(true)
                    }
                }
            } catch (e: Exception) {}

            // For all found groups, sync group details, members, expenses, settlements
            for (groupId in groupIdsToSync) {
                try {
                    // Sync Group Info
                    val groupSnap = ref.child("groups").child(groupId).get().await()
                    val group = groupSnap.getValue(GroupEntity::class.java)
                    if (group != null) {
                        database.groupDao().insertGroup(group)
                    }

                    // Sync Members
                    val membersSnap = ref.child("group_members").child(groupId).get().await()
                    val memberList = mutableListOf<GroupMemberEntity>()
                    for (mChild in membersSnap.children) {
                        var member = mChild.getValue(GroupMemberEntity::class.java)
                        if (member != null) {
                            try {
                                val userSnap = ref.child("users").child(member.userId).get().await()
                                val pImg = userSnap.child("profileImage").getValue(String::class.java) ?: ""
                                if (pImg.isNotBlank()) {
                                    member = member.copy(profileImage = pImg)
                                }
                            } catch (e: Exception) {}
                            memberList.add(member)
                        }
                    }
                    if (memberList.isNotEmpty()) {
                        database.groupDao().deleteMembersByGroupId(groupId)
                        for (m in memberList) {
                            database.groupDao().insertMember(m)
                            // If this user is a member, ensure mapping is recorded
                            if (m.userId == userId) {
                                ref.child("user_groups").child(userId).child(groupId).setValue(true)
                            }
                        }
                    }

                    // Sync Expenses
                    val expensesSnap = ref.child("group_expenses").child(groupId).get().await()
                    for (eChild in expensesSnap.children) {
                        val exp = eChild.getValue(ExpenseEntity::class.java)
                        if (exp != null) {
                            database.expenseDao().insertExpense(exp)
                        }
                    }

                    // Sync Settlements
                    val settlementsSnap = ref.child("group_settlements").child(groupId).get().await()
                    for (sChild in settlementsSnap.children) {
                        val set = sChild.getValue(SettlementEntity::class.java)
                        if (set != null) {
                            database.settlementDao().insertSettlement(set)
                        }
                    }
                } catch (e: Exception) {}
            }

            // 2. Sync Personal Expenses for this user
            try {
                val personalSnap = ref.child("personal_expenses").child(userId).get().await()
                for (pChild in personalSnap.children) {
                    val personal = pChild.getValue(PersonalExpenseEntity::class.java)
                    if (personal != null) {
                        database.personalExpenseDao().insertPersonalExpense(personal)
                    }
                }
            } catch (e: Exception) {}

            // 3. Sync Notifications
            try {
                val notifsSnap = ref.child("notifications").get().await()
                for (nChild in notifsSnap.children) {
                    val notif = nChild.getValue(NotificationEntity::class.java)
                    if (notif != null && (notif.recipientUserId == userId || notif.recipientUserId == "ALL" || notif.recipientUserId.isBlank())) {
                        database.notificationDao().insertNotification(notif)
                    }
                }
            } catch (e: Exception) {}

        } catch (e: Exception) {
            // Non-fatal sync error
        }
    }

    // Alias for backward compatibility if called elsewhere
    suspend fun syncDataFromFirestore(userId: String) = syncDataFromFirebase(userId)

    private var activeNotificationListener: ChildEventListener? = null
    private var activeGroupListeners = mutableMapOf<String, ValueEventListener>()
    private var notificationListenerStartTime = System.currentTimeMillis()

    fun startNotificationListener(userId: String) {
        if (userId.isBlank()) return
        try {
            activeNotificationListener?.let { dbRef.child("notifications").removeEventListener(it) }
            notificationListenerStartTime = System.currentTimeMillis()

            val listener = object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val notif = snapshot.getValue(NotificationEntity::class.java) ?: return
                    val isTargetRecipient = notif.recipientUserId == userId || notif.recipientUserId == "ALL" || notif.recipientUserId.isBlank()
                    if (isTargetRecipient) {
                        CoroutineScope(Dispatchers.IO).launch {
                            database.notificationDao().insertNotification(notif)
                        }
                        val isOtherSender = notif.senderUserId.isNotBlank() && notif.senderUserId != currentUserId
                        // Only alert if it's from another person AND created after listener started (or within last 30 seconds)
                        val isNewEvent = notif.timestamp >= (notificationListenerStartTime - 30_000L)
                        if (isOtherSender && isNewEvent) {
                            NotificationHelper.showDeviceNotification(
                                context = context,
                                notificationUniqueId = notif.id,
                                title = notif.title,
                                message = notif.message,
                                isWarning = notif.type == "WARNING"
                            )
                        }
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            }

            activeNotificationListener = listener
            dbRef.child("notifications").addChildEventListener(listener)
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
            val ref = dbRef
            ref.child("groups").child(groupId).setValue(group).await()
            ref.child("group_members").child(groupId).child(membershipId).setValue(member).await()
            ref.child("user_groups").child(currentUserId).child(groupId).setValue(true).await()
            ref.child("room_codes").child(roomCode).setValue(groupId).await()
        } catch (e: Exception) {
            // Offline fallback
        }
        return roomCode
    }

    suspend fun joinGroup(roomCode: String): Result<String> {
        val ref = dbRef
        val group: GroupEntity = database.groupDao().getGroupByRoomCode(roomCode)
            ?: try {
                val codeSnap = ref.child("room_codes").child(roomCode).get().await()
                val targetGroupId = codeSnap.getValue(String::class.java)
                if (targetGroupId.isNullOrBlank()) {
                    // Try looking in groups table directly
                    val allGroupsSnap = ref.child("groups").get().await()
                    var foundGroup: GroupEntity? = null
                    for (gChild in allGroupsSnap.children) {
                        val g = gChild.getValue(GroupEntity::class.java)
                        if (g != null && g.roomCode == roomCode) {
                            foundGroup = g
                            break
                        }
                    }
                    foundGroup ?: return Result.failure(Exception("Room not found with code: $roomCode"))
                } else {
                    val groupSnap = ref.child("groups").child(targetGroupId).get().await()
                    groupSnap.getValue(GroupEntity::class.java) ?: return Result.failure(Exception("Invalid room data"))
                }
            } catch (e: Exception) {
                return Result.failure(Exception("Failed to find room: ${e.message}"))
            }

        database.groupDao().insertGroup(group)

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
            ref.child("groups").child(group.groupId).setValue(updatedGroup).await()
            ref.child("group_members").child(group.groupId).child(membershipId).setValue(member).await()
            ref.child("user_groups").child(currentUserId).child(group.groupId).setValue(true).await()

            // Fetch and sync all existing expenses & members of this joined group
            val expensesSnap = ref.child("group_expenses").child(group.groupId).get().await()
            for (eChild in expensesSnap.children) {
                val exp = eChild.getValue(ExpenseEntity::class.java)
                if (exp != null) {
                    database.expenseDao().insertExpense(exp)
                }
            }

            val membersSnap = ref.child("group_members").child(group.groupId).get().await()
            for (mChild in membersSnap.children) {
                val m = mChild.getValue(GroupMemberEntity::class.java)
                if (m != null) {
                    database.groupDao().insertMember(m)
                }
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
            dbRef.child("group_expenses").child(groupId).child(expenseId).setValue(expense).await()
        } catch (e: Exception) {
            // Offline
        }

        // Notification: Show in notifications and trigger phone notification
        val notifId = UUID.randomUUID().toString()
        val amountFormatted = amount.toDouble()
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
            message = "$currentUserName added '$title' (₹$amount)$groupSuffix.",
            type = "INFO",
            expenseTitle = title,
            amount = amountFormatted,
            timestamp = System.currentTimeMillis()
        )
        database.notificationDao().insertNotification(notif)
        try {
            dbRef.child("notifications").child(notifId).setValue(notif).await()
        } catch (e: Exception) {}
    }

    suspend fun deleteExpense(expense: ExpenseEntity, groupName: String = "") {
        database.expenseDao().deleteExpense(expense.expenseId)
        try {
            dbRef.child("group_expenses").child(expense.groupId).child(expense.expenseId).removeValue().await()
        } catch (e: Exception) {}

        val notifId = UUID.randomUUID().toString()
        val deleter = currentUserName.ifBlank { "A group member" }
        val amountFormatted = expense.totalAmountInMinorUnits.toDouble()
        val resolvedGroupName = groupName.ifBlank {
            database.groupDao().getGroupById(expense.groupId)?.groupName ?: ""
        }
        val groupSuffix = if (resolvedGroupName.isNotBlank()) " in $resolvedGroupName" else ""

        val isOtherUserExpense = expense.paidByUserId.isNotBlank() && expense.paidByUserId != currentUserId

        if (isOtherUserExpense) {
            val warningNotif = NotificationEntity(
                id = notifId,
                recipientUserId = expense.paidByUserId,
                senderUserId = currentUserId,
                senderUserName = deleter,
                groupId = expense.groupId,
                groupName = resolvedGroupName,
                title = "⚠️ Warning: Expense Deleted",
                message = "Warning: $deleter deleted your expense '${expense.title}' (₹${expense.totalAmountInMinorUnits})$groupSuffix.",
                type = "WARNING",
                expenseTitle = expense.title,
                amount = amountFormatted,
                timestamp = System.currentTimeMillis()
            )
            database.notificationDao().insertNotification(warningNotif)
            try {
                dbRef.child("notifications").child(notifId).setValue(warningNotif).await()
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
                message = "You deleted expense '${expense.title}' (₹${expense.totalAmountInMinorUnits})$groupSuffix.",
                type = "INFO",
                expenseTitle = expense.title,
                amount = amountFormatted,
                timestamp = System.currentTimeMillis()
            )
            database.notificationDao().insertNotification(selfNotif)
            try {
                dbRef.child("notifications").child(notifId).setValue(selfNotif).await()
            } catch (e: Exception) {}
        }
    }

    suspend fun deleteExpense(expenseId: String) {
        database.expenseDao().deleteExpense(expenseId)
    }

    fun getNotificationsForUser(userId: String): Flow<List<NotificationEntity>> {
        return database.notificationDao().getNotificationsForUser(userId)
    }

    suspend fun deleteNotification(notificationId: String) {
        database.notificationDao().deleteNotification(notificationId)
        try {
            dbRef.child("notifications").child(notificationId).removeValue().await()
        } catch (e: Exception) {}
    }

    suspend fun clearNotifications(userId: String = "") {
        database.notificationDao().clearAll()
        try {
            if (userId.isNotBlank()) {
                val snapshot = dbRef.child("notifications").get().await()
                snapshot.children.forEach { child ->
                    val notif = child.getValue(NotificationEntity::class.java)
                    if (notif != null && (notif.recipientUserId == userId || notif.recipientUserId == "ALL" || notif.recipientUserId == "" || notif.senderUserId == userId)) {
                        child.ref.removeValue().await()
                    }
                }
            } else {
                dbRef.child("notifications").removeValue().await()
            }
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
        notes: String,
        payerId: String = "",
        payerName: String = "",
        groupName: String = ""
    ) {
        val settlementId = UUID.randomUUID().toString()
        val actualPayerId = payerId.ifBlank { currentUserId }
        val actualPayerName = payerName.ifBlank { currentUserName.ifBlank { "Member" } }
        val settlement = SettlementEntity(
            settlementId = settlementId,
            groupId = groupId,
            payerUserId = actualPayerId,
            payerName = actualPayerName,
            receiverUserId = receiverId,
            receiverName = receiverName,
            amountInMinorUnits = amount,
            paymentMethod = paymentMethod,
            status = "COMPLETED",
            settlementDate = System.currentTimeMillis(),
            notes = notes
        )
        database.settlementDao().insertSettlement(settlement)
        try {
            dbRef.child("group_settlements").child(groupId).child(settlementId).setValue(settlement).await()
        } catch (e: Exception) {}

        // Notification: Broadcast settlement
        val notifId = UUID.randomUUID().toString()
        val resolvedGroupName = groupName.ifBlank {
            database.groupDao().getGroupById(groupId)?.groupName ?: ""
        }
        val groupSuffix = if (resolvedGroupName.isNotBlank()) " in $resolvedGroupName" else ""
        val notif = NotificationEntity(
            id = notifId,
            recipientUserId = "ALL",
            senderUserId = actualPayerId,
            senderUserName = actualPayerName,
            groupId = groupId,
            groupName = resolvedGroupName,
            title = "🤝 Payment Settled",
            message = "$actualPayerName settled ₹$amount with $receiverName via $paymentMethod$groupSuffix.",
            type = "INFO",
            expenseTitle = "Settlement",
            amount = amount.toDouble(),
            timestamp = System.currentTimeMillis()
        )
        database.notificationDao().insertNotification(notif)
        try {
            dbRef.child("notifications").child(notifId).setValue(notif).await()
        } catch (e: Exception) {}
    }

    suspend fun deleteSettlement(settlement: SettlementEntity) {
        database.settlementDao().deleteSettlement(settlement.settlementId)
        try {
            dbRef.child("group_settlements").child(settlement.groupId).child(settlement.settlementId).removeValue().await()
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
        try {
            dbRef.child("personal_expenses").child(currentUserId).child(id).setValue(expense).await()
        } catch (e: Exception) {}
    }

    suspend fun deletePersonalExpense(id: String) {
        database.personalExpenseDao().deletePersonalExpense(id)
        try {
            dbRef.child("personal_expenses").child(currentUserId).child(id).removeValue().await()
        } catch (e: Exception) {}
    }

    suspend fun updateGroup(groupId: String, name: String, description: String, currency: String, maxMembers: Int) {
        val existing = database.groupDao().getGroupById(groupId) ?: return
        val updated = existing.copy(
            groupName = name,
            description = description,
            currency = currency,
            maximumMembers = maxMembers
        )
        database.groupDao().insertGroup(updated)
        try {
            dbRef.child("groups").child(groupId).setValue(updated).await()
        } catch (e: Exception) {}
    }

    suspend fun requestDeleteGroup(groupId: String, groupName: String) {
        val existing = database.groupDao().getGroupById(groupId) ?: return
        val updated = existing.copy(
            isDeletionRequested = true,
            deletionRequestedBy = currentUserId,
            approvedDeletionUserIds = currentUserId
        )
        database.groupDao().insertGroup(updated)
        try {
            dbRef.child("groups").child(groupId).setValue(updated).await()
        } catch (e: Exception) {}
    }

    suspend fun approveGroupDeletion(groupId: String) {
        val group = database.groupDao().getGroupById(groupId) ?: return
        val approvedList = group.approvedDeletionUserIds.split(",").filter { it.isNotBlank() }.toMutableSet()
        approvedList.add(currentUserId)
        val updatedApproved = approvedList.joinToString(",")

        val members = database.groupDao().getMembersForGroupList(groupId)
        val allApproved = members.isEmpty() || approvedList.size >= members.size

        if (allApproved) {
            deleteGroupFully(groupId)
        } else {
            val updated = group.copy(approvedDeletionUserIds = updatedApproved)
            database.groupDao().insertGroup(updated)
            try {
                dbRef.child("groups").child(groupId).setValue(updated).await()
            } catch (e: Exception) {}
        }
    }

    suspend fun rejectGroupDeletion(groupId: String) {
        val group = database.groupDao().getGroupById(groupId) ?: return
        val updated = group.copy(
            isDeletionRequested = false,
            deletionRequestedBy = "",
            approvedDeletionUserIds = ""
        )
        database.groupDao().insertGroup(updated)
        try {
            dbRef.child("groups").child(groupId).setValue(updated).await()
        } catch (e: Exception) {}
    }

    private suspend fun deleteGroupFully(groupId: String) {
        database.groupDao().deleteMembersByGroupId(groupId)
        val group = database.groupDao().getGroupById(groupId)
        if (group != null) {
            val deletedGroup = group.copy(status = "DELETED")
            database.groupDao().insertGroup(deletedGroup)
        }
        try {
            dbRef.child("groups").child(groupId).removeValue().await()
            dbRef.child("group_members").child(groupId).removeValue().await()
            dbRef.child("expenses").child(groupId).removeValue().await()
            dbRef.child("settlements").child(groupId).removeValue().await()
        } catch (e: Exception) {}
    }
}
