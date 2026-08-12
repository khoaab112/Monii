package com.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.app.data.AppDatabase
import com.app.data.Categories
import com.app.data.FinanceRepository
import com.app.data.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class BankNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    companion object {
        @Volatile
        var instance: BankNotificationListenerService? = null
            private set

        /**
         * Re-bind the service. Some device's aggressive battery managers (like Xiaomi HyperOS)
         * kill the service and never restart it. Toggling the component enabled state
         * forces the OS to rebind.
         */
        fun requestRebindService(context: android.content.Context) {
            val componentName = android.content.ComponentName(context, BankNotificationListenerService::class.java)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    android.service.notification.NotificationListenerService.requestRebind(componentName)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val context = applicationContext
        val packageName = sbn.packageName ?: ""
        val notificationKey = sbn.key
        val postedAt = sbn.postTime
        
        // Ignore notifications posted by our own app to prevent infinite loops
        if (packageName == context.packageName) {
            return
        }
        
        val extras = sbn.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (text.isBlank()) return

        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val repository = FinanceRepository(db.financeDao(), db)

                // 1. Check if notification reader is enabled in settings
                val isEnabled = repository.getSetting("notification_reader_enabled")?.value == "true"
                if (!isEnabled) {
                    return@launch
                }

                // 2. Parse the notification
                val parsed = NotificationParser.parse(title, text, packageName)
                if (!parsed.success) {
                    return@launch
                }

                // 3. Find matched Wallet from DB
                val wallets = (repository.allWallets.firstOrNull() ?: emptyList()).filter { it.type != "SAVINGS" }
                val matchedWallet = wallets.find { it.name.lowercase().contains(parsed.bankName.lowercase()) }
                    ?: wallets.find { it.name.lowercase().contains(parsed.detectedWalletName.lowercase()) }
                    ?: wallets.find { it.type == "BANK" }
                    ?: wallets.firstOrNull()

                val walletNameInput = matchedWallet?.name ?: parsed.detectedWalletName

                // 4. Save as PENDING so that user can review and confirm with complete certainty
                saveLog(repository, title, text, parsed, "PENDING", walletNameInput, notificationKey, postedAt)

                // 5. Fire system notification
                sendLocalNotification(context, parsed.amount, parsed.type)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendLocalNotification(context: android.content.Context, amount: Double, type: String) {
    val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    val channelId = "bank_sync_channel"
    
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val channel = android.app.NotificationChannel(
            channelId,
            "Thông báo giao dịch tự động",
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Thông báo khi ứng dụng quét được thông báo ngân hàng mới"
        }
        notificationManager.createNotificationChannel(channel)
    }

    val amountStr = com.app.ui.FormatHelper.formatVND(amount)
    val prefix = if (type == "EXPENSE") "-" else "+"
    val contentText = "Nhận được 1 giao dịch $prefix$amountStr"

    val intent = android.content.Intent(context, com.app.MainActivity::class.java).apply {
        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra("OPEN_BANK_NOTIFICATIONS", true)
    }
    val pendingIntent = android.app.PendingIntent.getActivity(
        context,
        0,
        intent,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )

    val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info) // Fallback icon
        .setContentTitle("Ví của tôi")
        .setContentText(contentText)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        // Build is handled above
    } else {
        @Suppress("DEPRECATION")
        builder.setPriority(android.app.Notification.PRIORITY_DEFAULT)
    }

    notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
}

private fun selectCategory(type: String, note: String): String {
        val lowerNote = note.lowercase()
        if (type == "INCOME") {
            return when {
                lowerNote.contains("luong") || lowerNote.contains("salary") -> "Lương"
                lowerNote.contains("thuong") || lowerNote.contains("gift") || lowerNote.contains("tang") -> "Thưởng"
                lowerNote.contains("ban hang") || lowerNote.contains("kinh doanh") || lowerNote.contains("sales") -> "Kinh doanh"
                else -> "Khác"
            }
        } else {
            return when {
                lowerNote.contains("an uong") || lowerNote.contains("restaurant") || lowerNote.contains("coffee") || lowerNote.contains("milktea") || lowerNote.contains("tra sua") || lowerNote.contains("com") || lowerNote.contains("pho") || lowerNote.contains("food") -> "Ăn uống"
                lowerNote.contains("grab") || lowerNote.contains("xe") || lowerNote.contains("beemin") || lowerNote.contains("gojek") || lowerNote.contains("gas") || lowerNote.contains("xang") || lowerNote.contains("petrol") || lowerNote.contains("taxi") -> "Di chuyển"
                lowerNote.contains("shopee") || lowerNote.contains("tiki") || lowerNote.contains("lazada") || lowerNote.contains("mua sam") || lowerNote.contains("shopping") || lowerNote.contains("quan ao") || lowerNote.contains("quanao") || lowerNote.contains("sieu thi") -> "Mua sắm"
                lowerNote.contains("cuoc") || lowerNote.contains("cuoc dt") || lowerNote.contains("the cao") || lowerNote.contains("wifi") || lowerNote.contains("dien nuoc") || lowerNote.contains("internet") || lowerNote.contains("hoa don") || lowerNote.contains("receipt") || lowerNote.contains("tien dien") -> "Hóa đơn"
                lowerNote.contains("game") || lowerNote.contains("phim") || lowerNote.contains("cgv") || lowerNote.contains("netflix") || lowerNote.contains("giai tri") || lowerNote.contains("ticket") -> "Giải trí"
                lowerNote.contains("hoc") || lowerNote.contains("school") || lowerNote.contains("sach") || lowerNote.contains("course") || lowerNote.contains("giao duc") -> "Giáo dục"
                lowerNote.contains("thuoc") || lowerNote.contains("hospital") || lowerNote.contains("benh vien") || lowerNote.contains("suc khoe") -> "Sức khỏe"
                lowerNote.contains("nha cua") || lowerNote.contains("thue phong") || lowerNote.contains("homestay") -> "Nhà cửa"
                else -> "Khác"
            }
        }
    }

    private suspend fun saveLog(
        repository: FinanceRepository,
        title: String,
        text: String,
        parsed: NotificationParser.ParsedNotification,
        status: String,
        walletName: String?,
        notificationKey: String,
        postedAt: Long
    ) {
        val logsSetting = repository.getSetting("notification_logs")?.value ?: "[]"
        val jsonArray = try { JSONArray(logsSetting) } catch (e: Exception) { JSONArray() }

        if ((0 until jsonArray.length()).any { index ->
                jsonArray.getJSONObject(index).optString("notificationKey") == notificationKey
            }) {
            return
        }

        val logObj = JSONObject()
        logObj.put("timestamp", postedAt)
        logObj.put("notificationKey", notificationKey)
        logObj.put("title", title)
        logObj.put("text", text)
        logObj.put("bankName", parsed.bankName)
        logObj.put("amount", parsed.amount)
        logObj.put("type", parsed.type)
        logObj.put("note", parsed.note)
        logObj.put("walletName", walletName ?: parsed.detectedWalletName)
        logObj.put("status", status)

        val newList = JSONArray()
        newList.put(logObj)
        val limitTime = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000)
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
}
