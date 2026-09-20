package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val profileImage: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val groupId: String = "",
    val groupName: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val roomCode: String = "",
    val createdBy: String = "",
    val currency: String = "INR",
    val maximumMembers: Int = 10,
    val currentMemberCount: Int = 1,
    val status: String = "ACTIVE",
    val isDeletionRequested: Boolean = false,
    val deletionRequestedBy: String = "",
    val approvedDeletionUserIds: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "group_members")
data class GroupMemberEntity(
    @PrimaryKey val membershipId: String = "",
    val groupId: String = "",
    val userId: String = "",
    val userName: String = "",
    val role: String = "MEMBER", // ADMIN or MEMBER
    val profileImage: String = "",
    val joinedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val expenseId: String = "",
    val groupId: String = "",
    val title: String = "",
    val totalAmountInMinorUnits: Long = 0L,
    val currency: String = "INR",
    val categoryName: String = "",
    val paidByUserId: String = "",
    val paidByName: String = "",
    val splitMethod: String = "EQUAL", // EQUAL, UNEQUAL, PERCENTAGE, SHARES, EXACT
    val expenseDate: Long = System.currentTimeMillis(),
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "settlements")
data class SettlementEntity(
    @PrimaryKey val settlementId: String = "",
    val groupId: String = "",
    val payerUserId: String = "",
    val payerName: String = "",
    val receiverUserId: String = "",
    val receiverName: String = "",
    val amountInMinorUnits: Long = 0L,
    val currency: String = "INR",
    val paymentMethod: String = "UPI", // CASH, UPI, BANK
    val status: String = "COMPLETED", // PENDING, COMPLETED
    val settlementDate: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(tableName = "personal_expenses")
data class PersonalExpenseEntity(
    @PrimaryKey val expenseId: String = "",
    val userId: String = "",
    val title: String = "",
    val amount: Long = 0L,
    val category: String = "",
    val date: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String = "",
    val recipientUserId: String = "", // specific userId for personal notifications, or "ALL" / ""
    val senderUserId: String = "",
    val senderUserName: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "INFO", // "INFO", "WARNING"
    val expenseTitle: String = "",
    val amount: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

