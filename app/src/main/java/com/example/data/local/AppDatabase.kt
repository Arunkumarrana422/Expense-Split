package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE groupId = :groupId ORDER BY expenseDate DESC, createdAt DESC")
    fun getExpensesForGroup(groupId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC, createdAt DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("""
        SELECT e.* FROM expenses e
        INNER JOIN group_members m ON e.groupId = m.groupId
        WHERE m.userId = :userId
        ORDER BY e.expenseDate DESC, e.createdAt DESC
    """)
    fun getExpensesForUser(userId: String): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE expenseId = :expenseId")
    suspend fun deleteExpense(expenseId: String)
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups WHERE status = 'ACTIVE'")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("""
        SELECT DISTINCT g.* FROM groups g
        INNER JOIN group_members m ON g.groupId = m.groupId
        WHERE m.userId = :userId AND g.status = 'ACTIVE'
    """)
    fun getGroupsForUser(userId: String): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE groupId = :groupId")
    suspend fun getGroupById(groupId: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE roomCode = :roomCode LIMIT 1")
    suspend fun getGroupByRoomCode(roomCode: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Query("SELECT * FROM group_members WHERE groupId = :groupId GROUP BY userId")
    fun getMembersForGroup(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId GROUP BY userId")
    suspend fun getMembersForGroupList(groupId: String): List<GroupMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun deleteMembersByGroupId(groupId: String)

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND userId = :userId")
    suspend fun deleteMemberByGroupAndUser(groupId: String, userId: String)

    @Query("UPDATE group_members SET userId = :newUserId WHERE userId = :oldUserId")
    suspend fun updateMemberUserId(oldUserId: String, newUserId: String)
}

@Dao
interface SettlementDao {
    @Query("SELECT * FROM settlements WHERE groupId = :groupId ORDER BY settlementDate DESC")
    fun getSettlementsForGroup(groupId: String): Flow<List<SettlementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlement(settlement: SettlementEntity)

    @Query("DELETE FROM settlements WHERE settlementId = :settlementId")
    suspend fun deleteSettlement(settlementId: String)
}

@Dao
interface PersonalExpenseDao {
    @Query("SELECT * FROM personal_expenses WHERE userId = :userId ORDER BY date DESC")
    fun getPersonalExpenses(userId: String): Flow<List<PersonalExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonalExpense(expense: PersonalExpenseEntity)

    @Query("DELETE FROM personal_expenses WHERE expenseId = :expenseId")
    suspend fun deletePersonalExpense(expenseId: String)

    @Query("UPDATE personal_expenses SET userId = :newUserId WHERE userId = :oldUserId")
    suspend fun updatePersonalExpenseUserId(oldUserId: String, newUserId: String)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE recipientUserId = :userId OR recipientUserId = 'ALL' OR recipientUserId = '' ORDER BY timestamp DESC")
    fun getNotificationsForUser(userId: String): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotification(id: String)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}

@Database(
    entities = [
        UserEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        ExpenseEntity::class,
        SettlementEntity::class,
        PersonalExpenseEntity::class,
        NotificationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun groupDao(): GroupDao
    abstract fun settlementDao(): SettlementDao
    abstract fun personalExpenseDao(): PersonalExpenseDao
    abstract fun notificationDao(): NotificationDao
}
