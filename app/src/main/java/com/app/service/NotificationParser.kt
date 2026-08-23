package com.app.service

import java.util.regex.Pattern

object NotificationParser {
    data class ParsedNotification(
        val amount: Double,
        val type: String, // "INCOME" or "EXPENSE"
        val bankName: String,
        val detectedWalletName: String,
        val note: String,
        val success: Boolean
    )

    fun parse(title: String, text: String, packageName: String): ParsedNotification {
        val lowerPackageName = packageName.lowercase()
        val lowerTitle = title.lowercase()
        val lowerText = text.lowercase()
        val combinedText = "$lowerTitle $lowerText"

        // 1. Determine Bank / App and corresponding Wallet name (prioritizing package name and title for accuracy)
        val (bankName, detectedWalletName) = when {
            // First Priority: Package name
            lowerPackageName.contains("vietcombank") || lowerPackageName.contains("vcb") -> Pair("Vietcombank", "Tài khoản ngân hàng")
            lowerPackageName.contains("techcombank") -> Pair("Techcombank", "Tài khoản ngân hàng")
            lowerPackageName.contains("momo") || lowerPackageName.contains("mservice.momo") -> Pair("MoMo", "Ví MoMo")
            lowerPackageName.contains("mbmobile") || lowerPackageName.contains("mbbank") -> Pair("MB Bank", "Tài khoản ngân hàng")
            lowerPackageName.contains("tpbank") -> Pair("TPBank", "Tài khoản ngân hàng")
            lowerPackageName.contains("agribank") -> Pair("Agribank", "Tài khoản ngân hàng")
            lowerPackageName.contains("acb") -> Pair("ACB", "Tài khoản ngân hàng")
            lowerPackageName.contains("zalopay") -> Pair("ZaloPay", "Ví MoMo")
            lowerPackageName.contains("viettel") -> Pair("Viettel Money", "Ví MoMo")
            lowerPackageName.contains("bidv") -> Pair("BIDV", "Tài khoản ngân hàng")
            lowerPackageName.contains("vpbank") -> Pair("VPBank", "Tài khoản ngân hàng")

            // Second Priority: Title
            lowerTitle.contains("vietcombank") || lowerTitle.contains("vcb") -> Pair("Vietcombank", "Tài khoản ngân hàng")
            lowerTitle.contains("techcombank") || lowerTitle.contains("tcb") -> Pair("Techcombank", "Tài khoản ngân hàng")
            lowerTitle.contains("momo") -> Pair("MoMo", "Ví MoMo")
            lowerTitle.contains("mb bank") || lowerTitle.contains("mbbank") || lowerTitle.contains("mb_bank") -> Pair("MB Bank", "Tài khoản ngân hàng")
            lowerTitle.contains("tpbank") -> Pair("TPBank", "Tài khoản ngân hàng")
            lowerTitle.contains("agribank") -> Pair("Agribank", "Tài khoản ngân hàng")
            lowerTitle.contains("acb") -> Pair("ACB", "Tài khoản ngân hàng")
            lowerTitle.contains("zalopay") || lowerTitle.contains("zalo pay") -> Pair("ZaloPay", "Ví MoMo")
            lowerTitle.contains("viettel pay") || lowerTitle.contains("viettel money") -> Pair("Viettel Money", "Ví MoMo")
            lowerTitle.contains("bidv") -> Pair("BIDV", "Tài khoản ngân hàng")
            lowerTitle.contains("vpbank") -> Pair("VPBank", "Tài khoản ngân hàng")

            // Third Priority: Body text fallbacks
            lowerText.contains("vietcombank") || lowerText.contains("vcb") -> Pair("Vietcombank", "Tài khoản ngân hàng")
            lowerText.contains("techcombank") || lowerText.contains("tcb") -> Pair("Techcombank", "Tài khoản ngân hàng")
            lowerText.contains("mb bank") || lowerText.contains("mbbank") || lowerText.contains("mb_bank") -> Pair("MB Bank", "Tài khoản ngân hàng")
            lowerText.contains("tpbank") -> Pair("TPBank", "Tài khoản ngân hàng")
            lowerText.contains("agribank") -> Pair("Agribank", "Tài khoản ngân hàng")
            lowerText.contains("acb") -> Pair("ACB", "Tài khoản ngân hàng")
            lowerText.contains("bidv") -> Pair("BIDV", "Tài khoản ngân hàng")
            lowerText.contains("vpbank") -> Pair("VPBank", "Tài khoản ngân hàng")
            lowerText.contains("momo") -> Pair("MoMo", "Ví MoMo")
            lowerText.contains("zalopay") || lowerText.contains("zalo pay") -> Pair("ZaloPay", "Ví MoMo")
            lowerText.contains("viettel pay") || lowerText.contains("viettel money") -> Pair("Viettel Money", "Ví MoMo")
            
            // International / Foreign Banks support
            combinedText.contains("hsbc") || packageName.contains("hsbc") -> Pair("HSBC", "Foreign Bank Account")
            combinedText.contains("citibank") || combinedText.contains("citi ") || packageName.contains("citibank") -> Pair("Citibank", "Foreign Bank Account")
            combinedText.contains("standard chartered") || combinedText.contains("stanchart") -> Pair("Standard Chartered", "Foreign Bank Account")
            combinedText.contains("chase") || packageName.contains("chase") -> Pair("Chase Bank", "Foreign Bank Account")
            combinedText.contains("revolut") || packageName.contains("revolut") -> Pair("Revolut", "Foreign Bank Account")
            combinedText.contains("paypal") || packageName.contains("paypal") -> Pair("PayPal", "Online Wallet")
            combinedText.contains("wise") || packageName.contains("wise") || combinedText.contains("transferwise") -> Pair("Wise", "Online Wallet")
            
            else -> {
                // Heuristic detection based on common bank abbreviations
                val words = listOf(
                    "bidv", "vpbank", "sacombank", "vietin", "vbi", "shb", "vib", "msb", "ocb", "hdbank", 
                    "scb", "lienviet", "lpbank", "shinhan", "cimb", "woori", "timo", "cake", "eximbank", 
                    "namabank", "seabank", "bacabank", "vietbank", "pvcombank"
                )
                val matched = words.find { combinedText.contains(it) }
                if (matched != null) {
                    Pair(matched.uppercase(), "Tài khoản ngân hàng")
                } else {
                    Pair("Ngân hàng", "Tài khoản ngân hàng")
                }
            }
        }

        // 2. Implement robust blocklisting (Ignore advertisements, OTP, logins, security alerts, saving/depositing advice)
        val blocklistKeywords = listOf(
            // OTP / Authen / Security
            "otp", "mã otp", "ma otp", "verification code", "security code", "mã pin", "ma pin",
            "mã xác thực", "ma xac thuc", "mã xác minh", "ma xac minh", "nhập mã", "nhan ma", "mã bảo mật",
            "đăng nhập", "dang nhap", "thiết bị mới", "thiet bi moi", "login", "đổi mật khẩu", "doi mat khau",
            "mật khẩu mới", "mã xác nhận", "ma xac nhan", "ma bao mat", "cảnh báo bảo mật", "khoá thẻ", "khóa thẻ",
            
            // Promotions / Discounts / Advertisements / Spam
            "khuyen mai", "khuyến mại", "khuyến mãi", "ưu đãi", "uu dai", "quang cao", "quảng cáo",
            "nhận quà", "quà tặng", "trúng thưởng", "vòng quay", "vong quay", "tri ân", "tri an",
            "hoàn tiền đến", "hoàn tiền lên đến", "hoàn đến", "hoan tien den", "hoàn ngay", "hoan ngay",
            "voucher", "giftcode", "mã giảm giá", "ma giam gia", "giảm ngay", "giam ngay", "giảm đến", "giam den",
            "tặng ngay", "tang ngay", "miễn phí", "mien phi", "free", "quà tặng", "qua tang", "ưu đãi lên tới",
            "giảm up to", "giam up to", "trúng ngay", "trung ngay", "cơ hội", "co hoi", "may mắn", "may man",
            "bốc thăm", "boc tham", "chương trình", "chuong trinh", "sự kiện", "su kien", "đại tiệc",
            "nhận voucher", "nhan voucher", "vàng 9999", "vàng sjc", "săn", "san code", "hoàn tiền 10%", "hoàn 10%",
            "nhận code", "tặng code", "nhập mã ngay", "mua sắm ngay", "chi tiêu ngay", "mua chung",
            "mở thẻ", "mo the", "mở tk", "mo tk", "mở ví", "mo vi", "mở tài khoản mới", "liên kết", "lien ket",
            "vay tiêu dùng", "vay tieu dung", "vay tín chấp", "vay siêu tốc", "lãi suất ưu đãi", "chúc mừng sinh nhật",
            "bảo hiểm", "bao hiem", "scb tuyển dụng", "gửi tiết kiệm", "gui tiet kiem", "gửi góp", "tiết kiệm trực tuyến",
            "hoàn tiền 20%", "hoàn tiền 5%", "hoàn tiền 30%", "quà tặng năng lượng", "gói quà", "lãi suất"
        )
        
        val isBlocklisted = blocklistKeywords.any { combinedText.contains(it) }
        if (isBlocklisted) {
            return ParsedNotification(
                amount = 0.0,
                type = "EXPENSE",
                bankName = bankName,
                detectedWalletName = detectedWalletName,
                note = "Bị chặn quảng cáo/bảo mật/OTP/Dịch vụ phụ",
                success = false
            )
        }

        // The financial model currently stores VND only. Reject foreign-currency
        // notifications instead of recording their numeric amount as VND.
        if (listOf("usd", "eur", "gbp").any { combinedText.contains(it) }) {
            return ParsedNotification(
                amount = 0.0,
                type = "EXPENSE",
                bankName = bankName,
                detectedWalletName = detectedWalletName,
                note = "Chưa hỗ trợ tự động ghi nhận giao dịch ngoại tệ",
                success = false
            )
        }

        // 3. Check for transaction indicators in any balance changes or payments
        val transactionKeywords = listOf(
            "số dư", "biến động", "bđsd", "sotien", "soduthaydoi", "sd tk", "so du tk", "tk sd", "gd:", "nd:", "nội dung gd",
            "giao dịch", "giao dich", "phát sinh", "bien dong so du", "bien dong gd", "biến động gd", "sự thay đổi số dư",
            "chuyển khoản", "chuyển tiền", "nhận tiền", "cộng tài khoản", "nạp tiền", "nhận từ", "vừa nhận", "giao dịch cộng",
            "giao dịch trừ", "thanh toán", "thanh toan", "rút tiền", "phí duy trì", "phí thường niên", "trừ phí", "hoàn tiền",
            "thanh toan thành cong", "chuyển khoản thành công", "được cộng", "bị trừ", "trừ tiền", "nợ tài khoản",
            // English equivalents
            "balance changed", "available balance", "account balance", "debited", "credited", "withdrawn", "deposited", "transferred",
            "transaction code", "payment of", "received money", "payment success", "billing info"
        )
        
        val hasTransactionIndicator = transactionKeywords.any { combinedText.contains(it) }
        
        // If a notification doesn't match a specific bank and has no transaction indicator, reject it
        if (bankName == "Ngân hàng" && !hasTransactionIndicator) {
            return ParsedNotification(
                amount = 0.0,
                type = "EXPENSE",
                bankName = bankName,
                detectedWalletName = detectedWalletName,
                note = "Không phải giao dịch ngân hàng",
                success = false
            )
        }
        
        // For general safety, require at least one transaction keyword, currency symbol, or dynamic change sign (+/-)
        val currencyKeywords = listOf("vnd", "đ", "d", "usd", "eur", "gbp", "đồng", "dong")
        val hasCurrencyIndicator = currencyKeywords.any { combinedText.contains(it) }
        val hasSign = combinedText.contains("+") || combinedText.contains("-")
        
        if (!hasTransactionIndicator && !hasCurrencyIndicator && !hasSign) {
            return ParsedNotification(
                amount = 0.0,
                type = "EXPENSE",
                bankName = bankName,
                detectedWalletName = detectedWalletName,
                note = "Không khớp định dạng biến động số dư",
                success = false
            )
        }

        // 4. Determine Transaction Type (INCOME or EXPENSE) with Vietnamese & English keywords
        val incomeKeywords = listOf(
            "+", "nhận tiền", "cong +", "nạp tiền", "nhận", "co:", "cộng", "transfer in", "chuyển đến", "tăng", "nhận từ", "được cộng",
            "received", "deposited", "refunded", "transfer from", "credit", "incoming", "added", "hoàn tiền", "hoan tien"
        )
        val expenseKeywords = listOf(
            "-", "trừ", "thanh toán", "chuyển đi", "nợ:", "trừ tiền", "chuyển khoản đi", "transfer out", "giảm", "rút tiền", "thanh toán hóa đơn", "phí",
            "paid", "spent", "purchase", "withdrew", "charge", "outgoing", "payment for", "phi gd"
        )

        var type = "EXPENSE" // default
        val cleanForType = getCleanText(combinedText)

        val hasPlus = Regex("""\+\s*\d""").containsMatchIn(cleanForType)
        val hasMinus = Regex("""-\s*\d""").containsMatchIn(cleanForType)

        if (hasPlus && !hasMinus) {
            type = "INCOME"
        } else if (hasMinus && !hasPlus) {
            type = "EXPENSE"
        } else {
            // Highly robust priority checks
            val incomeIndicators = listOf(
                "nhận được", "nhan duoc", "nhận từ", "nhan tu", "nhận tiền", "nhan tien", "cộng tài khoản", "cong tai khoan",
                "được cộng", "duoc cong", "hoàn tiền", "hoan tien", "hoàn trả", "hoan tra", "lương", "luong", "thưởng", "thuong"
            )
            val expenseIndicators = listOf(
                "thanh toán", "thanh toan", "chuyển đi", "chuyen di", "chuyển khoản đi", "chuyen khoan di", "bị trừ", "bi tru",
                "rút tiền", "rut tien", "mua sắm", "mua sam", "phí duy trì", "phi duy tri", "khấu trừ", "khau tru"
            )

            val matchesIncome = incomeIndicators.any { combinedText.contains(it) }
            val matchesExpense = expenseIndicators.any { combinedText.contains(it) }

            if (matchesIncome && !matchesExpense) {
                type = "INCOME"
            } else if (matchesExpense && !matchesIncome) {
                type = "EXPENSE"
            } else {
                val incomeScore = incomeKeywords.count { combinedText.contains(it) }
                val expenseScore = expenseKeywords.count { combinedText.contains(it) }
                if (incomeScore > expenseScore) {
                    type = "INCOME"
                } else if (expenseScore > incomeScore) {
                    type = "EXPENSE"
                } else {
                    // Check if '+' symbol appears anywhere in the raw text
                    if (combinedText.contains("+")) {
                        type = "INCOME"
                    }
                }
            }
        }

        // 5. Extract Amount with date and time safety
        val rawAmount = extractAmount(text)

        // 6. Extract clean transaction note
        var note = extractNote(text, bankName, type)
        if (note.length > 80) {
            note = note.substring(0, 77) + "..."
        }
        if (note.isBlank()) {
            note = if (type == "INCOME") "Giao dịch cộng $bankName" else "Giao dịch trừ $bankName"
        }

        return ParsedNotification(
            amount = rawAmount,
            type = type,
            bankName = bankName,
            detectedWalletName = detectedWalletName,
            note = note,
            success = rawAmount > 0
        )
    }

    private fun getCleanText(text: String): String {
        var cleanText = text.lowercase()
        // Replace 'đ' and 'd' safely only if they are used as currency (e.g. after a number)
        cleanText = cleanText.replace(Regex("""(?i)\bđ\b"""), "vnd")
        cleanText = cleanText.replace(Regex("""(?i)(?<=\d)\s*d\b"""), "vnd")
        cleanText = cleanText.replace(Regex("""(?i)(?<=\d)\s*đ\b"""), "vnd")
        cleanText = cleanText.replace("$", "usd")
            .replace("€", "eur")
            .replace("£", "gbp")

        // Remove the Account Balance (Số dư khả dụng / Số dư cuối / Số dư hiện tại...) so it doesn't get mistakenly parsed as the transaction amount
        // Note: Do NOT remove if it's "biến động số dư" / "bien dong so du"
        val balanceRegex = Regex("""(?<!biến động\s)(?<!bien dong\s)(?<!bđ\s)\b(?:sd|số dư|so du|sodu)\s*(?:khả dụng|kha dung|cuối|cuoi|hiện tại|hien tai|mới|moi|tk)?\s*[:]?\s*[+-]?\s*\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{2})?\s*(?:vnd|đ|d|usd|eur|gbp)?\b""")
        cleanText = cleanText.replace(balanceRegex, " ")

        // Remove account/card masks containing 'xxx' (e.g. 82xxx686)
        cleanText = cleanText.replace(Regex("""\b\w*xxx\w*\b"""), "")

        // Remove phone country code prefix (+84)
        cleanText = cleanText.replace("+84", "")
        
        // Remove specific timezone offsets (like +07:00, +0700, +07) without matching currency amounts
        cleanText = cleanText.replace(Regex("""\+0[789]:00\b"""), "")
        cleanText = cleanText.replace(Regex("""\+0[789]00\b"""), "")
        cleanText = cleanText.replace(Regex("""\+0[789]\b"""), "")

        // Remove long account/card/phone numbers of 8 to 18 digits (optionally with leading hyphen)
        // that are not followed by a currency suffix to preserve amount extraction
        cleanText = cleanText.replace(Regex("""(?:[-–]\s*)?\b\d{8,18}(?!\s*(?:vnd|đ|d|usd|eur|gbp|đồng|dong))\b"""), "")

        // Remove dates and times to prevent matching hours, minutes, seconds, or dates
        cleanText = cleanText.replace(Regex("""\b\d{1,2}[:h]\d{2}(?:[:ms]\d{2})?\b"""), "")
        cleanText = cleanText.replace(Regex("""\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b"""), "")
        
        // Remove standalone years (2024 to 2035)
        cleanText = cleanText.replace(Regex("""\b202[4-9]\b"""), "")
        cleanText = cleanText.replace(Regex("""\b203[0-5]\b"""), "")

        // Remove account numbers, cards (typically preceded by tk, stk, tai khoan, card, the)
        cleanText = cleanText.replace(Regex("""\b(?:tk|stk|tai khoan|tài khoản|account|so tk|sotk|the|thẻ|card)\s?\d+\b"""), "")
        
        // Remove phone numbers/hotlines
        cleanText = cleanText.replace(Regex("""\b(?:hotline|sđt|sdt|lien he|liên hệ|call|tel|phone|mb)\s?\d+\b"""), "")
        
        return cleanText
    }

    private fun extractAmount(text: String): Double {
        val cleanText = getCleanText(text)

        // Match numbers following signs or associated with currency keywords or direct transaction context
        val patterns = listOf(
            // 1. Clear dynamic sign (+ or -) followed by a number (lookahead prevents backtracking and truncating numbers with commas/dots)
            Pattern.compile("""[+-]\s?(\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{2})?)(?!\d|[.,]\d)"""),
            
            // 2. Number followed directly by currency keyword (vnd, usd, eur, gbp)
            Pattern.compile("""(\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{2})?)\s?(?:vnd|usd|eur|gbp)\b"""),
            
            // 3. Foreign currency prefix followed by a number
            Pattern.compile("""(?:usd|eur|gbp)\s?(\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{2})?)(?!\d|[.,]\d)"""),
            
            // 4. Number preceded by a direct transaction indicator to parse values safely
            Pattern.compile("""\b(?:số tiền|so tien|sotien|trị giá|tri gia|tiền|tien|thanh toán|thanh toan|biến động|bien dong|giao dịch|giao dich|gd|gd:|ps|ps:|phát sinh|phat sinh|giá trị|gia tri|cộng|co:|nợ:)\s*:?\s*(\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{2})?)(?!\d|[.,]\d)""")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(cleanText)
            while (matcher.find()) {
                val groupText = matcher.group(1) ?: continue
                
                var normalized = groupText
                val hasDecimal = normalized.length >= 4 && (normalized[normalized.length - 3] == '.' || normalized[normalized.length - 3] == ',')
                val decimalChar = if (hasDecimal) normalized[normalized.length - 3] else null
                
                if (hasDecimal && decimalChar != null) {
                    val integerPart = normalized.substring(0, normalized.length - 3)
                    val decimalPart = normalized.substring(normalized.length - 2)
                    
                    val cleanInt = integerPart.filter { it.isDigit() }
                    normalized = "$cleanInt.$decimalPart"
                } else {
                    normalized = normalized.filter { it.isDigit() }
                }

                val parsedVal = normalized.toDoubleOrNull() ?: 0.0
                val isForeign = cleanText.contains("usd") || cleanText.contains("eur") || cleanText.contains("gbp")
                if (isForeign) {
                    if (parsedVal >= 1.0) return parsedVal
                } else {
                    if (parsedVal >= 1000.0) return parsedVal
                }
            }
        }

        return 0.0
    }

    private fun extractNote(text: String, bankName: String, type: String): String {
        val lowerText = text.lowercase()
        // Typical prefixes showing transaction note
        val indicators = listOf("nội dung:", "noidung:", "lý do:", "gd:", "nd:", "ref:", "mô tả:", "nội dung gd:", "noi dung:")
        for (indicator in indicators) {
            val idx = lowerText.lastIndexOf(indicator)
            if (idx != -1) {
                val rem = text.substring(idx + indicator.length).trim()
                // Clean characters often found inside SMS
                return cleanNoteString(rem)
            }
        }

        // Fallback: If it's a MoMo or simple app, search for quoted text or text in parentheses
        val quotePattern = java.util.regex.Pattern.compile(""""([^"]+)"""")
        val qMatcher = quotePattern.matcher(text)
        if (qMatcher.find()) {
            return cleanNoteString(qMatcher.group(1) ?: "")
        }

        return if (type == "INCOME") "Giao dịch cộng $bankName" else "Giao dịch trừ $bankName"
    }

    private fun cleanNoteString(input: String): String {
        return input.replace(Regex("""[\[\]\(\)\{\}]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
