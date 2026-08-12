package com.app.ui

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.app.data.AppDatabase
import com.app.data.Transaction
import com.app.data.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], instrumentedPackages = ["androidx.room"])
class FinanceReportTest {

    private lateinit var database: AppDatabase
    private lateinit var viewModel: FinanceViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() = runTest {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Application>()
        
        database = AppDatabase.getDatabase(context)
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
        viewModel = FinanceViewModel(context)
    }

    @After
    fun tearDown() = runTest {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
        Dispatchers.resetMain()
    }

    private suspend fun setupTestData() {
        // Insert a wallet
        val walletId = database.financeDao().insertWallet(
            Wallet(name = "Main Wallet", type = "BANK", balance = 5000.0, colorHex = "#000", iconName = "Icon")
        ).toInt()

        // Insert some transactions
        val t1 = Transaction(
            walletId = walletId, walletName = "Main Wallet", type = "EXPENSE", amount = 100.0,
            categoryName = "Ăn uống", categoryIcon = "icon", categoryColor = "#FFF", note = "Ăn phở",
            timestamp = 1000000L
        )
        val t2 = Transaction(
            walletId = walletId, walletName = "Main Wallet", type = "INCOME", amount = 500.0,
            categoryName = "Lương", categoryIcon = "icon", categoryColor = "#FFF", note = "Lương tháng 7",
            timestamp = 2000000L
        )
        val t3 = Transaction(
            walletId = walletId, walletName = "Main Wallet", type = "EXPENSE", amount = 50.0,
            categoryName = "Đi lại", categoryIcon = "icon", categoryColor = "#FFF", note = "Đổ xăng",
            timestamp = 3000000L
        )

        database.financeDao().insertTransaction(t1)
        database.financeDao().insertTransaction(t2)
        database.financeDao().insertTransaction(t3)
        
        // Wait for flow emission
        for (i in 1..50) {
            if (database.financeDao().getAllTransactions().first().size == 3) break
            Thread.sleep(50)
            testDispatcher.scheduler.advanceTimeBy(10)
        }
    }

    @Test
    fun `filteredTransactions filters by type correctly`() = runTest {
        backgroundScope.launch { viewModel.filteredTransactions.collect {} }
        setupTestData()

        // Act
        viewModel.setTypeFilter("EXPENSE")
        
        // Wait for combine flow
        var filteredList: List<Transaction> = emptyList()
        for (i in 1..50) {
            testDispatcher.scheduler.advanceUntilIdle()
            filteredList = viewModel.filteredTransactions.value
            if (filteredList.size == 2 && filteredList.all { it.type == "EXPENSE" }) break
            Thread.sleep(50)
        }

        // Assert
        assertEquals("Expected 2 elements but got ${filteredList.size}: $filteredList", 2, filteredList.size)
        assertTrue("Expected all EXPENSE but got: $filteredList", filteredList.all { it.type == "EXPENSE" })
    }

    @Test
    fun `filteredTransactions filters by query correctly`() = runTest {
        backgroundScope.launch { viewModel.filteredTransactions.collect {} }
        setupTestData()

        // Act
        viewModel.setSearchQuery("lương")
        
        // Wait for combine flow
        var filteredList: List<Transaction> = emptyList()
        for (i in 1..50) {
            filteredList = viewModel.filteredTransactions.value
            if (filteredList.size == 1) break
            Thread.sleep(50)
            testDispatcher.scheduler.advanceUntilIdle()
        }

        // Assert
        assertEquals(1, filteredList.size)
        assertEquals("Lương", filteredList.first().categoryName)
        assertEquals("Lương tháng 7", filteredList.first().note)
    }

    @Test
    fun `filteredTransactions filters by date range correctly`() = runTest {
        backgroundScope.launch { viewModel.filteredTransactions.collect {} }
        setupTestData()

        // Act
        viewModel.setDateFilterRange(1500000L, 2500000L)
        
        // Wait for combine flow
        var filteredList: List<Transaction> = emptyList()
        for (i in 1..50) {
            filteredList = viewModel.filteredTransactions.value
            if (filteredList.size == 1) break
            Thread.sleep(50)
            testDispatcher.scheduler.advanceUntilIdle()
        }

        // Assert
        assertEquals(1, filteredList.size)
        assertEquals(2000000L, filteredList.first().timestamp)
    }
}
