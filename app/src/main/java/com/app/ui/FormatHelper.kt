package com.app.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object FormatHelper {
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build())
    private val timeFormatter = SimpleDateFormat("HH:mm", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build())
    private val dateTimeFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build())

    private val vndSymbols = DecimalFormatSymbols(java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build()).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    private val vndFormatter = DecimalFormat("#,###", vndSymbols)

    fun formatVND(amount: Double): String {
        return try {
            val formatted = synchronized(vndFormatter) {
                vndFormatter.format(amount)
            }
            "$formatted ₫"
        } catch (e: Exception) {
            "${String.format("%.0f", amount)} ₫"
        }
    }

    fun formatTime(timestamp: Long): String {
        return try {
            synchronized(timeFormatter) {
                timeFormatter.format(timestamp)
            }
        } catch (e: Exception) {
            val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
            String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }
    }

    fun formatInputNumber(input: String): String {
        val clean = input.filter { it.isDigit() }
        if (clean.isEmpty()) return ""
        val parsed = clean.toDoubleOrNull() ?: 0.0
        return try {
            synchronized(vndFormatter) {
                vndFormatter.format(parsed)
            }
        } catch (e: Exception) {
            clean
        }
    }

    fun formatExpression(expr: String): String {
        val regex = Regex("\\d+")
        return regex.replace(expr) { matchResult ->
            formatInputNumber(matchResult.value)
        }
    }

    fun evaluateExpression(expr: String): Double {
        var clean = expr.replace(".", "").replace("x", "*").replace("×", "*").replace("÷", "/")
        clean = clean.trim()
        while (clean.endsWith("+") || clean.endsWith("-") || clean.endsWith("*") || clean.endsWith("/")) {
            clean = clean.substring(0, clean.length - 1).trim()
        }
        if (clean.isEmpty()) return 0.0
        return try {
            evalSimpleExpr(clean)
        } catch (e: Exception) {
            0.0
        }
    }

    private fun evalSimpleExpr(str: String): Double {
        var opIndex = -1
        var opType = ' '

        // Prioritize + and - from right to left (respects left-associativity)
        for (i in str.length - 1 downTo 0) {
            val c = str[i]
            if (c == '+' || c == '-') {
                opIndex = i
                opType = c
                break
            }
        }

        if (opIndex != -1) {
            val left = str.substring(0, opIndex).trim()
            val right = str.substring(opIndex + 1).trim()
            val leftVal = if (left.isEmpty()) 0.0 else evalSimpleExpr(left)
            val rightVal = if (right.isEmpty()) 0.0 else evalSimpleExpr(right)
            return if (opType == '+') leftVal + rightVal else leftVal - rightVal
        }

        // Parentheses / Higher precedence operators: * and /
        for (i in str.length - 1 downTo 0) {
            val c = str[i]
            if (c == '*' || c == '/') {
                opIndex = i
                opType = c
                break
            }
        }

        if (opIndex != -1) {
            val left = str.substring(0, opIndex).trim()
            val right = str.substring(opIndex + 1).trim()
            val leftVal = if (left.isEmpty()) 1.0 else evalSimpleExpr(left)
            val rightVal = if (right.isEmpty()) 1.0 else evalSimpleExpr(right)
            return if (opType == '*') leftVal * rightVal else {
                if (rightVal == 0.0) 0.0 else leftVal / rightVal
            }
        }

        return str.toDoubleOrNull() ?: 0.0
    }

    fun parseInputNumber(input: String): Double {
        val clean = input.filter { it.isDigit() }
        return clean.toDoubleOrNull() ?: 0.0
    }

    fun parseColor(hex: String): androidx.compose.ui.graphics.Color {
        return try {
            androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) {
            androidx.compose.ui.graphics.Color(0xFF607D8B)
        }
    }

    fun formatDate(timestamp: Long): String {
        return try {
            synchronized(dateFormatter) {
                dateFormatter.format(timestamp)
            }
        } catch (e: Exception) {
            val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
            String.format("%02d/%02d/%04d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
        }
    }

    fun formatDateTime(timestamp: Long): String {
        return try {
            synchronized(dateTimeFormatter) {
                dateTimeFormatter.format(timestamp)
            }
        } catch (e: Exception) {
            "${formatDate(timestamp)} ${formatTime(timestamp)}"
        }
    }

    fun getStartOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getEndOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun getDaysDifference(fromMillis: Long, toMillis: Long): Int {
        val startOfFrom = getStartOfDay(fromMillis)
        val startOfTo = getStartOfDay(toMillis)
        return ((startOfTo - startOfFrom) / 86400000L).toInt()
    }

    fun isEventOngoing(startDate: Long, endDate: Long?, now: Long = System.currentTimeMillis()): Boolean {
        val startOfDay = getStartOfDay(startDate)
        val endOfDay = endDate?.let { getEndOfDay(it) }
        return now >= startOfDay && (endOfDay == null || now <= endOfDay)
    }

    fun isEventEnded(endDate: Long?, now: Long = System.currentTimeMillis()): Boolean {
        if (endDate == null) return false
        val endOfDay = getEndOfDay(endDate)
        return now > endOfDay
    }

    fun isEventUpcoming(startDate: Long, now: Long = System.currentTimeMillis()): Boolean {
        return now < getStartOfDay(startDate)
    }

    fun localDateToUtcMillis(localMillis: Long): Long {
        val localCal = Calendar.getInstance().apply { timeInMillis = localMillis }
        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                localCal.get(Calendar.YEAR),
                localCal.get(Calendar.MONTH),
                localCal.get(Calendar.DAY_OF_MONTH),
                0, 0, 0
            )
        }
        return utcCal.timeInMillis
    }

    fun utcMillisToLocalStartOfDay(utcMillis: Long): Long {
        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        val localCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return localCal.timeInMillis
    }
}

data class FinancialSummary(
    val realIncome: Double,
    val realExpense: Double,
    val netSavings: Double,
    val internalTransfers: Double
)

fun calculateRealFinancialSummary(
    transactions: List<com.app.data.Transaction>,
    savingsWalletIds: Set<Int> = emptySet()
): FinancialSummary {
    var income = 0.0
    var expense = 0.0
    var savings = 0.0
    var internalTransfers = 0.0

    for (tx in transactions) {
        val catName = tx.categoryName.trim()
        val isAdjustment = tx.type == "ADJUSTMENT" || catName.contains("Điều chỉnh số dư", ignoreCase = true)
        val isInternalTransfer = tx.type == "TRANSFER" && (tx.destinationWalletId == null || (tx.walletId !in savingsWalletIds && tx.destinationWalletId !in savingsWalletIds))
        val isSavingsDeposit = catName.contains("Gửi tiết kiệm", ignoreCase = true) || catName.contains("Cất quỹ", ignoreCase = true) || (tx.destinationWalletId != null && tx.destinationWalletId in savingsWalletIds)
        val isSavingsWithdraw = catName.contains("Đóng hũ", ignoreCase = true) || catName.contains("Rút từ tiết kiệm", ignoreCase = true) || (tx.walletId in savingsWalletIds && tx.destinationWalletId != null && tx.destinationWalletId !in savingsWalletIds)

        if (isAdjustment) {
            continue
        }

        if (isInternalTransfer) {
            internalTransfers += tx.amount
            continue
        }

        if (isSavingsDeposit) {
            savings += tx.amount
            continue
        }

        if (isSavingsWithdraw) {
            continue
        }

        when (tx.type) {
            "INCOME" -> {
                if (!isSavingsWithdraw && !catName.contains("Hoàn quỹ", ignoreCase = true)) {
                    income += tx.amount
                }
            }
            "EXPENSE" -> {
                if (!isSavingsDeposit) {
                    expense += tx.amount
                }
            }
        }
    }

    return FinancialSummary(
        realIncome = income,
        realExpense = expense,
        netSavings = savings,
        internalTransfers = internalTransfers
    )
}

