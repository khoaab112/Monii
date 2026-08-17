package com.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class FilterCriteria(
    val query: String,
    val type: String,
    val category: String,
    val start: Long?,
    val end: Long?
)

data class NotificationLog(
    val timestamp: Long,
    val title: String,
    val text: String,
    val bankName: String,
    val amount: Double,
    val type: String,
    val note: String,
    val walletName: String,
    val status: String, // "AUTO_ADDED", "FAILED_PARSE", "NO_WALLET"
    val notificationKey: String = ""
)

data class SmartWalletMapping(
    val bankName: String,
    val refKey: String,
    val walletId: Int,
    val walletName: String,
    val confidenceScore: Int,
    val lastConfirmed: Long
)

enum class NotificationType {
    SUCCESS, WARNING, ERROR
}

data class AppNotification(
    val id: String = java.util.UUID.randomUUID().toString(),
    val message: String,
    val type: NotificationType,
    val durationMs: Long = 3500L
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository

    private val _customTopBarTitle = MutableStateFlow<String?>(null)
    val customTopBarTitle: StateFlow<String?> = _customTopBarTitle.asStateFlow()

    fun setCustomTopBarTitle(title: String?) {
        _customTopBarTitle.value = title
    }

    private val _appNotifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val appNotifications: StateFlow<List<AppNotification>> = _appNotifications.asStateFlow()

    fun showNotification(message: String, type: NotificationType) {
        val newNotification = AppNotification(message = message, type = type)
        _appNotifications.value = _appNotifications.value + newNotification
        viewModelScope.launch {
            kotlinx.coroutines.delay(newNotification.durationMs)
            _appNotifications.value = _appNotifications.value.filter { it.id != newNotification.id }
        }
    }

    fun showSuccessNotification(message: String) = showNotification(message, NotificationType.SUCCESS)
    fun showWarningNotification(message: String) = showNotification(message, NotificationType.WARNING)
    fun showErrorNotification(message: String) = showNotification(message, NotificationType.ERROR)

    // Notification listener flows
    private val _notificationReaderEnabled = MutableStateFlow(false)
    val notificationReaderEnabled: StateFlow<Boolean> = _notificationReaderEnabled.asStateFlow()

    private val _notificationLogs = MutableStateFlow<List<NotificationLog>>(emptyList())
    val notificationLogs: StateFlow<List<NotificationLog>> = _notificationLogs.asStateFlow()

    private val _smartMappings = MutableStateFlow<List<SmartWalletMapping>>(emptyList())
    val smartMappings: StateFlow<List<SmartWalletMapping>> = _smartMappings.asStateFlow()

    private val _widgetsEnabled = MutableStateFlow(false)
    val widgetsEnabled: StateFlow<Boolean> = _widgetsEnabled.asStateFlow()

    // Local Backup Flow Variables
    private val _localBackupLastTime = MutableStateFlow("Chưa sao lưu")
    val localBackupLastTime: StateFlow<String> = _localBackupLastTime.asStateFlow()

    private val _localBackupCount = MutableStateFlow(0)
    val localBackupCount: StateFlow<Int> = _localBackupCount.asStateFlow()

    private val _syncStatus = MutableStateFlow("") // "IDLE", "SYNCING", "SUCCESS", "ERROR"
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _syncProgressLogs = MutableStateFlow<List<String>>(emptyList())
    val syncProgressLogs: StateFlow<List<String>> = _syncProgressLogs.asStateFlow()

    private val _lastBackupFile = MutableStateFlow<java.io.File?>(null)
    val lastBackupFile: StateFlow<java.io.File?> = _lastBackupFile.asStateFlow()

    // Base flows
    val allWallets: StateFlow<List<Wallet>>
    val allTransactions: StateFlow<List<Transaction>>
    val allBudgets: StateFlow<List<Budget>>
    val allSavingsGoals: StateFlow<List<SavingsGoal>>
    val allEvents: StateFlow<List<Event>>
    val allDebts: StateFlow<List<Debt>>

    val dailyWallets: StateFlow<List<Wallet>>
    val savingsWallets: StateFlow<List<Wallet>>
    val dailyTransactions: StateFlow<List<Transaction>>
    val savingsTransactions: StateFlow<List<Transaction>>

    private val _categoriesList = MutableStateFlow<List<FinanceCategory>>(Categories.list)
    val categoriesList: StateFlow<List<FinanceCategory>> = _categoriesList.asStateFlow()

    // PIN Protection Flow
    private val _isPinEnabled = MutableStateFlow(false)
    val isPinEnabled: StateFlow<Boolean> = _isPinEnabled.asStateFlow()

    private val _isCloudSyncEnabled = MutableStateFlow(false)
    val isCloudSyncEnabled: StateFlow<Boolean> = _isCloudSyncEnabled.asStateFlow()


    private val _geminiApiKey = MutableStateFlow("")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<com.app.service.GeminiAdvisorService.ChatMessage>>(
        listOf(
            com.app.service.GeminiAdvisorService.ChatMessage(
                role = "model",
                text = "Xin chào! Tôi là Trợ lý Cố vấn Tài chính AI của Monii. 💡\n\nTôi có thể giúp bạn phân tích chi tiêu, kiểm soát ngân sách, theo dõi các khoản nợ và đề xuất kế hoạch quản lý tài chính hiệu quả.\n\nBạn có thể chạm vào **biểu tượng mẫu câu** ở bên trái thanh nhập hoặc tự đặt câu hỏi tài chính cho tôi!"
            )
        )
    )
    val chatMessages: StateFlow<List<com.app.service.GeminiAdvisorService.ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    fun sendAIChatMessage(messageText: String) {
        val trimmed = messageText.trim()
        if (trimmed.isBlank() || _isChatLoading.value) return

        val userMsg = com.app.service.GeminiAdvisorService.ChatMessage(
            role = "user",
            text = trimmed
        )
        val currentHistory = _chatMessages.value
        _chatMessages.value = currentHistory + userMsg
        _isChatLoading.value = true

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val apiKey = geminiApiKey.value
            val financialContext = buildFinancialContextSummary()
            val aiReplyText = com.app.service.GeminiAdvisorService.sendChatMessage(
                history = currentHistory,
                userMessage = trimmed,
                financialContext = financialContext,
                customApiKey = apiKey
            )

            val aiMsg = com.app.service.GeminiAdvisorService.ChatMessage(
                role = "model",
                text = aiReplyText
            )
            _chatMessages.value = _chatMessages.value + aiMsg
            _isChatLoading.value = false
        }
    }

    fun clearAIChatHistory() {
        _chatMessages.value = listOf(
            com.app.service.GeminiAdvisorService.ChatMessage(
                role = "model",
                text = "Xin chào! Tôi là Trợ lý Cố vấn Tài chính AI của Monii. 💡\n\nTôi có thể giúp bạn phân tích chi tiêu, kiểm soát ngân sách, theo dõi các khoản nợ và đề xuất kế hoạch quản lý tài chính hiệu quả.\n\nBạn có thể chạm vào **biểu tượng mẫu câu** ở bên trái thanh nhập hoặc tự đặt câu hỏi tài chính cho tôi!"
            )
        )
    }

    private fun buildFinancialContextSummary(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val monthSdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val currentMonthStr = monthSdf.format(Date())

        // 1. Wallets & Total Balance
        val wallets = allWallets.value
        val totalBalance = wallets.sumOf { it.balance }
        val walletsText = if (wallets.isEmpty()) "Chưa có ví tiền nào"
        else wallets.joinToString("\n") { w ->
            val typeStr = when (w.type) {
                "CASH" -> "Tiền mặt"
                "BANK" -> "Tài khoản Ngân hàng"
                "WALLET" -> "Ví điện tử"
                "SAVINGS" -> "Sổ tiết kiệm"
                "CREDIT" -> "Thẻ tín dụng"
                else -> w.type
            }
            "- Ví '${w.name}' ($typeStr): ${FormatHelper.formatVND(w.balance)}"
        }

        // 2. Transactions & Period Breakdown
        val allTxs = allTransactions.value
        val currentMonthTxs = allTxs.filter {
            val txMonth = monthSdf.format(Date(it.timestamp))
            txMonth == currentMonthStr
        }

        val totalIncomeMonth = currentMonthTxs.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpenseMonth = currentMonthTxs.filter { it.type == "EXPENSE" }.sumOf { it.amount }

        // Expenses by category for current month
        val expenseByCatMonth = currentMonthTxs.filter { it.type == "EXPENSE" }
            .groupBy { it.categoryName }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .joinToString("\n") { "- Hạng mục '${it.key}': ${FormatHelper.formatVND(it.value)}" }

        // Recent 40 transactions with details (notes, date, amount, category, wallet)
        val recentTxsText = if (allTxs.isEmpty()) "Chưa có lịch sử giao dịch"
        else allTxs.take(40).joinToString("\n") { tx ->
            val typeLabel = if (tx.type == "INCOME") "THU" else if (tx.type == "EXPENSE") "CHI" else "CHUYỂN TIỀN"
            val dateStr = sdf.format(Date(tx.timestamp))
            val noteStr = if (tx.note.isNotBlank()) " | Ghi chú: '${tx.note}'" else ""
            "- [$dateStr] [$typeLabel] ${FormatHelper.formatVND(tx.amount)} | Hạng mục: '${tx.categoryName}' | Ví: '${tx.walletName}'$noteStr"
        }

        // 3. Budgets
        val budgets = allBudgets.value
        val budgetsText = if (budgets.isEmpty()) "Chưa thiết lập ngân sách hạn mức nào"
        else budgets.joinToString("\n") { b ->
            val remaining = b.limitAmount - b.spentAmount
            val statusStr = if (remaining < 0) "VƯỢT HẠN MỨC ${FormatHelper.formatVND(-remaining)}" else "Còn lại ${FormatHelper.formatVND(remaining)}"
            "- Hạn mức '${b.categoryName}': Giới hạn ${FormatHelper.formatVND(b.limitAmount)} | Đã tiêu ${FormatHelper.formatVND(b.spentAmount)} ($statusStr) [Tháng ${b.month}]"
        }

        // 4. Debts & Loans
        val debts = allDebts.value
        val debtsText = if (debts.isEmpty()) "Không có khoản nợ hay cho vay nào"
        else debts.joinToString("\n") { d ->
            val debtTypeLabel = if (d.type == "DEBT") "Tôi vay của" else "Tôi cho vay"
            val dueStr = d.dueDate?.let { " | Hạn trả: ${sdf.format(Date(it))}" } ?: ""
            val statusLabel = if (d.status == "COMPLETED") "Đã hoàn thành" else "Đang theo dõi"
            val noteStr = if (d.note.isNotBlank()) " | Ghi chú: '${d.note}'" else ""
            "- [$statusLabel] $debtTypeLabel '${d.personName}': Tổng ${FormatHelper.formatVND(d.totalAmount)}, Còn nợ lại ${FormatHelper.formatVND(d.remainingAmount)}$dueStr$noteStr"
        }

        // 5. Savings Goals
        val savingsGoals = allSavingsGoals.value
        val savingsGoalsText = if (savingsGoals.isEmpty()) "Chưa có mục tiêu tích lũy/tiết kiệm nào"
        else savingsGoals.joinToString("\n") { g ->
            val targetDateStr = sdf.format(Date(g.targetDate))
            val progress = if (g.targetAmount > 0) ((g.currentAmount / g.targetAmount) * 100).toInt() else 0
            "- Mục tiêu '${g.name}': Đã tích lũy ${FormatHelper.formatVND(g.currentAmount)} / ${FormatHelper.formatVND(g.targetAmount)} ($progress%) | Hạn chót: $targetDateStr"
        }

        // 6. Events
        val events = allEvents.value
        val eventsText = if (events.isEmpty()) "Chưa có sự kiện/chuyến đi nào"
        else events.joinToString("\n") { e ->
            val eventTxs = allTxs.filter { it.eventId == e.id }
            val spent = eventTxs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val limitStr = e.limitAmount?.let { " | Hạn mức: ${FormatHelper.formatVND(it)}" } ?: ""
            "- Sự kiện '${e.name}': Đã chi ${FormatHelper.formatVND(spent)}$limitStr"
        }

        return """
            ================ TÀI CHÍNH NỘI BỘ THỜI GIAN THỰC CỦA NGƯỜI DÙNG ================

            1. TỔNG SỐ DƯ & DANH SÁCH VÍ TIỀN:
            - Tổng số dư khả dụng toàn bộ ví: ${FormatHelper.formatVND(totalBalance)}
            $walletsText

            2. THU CHI THÁNG HIỆN TẠI ($currentMonthStr):
            - Tổng thu nhập tháng này: ${FormatHelper.formatVND(totalIncomeMonth)}
            - Tổng chi tiêu tháng này: ${FormatHelper.formatVND(totalExpenseMonth)}
            - Chi tiết chi tiêu theo từng hạng mục tháng này:
            ${if (expenseByCatMonth.isBlank()) "Chưa có chi tiêu tháng này" else expenseByCatMonth}

            3. TÌNH HÌNH HẠN MỨC NGÂN SÁCH CHI TIÊU:
            $budgetsText

            4. SỔ THEO DÕI NỢ & CHO VAY:
            $debtsText

            5. MỤC TIÊU TÍCH LŨY / TIẾT KIỆM:
            $savingsGoalsText

            6. SỰ KIỆN / CHUYẾN ĐỊNH KỲ:
            $eventsText

            7. LỊCH SỬ 40 GIAO DỊCH GẦN ĐÂY NHẤT (CHI TIẾT NGÀY, HẠNG MỤC, VÍ, GHI CHÚ):
            $recentTxsText

            ================================================================================
        """.trimIndent()
    }

    private val _advisorResult = MutableStateFlow<com.app.service.GeminiAdvisorService.AdvisorResult?>(null)
    val advisorResult: StateFlow<com.app.service.GeminiAdvisorService.AdvisorResult?> = _advisorResult.asStateFlow()

    private val _advisorLoading = MutableStateFlow(false)
    val advisorLoading: StateFlow<Boolean> = _advisorLoading.asStateFlow()

    private val _aiTransactionResult = MutableStateFlow<com.app.service.GeminiAdvisorService.AITransactionResult?>(null)
    val aiTransactionResult: StateFlow<com.app.service.GeminiAdvisorService.AITransactionResult?> = _aiTransactionResult.asStateFlow()

    private val _aiTransactionLoading = MutableStateFlow(false)
    val aiTransactionLoading: StateFlow<Boolean> = _aiTransactionLoading.asStateFlow()

    private val _focusedWalletId = MutableStateFlow<Int?>(null)
    val focusedWalletId: StateFlow<Int?> = _focusedWalletId.asStateFlow()

    fun setFocusedWalletId(id: Int?) {
        _focusedWalletId.value = id
    }

    // Start Screen Flow ("dashboard", "history", "stats", "add_transaction", "settings")
    private val _startScreen = MutableStateFlow("add_transaction")
    val startScreen: StateFlow<String> = _startScreen.asStateFlow()

    // Saved starting screen preference for settings UI
    private val _preferredStartScreen = MutableStateFlow("add_transaction")
    val preferredStartScreen: StateFlow<String> = _preferredStartScreen.asStateFlow()

    private val _isAppUnlocked = MutableStateFlow(false)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    private val _hasSeenOnboarding = MutableStateFlow(false)
    val hasSeenOnboarding: StateFlow<Boolean> = _hasSeenOnboarding.asStateFlow()

    val isDatabaseEmpty: StateFlow<Boolean>

    private val _openBankNotificationsEvent = MutableStateFlow(false)
    val openBankNotificationsEvent: StateFlow<Boolean> = _openBankNotificationsEvent.asStateFlow()

    fun triggerOpenBankNotifications() {
        _openBankNotificationsEvent.value = true
    }

    fun consumeOpenBankNotificationsEvent() {
        _openBankNotificationsEvent.value = false
    }

    private val _isLoadingSettings = MutableStateFlow(true)
    val isLoadingSettings: StateFlow<Boolean> = _isLoadingSettings.asStateFlow()

    private val _savedPinHash = MutableStateFlow("")
    val savedPinHash: StateFlow<String> = _savedPinHash.asStateFlow()

    // Transaction History Filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow("ALL") // ALL, INCOME, EXPENSE
    val selectedTypeFilter: StateFlow<String> = _selectedTypeFilter.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow("ALL") // ALL, and individual categories
    val selectedCategoryFilter: StateFlow<String> = _selectedCategoryFilter.asStateFlow()

    private val _filterStartDate = MutableStateFlow<Long?>(null)
    val filterStartDate: StateFlow<Long?> = _filterStartDate.asStateFlow()

    private val _filterEndDate = MutableStateFlow<Long?>(null)
    val filterEndDate: StateFlow<Long?> = _filterEndDate.asStateFlow()

    // Filtered transactions
    val filteredTransactions: StateFlow<List<Transaction>>

    // Current Active Month (For budgets and statistics) - default to current month
    private val _activeMonth = MutableStateFlow(
        Calendar.getInstance().let { cal ->
            String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        }
    )
    val activeMonth: StateFlow<String> = _activeMonth.asStateFlow()

    fun copyBudgetsFromPreviousMonth(targetMonth: String) {
        viewModelScope.launch {
            val parts = targetMonth.split("-")
            var y = parts[0].toInt()
            var m = parts[1].toInt()
            m--
            if (m < 1) {
                m = 12
                y--
            }
            val prevMonth = String.format("%04d-%02d", y, m)
            val allBud = repository.getAllBudgets().firstOrNull() ?: emptyList()
            val prevBudgets = allBud.filter { it.month == prevMonth }
            if (prevBudgets.isNotEmpty()) {
                for (b in prevBudgets) {
                    addBudget(b.categoryName, b.limitAmount, targetMonth, b.isRecurring)
                }
                showSuccessNotification("Đã sao chép ${prevBudgets.size} ngân sách từ tháng $prevMonth!")
            } else {
                showWarningNotification("Không tìm thấy ngân sách từ tháng $prevMonth để sao chép.")
            }
        }
    }

    init {
        val database = AppDatabase.getDatabase(application)
        val dao = database.financeDao()
        repository = FinanceRepository(dao, database)

        // Base states with distinctUntilChanged() optimization to avoid duplicate emissions
        allWallets = repository.allWallets
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        isDatabaseEmpty = repository.allWallets
            .map { it.isEmpty() }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

        allTransactions = repository.allTransactions
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        dailyWallets = repository.allWallets
            .map { list -> list.filter { it.type != "SAVINGS" } }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        savingsWallets = repository.allWallets
            .map { list -> list.filter { it.type == "SAVINGS" } }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        dailyTransactions = combine(repository.allTransactions, repository.allWallets) { txs, wts ->
            val savingsIds = wts.filter { it.type == "SAVINGS" }.map { it.id }.toSet()
            txs.filter { it.walletId !in savingsIds || (it.destinationWalletId != null && it.destinationWalletId !in savingsIds) }
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        savingsTransactions = combine(repository.allTransactions, repository.allWallets) { txs, wts ->
            val savingsIds = wts.filter { it.type == "SAVINGS" }.map { it.id }.toSet()
            txs.filter { it.walletId in savingsIds || (it.destinationWalletId != null && it.destinationWalletId in savingsIds) }
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allBudgets = repository.getAllBudgets()
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allSavingsGoals = repository.allSavingsGoals
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allEvents = repository.allEvents
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allDebts = repository.allDebts
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Setup filter flow with distinctUntilChanged()
        val filterCriteriaFlow = combine(
            _searchQuery,
            _selectedTypeFilter,
            _selectedCategoryFilter,
            _filterStartDate,
            _filterEndDate
        ) { q, t, c, s, e ->
            FilterCriteria(q, t, c, s, e)
        }.distinctUntilChanged()

        filteredTransactions = combine(
            dailyTransactions,
            filterCriteriaFlow
        ) { txs, criteria ->
            txs.filter { tx ->
                val matchesQuery = criteria.query.isEmpty() ||
                        tx.note.contains(criteria.query, ignoreCase = true) ||
                        tx.categoryName.contains(criteria.query, ignoreCase = true) ||
                        tx.walletName.contains(criteria.query, ignoreCase = true)
                val matchesType = criteria.type == "ALL" || tx.type == criteria.type
                val matchesCategory = criteria.category == "ALL" || tx.categoryName == criteria.category
                val matchesStart = criteria.start == null || tx.timestamp >= criteria.start
                val matchesEnd = criteria.end == null || tx.timestamp <= criteria.end

                matchesQuery && matchesType && matchesCategory && matchesStart && matchesEnd
            }
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Check settings and default data
        viewModelScope.launch {
            try {
                repository.allWallets.first()
                loadCategories()
                loadSecuritySettings()
                loadNotificationSettings()
                processRecurringTransactions()
                processRecurringBudgets()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                kotlinx.coroutines.delay(300)
                _isLoadingSettings.value = false
            }

            // Auto-trigger background cloud sync if enabled on app startup
            try {
                if (_isCloudSyncEnabled.value) {
                    com.app.service.CloudSyncWorker.setupPeriodicSync(getApplication())
                    triggerSilentCloudSync()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Automatic background sync observer: triggers silent background sync 3s after any database change
        viewModelScope.launch(Dispatchers.IO) {
            combine(allWallets, allTransactions, allBudgets) { _, _, _ -> Unit }
                .drop(1)
                .debounce(3000L)
                .collect {
                    if (_isCloudSyncEnabled.value) {
                        triggerSilentCloudSync()
                    }
                }
        }
    }

    // --- SECURITY PIN LOGIC ---
    private suspend fun loadSecuritySettings() {
        val pinEnabledSetting = repository.getSetting("pin_enabled")
        val pinHashSetting = repository.getSetting("pin_hash")
        val widgetsSetting = repository.getSetting("widgets_enabled")
        val startScreenSetting = repository.getSetting("start_screen")
        val cloudSyncSetting = repository.getSetting("cloud_sync_enabled")
        val geminiApiKeySetting = repository.getSetting("gemini_api_key")
        val hasSeenOnboardingSetting = repository.getSetting("has_seen_onboarding")

        val enabled = pinEnabledSetting?.value == "true"
        _isPinEnabled.value = enabled
        _isCloudSyncEnabled.value = cloudSyncSetting?.value == "true"
        _hasSeenOnboarding.value = hasSeenOnboardingSetting?.value == "true"



        _geminiApiKey.value = geminiApiKeySetting?.value ?: ""
        _savedPinHash.value = pinHashSetting?.value ?: ""
        _widgetsEnabled.value = widgetsSetting?.value == "true"
        
        val startVal = startScreenSetting?.value ?: "add_transaction"
        _startScreen.value = startVal
        _preferredStartScreen.value = startVal
        
        // If PIN is not enabled, the app is unlocked by default
        _isAppUnlocked.value = !enabled

        val lastTime = repository.getSetting("local_backup_last_time")?.value ?: "Chưa sao lưu"
        _localBackupLastTime.value = lastTime
        
        try {
            val context = getApplication<Application>()
            val backupDir = context.getExternalFilesDir("Backups")
            val filesList = backupDir?.listFiles { _, name -> name.endsWith(".json") }
            _localBackupCount.value = filesList?.size ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setWidgetsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.saveSetting("widgets_enabled", enabled.toString())
            _widgetsEnabled.value = enabled
        }
    }

    // --- BANK NOTIFICATION READER LOGIC ---
    private suspend fun loadNotificationSettings() {
        val enabledSetting = repository.getSetting("notification_reader_enabled")
        _notificationReaderEnabled.value = enabledSetting?.value == "true"
        loadSmartMappings()
        loadNotificationLogs()
    }

    fun loadSmartMappings() {
        viewModelScope.launch {
            val mappingsSetting = repository.getSetting("smart_wallet_mappings")?.value ?: "[]"
            val list = mutableListOf<SmartWalletMapping>()
            try {
                val array = org.json.JSONArray(mappingsSetting)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        SmartWalletMapping(
                            bankName = obj.optString("bankName", ""),
                            refKey = obj.optString("refKey", ""),
                            walletId = obj.optInt("walletId", 0),
                            walletName = obj.optString("walletName", ""),
                            confidenceScore = obj.optInt("confidenceScore", 1),
                            lastConfirmed = obj.optLong("lastConfirmed", System.currentTimeMillis())
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _smartMappings.value = list
        }
    }

    fun saveSmartMappings(list: List<SmartWalletMapping>) {
        viewModelScope.launch {
            val array = org.json.JSONArray()
            for (item in list) {
                val obj = org.json.JSONObject()
                obj.put("bankName", item.bankName)
                obj.put("refKey", item.refKey)
                obj.put("walletId", item.walletId)
                obj.put("walletName", item.walletName)
                obj.put("confidenceScore", item.confidenceScore)
                obj.put("lastConfirmed", item.lastConfirmed)
                array.put(obj)
            }
            repository.saveSetting("smart_wallet_mappings", array.toString())
            _smartMappings.value = list
        }
    }

    fun extractRefKey(title: String, text: String): String {
        val combined = "$title $text"
        val regex = Regex("""\b(?:tk|stk|tai\s*khoan|tài\s*khoản|account|so\s*tk|sotk|card|the|thẻ)\s*([a-zA-Z0-9]{3,16})\b""", RegexOption.IGNORE_CASE)
        val match = regex.find(combined)
        if (match != null) {
            return match.groupValues[1].uppercase()
        }
        val digitsRegex = Regex("""\b\d{4,16}\b""")
        val digitsMatch = digitsRegex.find(text)
        if (digitsMatch != null) {
            return digitsMatch.value
        }
        return ""
    }

    fun getSmartWalletRecommendation(log: NotificationLog, wallets: List<Wallet>): Pair<Wallet?, Int> {
        val mappings = _smartMappings.value
        val refKey = extractRefKey(log.title, log.text)
        
        if (refKey.isNotEmpty()) {
            val specificMatch = mappings.filter { 
                it.bankName.equals(log.bankName, ignoreCase = true) && 
                it.refKey.equals(refKey, ignoreCase = true) 
            }.maxByOrNull { it.confidenceScore }
            
            if (specificMatch != null) {
                val foundWallet = wallets.find { it.id == specificMatch.walletId }
                if (foundWallet != null) {
                    return Pair(foundWallet, specificMatch.confidenceScore)
                }
            }
        }
        
        val generalMatch = mappings.filter {
            it.bankName.equals(log.bankName, ignoreCase = true) && 
            it.refKey.isEmpty()
        }.maxByOrNull { it.confidenceScore }
        
        if (generalMatch != null) {
            val foundWallet = wallets.find { it.id == generalMatch.walletId }
            if (foundWallet != null) {
                return Pair(foundWallet, generalMatch.confidenceScore)
            }
        }
        
        val fallbackWallet = wallets.find { it.name.lowercase().contains(log.bankName.lowercase()) }
            ?: wallets.find { it.name.lowercase().contains(log.walletName.lowercase()) }
            ?: wallets.find { it.type == "BANK" }
            ?: wallets.firstOrNull()
            
        return Pair(fallbackWallet, 0)
    }

    fun loadNotificationLogs() {
        viewModelScope.launch {
            loadNotificationLogsSync()
        }
    }

    private suspend fun loadNotificationLogsSync() {
        var logsSetting = repository.getSetting("notification_logs")?.value ?: "[]"
        if (logsSetting.trim().isEmpty()) {
            logsSetting = "[]"
        }

        val list = mutableListOf<NotificationLog>()
        var modified = false
        val now = System.currentTimeMillis()
        val limitTime = now - (3L * 24 * 60 * 60 * 1000) // 3 days in ms
        try {
            val array = org.json.JSONArray(logsSetting)
            val newJSONArray = org.json.JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val timestamp = obj.optLong("timestamp", now)
                val status = obj.optString("status", "")
                if (status == "PENDING" || status == "DELETED" || timestamp >= limitTime) {
                    if (status != "DELETED") {
                        list.add(
                            NotificationLog(
                                timestamp = timestamp,
                                title = obj.optString("title", ""),
                                text = obj.optString("text", ""),
                                bankName = obj.optString("bankName", ""),
                                amount = obj.optDouble("amount", 0.0),
                                type = obj.optString("type", "EXPENSE"),
                                note = obj.optString("note", ""),
                                walletName = obj.optString("walletName", ""),
                                status = status,
                                notificationKey = obj.optString("notificationKey", "")
                            )
                        )
                    }
                    newJSONArray.put(obj)
                } else {
                    modified = true
                }
            }
            if (modified) {
                repository.saveSetting("notification_logs", newJSONArray.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _notificationLogs.value = list
    }

    fun resetSamplePendingLogs() {
        viewModelScope.launch {
            repository.saveSetting("notification_logs", "[]")
            loadNotificationLogs()
        }
    }

    fun setNotificationReaderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.saveSetting("notification_reader_enabled", enabled.toString())
            _notificationReaderEnabled.value = enabled
        }
    }

    fun scanNotificationsManual(
        context: android.content.Context,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                var serviceInstance = com.app.service.BankNotificationListenerService.instance
                if (serviceInstance == null) {
                    // Try to rebind service specifically for aggresive battery managers (like Xiaomi HyperOS)
                    com.app.service.BankNotificationListenerService.requestRebindService(context)
                    kotlinx.coroutines.delay(1500) // Wait up to 1.5s for service to bind
                    serviceInstance = com.app.service.BankNotificationListenerService.instance
                }

                if (serviceInstance == null) {
                    onError("Dịch vụ đọc thông báo chưa hoạt động. Hãy chắc chắn rằng bạn đã cấp quyền hoặc vui lòng khởi động lại dịch vụ bằng cách tắt/bật lại quyền.")
                    return@launch
                }

                val activeNotifications = try {
                    serviceInstance.activeNotifications
                } catch (e: Exception) {
                    onError("Không thể đọc thông báo chủ động từ hệ thống: ${e.localizedMessage}")
                    return@launch
                }

                if (activeNotifications.isNullOrEmpty()) {
                    onSuccess(0)
                    return@launch
                }

                var countAdded = 0
                val wallets = repository.allWallets.firstOrNull() ?: emptyList()

                for (sbn in activeNotifications) {
                    val packageName = sbn.packageName ?: ""
                    val extras = sbn.notification?.extras ?: continue
                    val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
                    val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""

                    if (text.isBlank()) continue

                    val parsed = com.app.service.NotificationParser.parse(title, text, packageName)
                    if (!parsed.success) continue

                    // Check for duplicate in current state/database logs
                    val logsSetting = repository.getSetting("notification_logs")?.value ?: "[]"
                    val jsonArray = try { org.json.JSONArray(logsSetting) } catch (e: Exception) { org.json.JSONArray() }
                    var duplicate = false
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        if (obj.optString("text") == text && obj.optString("title") == title) {
                            duplicate = true
                            break
                        }
                    }
                    if (duplicate) continue

                    // Find matched wallet
                    val matchedWallet = wallets.find { it.name.lowercase().contains(parsed.bankName.lowercase()) }
                        ?: wallets.find { it.name.lowercase().contains(parsed.detectedWalletName.lowercase()) }
                        ?: wallets.find { it.type == "BANK" }
                        ?: wallets.firstOrNull()

                    val walletLabel = matchedWallet?.name ?: parsed.detectedWalletName
                    saveLocalLog(title, text, parsed, "PENDING", walletLabel)
                    countAdded++
                }

                // Wait for the new items to populate the flow
                loadNotificationLogsSync()
                onSuccess(countAdded)
            } catch (e: Exception) {
                onError("Có lỗi: ${e.localizedMessage}")
            }
        }
    }

    fun clearNotificationLogs() {
        viewModelScope.launch {
            repository.saveSetting("notification_logs", "[]")
            _notificationLogs.value = emptyList()
        }
    }

    fun simulateBankNotification(title: String, text: String, packageName: String) {
        viewModelScope.launch {
            try {
                val parsed = com.app.service.NotificationParser.parse(title, text, packageName)
                if (parsed.success) {
                    val wallets = (repository.allWallets.firstOrNull() ?: emptyList()).filter { it.type != "SAVINGS" }
                    val matchedWallet = wallets.find { it.name.lowercase().contains(parsed.bankName.lowercase()) }
                        ?: wallets.find { it.name.lowercase().contains(parsed.detectedWalletName.lowercase()) }
                        ?: wallets.find { it.type == "BANK" }
                        ?: wallets.firstOrNull()

                    val walletLabel = matchedWallet?.name ?: parsed.detectedWalletName
                    saveLocalLog(title, text, parsed, "PENDING", walletLabel)
                    
                    // Reload logs
                    loadNotificationLogs()
                } else {
                    showWarningNotification("Nội dung tin nhắn không khớp cú pháp ngân hàng nào. Không lưu vào danh sách chờ!")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showErrorNotification("Lỗi mô phỏng: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun saveLocalLog(
        title: String,
        text: String,
        parsed: com.app.service.NotificationParser.ParsedNotification,
        status: String,
        walletName: String?
    ) {
        val logsSetting = repository.getSetting("notification_logs")?.value ?: "[]"
        val jsonArray = try { org.json.JSONArray(logsSetting) } catch (e: Exception) { org.json.JSONArray() }

        val logObj = org.json.JSONObject()
        logObj.put("timestamp", System.currentTimeMillis())
        logObj.put("title", title)
        logObj.put("text", text)
        logObj.put("bankName", parsed.bankName)
        logObj.put("amount", parsed.amount)
        logObj.put("type", parsed.type)
        logObj.put("note", parsed.note)
        logObj.put("walletName", walletName ?: parsed.detectedWalletName)
        logObj.put("status", status)

        val newList = org.json.JSONArray()
        newList.put(logObj)
        val limitTime = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000) // 3 days in ms
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val timestamp = item.optLong("timestamp", 0L)
            val statusVal = item.optString("status", "")
            if (statusVal == "PENDING" || statusVal == "DELETED" || timestamp >= limitTime) {
                newList.put(item)
            }
        }

        repository.saveSetting("notification_logs", newList.toString())
    }

    fun getActiveEventIdForTimestamp(timestamp: Long): Int? {
        val activeEvents = allEvents.value.filter {
            it.isActive && FormatHelper.isEventOngoing(it.startDate, it.endDate, timestamp)
        }.sortedWith(compareBy<com.app.data.Event> {
            if (it.endDate != null) 0 else 1
        }.thenBy {
            if (it.endDate != null) (it.endDate - it.startDate) else Long.MAX_VALUE
        }.thenBy {
            it.endDate?.let { end -> FormatHelper.getEndOfDay(end) } ?: Long.MAX_VALUE
        }.thenByDescending {
            it.startDate
        })
        return activeEvents.firstOrNull()?.id
    }

    fun confirmPendingNotificationLog(
        log: NotificationLog,
        walletId: Int,
        categoryName: String,
        overrideAmount: Double? = null,
        overrideNote: String? = null,
        overrideEventId: Int? = null
    ) {
        viewModelScope.launch {
            val wallet = repository.getWalletById(walletId) ?: return@launch
            val catDetails = getCategoryByName(categoryName)
            
            val walletsList = repository.allWallets.firstOrNull() ?: emptyList()
            val (suggestedWallet, _) = getSmartWalletRecommendation(log, walletsList)
            val suggestedWalletId = suggestedWallet?.id ?: 0
            
            val refKey = extractRefKey(log.title, log.text)
            val currentMappings = _smartMappings.value.toMutableList()
            
            val existingIndex = if (refKey.isNotEmpty()) {
                currentMappings.indexOfFirst { 
                    it.bankName.equals(log.bankName, ignoreCase = true) && 
                    it.refKey.equals(refKey, ignoreCase = true) 
                }
            } else {
                currentMappings.indexOfFirst { 
                    it.bankName.equals(log.bankName, ignoreCase = true) && 
                    it.refKey.isEmpty() 
                }
            }
            
            if (walletId == suggestedWalletId) {
                if (existingIndex != -1) {
                    val existing = currentMappings[existingIndex]
                    currentMappings[existingIndex] = existing.copy(
                        confidenceScore = existing.confidenceScore + 1,
                        lastConfirmed = System.currentTimeMillis()
                    )
                } else {
                    currentMappings.add(
                        SmartWalletMapping(
                            bankName = log.bankName,
                            refKey = refKey,
                            walletId = walletId,
                            walletName = wallet.name,
                            confidenceScore = 1,
                            lastConfirmed = System.currentTimeMillis()
                        )
                    )
                }
            } else {
                if (existingIndex != -1) {
                    val existing = currentMappings[existingIndex]
                    currentMappings[existingIndex] = existing.copy(
                        walletId = walletId,
                        walletName = wallet.name,
                        confidenceScore = 1,
                        lastConfirmed = System.currentTimeMillis()
                    )
                } else {
                    currentMappings.add(
                        SmartWalletMapping(
                            bankName = log.bankName,
                            refKey = refKey,
                            walletId = walletId,
                            walletName = wallet.name,
                            confidenceScore = 1,
                            lastConfirmed = System.currentTimeMillis()
                        )
                    )
                }
            }
            
            saveSmartMappings(currentMappings)
            
            val resolvedEventId = overrideEventId ?: getActiveEventIdForTimestamp(log.timestamp)
            val tx = Transaction(
                walletId = walletId,
                walletName = wallet.name,
                type = log.type,
                amount = overrideAmount ?: log.amount,
                categoryName = categoryName,
                categoryIcon = catDetails.iconName,
                categoryColor = catDetails.colorHex,
                note = overrideNote?.takeIf { it.isNotBlank() } ?: log.note.takeIf { it.isNotBlank() } ?: "Ghi từ thông báo",
                timestamp = log.timestamp,
                eventId = resolvedEventId,
                notificationKey = log.notificationKey.takeIf { it.isNotBlank() }
            )
            repository.insertTransaction(tx)
            updateLogStatus(log, "AUTO_ADDED", wallet.name)
        }
    }

    fun confirmPendingInternalTransfer(
        logExpense: NotificationLog,
        logIncome: NotificationLog,
        sourceWalletId: Int,
        destWalletId: Int,
        amount: Double,
        note: String
    ) {
        viewModelScope.launch {
            val sourceWallet = repository.getWalletById(sourceWalletId) ?: return@launch
            val destWallet = repository.getWalletById(destWalletId) ?: return@launch
            
            val tx = Transaction(
                walletId = sourceWalletId,
                walletName = sourceWallet.name,
                type = "TRANSFER",
                amount = amount,
                categoryName = "Chuyển khoản",
                categoryIcon = "swap_horiz",
                categoryColor = "#2196F3",
                note = note.takeIf { it.isNotBlank() } ?: "Chuyển tiền nội bộ",
                timestamp = logExpense.timestamp,
                destinationWalletId = destWalletId,
                eventId = null
            )
            repository.insertTransaction(tx)
            updateLogStatus(logExpense, "AUTO_ADDED", sourceWallet.name)
            updateLogStatus(logIncome, "AUTO_ADDED", destWallet.name)
        }
    }

    fun confirmPendingNotificationLogsBulk(logs: List<NotificationLog>, walletId: Int) {
        viewModelScope.launch {
            val wallet = repository.getWalletById(walletId) ?: return@launch
            val currentCategories = _categoriesList.value
            
            val logsSetting = repository.getSetting("notification_logs")?.value ?: "[]"
            val jsonArray = try { org.json.JSONArray(logsSetting) } catch (e: Exception) { org.json.JSONArray() }
            
            val targetKeys = logs.map { Pair(it.timestamp, it.text) }.toSet()
            
            val walletsList = repository.allWallets.firstOrNull() ?: emptyList()
            val currentMappings = _smartMappings.value.toMutableList()
            
            for (log in logs) {
                val matchedCategory = currentCategories.find { it.name.lowercase() == log.note.lowercase() }
                    ?: currentCategories.find { it.type == "BOTH" || it.type == log.type }
                    ?: currentCategories.firstOrNull()
                val categoryName = matchedCategory?.name ?: "Khác"
                val categoryIcon = matchedCategory?.iconName ?: "category"
                val categoryColor = matchedCategory?.colorHex ?: "#9E9E9E"
                
                val (suggestedWallet, _) = getSmartWalletRecommendation(log, walletsList)
                val suggestedWalletId = suggestedWallet?.id ?: 0
                val refKey = extractRefKey(log.title, log.text)
                
                val existingIndex = if (refKey.isNotEmpty()) {
                    currentMappings.indexOfFirst { 
                        it.bankName.equals(log.bankName, ignoreCase = true) && 
                        it.refKey.equals(refKey, ignoreCase = true) 
                    }
                } else {
                    currentMappings.indexOfFirst { 
                        it.bankName.equals(log.bankName, ignoreCase = true) && 
                        it.refKey.isEmpty() 
                    }
                }
                
                if (walletId == suggestedWalletId) {
                    if (existingIndex != -1) {
                        val existing = currentMappings[existingIndex]
                        currentMappings[existingIndex] = existing.copy(
                            confidenceScore = existing.confidenceScore + 1,
                            lastConfirmed = System.currentTimeMillis()
                        )
                    } else {
                        currentMappings.add(
                            SmartWalletMapping(
                                bankName = log.bankName,
                                refKey = refKey,
                                walletId = walletId,
                                walletName = wallet.name,
                                confidenceScore = 1,
                                lastConfirmed = System.currentTimeMillis()
                            )
                        )
                    }
                } else {
                    if (existingIndex != -1) {
                        val existing = currentMappings[existingIndex]
                        currentMappings[existingIndex] = existing.copy(
                            walletId = walletId,
                            walletName = wallet.name,
                            confidenceScore = 1,
                            lastConfirmed = System.currentTimeMillis()
                        )
                    } else {
                        currentMappings.add(
                            SmartWalletMapping(
                                bankName = log.bankName,
                                refKey = refKey,
                                walletId = walletId,
                                walletName = wallet.name,
                                confidenceScore = 1,
                                lastConfirmed = System.currentTimeMillis()
                            )
                        )
                    }
                }
                
                val matchedEventId = getActiveEventIdForTimestamp(log.timestamp)
                val tx = Transaction(
                    walletId = walletId,
                    walletName = wallet.name,
                    type = log.type,
                    amount = log.amount,
                    categoryName = categoryName,
                    categoryIcon = categoryIcon,
                    categoryColor = categoryColor,
                    note = log.note.ifEmpty { "Ghi từ thông báo hàng loạt" },
                    timestamp = log.timestamp,
                    eventId = matchedEventId,
                    notificationKey = log.notificationKey.takeIf { it.isNotBlank() }
                )
                repository.insertTransaction(tx)
            }
            
            saveSmartMappings(currentMappings)
            
            val newList = org.json.JSONArray()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val timestamp = obj.optLong("timestamp", 0L)
                val text = obj.optString("text", "")
                if (targetKeys.contains(Pair(timestamp, text))) {
                    obj.put("status", "AUTO_ADDED")
                    obj.put("walletName", wallet.name)
                }
                newList.put(obj)
            }
            
            repository.saveSetting("notification_logs", newList.toString())
            loadNotificationLogs()
        }
    }

    fun deleteNotificationLog(log: NotificationLog) {
        viewModelScope.launch {
            updateLogStatus(log, "DELETED", log.walletName)
        }
    }

    fun deleteNotificationLogsBulk(logs: List<NotificationLog>, deleteCompletely: Boolean = true) {
        viewModelScope.launch {
            val logsSetting = repository.getSetting("notification_logs")?.value ?: "[]"
            val jsonArray = try { org.json.JSONArray(logsSetting) } catch (e: Exception) { org.json.JSONArray() }
            
            val targetKeys = logs.map { Pair(it.timestamp, it.text) }.toSet()
            val newList = org.json.JSONArray()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val timestamp = obj.optLong("timestamp", 0L)
                val text = obj.optString("text", "")
                if (targetKeys.contains(Pair(timestamp, text))) {
                    if (deleteCompletely) {
                        continue // completely remove so it can be scanned again
                    } else {
                        obj.put("status", "DELETED")
                        newList.put(obj)
                    }
                } else {
                    newList.put(obj)
                }
            }
            repository.saveSetting("notification_logs", newList.toString())
            loadNotificationLogs()
        }
    }

    private suspend fun updateLogStatus(log: NotificationLog, newStatus: String, walletName: String) {
        val logsSetting = repository.getSetting("notification_logs")?.value ?: "[]"
        val jsonArray = try { org.json.JSONArray(logsSetting) } catch (e: Exception) { org.json.JSONArray() }
        val newList = org.json.JSONArray()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val timestamp = obj.optLong("timestamp", 0L)
            val text = obj.optString("text", "")
            val title = obj.optString("title", "")
            if (timestamp == log.timestamp && text == log.text && (title.isEmpty() || log.title.isEmpty() || title == log.title)) {
                obj.put("status", newStatus)
                obj.put("walletName", walletName)
            }
            newList.put(obj)
        }
        repository.saveSetting("notification_logs", newList.toString())
        loadNotificationLogsSync()
    }

    fun addManualTransactionFromLog(log: NotificationLog, walletId: Int, categoryName: String) {
        viewModelScope.launch {
            val wallet = repository.getWalletById(walletId) ?: return@launch
            val cat = getCategoryByName(categoryName)
            val matchedEventId = getActiveEventIdForTimestamp(log.timestamp)
            val tx = Transaction(
                walletId = walletId,
                walletName = wallet.name,
                type = log.type,
                amount = log.amount,
                categoryName = categoryName,
                categoryIcon = cat.iconName,
                categoryColor = cat.colorHex,
                note = log.note.ifEmpty { "Ghi từ thông báo" },
                timestamp = log.timestamp,
                eventId = matchedEventId,
                notificationKey = log.notificationKey.takeIf { it.isNotBlank() }
            )
            repository.insertTransaction(tx)
        }
    }

    // --- CATEGORIES LOGIC ---
    private fun serializeCategories(categories: List<FinanceCategory>): String {
        return categories.joinToString(";;") { "${it.name}|${it.iconName}|${it.colorHex}|${it.type}|${it.parentName ?: ""}" }
    }

    private fun deserializeCategories(data: String): List<FinanceCategory> {
        if (data.isEmpty()) return emptyList()
        return data.split(";;").mapNotNull {
            val parts = it.split("|")
            if (parts.size >= 4) {
                val parentName = if (parts.size >= 5 && parts[4].isNotEmpty()) parts[4] else null
                FinanceCategory(parts[0], parts[1], parts[2], parts[3], parentName)
            } else null
        }
    }

    private suspend fun loadCategories() {
        val catSetting = repository.getSetting("custom_categories")
        if (catSetting != null) {
            _categoriesList.value = deserializeCategories(catSetting.value)
        } else {
            // Seed defaults
            repository.saveSetting("custom_categories", serializeCategories(Categories.list))
            _categoriesList.value = Categories.list
        }
    }

    fun syncSystemCategories(categoriesToAdd: List<FinanceCategory>) {
        viewModelScope.launch {
            val currentList = _categoriesList.value.toMutableList()
            val existingNames = currentList.map { it.name.trim().lowercase() }.toSet()
            val toAdd = categoriesToAdd.filter { it.name.trim().lowercase() !in existingNames }
            if (toAdd.isNotEmpty()) {
                currentList.addAll(toAdd)
                repository.saveSetting("custom_categories", serializeCategories(currentList))
                _categoriesList.value = currentList
            }
        }
    }

    fun addCategory(name: String, iconName: String, colorHex: String, type: String, parentName: String? = null) {
        viewModelScope.launch {
            val currentList = _categoriesList.value.toMutableList()
            if (!currentList.any { it.name.lowercase() == name.lowercase() }) {
                currentList.add(FinanceCategory(name, iconName, colorHex, type, parentName))
                repository.saveSetting("custom_categories", serializeCategories(currentList))
                _categoriesList.value = currentList
            }
        }
    }

    fun updateCategory(oldCategory: FinanceCategory, newName: String, newIconName: String, newColorHex: String, newType: String, newParentName: String? = null) {
        viewModelScope.launch {
            val currentList = _categoriesList.value.toMutableList()
            
            // Check if user is renaming to a name that already exists (and it's not the same old name)
            if (newName.lowercase() != oldCategory.name.lowercase() && currentList.any { it.name.lowercase() == newName.lowercase() }) {
                // Return if name conflict
                return@launch
            }

            // Find index to update
            val index = currentList.indexOfFirst { it.name.lowercase() == oldCategory.name.lowercase() }
            if (index != -1) {
                // Replace category
                currentList[index] = FinanceCategory(newName, newIconName, newColorHex, newType, newParentName)
                
                // Also update any child category's parentName if the parent was renamed!
                if (newName != oldCategory.name) {
                    for (i in currentList.indices) {
                        if (currentList[i].parentName?.lowercase() == oldCategory.name.lowercase()) {
                            currentList[i] = currentList[i].copy(parentName = newName)
                        }
                    }
                }

                repository.saveSetting("custom_categories", serializeCategories(currentList))
                _categoriesList.value = currentList
                
                // Cascade update to transactions and budgets
                repository.updateCategoryInRelatedData(oldCategory.name, newName, newIconName, newColorHex)
            }
        }
    }

    fun deleteCategory(category: FinanceCategory) {
        viewModelScope.launch {
            val currentList = _categoriesList.value.toMutableList()
            val iterator = currentList.iterator()
            while (iterator.hasNext()) {
                val cat = iterator.next()
                if (cat.name.lowercase() == category.name.lowercase()) {
                    iterator.remove()
                }
            }
            
            // If the deleted category was a parent, detach its children (set parentName = null) so they are not orphaned
            for (i in currentList.indices) {
                if (currentList[i].parentName?.lowercase() == category.name.lowercase()) {
                    currentList[i] = currentList[i].copy(parentName = null)
                }
            }
            
            repository.saveSetting("custom_categories", serializeCategories(currentList))
            _categoriesList.value = currentList
        }
    }

    fun updateCategoriesOrder(orderedSubList: List<FinanceCategory>, typeTab: String) {
        viewModelScope.launch {
            val fullList = _categoriesList.value.toMutableList()
            val subNames = orderedSubList.map { it.name.lowercase() }.toSet()
            val newList = mutableListOf<FinanceCategory>()
            var subIndex = 0
            for (cat in fullList) {
                if (cat.name.lowercase() in subNames) {
                    if (subIndex < orderedSubList.size) {
                        newList.add(orderedSubList[subIndex])
                        subIndex++
                    }
                } else {
                    newList.add(cat)
                }
            }
            while (subIndex < orderedSubList.size) {
                newList.add(orderedSubList[subIndex])
                subIndex++
            }
            repository.saveSetting("custom_categories", serializeCategories(newList))
            _categoriesList.value = newList
        }
    }

    fun getCategoryByName(name: String): FinanceCategory {
        return _categoriesList.value.firstOrNull { it.name.lowercase() == name.lowercase() }
            ?: FinanceCategory("Khác", "Category", "#607D8B", "BOTH")
    }

    fun enablePin(pin: String) {
        viewModelScope.launch {
            repository.saveSetting("pin_enabled", "true")
            repository.saveSetting("pin_hash", pin)
            _isPinEnabled.value = true
            _savedPinHash.value = pin
            _isAppUnlocked.value = true
        }
    }

    fun setHasSeenOnboarding(hasSeen: Boolean) {
        viewModelScope.launch {
            repository.saveSetting("has_seen_onboarding", hasSeen.toString())
            _hasSeenOnboarding.value = hasSeen
            
            // If starting fresh as a new user and database is empty, initialize default wallets
            val initialWallets = repository.allWallets.first()
            if (hasSeen && initialWallets.isEmpty()) {
                val cashWallet = Wallet(name = "Tiền mặt", type = "CASH", balance = 0.0, colorHex = "#4CAF50", iconName = "wallet")
                val bankWallet = Wallet(name = "Ngân hàng", type = "BANK", balance = 0.0, colorHex = "#2196F3", iconName = "account_balance")
                repository.insertWallet(cashWallet)
                repository.insertWallet(bankWallet)
            }
        }
    }

    fun disablePin() {
        viewModelScope.launch {
            repository.saveSetting("pin_enabled", "false")
            repository.saveSetting("pin_hash", "")
            _isPinEnabled.value = false
            _savedPinHash.value = ""
            _isAppUnlocked.value = true
        }
    }

    fun triggerSilentCloudSync() {
        if (_isCloudSyncEnabled.value) {
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(getApplication())?.account
            if (account != null) {
                com.app.service.CloudSyncWorker.triggerOneTimeSync(getApplication())
            }
        }
    }

    fun toggleCloudSync(enabled: Boolean) {
        viewModelScope.launch {
            repository.saveSetting("cloud_sync_enabled", enabled.toString())
            _isCloudSyncEnabled.value = enabled
            if (enabled) {
                com.app.service.CloudSyncWorker.setupPeriodicSync(getApplication())
                triggerSilentCloudSync()
            }
        }
    }

    fun saveGeminiApiKey(key: String) {
        viewModelScope.launch {
            repository.saveSetting("gemini_api_key", key)
            _geminiApiKey.value = key
            showSuccessNotification("Đã lưu API Key thành công!")
        }
    }

    fun resetAITransactionResult() {
        _aiTransactionResult.value = null
    }

    fun parseTransactionsWithAI(text: String) {
        if (_geminiApiKey.value.isBlank()) {
            _aiTransactionResult.value = com.app.service.GeminiAdvisorService.AITransactionResult(
                success = false,
                errorMessage = "API Key chưa được cấu hình! Vui lòng cài đặt Gemini API Key trong phần Cài đặt."
            )
            return
        }
        
        _aiTransactionLoading.value = true
        _aiTransactionResult.value = null
        
        viewModelScope.launch {
            try {
                val wallets = allWallets.value
                val walletsInfo = wallets.joinToString(", ") { it.name }
                val categories = _categoriesList.value
                val categoriesInfo = categories.joinToString(", ") { it.name }

                val result = com.app.service.GeminiAdvisorService.parseTransactionsFromText(
                    inputText = text,
                    walletsInfo = walletsInfo,
                    categoriesInfo = categoriesInfo,
                    customApiKey = _geminiApiKey.value
                )
                _aiTransactionResult.value = result
            } catch (e: Exception) {
                _aiTransactionResult.value = com.app.service.GeminiAdvisorService.AITransactionResult(
                    success = false,
                    errorMessage = "Lỗi xử lý AI: ${e.message}"
                )
            } finally {
                _aiTransactionLoading.value = false
            }
        }
    }

    fun runFinancialAdvisor() {
        if (_geminiApiKey.value.isBlank()) {
            _advisorResult.value = com.app.service.GeminiAdvisorService.AdvisorResult(
                assessment = "",
                warnings = emptyList(),
                recommendations = emptyList(),
                success = false,
                errorMessage = "API Key chưa được cấu hình! Vui lòng cài đặt Gemini API Key trong phần Cài đặt."
            )
            return
        }

        _advisorLoading.value = true
        _advisorResult.value = null

        viewModelScope.launch {
            try {
                // 1. Gather wallets
                val wallets = allWallets.value
                val walletsInfo = wallets.joinToString("\n") { 
                    "- ${it.name}: ${FormatHelper.formatVND(it.balance)} (${if (it.type == "SAVINGS") "Hũ tiết kiệm" else "Ví chi tiêu"})" 
                }

                // 2. Monthly Summary
                val currentMonthStr = activeMonth.value // format: YYYY-MM
                val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
                val txs = allTransactions.value.filter { 
                    try {
                        val date = Date(it.timestamp)
                        sdf.format(date) == currentMonthStr
                    } catch (e: Exception) {
                        false
                    }
                }

                val savingsWalletIds = savingsWallets.value.map { it.id }.toSet()
                val currentSummary = calculateRealFinancialSummary(txs, savingsWalletIds)
                val totalIncome = currentSummary.realIncome
                val totalExpense = currentSummary.realExpense

                // Calculate previous month values
                val cal = Calendar.getInstance()
                val year = currentMonthStr.substring(0, 4).toInt()
                val month = currentMonthStr.substring(5, 7).toInt()
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month - 2)
                val prevMonthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
                val prevMonthTxs = allTransactions.value.filter { 
                    try {
                        val date = Date(it.timestamp)
                        sdf.format(date) == prevMonthStr
                    } catch (e: Exception) {
                        false
                    }
                }
                val prevSummary = calculateRealFinancialSummary(prevMonthTxs, savingsWalletIds)
                val prevTotalIncome = prevSummary.realIncome
                val prevTotalExpense = prevSummary.realExpense

                val monthlySummary = "Tháng hiện tại: $currentMonthStr\n- Tổng Thu nhập: ${FormatHelper.formatVND(totalIncome)}\n- Tổng Chi tiêu: ${FormatHelper.formatVND(totalExpense)}\nTháng trước ($prevMonthStr):\n- Tổng Thu nhập: ${FormatHelper.formatVND(prevTotalIncome)}\n- Tổng Chi tiêu: ${FormatHelper.formatVND(prevTotalExpense)}"

                // 3. Top Expenses
                val topExpensesList = txs.filter { it.type == "EXPENSE" }
                    .sortedByDescending { it.amount }
                    .take(5)
                val topExpenses = if (topExpensesList.isEmpty()) "Không có chi tiêu nào trong tháng này." else {
                    topExpensesList.joinToString("\n") { 
                        "- ${it.categoryName} (${it.note}): ${FormatHelper.formatVND(it.amount)}" 
                    }
                }

                // 4. Debts Info
                val debts = allDebts.value
                val debtsInfo = if (debts.isEmpty()) "Không có khoản nợ/cho vay nào." else {
                    debts.joinToString("\n") {
                        val typeText = if (it.type == "DEBT") "Đi vay" else "Cho vay"
                        "- ${it.personName} (${typeText}): Tổng ${FormatHelper.formatVND(it.totalAmount)}, Còn lại: ${FormatHelper.formatVND(it.remainingAmount)} (Loại hình: ${it.repaymentType})"
                    }
                }

                // 5. Budgets Info
                val budgets = allBudgets.value
                val budgetsInfo = if (budgets.isEmpty()) "Chưa thiết lập hạn mức chi tiêu." else {
                    budgets.joinToString("\n") { b ->
                        val spent = txs.filter { it.type == "EXPENSE" && it.categoryName == b.categoryName }.sumOf { it.amount }
                        "- Hạng mục ${b.categoryName}: Hạn mức ${FormatHelper.formatVND(b.limitAmount)}, Đã tiêu ${FormatHelper.formatVND(spent)}"
                    }
                }

                // 6. Events Info
                val events = allEvents.value
                val eventsInfo = if (events.isEmpty()) "Không có sự kiện chi tiêu nào." else {
                    events.joinToString("\n") { e ->
                        val spent = allTransactions.value.filter { it.eventId == e.id && it.type == "EXPENSE" }.sumOf { it.amount }
                        "- Sự kiện ${e.name}: Hạn mức ${if (e.limitAmount != null) FormatHelper.formatVND(e.limitAmount) else "Không giới hạn"}, Đã chi ${FormatHelper.formatVND(spent)}"
                    }
                }

                // 7. Savings Goals Info
                val goals = allSavingsGoals.value
                val savingsGoalsInfo = if (goals.isEmpty()) "Chưa thiết lập mục tiêu tích lũy." else {
                    goals.joinToString("\n") { g ->
                        "- Mục tiêu ${g.name}: Cần đạt ${FormatHelper.formatVND(g.targetAmount)}, Hiện có ${FormatHelper.formatVND(g.currentAmount)} (Hạn tới: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(g.targetDate))})"
                    }
                }

                // Call Service
                val result = com.app.service.GeminiAdvisorService.getFinancialAdvice(
                    walletsInfo = walletsInfo,
                    monthlySummary = monthlySummary,
                    topExpenses = topExpenses,
                    debtsInfo = debtsInfo,
                    budgetsInfo = budgetsInfo,
                    eventsInfo = eventsInfo,
                    savingsGoalsInfo = savingsGoalsInfo,
                    customApiKey = _geminiApiKey.value
                )

                _advisorResult.value = result
            } catch (e: Exception) {
                _advisorResult.value = com.app.service.GeminiAdvisorService.AdvisorResult(
                    assessment = "",
                    warnings = emptyList(),
                    recommendations = emptyList(),
                    success = false,
                    errorMessage = "Lỗi xử lý dữ liệu: ${e.message}"
                )
            } finally {
                _advisorLoading.value = false
            }
        }
    }


    fun setStartScreen(route: String) {
        viewModelScope.launch {
            repository.saveSetting("start_screen", route)
            _preferredStartScreen.value = route
        }
    }

    fun unlockApp(pin: String): Boolean {
        return if (pin == _savedPinHash.value) {
            _isAppUnlocked.value = true
            true
        } else {
            false
        }
    }

    fun lockApp() {
        if (_isPinEnabled.value) {
            _isAppUnlocked.value = false
        }
    }

    // --- RECURRING BUDGETS ENGINE ---
    private suspend fun processRecurringBudgets() {
        val allBud = repository.getAllBudgets().firstOrNull() ?: return
        val currentMonth = _activeMonth.value
        val recurringBudgets = allBud.filter { it.isRecurring }
        
        // Find latest month
        val latestMonth = allBud.maxByOrNull { it.month }?.month ?: currentMonth
        
        // Let's create budgets for the *next* month if it's the 1st of the month or something?
        // Actually, let's just create if it is currently after the month of the budget. 
        // No, that's not quite right.
        // If a budget for 2026-05 exists and it's 2026-06, we should create 2026-06.
        val nextMonth = getNextMonth(currentMonth)
        
        for (budget in recurringBudgets) {
            // Check if budget exists for the month following budget.month
            val budgetMonth = budget.month
            val nextBudgetMonth = getNextMonth(budgetMonth)
            
            if (nextBudgetMonth <= currentMonth) {
                if (allBud.none { it.categoryName == budget.categoryName && it.month == nextBudgetMonth }) {
                    // Create
                    addBudget(budget.categoryName, budget.limitAmount, nextBudgetMonth, true)
                }
            }
        }
    }

    private fun getNextMonth(month: String): String {
        return try {
            val parts = month.split("-")
            if (parts.size < 2) return month
            var y = parts[0].toInt()
            var m = parts[1].toInt()
            m++
            if (m > 12) {
                m = 1
                y++
            }
            String.format("%04d-%02d", y, m)
        } catch (e: Exception) {
            month
        }
    }

    // --- RECURRING TRANSACTIONS ENGINE ---
    private suspend fun processRecurringTransactions() {
        try {
            // Find existing recurring transactions and check if duplicate is due
            val txs = repository.allTransactions.firstOrNull() ?: return
            val recurringSource = txs.filter { it.isRecurring && it.recurrencePeriod != "NONE" }
            
            val currentTime = System.currentTimeMillis()
            
            for (src in recurringSource) {
                var lastOccurrenceTime = src.timestamp
                // Find other occurrences in history of the same source
                val related = txs.filter { it.note == src.note && it.amount == src.amount && it.walletId == src.walletId && it.categoryName == src.categoryName }
                val latestInstance = related.maxByOrNull { it.timestamp }
                if (latestInstance != null) {
                    lastOccurrenceTime = latestInstance.timestamp
                }

                val intervalMs = when (src.recurrencePeriod) {
                    "DAILY" -> 24L * 60L * 60L * 1000L
                    "WEEKLY" -> 7L * 24L * 60L * 60L * 1000L
                    "MONTHLY" -> 30L * 24L * 60L * 60L * 1000L // Simple approximation
                    else -> Long.MAX_VALUE
                }

                if (intervalMs <= 0 || intervalMs == Long.MAX_VALUE) continue

                var nextTime = lastOccurrenceTime + intervalMs
                var iterations = 0
                while (nextTime <= currentTime && iterations < 12) {
                    // Insert a duplicate dated nextTime!
                    val newTx = Transaction(
                        walletId = src.walletId,
                        walletName = src.walletName,
                        type = src.type,
                        amount = src.amount,
                        categoryName = src.categoryName,
                        categoryIcon = src.categoryIcon,
                        categoryColor = src.categoryColor,
                        note = src.note,
                        timestamp = nextTime,
                        isRecurring = src.isRecurring,
                        recurrencePeriod = src.recurrencePeriod
                    )
                    repository.insertTransaction(newTx)
                    nextTime += intervalMs
                    iterations++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- WALLETS SERVICES ---
    fun addWallet(name: String, type: String, initialBalance: Double, colorHex: String, iconName: String, targetAmount: Double? = null) {
        viewModelScope.launch {
            val list = allWallets.value
            val maxOrder = list.maxOfOrNull { it.displayOrder } ?: -1
            repository.insertWallet(
                Wallet(
                    name = name,
                    type = type,
                    balance = initialBalance,
                    colorHex = colorHex,
                    iconName = iconName,
                    displayOrder = maxOrder + 1,
                    targetAmount = targetAmount
                )
            )
        }
    }

    fun moveWalletUp(wallet: Wallet) {
        viewModelScope.launch {
            val list = allWallets.value
            val needsReassign = list.map { it.displayOrder }.toSet().size < list.size
            val sortedList = if (needsReassign) {
                list.sortedBy { it.id }.mapIndexed { index, w -> 
                    val updated = w.copy(displayOrder = index)
                    repository.updateWallet(updated)
                    updated
                }
            } else {
                list.sortedWith(compareBy<Wallet> { it.displayOrder }.thenBy { it.id })
            }
            
            val index = sortedList.indexOfFirst { it.id == wallet.id }
            if (index > 0) {
                val prev = sortedList[index - 1]
                val prevOrder = prev.displayOrder
                val currOrder = sortedList[index].displayOrder
                
                repository.updateWallet(prev.copy(displayOrder = currOrder))
                repository.updateWallet(sortedList[index].copy(displayOrder = prevOrder))
            }
        }
    }

    fun moveWalletDown(wallet: Wallet) {
        viewModelScope.launch {
            val list = allWallets.value
            val needsReassign = list.map { it.displayOrder }.toSet().size < list.size
            val sortedList = if (needsReassign) {
                list.sortedBy { it.id }.mapIndexed { index, w -> 
                    val updated = w.copy(displayOrder = index)
                    repository.updateWallet(updated)
                    updated
                }
            } else {
                list.sortedWith(compareBy<Wallet> { it.displayOrder }.thenBy { it.id })
            }
            
            val index = sortedList.indexOfFirst { it.id == wallet.id }
            if (index >= 0 && index < sortedList.lastIndex) {
                val next = sortedList[index + 1]
                val nextOrder = next.displayOrder
                val currOrder = sortedList[index].displayOrder
                
                repository.updateWallet(next.copy(displayOrder = currOrder))
                repository.updateWallet(sortedList[index].copy(displayOrder = nextOrder))
            }
        }
    }

    fun updateWallet(wallet: Wallet) {
        viewModelScope.launch {
            repository.updateWallet(wallet)
        }
    }

    fun deleteWallet(wallet: Wallet) {
        viewModelScope.launch {
            val deleted = repository.deleteWallet(wallet)
            if (!deleted) {
                showWarningNotification("Không thể xóa ví đang có lịch sử giao dịch. Hãy đóng ví để lưu trữ lịch sử.")
            }
        }
    }

    fun closeSavingsVault(vault: Wallet, targetWalletId: Int?) {
        viewModelScope.launch {
            val freshVault = repository.getWalletById(vault.id) ?: vault
            if (freshVault.balance > 0 && targetWalletId != null) {
                val targetWallet = repository.getWalletById(targetWalletId)
                if (targetWallet != null) {
                    val transferTx = Transaction(
                        walletId = freshVault.id,
                        walletName = freshVault.name,
                        type = "TRANSFER",
                        amount = freshVault.balance,
                        categoryName = "Đóng hũ tiết kiệm",
                        categoryIcon = "Savings",
                        categoryColor = freshVault.colorHex,
                        note = "Chuyển số dư tích lũy về ví ${targetWallet.name} để đóng hũ",
                        timestamp = System.currentTimeMillis(),
                        destinationWalletId = targetWallet.id
                    )
                    repository.insertTransaction(transferTx)
                }
            }
            val updatedVault = freshVault.copy(isClosed = true, balance = 0.0)
            repository.updateWallet(updatedVault)
        }
    }

    fun updateWalletsOrder(orderedList: List<Wallet>) {
        viewModelScope.launch {
            orderedList.forEachIndexed { index, wallet ->
                if (wallet.displayOrder != index) {
                    repository.updateWallet(wallet.copy(displayOrder = index))
                }
            }
        }
    }

    fun reconcileWallet(walletId: Int, actualBalance: Double) {
        viewModelScope.launch {
            val wallet = repository.getWalletById(walletId) ?: return@launch
            val difference = actualBalance - wallet.balance
            
            if (difference != 0.0) {
                val amount = Math.abs(difference)
                
                val tx = Transaction(
                    walletId = wallet.id,
                    walletName = wallet.name,
                    type = "ADJUSTMENT",
                    amount = amount,
                    categoryName = "Điều chỉnh số dư",
                    categoryIcon = "AccountBalance",
                    categoryColor = "#FF9800",
                    note = if (difference > 0) "Điều chỉnh tăng số dư ví" else "Điều chỉnh giảm số dư ví",
                    timestamp = System.currentTimeMillis()
                )
                repository.insertTransaction(tx)
                showSuccessNotification("Đã cân bằng số dư ví thành công!")
            }
        }
    }

    // --- TRANSACTIONS SERVICES ---
    fun addTransaction(
        walletId: Int,
        type: String,
        amount: Double,
        categoryName: String,
        note: String,
        timestamp: Long,
        isRecurring: Boolean = false,
        recurrencePeriod: String = "NONE",
        eventId: Int? = null,
        destinationWalletId: Int? = null
    ) {
        viewModelScope.launch {
            val wallet = repository.getWalletById(walletId) ?: return@launch
            val cat = getCategoryByName(categoryName)
            val tx = Transaction(
                walletId = walletId,
                walletName = wallet.name,
                type = type,
                amount = amount,
                categoryName = categoryName,
                categoryIcon = cat.iconName,
                categoryColor = cat.colorHex,
                note = note.ifEmpty { categoryName },
                timestamp = timestamp,
                isRecurring = isRecurring,
                recurrencePeriod = recurrencePeriod,
                eventId = eventId,
                destinationWalletId = destinationWalletId
            )
            repository.insertTransaction(tx)
        }
    }

    fun updateTransaction(tx: Transaction) {
        viewModelScope.launch {
            repository.updateTransaction(tx)
        }
    }

    fun deleteTransaction(tx: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(tx)
        }
    }

    // --- DEBT SERVICES ---
    fun addDebt(
        personName: String,
        amount: Double,
        type: String, // DEBT or LOAN
        note: String,
        dueDate: Long?,
        walletId: Int,
        repaymentType: String = "FLEXIBLE",
        periodicAmount: Double? = null,
        periodType: String? = null
    ) {
        viewModelScope.launch {
            val wallet = repository.getWalletById(walletId) ?: return@launch
            
            val debtId = repository.insertDebt(
                Debt(
                    personName = personName,
                    type = type,
                    totalAmount = amount,
                    remainingAmount = amount,
                    walletId = walletId,
                    creationDate = System.currentTimeMillis(),
                    dueDate = dueDate,
                    note = note,
                    status = "ACTIVE",
                    repaymentType = repaymentType,
                    periodicAmount = periodicAmount,
                    periodType = periodType
                )
            )
            
            // Generate a corresponding Transaction to reflect the balance change
            val cat = getCategoryByName(if (type == "DEBT") "Đi vay" else "Cho vay")
            val txType = if (type == "DEBT") "INCOME" else "EXPENSE"
            
            val tx = Transaction(
                walletId = walletId,
                walletName = wallet.name,
                type = txType,
                amount = amount,
                categoryName = cat.name,
                categoryIcon = cat.iconName,
                categoryColor = cat.colorHex,
                note = if (note.isNotBlank()) note else (if (type == "DEBT") "Vay tiền từ $personName" else "Cho $personName vay tiền"),
                timestamp = System.currentTimeMillis(),
                isRecurring = false,
                recurrencePeriod = "NONE",
                eventId = null,
                destinationWalletId = null,
                debtId = debtId.toInt()
            )
            repository.insertTransaction(tx)
            
            showSuccessNotification(if (type == "DEBT") "Đã ghi nhận khoản vay thành công!" else "Đã ghi nhận khoản cho vay thành công!")
        }
    }

    fun increaseDebt(debt: Debt, amount: Double, walletId: Int, note: String) {
        viewModelScope.launch {
            val wallet = repository.getWalletById(walletId) ?: return@launch
            val newTotal = debt.totalAmount + amount
            val newRemaining = debt.remainingAmount + amount
            
            repository.updateDebt(debt.copy(
                totalAmount = newTotal, 
                remainingAmount = newRemaining, 
                status = "ACTIVE"
            ))
            
            // Create transaction for new debt
            val cat = getCategoryByName(if (debt.type == "DEBT") "Đi vay" else "Cho vay")
            val txType = if (debt.type == "DEBT") "INCOME" else "EXPENSE"
            
            val tx = Transaction(
                walletId = walletId,
                walletName = wallet.name,
                type = txType,
                amount = amount,
                categoryName = cat.name,
                categoryIcon = cat.iconName,
                categoryColor = cat.colorHex,
                note = if (note.isNotBlank()) note else (if (debt.type == "DEBT") "Vay thêm từ ${debt.personName}" else "Cho ${debt.personName} vay thêm"),
                timestamp = System.currentTimeMillis(),
                isRecurring = false,
                recurrencePeriod = "NONE",
                eventId = null,
                destinationWalletId = null,
                debtId = debt.id
            )
            repository.insertTransaction(tx)
            showSuccessNotification("Đã ghi nhận phát sinh nợ thành công!")
        }
    }

    fun payDebt(debt: Debt, amount: Double, walletId: Int, note: String) {
        viewModelScope.launch {
            val wallet = repository.getWalletById(walletId) ?: return@launch
            val newRemaining = Math.max(0.0, debt.remainingAmount - amount)
            val newStatus = if (newRemaining <= 0.01) "COMPLETED" else "ACTIVE"
            val finalRemaining = if (newStatus == "COMPLETED") 0.0 else newRemaining
            
            repository.updateDebt(debt.copy(remainingAmount = finalRemaining, status = newStatus))
            
            // Create transaction for repayment
            val cat = getCategoryByName(if (debt.type == "DEBT") "Trả nợ" else "Thu nợ")
            val txType = if (debt.type == "DEBT") "EXPENSE" else "INCOME"
            
            val tx = Transaction(
                walletId = walletId,
                walletName = wallet.name,
                type = txType,
                amount = amount,
                categoryName = cat.name,
                categoryIcon = cat.iconName,
                categoryColor = cat.colorHex,
                note = if (note.isNotBlank()) note else (if (debt.type == "DEBT") "Trả nợ cho ${debt.personName}" else "Thu nợ từ ${debt.personName}"),
                timestamp = System.currentTimeMillis(),
                isRecurring = false,
                recurrencePeriod = "NONE",
                eventId = null,
                destinationWalletId = null,
                debtId = debt.id
            )
            repository.insertTransaction(tx)
            showSuccessNotification(if (debt.type == "DEBT") "Ghi nhận trả nợ thành công!" else "Ghi nhận thu nợ thành công!")
        }
    }

    fun updateDebtDueDate(debt: Debt, newDueDate: Long) {
        viewModelScope.launch {
            repository.updateDebt(debt.copy(dueDate = newDueDate))
            showSuccessNotification("Cập nhật hạn trả nợ thành công!")
        }
    }

    fun updateDebtCreationDate(debt: Debt, newCreationDate: Long) {
        viewModelScope.launch {
            repository.updateDebt(debt.copy(creationDate = newCreationDate))
            showSuccessNotification("Cập nhật ngày bắt đầu thành công!")
        }
    }


    // --- BUDGETS SERVICES ---
    fun toggleBudgetRecurring(budget: Budget) {
        viewModelScope.launch {
            repository.updateBudget(budget.copy(isRecurring = !budget.isRecurring))
        }
    }

    fun addBudget(categoryName: String, limitAmount: Double, month: String, isRecurring: Boolean = false) {
        viewModelScope.launch {
            val cat = getCategoryByName(categoryName)
            
            // Collect child categories for seeding
            val allCats = categoriesList.value
            val familyCategories = mutableSetOf(categoryName)
            var added = true
            while (added) {
                added = false
                for (c in allCats) {
                    if (c.name !in familyCategories && c.parentName in familyCategories) {
                        familyCategories.add(c.name)
                        added = true
                    }
                }
            }
            
            // Calculate current month's spending for this category and its children to seed spentAmount!
            val txs = repository.allTransactions.firstOrNull() ?: emptyList()
            val spent = txs.filter {
                it.type == "EXPENSE" && it.categoryName in familyCategories && isTimestampInMonth(it.timestamp, month)
            }.sumOf { it.amount }

            repository.insertBudget(
                Budget(
                    categoryName = categoryName,
                    categoryIcon = cat.iconName,
                    categoryColor = cat.colorHex,
                    limitAmount = limitAmount,
                    spentAmount = spent,
                    month = month,
                    isRecurring = isRecurring
                )
            )
        }
    }

    fun updateBudget(budget: Budget) {
        viewModelScope.launch {
            repository.updateBudget(budget)
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            repository.deleteBudget(budget)
        }
    }

    private fun isTimestampInMonth(timestamp: Long, monthStr: String): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val checkMonth = String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        return checkMonth == monthStr
    }

    // --- SAVINGS GOALS SERVICES ---
    fun addSavingsGoal(name: String, targetAmount: Double, currentAmount: Double, targetDate: Long, note: String) {
        viewModelScope.launch {
            repository.insertSavingsGoal(
                SavingsGoal(
                    name = name,
                    targetAmount = targetAmount,
                    currentAmount = currentAmount,
                    targetDate = targetDate,
                    note = note
                )
            )
        }
    }

    fun updateSavingsGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            repository.updateSavingsGoal(goal)
        }
    }

    fun deleteSavingsGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            repository.deleteSavingsGoal(goal)
        }
    }

    // --- STATE FILTERS SETTERS ---
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTypeFilter(type: String) {
        _selectedTypeFilter.value = type
    }

    fun setCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    fun setDateFilterRange(start: Long?, end: Long?) {
        _filterStartDate.value = start
        _filterEndDate.value = end
    }

    fun setActiveMonth(month: String) {
        _activeMonth.value = month
    }

    // --- EVENTS SERVICES ---
    fun addEvent(name: String, description: String, startDate: Long, endDate: Long?, limitAmount: Double?, colorHex: String = "#FF9800", isActive: Boolean = true) {
        viewModelScope.launch {
            repository.insertEvent(Event(
                name = name,
                description = description,
                startDate = startDate,
                endDate = endDate,
                limitAmount = limitAmount,
                colorHex = colorHex,
                isActive = isActive
            ))
        }
    }

    fun updateEvent(event: Event) {
        viewModelScope.launch {
            repository.updateEvent(event)
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            repository.deleteEvent(event)
        }
    }

    fun reorderEvents(reorderedEvents: List<Event>) {
        viewModelScope.launch {
            reorderedEvents.forEachIndexed { index, event ->
                repository.updateEvent(event.copy(displayOrder = index))
            }
            showSuccessNotification("Đã cập nhật thứ tự ưu tiên sự kiện!")
        }
    }

    // --- GOOGLE CLOUD SYNC SERVICES ---
    fun clearSyncLogs() {
        _syncStatus.value = ""
        _syncProgressLogs.value = emptyList()
        _lastBackupFile.value = null
    }

    fun exportLocalBackup(context: android.content.Context) {
        viewModelScope.launch {
            _syncStatus.value = "SYNCING"
            val logs = mutableListOf<String>()
            fun addLog(msg: String) {
                logs.add("[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}] $msg")
                _syncProgressLogs.value = logs.toList()
            }

            try {
                addLog("Bắt đầu tạo bản sao lưu cục bộ...")

                val jsonString = repository.exportAllDataAsJson()

                // Save to context.getExternalFilesDir("Backups")
                val backupDir = context.getExternalFilesDir("Backups") ?: context.filesDir
                if (!backupDir.exists()) {
                    backupDir.mkdirs()
                }

                val formatSdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val filename = "SoChiTieu_Backup_${formatSdf.format(Date())}.json"
                val file = java.io.File(backupDir, filename)

                withContext(Dispatchers.IO) {
                    file.writeText(jsonString)
                }

                addLog("Đã viết file hệ thống thành công: $filename")
                addLog("Đường dẫn file hệ thống: ${file.absolutePath}")

                // Save copies in the Public Downloads folder under "SoChiTieuBackups" as well
                var publicSavedPath: String? = null
                withContext(Dispatchers.IO) {
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val resolver = context.contentResolver
                            val contentValues = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
                                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/SoChiTieuBackups")
                            }
                            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                            if (uri != null) {
                                resolver.openOutputStream(uri)?.use { outputStream ->
                                    outputStream.write(jsonString.toByteArray())
                                }
                                publicSavedPath = "Bộ nhớ trong > Download > SoChiTieuBackups > $filename"
                            }
                        } else {
                            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                            val subDir = java.io.File(downloadsDir, "SoChiTieuBackups")
                            if (!subDir.exists()) {
                                subDir.mkdirs()
                            }
                            val publicFile = java.io.File(subDir, filename)
                            publicFile.writeText(jsonString)
                            publicSavedPath = publicFile.absolutePath
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (publicSavedPath != null) {
                    addLog("✨ Đã tự động nhân bản file sao lưu công khai vào thư mục Downloads của bạn!")
                    addLog("Đường dẫn: $publicSavedPath")
                }

                val nowStr = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(Date())
                repository.saveSetting("local_backup_last_time", nowStr)
                _localBackupLastTime.value = nowStr

                // Dọn dẹp các bản sao lưu cũ trong thư mục riêng của app
                val allBackupFiles = backupDir.listFiles { _, name -> name.endsWith(".json") }
                if (allBackupFiles != null && allBackupFiles.size > 3) {
                    val sortedFiles = allBackupFiles.sortedByDescending { it.lastModified() }
                    for (i in 3 until sortedFiles.size) {
                        try {
                            val fileToDelete = sortedFiles[i]
                            val deleted = fileToDelete.delete()
                            if (deleted) {
                                addLog("🗑️ Đã xóa bản sao lưu cũ thừa ở thư mục app: ${fileToDelete.name}")
                            }
                        } catch (ex: Exception) {
                            addLog("⚠️ Lỗi khi tự động xóa file cũ (app): ${ex.message}")
                        }
                    }
                }
                
                // Dọn dẹp các bản sao lưu cũ trong thư mục Public Downloads
                withContext(Dispatchers.IO) {
                    // CÁCH 1: Dọn dẹp mạnh mẽ qua File API (Hỗ trợ tốt trên Android 11+ và Android cũ)
                    try {
                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val subDir = java.io.File(downloadsDir, "SoChiTieuBackups")
                        if (subDir.exists()) {
                            val publicBackupFiles = subDir.listFiles { _, name -> name.endsWith(".json") }
                            if (publicBackupFiles != null && publicBackupFiles.size > 3) {
                                val sortedPublicFiles = publicBackupFiles.sortedByDescending { it.lastModified() }
                                for (i in 3 until sortedPublicFiles.size) {
                                    try {
                                        val deleted = sortedPublicFiles[i].delete()
                                        if (deleted) addLog("🗑️ Đã xóa file public cũ: ${sortedPublicFiles[i].name}")
                                    } catch (e: Exception) {
                                        // Ghi nhận nhưng tiếp tục
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        addLog("Cảnh báo: Dọn file bằng File API lỗi: ${e.message}")
                    }

                    // CÁCH 2: Dọn dẹp Database MediaStore cho đồng bộ (Android 10+)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        try {
                            val resolver = context.contentResolver
                            val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                            val projection = arrayOf(
                                android.provider.MediaStore.MediaColumns._ID,
                                android.provider.MediaStore.MediaColumns.DATE_MODIFIED
                            )
                            val selection = "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
                            val selectionArgs = arrayOf("%SoChiTieuBackups%", "%.json")
                            
                            resolver.query(collection, projection, selection, selectionArgs, "${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                                val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
                                var count = 0
                                while (cursor.moveToNext()) {
                                    count++
                                    if (count > 3) {
                                        val id = cursor.getLong(idColumn)
                                        val uri = android.content.ContentUris.withAppendedId(collection, id)
                                        try {
                                            val deleted = resolver.delete(uri, null, null)
                                            if (deleted > 0) addLog("🗑️ Đã dọn entry public cũ trong MediaStore.")
                                        } catch (e: Exception) {
                                            // Ignored
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            addLog("⚠️ Cảnh báo: Không thể dọn dẹp entry MediaStore cũ.")
                        }
                    }
                }

                val filesList = backupDir.listFiles { _, name -> name.endsWith(".json") }
                _localBackupCount.value = filesList?.size ?: 0

                addLog("🎉 SAO LƯU THÀNH CÔNG!")
                _lastBackupFile.value = file
                _syncStatus.value = "SUCCESS_LOCAL_BACKUP"
            } catch (e: Exception) {
                addLog("Sao lưu lỗi: ${e.localizedMessage ?: "Lỗi không xác định"}")
                _syncStatus.value = "ERROR"
                e.printStackTrace()
            }
        }
    }

    fun shareBackupFile(context: android.content.Context) {
        val file = _lastBackupFile.value ?: return
        try {
            val fileUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Sổ Chi Tiêu Backup File")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooserIntent = android.content.Intent.createChooser(shareIntent, "Chia sẻ & Sao lưu dữ liệu JSON")
            chooserIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            // Fallback
            try {
                val jsonString = file.readText()
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Sổ Chi Tiêu JSON Backup", jsonString)
                clipboard.setPrimaryClip(clip)
                showSuccessNotification("Đã sao chép nội dung JSON vào Clipboard!")
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        } finally {
            clearSyncLogs()
        }
    }

    fun openBackupFolder(context: android.content.Context) {
        viewModelScope.launch {
            _syncStatus.value = "SYNCING"
            val logs = mutableListOf<String>()
            fun addLog(msg: String) {
                logs.add("[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}] $msg")
                _syncProgressLogs.value = logs.toList()
            }

            try {
                addLog("Đang kết nối đến thư mục lưu trữ...")
                val backupDir = context.getExternalFilesDir("Backups") ?: context.filesDir
                if (!backupDir.exists()) {
                    backupDir.mkdirs()
                }

                val publicPath = "Download/SoChiTieuBackups"
                addLog("Thư mục sao lưu công khai: $publicPath")

                // Copy path to clipboard as backup option
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Đường dẫn sao lưu Sổ Chi Tiêu", publicPath)
                clipboard.setPrimaryClip(clip)
                addLog("📋 ĐÃ COPY ĐƯỜNG DẪN 'Download/SoChiTieuBackups' VÀO CLIPBOARD.")

                addLog("Đang kích hoạt trình quản lý tệp trên thiết bị...")

                try {
                    // Start an intent to view the Download directory
                    val intent = android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    addLog("🚀 Mở thư mục Tải xuống thành công!")
                } catch (e: Exception) {
                    try {
                        val fileUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            backupDir
                        )
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(fileUri, "resource/folder")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        addLog("🚀 Mở thư mục ứng dụng thành công!")
                    } catch (ex: Exception) {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                                type = "*/*"
                                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                            addLog("🚀 Mở trình chọn tệp hệ thống thành công!")
                        } catch (ey: Exception) {
                            addLog("⚠️ Thiết bị thiếu ứng dụng File Manager tương ứng.")
                        }
                    }
                }

                addLog("\n💡 HƯỚNG DẪN TÌM THỦ CÔNG:")
                addLog("1. Mở ứng dụng 'Tệp' (Files by Google), 'Tệp của tôi' hoặc bất kỳ app quản lý file nào.")
                addLog("2. Điều hướng vào: Bộ nhớ trong > Download > SoChiTieuBackups")
                addLog("3. Tại đây bạn sẽ thấy tất cả file backup .json đã được tạo.")
                
                _syncStatus.value = "SUCCESS"
            } catch (e: Exception) {
                addLog("Mở thư mục lỗi: ${e.localizedMessage ?: "Lỗi không xác định"}")
                _syncStatus.value = "ERROR"
                e.printStackTrace()
            }
        }
    }

    fun seedSampleData() {
        viewModelScope.launch {
            // 1. Check and create default wallets and budgets
            repository.checkAndSeedDatabase()
            
            // 2. Add some demo transactions
            val now = System.currentTimeMillis()
            val oneDay = 24L * 60L * 60L * 1000L
            
            // Fetch wallets to ensure we get correct IDs
            val wts = repository.allWallets.firstOrNull() ?: emptyList()
            val cashWallet = wts.find { it.type == "CASH" }?.id ?: 1
            val bankWallet = wts.find { it.type == "BANK" }?.id ?: 2
            val electronicWallet = wts.find { it.type == "WALLET" }?.id ?: 3
            val savingsWallet = wts.find { it.type == "SAVINGS" }?.id ?: 4
            
            // Lương & Thu nhập (Bank)
            addTransaction(walletId = bankWallet, type = "INCOME", amount = 18000000.0, categoryName = "Lương", note = "Lương tháng này", timestamp = now - oneDay * 5)
            addTransaction(walletId = bankWallet, type = "INCOME", amount = 2000000.0, categoryName = "Thưởng", note = "Thưởng dự án đạt mốc", timestamp = now - oneDay * 4)
            addTransaction(walletId = electronicWallet, type = "INCOME", amount = 500000.0, categoryName = "Khác", note = "Hoàn tiền thẻ tín dụng", timestamp = now - oneDay * 2)

            // Sinh hoạt phí (Tiền mặt & Momo)
            addTransaction(walletId = cashWallet, type = "EXPENSE", amount = 45000.0, categoryName = "Ăn uống", note = "Ăn sáng phở", timestamp = now - oneDay * 3)
            addTransaction(walletId = cashWallet, type = "EXPENSE", amount = 150000.0, categoryName = "Ăn uống", note = "Ăn trưa quán vỉa hè", timestamp = now - oneDay * 2)
            addTransaction(walletId = electronicWallet, type = "EXPENSE", amount = 250000.0, categoryName = "Ăn uống", note = "Ăn tối lẩu Thái", timestamp = now - oneDay)
            addTransaction(walletId = electronicWallet, type = "EXPENSE", amount = 65000.0, categoryName = "Cà phê", note = "Phúc Long", timestamp = now)

            // Di chuyển
            addTransaction(walletId = cashWallet, type = "EXPENSE", amount = 60000.0, categoryName = "Di chuyển", note = "Đổ xăng", timestamp = now - oneDay * 4)
            addTransaction(walletId = bankWallet, type = "EXPENSE", amount = 120000.0, categoryName = "Di chuyển", note = "GrabBike đi siêu thị", timestamp = now - oneDay * 1)

            // Hóa đơn & Mua sắm (Bank)
            addTransaction(walletId = bankWallet, type = "EXPENSE", amount = 950000.0, categoryName = "Hóa đơn", note = "Tiền điện và nước", timestamp = now - oneDay * 3)
            addTransaction(walletId = bankWallet, type = "EXPENSE", amount = 300000.0, categoryName = "Hóa đơn", note = "Cước internet", timestamp = now - oneDay * 3)
            addTransaction(walletId = bankWallet, type = "EXPENSE", amount = 1200000.0, categoryName = "Mua sắm", note = "Quần áo Uniqlo", timestamp = now - oneDay * 2)
            addTransaction(walletId = electronicWallet, type = "EXPENSE", amount = 150000.0, categoryName = "Mua sắm", note = "Sách Shopee", timestamp = now - oneDay)

            // Tiết kiệm
            addTransaction(walletId = savingsWallet, type = "INCOME", amount = 2000000.0, categoryName = "Lương", note = "Chích lương vào tiết kiệm", timestamp = now - oneDay * 4)
            addTransaction(walletId = savingsWallet, type = "INCOME", amount = 500000.0, categoryName = "Thưởng", note = "Gửi thêm sau thưởng dự án", timestamp = now - oneDay * 1)
            
            // Giải trí
            addTransaction(walletId = electronicWallet, type = "EXPENSE", amount = 120000.0, categoryName = "Giải trí", note = "Vé xem phim CGV", timestamp = now)

            // Đầu tư
            addTransaction(walletId = bankWallet, type = "EXPENSE", amount = 5000000.0, categoryName = "Đầu tư", note = "Mua ETF chứng khoán", timestamp = now - oneDay)

            // Sức khỏe
            addTransaction(walletId = cashWallet, type = "EXPENSE", amount = 350000.0, categoryName = "Sức khỏe", note = "Mua thuốc và vitamin", timestamp = now - oneDay * 5)
            
            // 3. Add demo events
            val eventId1 = repository.insertEvent(Event(
                name = "Du lịch hè Nha Trang",
                description = "Chuyến đi Nha Trang 4 ngày 3 đêm cùng gia đình",
                startDate = now - oneDay * 2,
                endDate = now + oneDay * 5,
                limitAmount = 8000000.0,
                colorHex = "#2196F3"
            )).toInt()

            val eventId2 = repository.insertEvent(Event(
                name = "Liên hoan phòng ban",
                description = "Tiệc liên hoan cuối quý của bộ phận phát triển sản phẩm",
                startDate = now - oneDay,
                endDate = now + oneDay,
                limitAmount = 1500000.0,
                colorHex = "#4CAF50"
            )).toInt()

            // Add some transactions under these events
            val transportCategory = getCategoryByName("Di chuyển")
            val foodCategory = getCategoryByName("Ăn uống")
            
            val tx1 = Transaction(
                walletId = bankWallet,
                walletName = wts.find { it.id == bankWallet }?.name ?: "Ví điện tử",
                type = "EXPENSE",
                amount = 1200000.0,
                categoryName = transportCategory.name,
                categoryIcon = transportCategory.iconName,
                categoryColor = transportCategory.colorHex,
                note = "Vé máy bay khứ hồi Nha Trang",
                timestamp = now - oneDay,
                eventId = eventId1
            )
            repository.insertTransaction(tx1)

            val tx2 = Transaction(
                walletId = cashWallet,
                walletName = wts.find { it.id == cashWallet }?.name ?: "Tiền mặt",
                type = "EXPENSE",
                amount = 850000.0,
                categoryName = foodCategory.name,
                categoryIcon = foodCategory.iconName,
                categoryColor = foodCategory.colorHex,
                note = "Ăn hải sản tối Nha Trang",
                timestamp = now - oneDay,
                eventId = eventId1
            )
            repository.insertTransaction(tx2)

            // 4. Seed PENDING bank notification logs that fall under these events' timeframes
            val pendingLogs = org.json.JSONArray()
            
            val log1 = org.json.JSONObject().apply {
                put("timestamp", now)
                put("title", "Vietcombank")
                put("text", "TK 9967618785 -550,000 VND luc 12:14. ND: Thanhtoan khachsan NhaTrang")
                put("bankName", "Vietcombank")
                put("amount", 550000.0)
                put("type", "EXPENSE")
                put("note", "Thanh toán khách sạn Nha Trang")
                put("walletName", wts.find { it.id == bankWallet }?.name ?: "Ví điện tử")
                put("status", "PENDING")
            }
            pendingLogs.put(log1)

            val log2 = org.json.JSONObject().apply {
                put("timestamp", now)
                put("title", "Techcombank")
                put("text", "Techcombank: TK 1903 -420,000 VND. ND: Lien hoan nhe phong")
                put("bankName", "Techcombank")
                put("amount", 420000.0)
                put("type", "EXPENSE")
                put("note", "Liên hoan nhẹ phòng")
                put("walletName", wts.find { it.id == bankWallet }?.name ?: "Ví điện tử")
                put("status", "PENDING")
            }
            pendingLogs.put(log2)

            repository.saveSetting("notification_logs", pendingLogs.toString())
            loadNotificationLogs()

            // Refresh
            loadCategories()
        }
    }

    
    private suspend fun performMergeFromJsonString(jsonString: String, addLog: (String) -> Unit) {
        val root = org.json.JSONObject(jsonString)
        val version = root.optInt("version", 1)
        addLog("Phân tích file sao lưu để gộp dữ liệu (phiên bản $version)...")

        val existingWallets = repository.allWallets.first()
        val existingTxs = repository.allTransactions.first()
        val existingBudgets = repository.getAllBudgets().first()
        val existingSavings = repository.allSavingsGoals.first()
        val existingEvents = repository.allEvents.first()
        val existingDebts = repository.allDebts.first()

        // 1. Wallets
        val walletsArray = root.optJSONArray("wallets")
        if (walletsArray != null) {
            var addedWallets = 0
            for (i in 0 until walletsArray.length()) {
                val obj = walletsArray.getJSONObject(i)
                val name = obj.optString("name", "Ví")
                val type = obj.optString("type", "CASH")
                if (existingWallets.none { it.name.equals(name, ignoreCase = true) && it.type == type }) {
                    val w = com.app.data.Wallet(
                        name = name,
                        type = type,
                        balance = obj.optDouble("balance", 0.0),
                        colorHex = obj.optString("colorHex", "#9E9E9E"),
                        iconName = obj.optString("iconName", "AccountBalanceWallet"),
                        displayOrder = obj.optInt("displayOrder", 0)
                    )
                    repository.insertWallet(w)
                    addedWallets++
                }
            }
            addLog("Đã bổ sung $addedWallets ví mới từ đám mây.")
        }

        // Refresh wallet list for transaction mapping
        val currentWallets = repository.allWallets.first()
        val defaultWalletId = currentWallets.firstOrNull()?.id ?: 1
        val defaultWalletName = currentWallets.firstOrNull()?.name ?: "Ví chính"

        // 2. Transactions
        val transactionsArray = root.optJSONArray("transactions")
        if (transactionsArray != null) {
            var addedTxs = 0
            for (i in 0 until transactionsArray.length()) {
                val obj = transactionsArray.getJSONObject(i)
                val amount = obj.optDouble("amount", 0.0)
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                val note = obj.optString("note", "")
                val categoryName = obj.optString("categoryName", "")
                val type = obj.optString("type", "EXPENSE")

                // Check duplicate transaction (same timestamp, amount, type, note)
                val isDuplicate = existingTxs.any { 
                    it.timestamp == timestamp && it.amount == amount && it.type == type && it.note == note 
                }

                if (!isDuplicate) {
                    val targetWallet = currentWallets.find { it.name.equals(obj.optString("walletName", ""), ignoreCase = true) }
                    val wId = targetWallet?.id ?: defaultWalletId
                    val wName = targetWallet?.name ?: defaultWalletName

                    val t = com.app.data.Transaction(
                        walletId = wId,
                        walletName = wName,
                        type = type,
                        amount = amount,
                        categoryName = categoryName,
                        categoryIcon = obj.optString("categoryIcon", ""),
                        categoryColor = obj.optString("categoryColor", ""),
                        note = note,
                        timestamp = timestamp,
                        isRecurring = obj.optBoolean("isRecurring", false),
                        recurrencePeriod = obj.optString("recurrencePeriod", "NONE")
                    )
                    repository.insertTransaction(t)
                    addedTxs++
                }
            }
            addLog("Đã hợp nhất thành công $addedTxs giao dịch mới!")
        }

        // 3. Budgets
        val budgetsArray = root.optJSONArray("budgets")
        if (budgetsArray != null) {
            for (i in 0 until budgetsArray.length()) {
                val obj = budgetsArray.getJSONObject(i)
                val catName = obj.optString("categoryName", "")
                val month = obj.optString("month", "")
                if (catName.isNotEmpty() && existingBudgets.none { it.categoryName == catName && it.month == month }) {
                    val b = com.app.data.Budget(
                        categoryName = catName,
                        categoryIcon = obj.optString("categoryIcon", ""),
                        categoryColor = obj.optString("categoryColor", ""),
                        limitAmount = obj.optDouble("limitAmount", 0.0),
                        spentAmount = obj.optDouble("spentAmount", 0.0),
                        month = month,
                        isRecurring = obj.optBoolean("isRecurring", false)
                    )
                    repository.insertBudget(b)
                }
            }
        }

        // 4. Savings Goals
        val savingsGoalsArray = root.optJSONArray("savingsGoals")
        if (savingsGoalsArray != null) {
            for (i in 0 until savingsGoalsArray.length()) {
                val obj = savingsGoalsArray.getJSONObject(i)
                val name = obj.optString("name", "Mục tiêu")
                if (existingSavings.none { it.name.equals(name, ignoreCase = true) }) {
                    val s = com.app.data.SavingsGoal(
                        name = name,
                        targetAmount = obj.optDouble("targetAmount", 0.0),
                        currentAmount = obj.optDouble("currentAmount", 0.0),
                        targetDate = obj.optLong("targetDate", System.currentTimeMillis()),
                        note = obj.optString("note", "")
                    )
                    repository.insertSavingsGoal(s)
                }
            }
        }

        // Reload settings
        loadCategories()
        loadSecuritySettings()
        loadNotificationSettings()

        addLog("🎉 HỢP NHẤT DỮ LIỆU THÀNH CÔNG!")
        _syncStatus.value = "SUCCESS"
        showSuccessNotification("Hợp nhất dữ liệu đám mây thành công!")
    }

    fun mergeFromDrive(context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _syncStatus.value = "SYNCING"
            val logs = mutableListOf<String>()
            fun addLog(msg: String) {
                logs.add("[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}] $msg")
                _syncProgressLogs.value = logs.toList()
            }
            try {
                addLog("Bắt đầu hợp nhất dữ liệu từ Google Drive...")
                val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)?.account
                if (account == null) {
                    addLog("Lỗi: Bạn chưa đăng nhập tài khoản Google.")
                    _syncStatus.value = "ERROR"
                    return@launch
                }
                val scope = "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.readonly"
                val token = com.google.android.gms.auth.GoogleAuthUtil.getToken(context, account, scope)
                
                val folderName = "[APP_FINANCE]"
                val fileName = "finance_backup.json"
                val client = okhttp3.OkHttpClient()
                
                var folderId: String? = null
                val searchFolderRequest = okhttp3.Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?q=name='${folderName}' and mimeType='application/vnd.google-apps.folder'&spaces=drive")
                    .header("Authorization", "Bearer ${token}")
                    .build()
                val searchFolderResponse = client.newCall(searchFolderRequest).execute()
                if (searchFolderResponse.isSuccessful) {
                    val json = searchFolderResponse.body?.string()
                    if (json != null) {
                        val jsonObj = org.json.JSONObject(json)
                        val files = jsonObj.optJSONArray("files")
                        if (files != null && files.length() > 0) {
                            folderId = files.getJSONObject(0).getString("id")
                        }
                    }
                }

                if (folderId == null) {
                    addLog("Dữ liệu không tồn tại: Thư mục ${folderName} chưa được tạo trên Drive.")
                    _syncStatus.value = "ERROR"
                    return@launch
                }
                
                var fileId: String? = null
                val searchFileRequest = okhttp3.Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?q=name='${fileName}' and '${folderId}' in parents&spaces=drive")
                    .header("Authorization", "Bearer ${token}")
                    .build()
                
                val searchResponse = client.newCall(searchFileRequest).execute()
                if (searchResponse.isSuccessful) {
                    val json = searchResponse.body?.string()
                    if (json != null) {
                        val jsonObj = org.json.JSONObject(json)
                        val files = jsonObj.optJSONArray("files")
                        if (files != null && files.length() > 0) {
                            fileId = files.getJSONObject(0).getString("id")
                        }
                    }
                }
                
                if (fileId == null) {
                    addLog("Dữ liệu không tồn tại: Không có bản sao lưu nào.")
                    _syncStatus.value = "ERROR"
                    return@launch
                }
                
                addLog("Đang tải file sao lưu...")
                val downloadRequest = okhttp3.Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/${fileId}?alt=media")
                    .header("Authorization", "Bearer ${token}")
                    .build()
                    
                val downloadResponse = client.newCall(downloadRequest).execute()
                if (downloadResponse.isSuccessful) {
                    val jsonString = downloadResponse.body?.string()
                    if (jsonString.isNullOrBlank()) {
                        addLog("Lỗi: File trống.")
                        _syncStatus.value = "ERROR"
                        return@launch
                    }
                    performMergeFromJsonString(jsonString, ::addLog)
                } else {
                    addLog("Lỗi tải file: ${downloadResponse.code}")
                    _syncStatus.value = "ERROR"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                addLog("Lỗi hệ thống: ${e.message}")
                _syncStatus.value = "ERROR"
            }
        }
    }

    private suspend fun performRestoreFromJsonString(jsonString: String, addLog: (String) -> Unit) {
        val root = org.json.JSONObject(jsonString)
                val version = root.optInt("version", 1)
                addLog("Phân tích file phiên bản: $version")

                // Start clear databases first
                addLog("Khởi tạo tiến trình dọn dẹp cơ sở dữ liệu...")
                repository.clearAllData()
                repository.deleteAllSettings()

                // 1. Wallets
                val walletsArray = root.optJSONArray("wallets")
                if (walletsArray != null) {
                    addLog("Đang nhập lại ${walletsArray.length()} ví tài chính...")
                    for (i in 0 until walletsArray.length()) {
                        val obj = walletsArray.getJSONObject(i)
                        val w = com.app.data.Wallet(
                            id = obj.optInt("id", 0),
                            name = obj.optString("name", "Ví"),
                            type = obj.optString("type", "CASH"),
                            balance = obj.optDouble("balance", 0.0),
                            colorHex = obj.optString("colorHex", "#9E9E9E"),
                            iconName = obj.optString("iconName", "AccountBalanceWallet"),
                            displayOrder = obj.optInt("displayOrder", 0),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            isClosed = obj.optBoolean("isClosed", false),
                            targetAmount = if (obj.has("targetAmount") && !obj.isNull("targetAmount")) obj.optDouble("targetAmount") else null
                        )
                        repository.insertWalletDirect(w)
                    }
                }

                // 2. Transactions
                val transactionsArray = root.optJSONArray("transactions")
                if (transactionsArray != null) {
                    addLog("Đang nhập lại ${transactionsArray.length()} giao dịch tài chính...")
                    for (i in 0 until transactionsArray.length()) {
                        val obj = transactionsArray.getJSONObject(i)
                        val t = com.app.data.Transaction(
                            id = obj.optInt("id", 0),
                            walletId = obj.optInt("walletId", 0),
                            walletName = obj.optString("walletName", ""),
                            type = obj.optString("type", "EXPENSE"),
                            amount = obj.optDouble("amount", 0.0),
                            categoryName = obj.optString("categoryName", ""),
                            categoryIcon = obj.optString("categoryIcon", ""),
                            categoryColor = obj.optString("categoryColor", ""),
                            note = obj.optString("note", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            isRecurring = obj.optBoolean("isRecurring", false),
                            recurrencePeriod = obj.optString("recurrencePeriod", "NONE"),
                            eventId = if (obj.has("eventId") && !obj.isNull("eventId")) obj.optInt("eventId") else null,
                            destinationWalletId = if (obj.has("destinationWalletId") && !obj.isNull("destinationWalletId")) obj.optInt("destinationWalletId") else null,
                            debtId = if (obj.has("debtId") && !obj.isNull("debtId")) obj.optInt("debtId") else null,
                            notificationKey = if (obj.has("notificationKey") && !obj.isNull("notificationKey")) obj.optString("notificationKey") else null
                        )
                        repository.insertTransactionDirect(t)
                    }
                }

                // 3. Budgets
                val budgetsArray = root.optJSONArray("budgets")
                if (budgetsArray != null) {
                    addLog("Đang nhập lại ${budgetsArray.length()} hạn mức ngân sách...")
                    for (i in 0 until budgetsArray.length()) {
                        val obj = budgetsArray.getJSONObject(i)
                        val b = com.app.data.Budget(
                            id = obj.optInt("id", 0),
                            categoryName = obj.optString("categoryName", ""),
                            categoryIcon = obj.optString("categoryIcon", ""),
                            categoryColor = obj.optString("categoryColor", ""),
                            limitAmount = obj.optDouble("limitAmount", 0.0),
                            spentAmount = obj.optDouble("spentAmount", 0.0),
                            month = obj.optString("month", ""),
                            isRecurring = obj.optBoolean("isRecurring", false)
                        )
                        repository.insertBudgetDirect(b)
                    }
                }

                // 4. Savings Goals
                val savingsGoalsArray = root.optJSONArray("savingsGoals")
                if (savingsGoalsArray != null) {
                    addLog("Đang nhập lại ${savingsGoalsArray.length()} mục tiêu tích lũy...")
                    for (i in 0 until savingsGoalsArray.length()) {
                        val obj = savingsGoalsArray.getJSONObject(i)
                        val s = com.app.data.SavingsGoal(
                            id = obj.optInt("id", 0),
                            name = obj.optString("name", "Mục tiêu"),
                            targetAmount = obj.optDouble("targetAmount", 0.0),
                            currentAmount = obj.optDouble("currentAmount", 0.0),
                            targetDate = obj.optLong("targetDate", System.currentTimeMillis()),
                            note = obj.optString("note", "")
                        )
                        repository.insertSavingsGoalDirect(s)
                    }
                }

                // 4.5 Events
                val eventsArray = root.optJSONArray("events")
                if (eventsArray != null) {
                    addLog("Đang nhập lại ${eventsArray.length()} sự kiện...")
                    for (i in 0 until eventsArray.length()) {
                        val obj = eventsArray.getJSONObject(i)
                        val e = com.app.data.Event(
                            id = obj.optInt("id", 0),
                            name = obj.optString("name", "Sự kiện"),
                            description = obj.optString("description", ""),
                            startDate = obj.optLong("startDate", System.currentTimeMillis()),
                            endDate = if (obj.has("endDate") && !obj.isNull("endDate")) obj.optLong("endDate") else null,
                            limitAmount = if (obj.has("limitAmount") && !obj.isNull("limitAmount")) obj.optDouble("limitAmount") else null,
                            colorHex = obj.optString("colorHex", "#2196F3"),
                            isActive = obj.optBoolean("isActive", true),
                            displayOrder = obj.optInt("displayOrder", 0)
                        )
                        repository.insertEventDirect(e)
                    }
                }

                // 4.6 Debts
                val debtsArray = root.optJSONArray("debts")
                if (debtsArray != null) {
                    addLog("Đang nhập lại ${debtsArray.length()} khoản nợ...")
                    for (i in 0 until debtsArray.length()) {
                        val obj = debtsArray.getJSONObject(i)
                        val d = com.app.data.Debt(
                            id = obj.optInt("id", 0),
                            personName = obj.optString("personName", ""),
                            type = obj.optString("type", "DEBT"),
                            totalAmount = obj.optDouble("totalAmount", 0.0),
                            remainingAmount = obj.optDouble("remainingAmount", 0.0),
                            walletId = obj.optInt("walletId", 0),
                            creationDate = obj.optLong("creationDate", System.currentTimeMillis()),
                            dueDate = if (obj.has("dueDate") && !obj.isNull("dueDate")) obj.optLong("dueDate") else null,
                            note = obj.optString("note", ""),
                            status = obj.optString("status", "ACTIVE"),
                            repaymentType = obj.optString("repaymentType", "FLEXIBLE"),
                            periodicAmount = if (obj.has("periodicAmount") && !obj.isNull("periodicAmount")) obj.optDouble("periodicAmount") else null,
                            periodType = if (obj.has("periodType") && !obj.isNull("periodType")) obj.optString("periodType") else null
                        )
                        repository.insertDebtDirect(d)
                    }
                }

                // 5. Restore Settings (app settings, PIN, configurations)
                val settingsObj = root.optJSONObject("app_settings")
                if (settingsObj != null) {
                    addLog("Đang khôi phục cài đặt và cấu hình ứng dụng...")
                    val keys = settingsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = settingsObj.optString(key, "")
                        // Ticket 4 Fix: Exclude cloud sync state from imported JSON file so local restore doesn't auto-enable sync
                        if (value.isNotEmpty() && key != "is_cloud_sync_enabled") {
                            repository.saveSetting(key, value)
                        }
                    }
                } else {
                    // Fallback to format v1
                    addLog("Đang khôi phục cài đặt định dạng cũ (V1)...")
                    val customCategories = root.optString("customCategories", "")
                    if (customCategories.isNotEmpty()) {
                        repository.saveSetting("custom_categories", customCategories)
                    }
                    val notificationLogsRestore = root.optString("notificationLogs", "")
                    if (notificationLogsRestore.isNotEmpty()) {
                        repository.saveSetting("notification_logs", notificationLogsRestore)
                    }
                }

                // Explicitly set cloud sync to false when doing local JSON file restore
                repository.saveSetting("is_cloud_sync_enabled", "false")
                repository.saveSetting("cloud_sync_enabled", "false")

                // 6. Reload Settings & Cache flows inside memory
                loadCategories()
                loadSecuritySettings()
                loadNotificationSettings()

                // Fix: Always ensure has_seen_onboarding remains true after data restore so user is not kicked back to onboarding
                repository.saveSetting("has_seen_onboarding", "true")
                _hasSeenOnboarding.value = true

                addLog("🎉 KHÔI PHỤC DỮ LIỆU THÀNH CÔNG HOÀN TOÀN!")
                _syncStatus.value = "SUCCESS"
                showSuccessNotification("Khôi phục dữ liệu từ bản sao lưu thành công!")
    }


    fun backupToDriveNow(context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _syncStatus.value = "SYNCING"
            val logs = mutableListOf<String>()
            fun addLog(msg: String) {
                logs.add("[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}] $msg")
                _syncProgressLogs.value = logs.toList()
            }
            try {
                addLog("Bắt đầu sao lưu thủ công lên Google Drive...")
                val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)?.account
                if (account == null) {
                    addLog("Lỗi: Bạn chưa đăng nhập tài khoản Google.")
                    _syncStatus.value = "ERROR"
                    return@launch
                }
                val scope = "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.readonly"
                val token = com.google.android.gms.auth.GoogleAuthUtil.getToken(context, account, scope)
                
                val exportedData = repository.exportAllDataAsJson()
                addLog("Đã nén xong dữ liệu nội bộ.")
                
val folderName = "[APP_FINANCE]"
                val fileName = "finance_backup.json"
                val client = okhttp3.OkHttpClient()
                
                addLog("Đang kết nối thư mục sao lưu...")
                var folderId: String? = null
                val searchFolderRequest = okhttp3.Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?q=name='${folderName}' and mimeType='application/vnd.google-apps.folder'&spaces=drive")
                    .header("Authorization", "Bearer ${token}")
                    .build()
                val searchFolderResponse = client.newCall(searchFolderRequest).execute()
                if (searchFolderResponse.isSuccessful) {
                    val json = searchFolderResponse.body?.string()
                    if (json != null) {
                        val jsonObj = org.json.JSONObject(json)
                        val files = jsonObj.optJSONArray("files")
                        if (files != null && files.length() > 0) {
                            folderId = files.getJSONObject(0).getString("id")
                        }
                    }
                }

                if (folderId == null) {
                    addLog("Đang tạo mới thư mục ${folderName} trên Drive...")
                    val folderMetadata = org.json.JSONObject()
                    folderMetadata.put("name", folderName)
                    folderMetadata.put("mimeType", "application/vnd.google-apps.folder")
                    val mediaType = "application/json; charset=UTF-8".toMediaTypeOrNull()
                    val createFolderRequest = okhttp3.Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files")
                        .header("Authorization", "Bearer ${token}")
                        .post(folderMetadata.toString().toRequestBody(mediaType))
                        .build()
                    val createFolderResponse = client.newCall(createFolderRequest).execute()
                    if (createFolderResponse.isSuccessful) {
                        val json = createFolderResponse.body?.string()
                        if (json != null) {
                            val jsonObj = org.json.JSONObject(json)
                            folderId = jsonObj.optString("id")
                        }
                    }
                }

                if (folderId == null) {
                    addLog("Lỗi: Không thể truy cập hoặc tạo thư mục ${folderName}.")
                    _syncStatus.value = "ERROR"
                    return@launch
                }
                
                var fileId: String? = null
                val searchFileRequest = okhttp3.Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?q=name='${fileName}' and '${folderId}' in parents&spaces=drive")
                    .header("Authorization", "Bearer ${token}")
                    .build()
                
                val searchResponse = client.newCall(searchFileRequest).execute()
                if (searchResponse.isSuccessful) {
                    val json = searchResponse.body?.string()
                    if (json != null) {
                        val jsonObj = org.json.JSONObject(json)
                        val files = jsonObj.optJSONArray("files")
                        if (files != null && files.length() > 0) {
                            fileId = files.getJSONObject(0).getString("id")
                            addLog("Tìm thấy file sao lưu cũ trong thư mục. Đang ghi đè...")
                        } else {
                            addLog("Đang tạo file sao lưu mới trong thư mục...")
                        }
                    }
                }
                
                val metadata = org.json.JSONObject()
                metadata.put("name", fileName)
                metadata.put("mimeType", "application/json")
                if (fileId == null) {
                    metadata.put("parents", org.json.JSONArray().put(folderId))
                }
                
                val mediaType = "application/json; charset=UTF-8".toMediaTypeOrNull()
                val requestBody = okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart("metadata", null, metadata.toString().toRequestBody(mediaType))
                    .addFormDataPart("file", fileName, exportedData.toRequestBody(mediaType))
                    .build()

                val uploadUrl = if (fileId == null) {
                    "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
                } else {
                    "https://www.googleapis.com/upload/drive/v3/files/${fileId}?uploadType=multipart"
                }
                
                val requestBuilder = okhttp3.Request.Builder()
                    .url(uploadUrl)
                    .header("Authorization", "Bearer ${token}")
                
                if (fileId != null) {
                    requestBuilder.patch(requestBody)
                } else {
                    requestBuilder.post(requestBody)
                }
                
                val response = client.newCall(requestBuilder.build()).execute()
                if (response.isSuccessful) {
                    addLog("Tải lên Google Drive thành công!")
                    _syncStatus.value = "SUCCESS"
                    showSuccessNotification("Sao lưu lên Google Drive thành công!")
                } else {
                    addLog("Lỗi tải lên: ${response.code}")
                    _syncStatus.value = "ERROR"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                addLog("Lỗi hệ thống: ${e.message}")
            }
        }
    }

    fun checkDriveBackupConflict(context: android.content.Context, callback: (Boolean) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)?.account
                if (account == null) {
                    callback(false)
                    return@launch
                }
                val scope = "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.readonly"
                val token = com.google.android.gms.auth.GoogleAuthUtil.getToken(context, account, scope)
                
                val folderName = "[APP_FINANCE]"
                val fileName = "finance_backup.json"
                val client = okhttp3.OkHttpClient()
                
                var folderId: String? = null
                val searchFolderRequest = okhttp3.Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?q=name='${folderName}' and mimeType='application/vnd.google-apps.folder'&spaces=drive")
                    .header("Authorization", "Bearer ${token}")
                    .build()
                val searchFolderResponse = client.newCall(searchFolderRequest).execute()
                if (searchFolderResponse.isSuccessful) {
                    val json = searchFolderResponse.body?.string()
                    if (json != null) {
                        val jsonObj = org.json.JSONObject(json)
                        val files = jsonObj.optJSONArray("files")
                        if (files != null && files.length() > 0) {
                            folderId = files.getJSONObject(0).getString("id")
                        }
                    }
                }

                if (folderId == null) {
                    callback(false)
                    return@launch
                }

                var fileExists = false
                val searchFileRequest = okhttp3.Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?q=name='${fileName}' and '${folderId}' in parents&spaces=drive")
                    .header("Authorization", "Bearer ${token}")
                    .build()
                
                val searchResponse = client.newCall(searchFileRequest).execute()
                if (searchResponse.isSuccessful) {
                    val json = searchResponse.body?.string()
                    if (json != null) {
                        val jsonObj = org.json.JSONObject(json)
                        val files = jsonObj.optJSONArray("files")
                        if (files != null && files.length() > 0) {
                            fileExists = true
                        }
                    }
                }
                callback(fileExists)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false)
            }
        }
    }

    sealed class GoogleOnboardingResult {
        data class ExistingUserSuccess(val message: String = "Chào mừng bạn đã quay trở lại") : GoogleOnboardingResult()
        data class InvalidFileNewUser(val message: String = "Không tìm thấy tệp sao lưu hợp lệ. Đã khởi tạo dữ liệu mới.") : GoogleOnboardingResult()
        data class NoFolderNewUser(val message: String = "Chào mừng bạn đến với Ứng dụng lịch sử chi tiêu") : GoogleOnboardingResult()
        data class Error(val message: String) : GoogleOnboardingResult()
    }

    fun checkAndPerformGoogleSignInOnboarding(
        context: android.content.Context,
        onResult: (GoogleOnboardingResult) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _syncStatus.value = "SYNCING"
            try {
                val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)?.account
                if (account == null) {
                    _syncStatus.value = "ERROR"
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onResult(GoogleOnboardingResult.Error("Lỗi: Không tìm thấy tài khoản Google."))
                    }
                    return@launch
                }

                val scope = "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.readonly"
                val token = com.google.android.gms.auth.GoogleAuthUtil.getToken(context, account, scope)
                
                val folderName = "[APP_FINANCE]"
                val fileName = "finance_backup.json"
                val client = okhttp3.OkHttpClient()
                
                // 1. Check folder [APP_FINANCE]
                var folderId: String? = null
                val searchFolderRequest = okhttp3.Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?q=name='${folderName}' and mimeType='application/vnd.google-apps.folder' and trashed=false&spaces=drive&orderBy=modifiedTime desc")
                    .header("Authorization", "Bearer ${token}")
                    .build()
                val searchFolderResponse = client.newCall(searchFolderRequest).execute()
                if (searchFolderResponse.isSuccessful) {
                    val json = searchFolderResponse.body?.string()
                    if (json != null) {
                        val jsonObj = org.json.JSONObject(json)
                        val files = jsonObj.optJSONArray("files")
                        if (files != null && files.length() > 0) {
                            folderId = files.getJSONObject(0).getString("id")
                        }
                    }
                }

                // 2. Search backup file inside folder with orderBy=modifiedTime desc (picks newest modified file)
                var fileId: String? = null
                if (folderId != null) {
                    val searchFileRequest = okhttp3.Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files?q=name='${fileName}' and '${folderId}' in parents and trashed=false&spaces=drive&orderBy=modifiedTime desc")
                        .header("Authorization", "Bearer ${token}")
                        .build()
                    
                    val searchResponse = client.newCall(searchFileRequest).execute()
                    if (searchResponse.isSuccessful) {
                        val json = searchResponse.body?.string()
                        if (json != null) {
                            val jsonObj = org.json.JSONObject(json)
                            val files = jsonObj.optJSONArray("files")
                            if (files != null && files.length() > 0) {
                                fileId = files.getJSONObject(0).getString("id")
                            }
                        }
                    }
                }

                // 3. Fallback search across entire Drive space if fileId is still null
                if (fileId == null) {
                    val fallbackSearchRequest = okhttp3.Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files?q=name='${fileName}' and trashed=false&spaces=drive&orderBy=modifiedTime desc")
                        .header("Authorization", "Bearer ${token}")
                        .build()
                    val fallbackResponse = client.newCall(fallbackSearchRequest).execute()
                    if (fallbackResponse.isSuccessful) {
                        val json = fallbackResponse.body?.string()
                        if (json != null) {
                            val jsonObj = org.json.JSONObject(json)
                            val files = jsonObj.optJSONArray("files")
                            if (files != null && files.length() > 0) {
                                fileId = files.getJSONObject(0).getString("id")
                            }
                        }
                    }
                }

                // CASE 1: Không tìm thấy file sao lưu -> Coi như New User
                if (fileId == null) {
                    _syncStatus.value = "SUCCESS"
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onResult(GoogleOnboardingResult.InvalidFileNewUser())
                    }
                    return@launch
                }

                // Download file content and check format validity
                val downloadRequest = okhttp3.Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/${fileId}?alt=media")
                    .header("Authorization", "Bearer ${token}")
                    .build()
                    
                val downloadResponse = client.newCall(downloadRequest).execute()
                if (downloadResponse.isSuccessful) {
                    val jsonString = downloadResponse.body?.string()
                    if (jsonString.isNullOrBlank()) {
                        // CASE 1: File rỗng -> Báo không tìm được file hợp lệ
                        _syncStatus.value = "SUCCESS"
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onResult(GoogleOnboardingResult.InvalidFileNewUser())
                        }
                        return@launch
                    }

                    // Test JSON format validity
                    val isValidJson = try {
                        org.json.JSONObject(jsonString)
                        true
                    } catch (e: Exception) {
                        try {
                            org.json.JSONArray(jsonString)
                            true
                        } catch (e2: Exception) {
                            false
                        }
                    }

                    if (!isValidJson) {
                        // CASE 1: File không hợp lệ -> Báo không tìm thấy tệp sao lưu hợp lệ
                        _syncStatus.value = "SUCCESS"
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onResult(GoogleOnboardingResult.InvalidFileNewUser())
                        }
                        return@launch
                    }

                    // CASE 2: File có và định dạng hợp lệ -> Xử lý khôi phục bình thường & báo chào mừng quay trở lại
                    performRestoreFromJsonString(jsonString, {})
                    repository.saveSetting("cloud_sync_enabled", "true")
                    repository.saveSetting("is_cloud_sync_enabled", "true")
                    _isCloudSyncEnabled.value = true
                    try {
                        kotlinx.coroutines.withTimeout(3000) {
                            allWallets.first { it.isNotEmpty() }
                        }
                    } catch (e: Exception) {}

                    _syncStatus.value = "SUCCESS"
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onResult(GoogleOnboardingResult.ExistingUserSuccess())
                    }
                } else {
                    _syncStatus.value = "SUCCESS"
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onResult(GoogleOnboardingResult.InvalidFileNewUser())
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _syncStatus.value = "ERROR"
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(GoogleOnboardingResult.Error("Lỗi hệ thống: ${e.message}"))
                }
            }
        }
    }

    fun restoreFromDrive(context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _syncStatus.value = "SYNCING"
            val logs = mutableListOf<String>()
            fun addLog(msg: String) {
                logs.add("[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}] $msg")
                _syncProgressLogs.value = logs.toList()
            }
            try {
                addLog("Bắt đầu khôi phục từ Google Drive...")
                val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)?.account
                if (account == null) {
                    addLog("Lỗi: Bạn chưa đăng nhập tài khoản Google.")
                    _syncStatus.value = "ERROR"
                    return@launch
                }
                val scope = "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.readonly"
                val token = com.google.android.gms.auth.GoogleAuthUtil.getToken(context, account, scope)
                
                val folderName = "[APP_FINANCE]"
                val fileName = "finance_backup.json"
                val client = okhttp3.OkHttpClient()
                
                addLog("Đang kiểm tra thư mục ${folderName}...")
                var folderId: String? = null
                val searchFolderRequest = okhttp3.Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?q=name='${folderName}' and mimeType='application/vnd.google-apps.folder' and trashed=false&spaces=drive&orderBy=modifiedTime desc")
                    .header("Authorization", "Bearer ${token}")
                    .build()
                val searchFolderResponse = client.newCall(searchFolderRequest).execute()
                if (searchFolderResponse.isSuccessful) {
                    val json = searchFolderResponse.body?.string()
                    if (json != null) {
                        val jsonObj = org.json.JSONObject(json)
                        val files = jsonObj.optJSONArray("files")
                        if (files != null && files.length() > 0) {
                            folderId = files.getJSONObject(0).getString("id")
                        }
                    }
                }

                var fileId: String? = null
                if (folderId != null) {
                    addLog("Đang tìm file sao lưu bên trong thư mục...")
                    val searchFileRequest = okhttp3.Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files?q=name='${fileName}' and '${folderId}' in parents and trashed=false&spaces=drive&orderBy=modifiedTime desc")
                        .header("Authorization", "Bearer ${token}")
                        .build()
                    
                    val searchResponse = client.newCall(searchFileRequest).execute()
                    if (searchResponse.isSuccessful) {
                        val json = searchResponse.body?.string()
                        if (json != null) {
                            val jsonObj = org.json.JSONObject(json)
                            val files = jsonObj.optJSONArray("files")
                            if (files != null && files.length() > 0) {
                                fileId = files.getJSONObject(0).getString("id")
                                addLog("Đã tìm thấy file sao lưu trong thư mục ${folderName}!")
                            }
                        }
                    }
                }
                
                if (fileId == null) {
                    addLog("Đang tìm kiếm file sao lưu finance_backup.json trên không gian Drive...")
                    val searchFileRequest = okhttp3.Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files?q=name='${fileName}' and trashed=false&spaces=drive&orderBy=modifiedTime desc")
                        .header("Authorization", "Bearer ${token}")
                        .build()
                    
                    val searchResponse = client.newCall(searchFileRequest).execute()
                    if (searchResponse.isSuccessful) {
                        val json = searchResponse.body?.string()
                        if (json != null) {
                            val jsonObj = org.json.JSONObject(json)
                            val files = jsonObj.optJSONArray("files")
                            if (files != null && files.length() > 0) {
                                fileId = files.getJSONObject(0).getString("id")
                                addLog("Đã tìm thấy file sao lưu mới nhất trên Google Drive!")
                            }
                        }
                    }
                }

                if (fileId == null) {
                    addLog("Dữ liệu không tồn tại: Không tìm thấy tệp sao lưu ${fileName} trên Google Drive.")
                    _syncStatus.value = "ERROR"
                    return@launch
                }
                
                addLog("Đang tải dữ liệu về...")
                val downloadRequest = okhttp3.Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/${fileId}?alt=media")
                    .header("Authorization", "Bearer ${token}")
                    .build()
                    
                val downloadResponse = client.newCall(downloadRequest).execute()
                if (downloadResponse.isSuccessful) {
                    val jsonString = downloadResponse.body?.string()
                    if (jsonString == null || jsonString.isBlank()) {
                        addLog("Lỗi: File trống.")
                        _syncStatus.value = "ERROR"
                        return@launch
                    }
                    addLog("Tải file thành công. Tiến hành khôi phục...")
                    performRestoreFromJsonString(jsonString, ::addLog)
                    
                    try {
                        kotlinx.coroutines.withTimeout(3000) {
                            allWallets.first { it.isNotEmpty() }
                        }
                    } catch (e: Exception) {}
                    
                    addLog("🎉 KHÔI PHỤC TỪ GOOGLE DRIVE THÀNH CÔNG!")
                    _syncStatus.value = "SUCCESS"
                    showSuccessNotification("Khôi phục dữ liệu đám mây thành công!")
                } else {
                    addLog("Lỗi tải file: ${downloadResponse.code}")
                    _syncStatus.value = "ERROR"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                addLog("Lỗi hệ thống: ${e.message}")
                _syncStatus.value = "ERROR"
            }
        }
    }

    fun clearAllData(context: android.content.Context) {
        viewModelScope.launch {
            try {
                _syncStatus.value = "SYNCING"
                val logs = mutableListOf<String>()
                fun addLog(msg: String) {
                    logs.add("[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}] $msg")
                    _syncProgressLogs.value = logs.toList()
                }
                addLog("Khởi chạy tiến trình xóa sạch dữ liệu...")
                repository.clearAllData()
                addLog("Dọn dẹp các giao dịch, ví, ngân sách, mục tiêu tích lũy...")
                
                repository.deleteAllSettings()
                addLog("Xóa sạch toàn bộ cấu hình cài đặt và mã khóa PIN bảo vệ...")
                
                // Reload in-memory livedata states
                loadCategories()
                loadSecuritySettings()
                loadNotificationSettings()
                
                addLog("Đã xóa hoàn tất và khôi phục cài đặt gốc thành công!")
                _syncStatus.value = "SUCCESS_CLEAR"
                showSuccessNotification("Xóa toàn bộ dữ liệu & thiết lập lại thành công")
            } catch (e: Exception) {
                _syncStatus.value = "ERROR"
                e.printStackTrace()
            }
        }
    }

    fun importLocalBackup(
        context: android.content.Context,
        uri: android.net.Uri,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _syncStatus.value = "SYNCING"
            val logs = mutableListOf<String>()
            fun addLog(msg: String) {
                logs.add("[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}] $msg")
                _syncProgressLogs.value = logs.toList()
            }

            try {
                addLog("Bắt đầu giải nén file sao lưu...")
                val contentResolver = context.contentResolver
                val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }

                if (jsonString.isNullOrBlank()) {
                    addLog("Lỗi: File trống hoặc không đọc được.")
                    _syncStatus.value = "ERROR"
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onResult?.invoke(false)
                    }
                    return@launch
                }

                performRestoreFromJsonString(jsonString, ::addLog)
                
                try {
                    kotlinx.coroutines.withTimeout(3000) {
                        allWallets.first { it.isNotEmpty() }
                    }
                } catch (e: Exception) {}
                
                _syncStatus.value = "SUCCESS"
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult?.invoke(true)
                }
            } catch (e: Exception) {
                addLog("Lỗi khôi phục: ${e.localizedMessage ?: "File JSON lỗi cấu trúc."}")
                _syncStatus.value = "ERROR"
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult?.invoke(false)
                }
            }
        }
    }
}
