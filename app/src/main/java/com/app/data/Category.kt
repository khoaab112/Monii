package com.app.data

data class FinanceCategory(
    val name: String,
    val iconName: String,
    val colorHex: String,
    val type: String, // INCOME, EXPENSE, BOTH
    val parentName: String? = null // For subcategories
)

object Categories {
    val list = listOf(
        FinanceCategory("Ăn uống", "Restaurant", "#FF9800", "EXPENSE"),
        FinanceCategory("Giải khát", "LocalCafe", "#795548", "EXPENSE", parentName = "Ăn uống"),
        FinanceCategory("Di chuyển", "DirectionsCar", "#2196F3", "EXPENSE"),
        FinanceCategory("Xăng xe", "LocalGasStation", "#FF5722", "EXPENSE", parentName = "Di chuyển"),
        FinanceCategory("Giao thông", "Commute", "#3F51B5", "EXPENSE", parentName = "Di chuyển"),
        FinanceCategory("Mua sắm", "ShoppingCart", "#E91E63", "EXPENSE"),
        FinanceCategory("Quần áo", "Checkroom", "#9C27B0", "EXPENSE", parentName = "Mua sắm"),
        FinanceCategory("Hóa đơn", "Receipt", "#FFC107", "EXPENSE"),
        FinanceCategory("Giải trí", "SportsEsports", "#673AB7", "EXPENSE"),
        FinanceCategory("Phim ảnh", "Movie", "#E040FB", "EXPENSE", parentName = "Giải trí"),
        FinanceCategory("Ngủ nghỉ", "Bed", "#00BCD4", "EXPENSE"),
        FinanceCategory("Du lịch", "Flight", "#009688", "EXPENSE"),
        FinanceCategory("Y tế", "MedicalServices", "#F44336", "EXPENSE"),
        FinanceCategory("Ốm đau", "Sick", "#D32F2F", "EXPENSE", parentName = "Y tế"),
        FinanceCategory("Thuốc men", "Medication", "#009688", "EXPENSE", parentName = "Y tế"),
        FinanceCategory("Sức khỏe", "FitnessCenter", "#8BC34A", "EXPENSE"),
        FinanceCategory("Giáo dục", "School", "#03A9F4", "EXPENSE"),
        FinanceCategory("Nhà cửa", "Home", "#8D6E63", "EXPENSE"),
        FinanceCategory("Thú cưng", "Pets", "#FF7043", "EXPENSE"),
        FinanceCategory("Lương", "Work", "#4CAF50", "INCOME"),
        FinanceCategory("Thưởng", "CardGiftcard", "#8BC34A", "INCOME"),
        FinanceCategory("Kinh doanh", "Storefront", "#009688", "INCOME"),
        FinanceCategory("Đầu tư", "TrendingUp", "#00C853", "INCOME"),
        FinanceCategory("Tiền tệ", "CurrencyExchange", "#2E7D32", "BOTH"),
        FinanceCategory("Tài chính", "AccountBalance", "#1976D2", "BOTH"),
        FinanceCategory("Tích lũy", "Savings", "#FFCA28", "INCOME"),
        FinanceCategory("Khác", "Category", "#607D8B", "BOTH")
    )

    fun getByCategoryName(name: String): FinanceCategory {
        return list.firstOrNull { it.name.lowercase() == name.lowercase() }
            ?: FinanceCategory("Khác", "Category", "#607D8B", "BOTH")
    }
}
