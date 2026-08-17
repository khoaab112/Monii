package com.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinanceRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: FinanceDao
    private lateinit var repository: FinanceRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.financeDao()
        repository = FinanceRepository(dao, database)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert transaction updates wallet balance correctly`() = runTest {
        // 1. Create a logical wallet
        val walletId = repository.insertWallet(
            Wallet(
                name = "Test Wallet",
                type = "CASH",
                balance = 1000.0,
                colorHex = "#FFFFFF",
                iconName = "Icon"
            )
        ).toInt()

        // 2. Add an EXPENSE transaction
        repository.insertTransaction(
            Transaction(
                walletId = walletId,
                walletName = "Test Wallet",
                type = "EXPENSE",
                amount = 200.0,
                categoryName = "Food",
                categoryIcon = "Food",
                categoryColor = "#FFF",
                note = "Lunch",
                timestamp = System.currentTimeMillis()
            )
        )

        // 3. Check wallet balance
        var wallet = repository.getWalletById(walletId)
        requireNotNull(wallet)
        assertEquals(800.0, wallet.balance, 0.0)

        // 4. Add an INCOME transaction
        repository.insertTransaction(
            Transaction(
                walletId = walletId,
                walletName = "Test Wallet",
                type = "INCOME",
                amount = 500.0,
                categoryName = "Salary",
                categoryIcon = "Salary",
                categoryColor = "#FFF",
                note = "Bonus",
                timestamp = System.currentTimeMillis()
            )
        )

        // 5. Check wallet balance again
        wallet = repository.getWalletById(walletId)
        requireNotNull(wallet)
        assertEquals(1300.0, wallet.balance, 0.0)
    }

    @Test
    fun `delete transaction reverts wallet balance`() = runTest {
        val walletId = repository.insertWallet(
            Wallet(
                name = "Test Wallet",
                type = "CASH",
                balance = 1000.0,
                colorHex = "#FFFFFF",
                iconName = "Icon"
            )
        ).toInt()

        val txId = repository.insertTransaction(
            Transaction(
                walletId = walletId,
                walletName = "Test Wallet",
                type = "EXPENSE",
                amount = 300.0,
                categoryName = "Food",
                categoryIcon = "Food",
                categoryColor = "#FFF",
                note = "Dinner",
                timestamp = System.currentTimeMillis()
            )
        )

        var wallet = repository.getWalletById(walletId)
        assertEquals(700.0, wallet!!.balance, 0.0)

        val tx = repository.getTransactionById(txId.toInt())
        requireNotNull(tx)

        repository.deleteTransaction(tx)

        wallet = repository.getWalletById(walletId)
        assertEquals(1000.0, wallet!!.balance, 0.0)
    }

    @Test
    fun `update transaction correctly adjusts wallet balance`() = runTest {
        val walletId = repository.insertWallet(
            Wallet(
                name = "Test Wallet",
                type = "CASH",
                balance = 1000.0,
                colorHex = "#FFFFFF",
                iconName = "Icon"
            )
        ).toInt()

        val txId = repository.insertTransaction(
            Transaction(
                walletId = walletId,
                walletName = "Test Wallet",
                type = "EXPENSE",
                amount = 200.0,
                categoryName = "Food",
                categoryIcon = "Food",
                categoryColor = "#FFF",
                note = "Lunch",
                timestamp = System.currentTimeMillis()
            )
        )

        // Balance should be 800
        var wallet = repository.getWalletById(walletId)
        assertEquals(800.0, wallet!!.balance, 0.0)

        // Now modify the transaction amount to 150
        val tx = repository.getTransactionById(txId.toInt())
        requireNotNull(tx)

        repository.updateTransaction(tx.copy(amount = 150.0))

        // Reverting -200 means +200 -> 1000, then applying -150 -> 850
        wallet = repository.getWalletById(walletId)
        assertEquals(850.0, wallet!!.balance, 0.0)
    }

    @Test
    fun `expense transaction also updates corresponding budget`() = runTest {
        // Setup budget for the current month
        val cal = Calendar.getInstance()
        val currentMonth = String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        val budgetId = repository.insertBudget(
            Budget(
                categoryName = "Ăn uống",
                categoryIcon = "Food",
                categoryColor = "#FFF",
                limitAmount = 2000.0,
                spentAmount = 0.0,
                month = currentMonth
            )
        ).toInt()

        val walletId = repository.insertWallet(
            Wallet(
                name = "Test Wallet",
                type = "CASH",
                balance = 10000.0,
                colorHex = "#FFFFFF",
                iconName = "Icon"
            )
        ).toInt()

        // Insert Expense
        repository.insertTransaction(
            Transaction(
                walletId = walletId,
                walletName = "Test Wallet",
                type = "EXPENSE",
                amount = 500.0,
                categoryName = "Ăn uống",
                categoryIcon = "Food",
                categoryColor = "#FFF",
                note = "Lunch",
                timestamp = System.currentTimeMillis()
            )
        )

        // Check budget spentAmount
        val budgets = dao.getBudgetsByMonth(currentMonth).first()
        val foodBudget = budgets.find { it.categoryName == "Ăn uống" }
        requireNotNull(foodBudget)
        assertEquals(500.0, foodBudget.spentAmount, 0.0)
    }

    @Test
    fun `reconcile wallet balance via adjustment transactions`() = runTest {
        val walletId = repository.insertWallet(
            Wallet(
                name = "Test Wallet",
                type = "CASH",
                balance = 1000.0,
                colorHex = "#FFFFFF",
                iconName = "Icon"
            )
        ).toInt()

        // 1. Reconcile up (+200)
        val txId1 = repository.insertTransaction(
            Transaction(
                walletId = walletId,
                walletName = "Test Wallet",
                type = "ADJUSTMENT",
                amount = 200.0,
                categoryName = "Điều chỉnh số dư",
                categoryIcon = "AccountBalance",
                categoryColor = "#FF9800",
                note = "Điều chỉnh tăng số dư ví",
                timestamp = System.currentTimeMillis()
            )
        )

        var wallet = repository.getWalletById(walletId)
        assertEquals(1200.0, wallet!!.balance, 0.0)

        // 2. Reconcile down (-500)
        val txId2 = repository.insertTransaction(
            Transaction(
                walletId = walletId,
                walletName = "Test Wallet",
                type = "ADJUSTMENT",
                amount = 500.0,
                categoryName = "Điều chỉnh số dư",
                categoryIcon = "AccountBalance",
                categoryColor = "#FF9800",
                note = "Điều chỉnh giảm số dư ví",
                timestamp = System.currentTimeMillis()
            )
        )

        wallet = repository.getWalletById(walletId)
        assertEquals(700.0, wallet!!.balance, 0.0)

        // 3. Delete the negative adjustment (+500 back to 1200)
        val tx2 = repository.getTransactionById(txId2.toInt())
        requireNotNull(tx2)
        repository.deleteTransaction(tx2)

        wallet = repository.getWalletById(walletId)
        assertEquals(1200.0, wallet!!.balance, 0.0)

        // 4. Update the positive adjustment to be different (+300 instead of +200)
        val tx1 = repository.getTransactionById(txId1.toInt())
        requireNotNull(tx1)
        repository.updateTransaction(tx1.copy(amount = 300.0))

        wallet = repository.getWalletById(walletId)
        assertEquals(1300.0, wallet!!.balance, 0.0)
    }

    @Test
    fun `transfer updates both wallets and cannot target its source`() = runTest {
        val sourceId = repository.insertWallet(Wallet(name = "Source", type = "BANK", balance = 1_000.0, colorHex = "#FFF", iconName = "AccountBalance")).toInt()
        val destinationId = repository.insertWallet(Wallet(name = "Destination", type = "CASH", balance = 200.0, colorHex = "#FFF", iconName = "Payments")).toInt()

        repository.insertTransaction(Transaction(walletId = sourceId, walletName = "Source", type = "TRANSFER", amount = 300.0, categoryName = "Transfer", categoryIcon = "Swap", categoryColor = "#FFF", note = "Transfer", timestamp = System.currentTimeMillis(), destinationWalletId = destinationId))

        assertEquals(700.0, repository.getWalletById(sourceId)!!.balance, 0.0)
        assertEquals(500.0, repository.getWalletById(destinationId)!!.balance, 0.0)
    }

    @Test
    fun `duplicate notification key creates one transaction only`() = runTest {
        val walletId = repository.insertWallet(Wallet(name = "Wallet", type = "BANK", balance = 1_000.0, colorHex = "#FFF", iconName = "AccountBalance")).toInt()
        val transaction = Transaction(walletId = walletId, walletName = "Wallet", type = "INCOME", amount = 100.0, categoryName = "Other", categoryIcon = "Category", categoryColor = "#FFF", note = "Bank alert", timestamp = System.currentTimeMillis(), notificationKey = "bank-notification-1")

        assertTrue(repository.insertTransaction(transaction) > 0)
        assertEquals(-1L, repository.insertTransaction(transaction))
        assertEquals(1_100.0, repository.getWalletById(walletId)!!.balance, 0.0)
    }

    @Test
    fun `editing a transaction can move it to another wallet and change its type`() = runTest {
        val sourceId = repository.insertWallet(Wallet(name = "Source", type = "BANK", balance = 1_000.0, colorHex = "#FFF", iconName = "AccountBalance")).toInt()
        val destinationId = repository.insertWallet(Wallet(name = "Destination", type = "CASH", balance = 500.0, colorHex = "#FFF", iconName = "Payments")).toInt()
        val txId = repository.insertTransaction(Transaction(walletId = sourceId, walletName = "Source", type = "EXPENSE", amount = 200.0, categoryName = "Food", categoryIcon = "Restaurant", categoryColor = "#FFF", note = "Lunch", timestamp = System.currentTimeMillis()))

        repository.updateTransaction(repository.getTransactionById(txId.toInt())!!.copy(walletId = destinationId, walletName = "Destination", type = "INCOME", amount = 300.0))

        assertEquals(1_000.0, repository.getWalletById(sourceId)!!.balance, 0.0)
        assertEquals(800.0, repository.getWalletById(destinationId)!!.balance, 0.0)
    }

    @Test
    fun `updating and deleting an expense keeps its budget accurate`() = runTest {
        val month = String.format("%04d-%02d", Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH) + 1)
        repository.insertBudget(Budget(categoryName = "Food", categoryIcon = "Restaurant", categoryColor = "#FFF", limitAmount = 1_000.0, month = month))
        val walletId = repository.insertWallet(Wallet(name = "Wallet", type = "CASH", balance = 2_000.0, colorHex = "#FFF", iconName = "Payments")).toInt()
        val txId = repository.insertTransaction(Transaction(walletId = walletId, walletName = "Wallet", type = "EXPENSE", amount = 200.0, categoryName = "Food", categoryIcon = "Restaurant", categoryColor = "#FFF", note = "Food", timestamp = System.currentTimeMillis()))

        var transaction = repository.getTransactionById(txId.toInt())!!
        repository.updateTransaction(transaction.copy(amount = 350.0))
        assertEquals(350.0, dao.getBudgetsByMonth(month).first().single().spentAmount, 0.0)

        transaction = repository.getTransactionById(txId.toInt())!!
        repository.deleteTransaction(transaction)
        assertEquals(0.0, dao.getBudgetsByMonth(month).first().single().spentAmount, 0.0)
    }

    @Test
    fun `wallet with transaction history cannot be deleted`() = runTest {
        val wallet = Wallet(name = "Wallet", type = "CASH", balance = 1_000.0, colorHex = "#FFF", iconName = "Payments")
        val walletId = repository.insertWallet(wallet).toInt()
        val persistedWallet = repository.getWalletById(walletId)!!
        repository.insertTransaction(Transaction(walletId = walletId, walletName = "Wallet", type = "EXPENSE", amount = 100.0, categoryName = "Food", categoryIcon = "Restaurant", categoryColor = "#FFF", note = "Food", timestamp = System.currentTimeMillis()))

        assertFalse(repository.deleteWallet(persistedWallet))
        assertTrue(repository.getWalletById(walletId) != null)
    }

    @Test
    fun `deleting an event preserves its transactions but unassigns the event`() = runTest {
        val walletId = repository.insertWallet(Wallet(name = "Wallet", type = "CASH", balance = 1_000.0, colorHex = "#FFF", iconName = "Payments")).toInt()
        val eventId = repository.insertEvent(Event(name = "Trip", description = "", startDate = System.currentTimeMillis())).toInt()
        val txId = repository.insertTransaction(Transaction(walletId = walletId, walletName = "Wallet", type = "EXPENSE", amount = 100.0, categoryName = "Food", categoryIcon = "Restaurant", categoryColor = "#FFF", note = "Food", timestamp = System.currentTimeMillis(), eventId = eventId))

        repository.deleteEvent(repository.allEvents.first().single())

        assertEquals(null, repository.getTransactionById(txId.toInt())!!.eventId)
        assertTrue(repository.allTransactions.first().any { it.id == txId.toInt() })
    }

    @Test
    fun `renaming a category updates its transaction history and budget`() = runTest {
        val month = String.format("%04d-%02d", Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH) + 1)
        val walletId = repository.insertWallet(Wallet(name = "Wallet", type = "CASH", balance = 1_000.0, colorHex = "#FFF", iconName = "Payments")).toInt()
        repository.insertBudget(Budget(categoryName = "Food", categoryIcon = "Restaurant", categoryColor = "#AAA", limitAmount = 1_000.0, month = month))
        val txId = repository.insertTransaction(Transaction(walletId = walletId, walletName = "Wallet", type = "EXPENSE", amount = 100.0, categoryName = "Food", categoryIcon = "Restaurant", categoryColor = "#AAA", note = "Food", timestamp = System.currentTimeMillis()))

        repository.updateCategoryInRelatedData("Food", "Dining", "LocalDining", "#BBB")

        val transaction = repository.getTransactionById(txId.toInt())!!
        val budget = dao.getBudgetsByMonth(month).first().single()
        assertEquals("Dining", transaction.categoryName)
        assertEquals("LocalDining", transaction.categoryIcon)
        assertEquals("#BBB", transaction.categoryColor)
        assertEquals("Dining", budget.categoryName)
        assertEquals("LocalDining", budget.categoryIcon)
        assertEquals("#BBB", budget.categoryColor)
    }

    @Test
    fun `insert TRANSFER transaction correctly updates source and destination wallets`() = runTest {
        val sourceId = repository.insertWallet(Wallet(name = "Source", type = "BANK", balance = 2000.0, colorHex = "#FFF", iconName = "AccountBalance")).toInt()
        val destId = repository.insertWallet(Wallet(name = "Dest", type = "CASH", balance = 100.0, colorHex = "#FFF", iconName = "Payments")).toInt()
        
        repository.insertTransaction(
            Transaction(
                walletId = sourceId,
                walletName = "Source",
                type = "TRANSFER",
                amount = 500.0,
                categoryName = "Transfer",
                categoryIcon = "SwapHoriz",
                categoryColor = "#000",
                destinationWalletId = destId,
                note = "Internal",
                timestamp = System.currentTimeMillis()
            )
        )
        
        assertEquals(1500.0, repository.getWalletById(sourceId)!!.balance, 0.0)
        assertEquals(600.0, repository.getWalletById(destId)!!.balance, 0.0)
    }

    @Test
    fun `debt operations should be fully independent and not affect wallet balances`() = runTest {
        val initialWalletBalance = 5000.0
        val walletId = repository.insertWallet(Wallet(name = "Wallet", type = "CASH", balance = initialWalletBalance, colorHex = "#FFF", iconName = "Payments")).toInt()
        
        // Thêm nợ
        val debtId = repository.insertDebt(Debt(personName = "John", type = "DEBT", totalAmount = 1000.0, remainingAmount = 1000.0, status = "ACTIVE", dueDate = System.currentTimeMillis() + 10000, walletId = walletId, creationDate = System.currentTimeMillis())).toInt()
        
        // Kiểm tra số dư ví KHÔNG bị trừ (chứng minh tính độc lập)
        assertEquals(initialWalletBalance, repository.getWalletById(walletId)!!.balance, 0.0)
        
        // Sửa nợ
        val debt = repository.allDebts.first().first { it.id == debtId }
        repository.updateDebt(debt.copy(remainingAmount = 500.0))
        assertEquals(initialWalletBalance, repository.getWalletById(walletId)!!.balance, 0.0)
        
        // Xóa nợ
        repository.deleteDebt(debt)
        assertEquals(initialWalletBalance, repository.getWalletById(walletId)!!.balance, 0.0)
    }

    @Test
    fun `updating transaction eventId and amount updates repository correctly`() = runTest {
        val walletId = repository.insertWallet(Wallet(name = "Wallet", type = "CASH", balance = 1000000.0, colorHex = "#FFF", iconName = "Payments")).toInt()
        val eventId = repository.insertEvent(Event(name = "Trip", description = "Test trip", startDate = System.currentTimeMillis(), limitAmount = 500000.0, colorHex = "#FF0000")).toInt()

        val txId = repository.insertTransaction(
            Transaction(
                walletId = walletId,
                walletName = "Wallet",
                type = "EXPENSE",
                amount = 100000.0,
                categoryName = "Food",
                categoryIcon = "Restaurant",
                categoryColor = "#FFF",
                note = "Lunch",
                timestamp = System.currentTimeMillis(),
                eventId = eventId
            )
        ).toInt()

        assertEquals(900000.0, repository.getWalletById(walletId)!!.balance, 0.0)

        // Update transaction: change amount to 150000 and unlink event (eventId = null)
        val originalTx = repository.getTransactionById(txId)!!
        repository.updateTransaction(originalTx.copy(amount = 150000.0, eventId = null))

        val updatedTx = repository.getTransactionById(txId)!!
        assertEquals(150000.0, updatedTx.amount, 0.0)
        org.junit.Assert.assertNull(updatedTx.eventId)
        assertEquals(850000.0, repository.getWalletById(walletId)!!.balance, 0.0)
    }
}
