package com.example

import com.example.domain.calculator.ExpenseCalculator
import com.example.domain.calculator.PayerContribution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseCalculatorTest {

    @Test
    fun testEqualSplit_2Members() {
        val result = ExpenseCalculator.calculateEqualSplit(1000L, listOf("A", "B"))
        assertEquals(2, result.size)
        assertEquals(500L, result["A"])
        assertEquals(500L, result["B"])
    }

    @Test
    fun testEqualSplit_3MembersWithRemainder() {
        val result = ExpenseCalculator.calculateEqualSplit(1000L, listOf("A", "B", "C"))
        assertEquals(3, result.size)
        val sum = result.values.sum()
        assertEquals(1000L, sum)
        assertTrue(result["A"] == 334L || result["A"] == 333L)
    }

    @Test
    fun testEqualSplit_10Members() {
        val members = (1..10).map { "User$it" }
        val result = ExpenseCalculator.calculateEqualSplit(12345L, members)
        assertEquals(10, result.size)
        assertEquals(12345L, result.values.sum())
    }

    @Test
    fun testUnequalSplit_Valid() {
        val shares = mapOf("A" to 400L, "B" to 300L, "C" to 300L)
        val result = ExpenseCalculator.calculateUnequalSplit(shares, 1000L)
        assertTrue(result.isSuccess)
        assertEquals(1000L, result.getOrNull()?.values?.sum())
    }

    @Test
    fun testPercentageSplit_Valid() {
        val percentages = mapOf("A" to 50.0, "B" to 30.0, "C" to 20.0)
        val result = ExpenseCalculator.calculatePercentageSplit(percentages, 1000L)
        assertTrue(result.isSuccess)
        val shares = result.getOrNull()!!
        assertEquals(500L, shares["A"])
        assertEquals(300L, shares["B"])
        assertEquals(200L, shares["C"])
        assertEquals(1000L, shares.values.sum())
    }

    @Test
    fun testSharesSplit_Valid() {
        val weights = mapOf("A" to 2, "B" to 1, "C" to 1)
        val result = ExpenseCalculator.calculateSharesSplit(weights, 1000L)
        assertTrue(result.isSuccess)
        val shares = result.getOrNull()!!
        assertEquals(500L, shares["A"])
        assertEquals(250L, shares["B"])
        assertEquals(250L, shares["C"])
        assertEquals(1000L, shares.values.sum())
    }

    @Test
    fun testPayerContributions_Validation() {
        val contributions = listOf(
            PayerContribution("A", 700L),
            PayerContribution("B", 300L)
        )
        assertTrue(ExpenseCalculator.validatePayerContributions(contributions, 1000L))
    }

    @Test
    fun testSimplifyDebts() {
        val balances = mapOf("A" to 500L, "B" to -100L, "C" to -400L)
        val transactions = ExpenseCalculator.simplifyDebts(balances)
        assertEquals(2, transactions.size)
        assertEquals(100L, transactions.find { it.fromUserId == "B" }?.amount)
        assertEquals(400L, transactions.find { it.fromUserId == "C" }?.amount)
    }
}
