package com.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "wallets")
data class Wallet(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // CASH, BANK, WALLET, SAVINGS, CREDIT
    val balance: Double,
    val colorHex: String,
    val iconName: String,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isClosed: Boolean = false,
    val targetAmount: Double? = null
)

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["notificationKey"], unique = true)]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val walletId: Int,
    val walletName: String,
    val type: String, // INCOME, EXPENSE, TRANSFER
    val amount: Double,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: String,
    val note: String,
    val timestamp: Long,
    val isRecurring: Boolean = false,
    val recurrencePeriod: String = "NONE", // NONE, DAILY, WEEKLY, MONTHLY
    val eventId: Int? = null,
    val destinationWalletId: Int? = null,
    val debtId: Int? = null,
    val notificationKey: String? = null
)

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val startDate: Long,
    val endDate: Long? = null,
    val limitAmount: Double? = null,
    val colorHex: String = "#FF9800",
    val isActive: Boolean = true,
    val displayOrder: Int = 0
)

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: String,
    val limitAmount: Double,
    val spentAmount: Double = 0.0,
    val month: String, // Format: "YYYY-MM"
    val isRecurring: Boolean = false
)

@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val targetDate: Long,
    val note: String = ""
)

@Entity(tableName = "settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val personName: String, // Tên người vay hoặc người cho vay
    val type: String, // "DEBT" (Mình đi vay), "LOAN" (Mình cho vay)
    val totalAmount: Double,
    val remainingAmount: Double,
    val walletId: Int, // Ví liên quan (nhận tiền vay hoặc chi tiền cho vay)
    val creationDate: Long,
    val dueDate: Long? = null,
    val note: String = "",
    val status: String = "ACTIVE", // ACTIVE, COMPLETED
    val repaymentType: String = "FLEXIBLE", // ONE_TIME, INSTALLMENT, FLEXIBLE, PERIODIC_FLEXIBLE, ACCUMULATING
    val periodicAmount: Double? = null,
    val periodType: String? = null // MONTHLY, WEEKLY, YEARLY
)
