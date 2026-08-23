package com.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationParserTest {

    @Test
    fun parseIncomeVietcombank() {
        val title = "Vietcombank thong bao"
        val text = "SD TK 0451 thay doi +500,000 VND luc 12:30. GD: Chuyen khoan luong thang"
        val packageName = "com.vietcombank.restyle"

        val result = NotificationParser.parse(title, text, packageName)

        assertTrue(result.success)
        assertEquals(500000.0, result.amount, 0.0)
        assertEquals("INCOME", result.type)
        assertEquals("Vietcombank", result.bankName)
        assertEquals("Chuyen khoan luong thang", result.note)
    }

    @Test
    fun parseExpenseTechcombank() {
        val title = "Techcombank: TK 1903 bien dong"
        val text = "-150,000 VND. Noi dung: Mua tra sua"
        val packageName = ""

        val result = NotificationParser.parse(title, text, packageName)

        assertTrue(result.success)
        assertEquals(150000.0, result.amount, 0.0)
        assertEquals("EXPENSE", result.type)
        assertEquals("Techcombank", result.bankName)
        assertEquals("Mua tra sua", result.note)
    }

    @Test
    fun parseExpenseMomo() {
        val title = "Thanh toán thành công"
        val text = "Ban da thanh toan thanh cong so tien -50,000 d cho dich vu GrabFood qua vi MoMo"
        val packageName = "com.mservice.momo"

        val result = NotificationParser.parse(title, text, packageName)

        assertTrue(result.success)
        assertEquals(50000.0, result.amount, 0.0)
        assertEquals("EXPENSE", result.type)
        assertEquals("MoMo", result.bankName)
    }

    @Test
    fun parseMBVNDFormat() {
        val title = "MB_BANK: GD TK..."
        val text = "+200.000 VND luc 15:40. ND: Tien mung sinh nhat"
        val packageName = "com.mbmobile"

        val result = NotificationParser.parse(title, text, packageName)

        assertTrue(result.success)
        assertEquals(200000.0, result.amount, 0.0)
        assertEquals("MB Bank", result.bankName)
        assertEquals("Tien mung sinh nhat", result.note)
    }

    @Test
    fun rejectForeignCurrencyUntilCurrencySupportIsImplemented() {
        val result = NotificationParser.parse(
            title = "Bank alert",
            text = "Account credited +100 USD. Balance changed.",
            packageName = "com.example.bank"
        )

        assertTrue(!result.success)
    }

    @Test
    fun rejectOtpAndPromotionalNotifications() {
        val otp = NotificationParser.parse("Vietcombank", "Ma OTP cua Quy khach la 123456", "com.vietcombank.restyle")
        val promotion = NotificationParser.parse("MoMo", "Nhan voucher giam gia 50% khi thanh toan", "com.mservice.momo")

        assertTrue(!otp.success)
        assertTrue(!promotion.success)
    }

    @Test
    fun parseBIDVIncomeFromPackageName() {
        val result = NotificationParser.parse(
            title = "Thong bao giao dich",
            text = "TK vua nhan +1,250,000 VND. ND: Hoan tien",
            packageName = "com.bidv.smartbanking"
        )

        assertTrue(result.success)
        assertEquals("BIDV", result.bankName)
        assertEquals("INCOME", result.type)
        assertEquals(1_250_000.0, result.amount, 0.0)
    }

    @Test
    fun parseExpandedNotificationTechcombank() {
        val title = "Techcombank Mobile"
        val text = "Tài khoản thanh toán 1903xxx\nBiến động số dư: -320.000 VND lúc 10:15 20/08/2026\nSố dư khả dụng: 5.430.000 VND\nNội dung: Thanh toan tien dien"
        val packageName = "com.techcombank.mobile"

        val result = NotificationParser.parse(title, text, packageName)

        assertTrue(result.success)
        assertEquals("Techcombank", result.bankName)
        assertEquals("EXPENSE", result.type)
        assertEquals(320_000.0, result.amount, 0.0)
        assertEquals("Thanh toan tien dien", result.note)
    }

    @Test
    fun parseVPBankNotificationWithDotSeparators() {
        val title = "VPBank NEO"
        val text = "TK 123xxx: So du thay doi +2.500.000 VND. So du: 15.000.000 VND. Ref: Tien thuong quy"
        val packageName = "com.vpb.vpbankneo"

        val result = NotificationParser.parse(title, text, packageName)

        assertTrue(result.success)
        assertEquals("VPBank", result.bankName)
        assertEquals("INCOME", result.type)
        assertEquals(2_500_000.0, result.amount, 0.0)
        assertEquals("Tien thuong quy", result.note)
    }
}
