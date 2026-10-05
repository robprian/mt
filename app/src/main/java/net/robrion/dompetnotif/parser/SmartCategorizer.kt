package net.robrion.dompetnotif.parser

/**
 * Kategorisasi CERDAS transaksi.
 *
 * Urutan penentuan kategori:
 *  1. Memori belajar: bila user pernah mengoreksi kategori sebuah merchant,
 *     pilihan user selalu menang (aplikasi "belajar" dari koreksi).
 *  2. Aturan keyword: pola nama merchant/aplikasi Indonesia.
 *  3. Fallback dari jenis transaksi.
 *
 * Ini yang membuat aplikasi "smart": makin sering dikoreksi, makin akurat.
 */
object SmartCategorizer {

    val DEFAULT_CATEGORIES = listOf(
        "Makanan & Minum", "Belanja", "Transportasi", "Tagihan",
        "Kesehatan", "Hiburan", "Top Up", "Transfer",
        "Tarik Tunai", "Biaya Bank", "Pemasukan", "Lainnya"
    )

    private val RULES: List<Pair<Regex, String>> = listOf(
        Regex("alfamart|indomaret|superindo|hypermart|transmart|lottemart|giant|carrefour|yogya|borma") to "Belanja",
        Regex("kopi|starbucks|janji jiwa|kenangan|mcd|kfc|pizza|resto|rumah makan|warteg|dapur|cafe|kafe|bakso|soto|ayam|martabak|gofood|grabfood|shopeefood|kantin|warkop") to "Makanan & Minum",
        Regex("gojek|grab|ojek|kai|tiket\\.com|traveloka|tiket|travel|bensin|shell|pertamina|parkir|tol |tol_") to "Transportasi",
        Regex("pln|token listrik|indihome|telkom|pdam|bpjs|pajak|pbb") to "Tagihan",
        Regex("apotek|klinik|rumah sakit|\\brs\\b|dokter|halodoc|alodokter|puskesmas|lab klinik") to "Kesehatan",
        Regex("xxi|cgv|netflix|spotify|disney|steam|playstation|game|karaoke") to "Hiburan",
        Regex("gopay|dana|ovo|shopeepay|linkaja|isaku") to "Top Up",
    )

    /**
     * @param learned peta "merchant lowercase" -> kategori pilihan user.
     */
    fun suggest(
        merchant: String?,
        appName: String,
        kind: String,
        direction: String,
        learned: Map<String, String>,
    ): String {
        if (direction == Direction.MASUK) return "Pemasukan"

        val key = (merchant ?: appName).trim().lowercase()
        learned[key]?.let { return it }

        val hay = "$key ${appName.lowercase()}"
        RULES.firstOrNull { it.first.containsMatchIn(hay) }?.let { return it.second }

        return when (kind) {
            TxnKind.QRIS -> "Belanja"
            TxnKind.TRANSFER -> "Transfer"
            TxnKind.TOPUP -> "Top Up"
            TxnKind.TARIK_TUNAI -> "Tarik Tunai"
            TxnKind.BIAYA_ADMIN -> "Biaya Bank"
            else -> "Lainnya"
        }
    }
}
