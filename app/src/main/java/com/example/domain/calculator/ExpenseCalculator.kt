package com.example.domain.calculator

data class MemberShare(
    val userId: String,
    val shareAmount: Long, // in minor units (paise)
    val percentage: Double = 0.0,
    val shares: Int = 1,
    val isExcluded: Boolean = false
)

data class PayerContribution(
    val userId: String,
    val amountPaid: Long // in minor units
)

data class MemberBalance(
    val userId: String,
    val totalPaid: Long = 0L,
    val totalShare: Long = 0L,
    val totalReceivedSettlements: Long = 0L,
    val totalPaidSettlements: Long = 0L
) {
    val netBalance: Long
        get() = (totalPaid + totalPaidSettlements) - (totalShare + totalReceivedSettlements)
}

data class DebtTransaction(
    val fromUserId: String,
    val toUserId: String,
    val amount: Long
)

object ExpenseCalculator {

    fun calculateEqualSplit(
        totalAmount: Long,
        participantIds: List<String>
    ): Map<String, Long> {
        if (participantIds.isEmpty() || totalAmount <= 0L) return emptyMap()
        val count = participantIds.size
        val baseShare = totalAmount / count
        val remainder = (totalAmount % count).toInt()

        val result = mutableMapOf<String, Long>()
        participantIds.forEachIndexed { index, id ->
            val extra = if (index < remainder) 1L else 0L
            result[id] = baseShare + extra
        }
        return result
    }

    fun calculateUnequalSplit(
        enteredShares: Map<String, Long>,
        totalAmount: Long
    ): Result<Map<String, Long>> {
        val sum = enteredShares.values.sum()
        if (sum != totalAmount) {
            return Result.failure(IllegalArgumentException("Sum of shares ($sum) must equal total amount ($totalAmount)"))
        }
        return Result.success(enteredShares)
    }

    fun calculatePercentageSplit(
        percentages: Map<String, Double>,
        totalAmount: Long
    ): Result<Map<String, Long>> {
        val totalPercentage = percentages.values.sum()
        if (kotlin.math.abs(totalPercentage - 100.0) > 0.01) {
            return Result.failure(IllegalArgumentException("Percentages must sum to 100% (got $totalPercentage%)"))
        }
        
        val rawShares = mutableMapOf<String, Long>()
        var allocated = 0L
        percentages.forEach { (id, pct) ->
            val share = ((totalAmount * pct) / 100.0).toLong()
            rawShares[id] = share
            allocated += share
        }

        var remainder = (totalAmount - allocated).toInt()
        val sortedKeys = percentages.keys.toList()
        var idx = 0
        while (remainder > 0 && sortedKeys.isNotEmpty()) {
            val key = sortedKeys[idx % sortedKeys.size]
            rawShares[key] = (rawShares[key] ?: 0L) + 1L
            remainder--
            idx++
        }
        return Result.success(rawShares)
    }

    fun calculateSharesSplit(
        weightShares: Map<String, Int>,
        totalAmount: Long
    ): Result<Map<String, Long>> {
        val totalWeights = weightShares.values.sum()
        if (totalWeights <= 0) {
            return Result.failure(IllegalArgumentException("Total shares must be greater than zero"))
        }

        val rawShares = mutableMapOf<String, Long>()
        var allocated = 0L
        weightShares.forEach { (id, weight) ->
            val share = (totalAmount * weight) / totalWeights
            rawShares[id] = share
            allocated += share
        }

        var remainder = (totalAmount - allocated).toInt()
        val sortedKeys = weightShares.keys.toList()
        var idx = 0
        while (remainder > 0 && sortedKeys.isNotEmpty()) {
            val key = sortedKeys[idx % sortedKeys.size]
            rawShares[key] = (rawShares[key] ?: 0L) + 1L
            remainder--
            idx++
        }
        return Result.success(rawShares)
    }

    fun validatePayerContributions(
        contributions: List<PayerContribution>,
        totalAmount: Long
    ): Boolean {
        return contributions.sumOf { it.amountPaid } == totalAmount
    }

    fun simplifyDebts(netBalances: Map<String, Long>): List<DebtTransaction> {
        val debtors = netBalances.filter { it.value < 0L }.map { it.key to -it.value }.toMutableList()
        val creditors = netBalances.filter { it.value > 0L }.map { it.key to it.value }.toMutableList()

        debtors.sortByDescending { it.second }
        creditors.sortByDescending { it.second }

        val transactions = mutableListOf<DebtTransaction>()
        var dIdx = 0
        var cIdx = 0

        while (dIdx < debtors.size && cIdx < creditors.size) {
            val (debtorId, debtAmount) = debtors[dIdx]
            val (creditorId, creditAmount) = creditors[cIdx]

            val settleAmount = kotlin.math.min(debtAmount, creditAmount)
            if (settleAmount > 0) {
                transactions.add(DebtTransaction(fromUserId = debtorId, toUserId = creditorId, amount = settleAmount))
            }

            val remainingDebt = debtAmount - settleAmount
            val remainingCredit = creditAmount - settleAmount

            if (remainingDebt == 0L) dIdx++ else debtors[dIdx] = debtorId to remainingDebt
            if (remainingCredit == 0L) cIdx++ else creditors[cIdx] = creditorId to remainingCredit
        }

        return transactions
    }
}
