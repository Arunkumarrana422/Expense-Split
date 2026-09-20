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
    @Query("SELECT * FROM expenses WHERE groupId = :groupId ORDER BY expenseDate DESC")
    fun getExpensesForGroup(groupId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("""
        SELECT e.* FROM expenses e
        INNER JOIN group_members m ON e.groupId = m.groupId
        WHERE m.userId = :userId
        ORDER BY e.expenseDate DESC
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
        SELECT g.* FROM groups g
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

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    fun getMembersForGroup(groupId: String): Flow<List<GroupMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity)

    @Query("UPDATE group_members SET userId = :newUserId WHERE userId = :oldUserId")
    suspend fun updateMemberUserId(oldUserId: String, newUserId: String)
}

@Dao
interface SettlementDao {
    @Query("SELECT * FROM settlements WHERE groupId = :groupId ORDER BY settlementDate DESC")
    fun getSettlementsForGroup(groupId: String): Flow<List<SettlementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlement(settlement: SettlementEntity)
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

@Database(
    entities = [
        UserEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        ExpenseEntity::class,
        SettlementEntity::class,
        PersonalExpenseEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun groupDao(): GroupDao
    abstract fun settlementDao(): SettlementDao
    abstract fun personalExpenseDao(): PersonalExpenseDao
}
