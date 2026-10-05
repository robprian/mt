package net.robrion.dompetnotif.parser

/** Jenis transaksi hasil parsing. */
object TxnKind {
    const val QRIS = "QRIS"
    const val TRANSFER = "TRANSFER"
    const val TOPUP = "TOPUP"
    const val TARIK_TUNAI = "TARIK_TUNAI"
    const val BIAYA_ADMIN = "BIAYA_ADMIN"
    const val LAINNYA = "LAINNYA"
}

/** Arah aliran uang. */
object Direction {
    const val KELUAR = "KELUAR"
    const val MASUK = "MASUK"
    const val UNKNOWN = "UNKNOWN"
}

data class ParsedTxn(
    val amount: Long,        // rupiah, dibulatkan
    val merchant: String?,   // merchant/penerima, null bila tak terdeteksi
    val kind: String,        // TxnKind.*
    val direction: String,   // Direction.*
    val confidence: Float,   // 0..1
)

/**
 * Parser notifikasi transaksi berbahasa Indonesia.
 *
 * Strategi: generik, tidak bergantung pada nama paket aplikasi bank tertentu,
 * karena tiap bank/e-wallet punya format notifikasi berbeda dan bisa berubah
 * sewaktu-waktu. Hasil confidence rendah (< threshold) masuk status PENDING
 * agar user meninjau manual di aplikasi.
 *
 * Contoh yang ditangani:
 *  - "Yes, Transaksi QRIS Berhasil!" / "Transaksi di ALFA_1M4T_JLMAMPANGVI Rp 40.600 berhasil"
 *    -> 40600, merchant ALFA_1M4T_JLMAMPANGVI, QRIS, KELUAR
 *  - "Transfer ke BCA 1234567890 sebesar Rp 1.500.000 berhasil"
 *    -> 1500000, TRANSFER, KELUAR
 *  - "Dana masuk Rp 250.000 dari Budi Santoso" -> 250000, MASUK
 */
object TxnParser {

    // Rp 40.600 / Rp40.600 / Rp 1.500.000,50
    private val amountRe =
        Regex("""Rp\.?\s*([\d.]+(?:,\d{1,2})?)""", RegexOption.IGNORE_CASE)

    // Akhir nama merchant: diikuti "Rp"/kata kunci, atau akhir teks.
    // (lookahead + lazy matching agar grup berhenti di batas yang benar)
    private const val MERCHANT_END =
        """(?=\s+(?:[Rr][Pp]\b|sebesar\b|senilai\b|berhasil\b|sukses\b|telah\b|pada\b|dengan\b|sejumlah\b|ke\b|dari\b)|\s*$)"""

    // "Transaksi di ALFA_1M4T_JLMAMPANGVI Rp 40.600 ..." -> merchant
    private val merchantDiRe =
        Regex("""\b[Dd]i\s+([A-Za-z0-9][A-Za-z0-9_\-. ,]{1,42}?)$MERCHANT_END""")

    // "Transfer ke BCA 1234567890 ..." / "kepada Budi Santoso" -> penerima
    private val merchantKeRe = Regex(
        """\b[Kk]epada\s+([A-Za-z0-9][A-Za-z0-9_\-. ,]{1,42}?)$MERCHANT_END""" +
            """|\b[Kk]e\s+([A-Za-z0-9][A-Za-z0-9_\-. ,]{1,42}?)$MERCHANT_END"""
    )

    /** Ubah "40.600" / "1.500.000,50" menjadi Long rupiah. */
    fun parseRp(raw: String): Long {
        val normalized = raw.replace(".", "").replace(',', '.')
        return normalized.toDoubleOrNull()?.let { Math.round(it) } ?: 0L
    }

    /**
     * Ambil nominal paling relevan. Bila ada beberapa angka Rp (misal sisa
     * saldo ikut tercantum), pilih yang konteksnya paling "transaksional"
     * dan penalti yang konteksnya "saldo/sisa".
     */
    fun extractAmount(text: String): Long? {
        data class Cand(val value: Long, val score: Int, val pos: Int)
        val cands = amountRe.findAll(text).mapNotNull { m ->
            val value = parseRp(m.groupValues[1])
            if (value <= 0) return@mapNotNull null
            val before = text.substring(maxOf(0, m.range.first - 40), m.range.first)
                .lowercase()
            var score = 0
            if (before.contains("sebesar") || before.contains("senilai") ||
                before.contains("nominal") || before.contains("total")
            ) score += 3
            if (before.contains("transaksi") || before.contains("bayar") ||
                before.contains("transfer") || before.contains("top up") ||
                before.contains("topup") || before.contains("tarik")
            ) score += 2
            if (before.contains("saldo") || before.contains("sisa")) score -= 4
            Cand(value, score, m.range.first)
        }.toList()
        if (cands.isEmpty()) return null
        return cands.sortedWith(compareByDescending<Cand> { it.score }.thenBy { it.pos })
            .first().value
    }

    fun detectKind(text: String): String {
        val l = text.lowercase()
        return when {
            l.contains("qris") -> TxnKind.QRIS
            l.contains("tarik tunai") || l.contains("penarikan tunai") -> TxnKind.TARIK_TUNAI
            l.contains("top up") || l.contains("topup") || l.contains("isi saldo") -> TxnKind.TOPUP
            l.contains("biaya admin") || l.contains("admin fee") -> TxnKind.BIAYA_ADMIN
            l.contains("transfer") -> TxnKind.TRANSFER
            else -> TxnKind.LAINNYA
        }
    }

    fun detectDirection(text: String): String {
        val l = text.lowercase()
        return when {
            l.contains("diterima") -> Direction.MASUK
            l.contains("dana masuk") || l.contains("saldo masuk") ||
                l.contains("uang masuk") -> Direction.MASUK
            // "Transfer dari Budi ..." = uang masuk; "Transfer ke ..." = uang keluar
            l.contains("dari ") && l.contains("transfer") -> Direction.MASUK
            l.contains("penarikan") || l.contains("tarik tunai") -> Direction.KELUAR
            l.contains("dipotong") || l.contains("terpotong") ||
                l.contains("dibebankan") -> Direction.KELUAR
            l.contains("berhasil") || l.contains("sukses") -> Direction.KELUAR
            else -> Direction.UNKNOWN
        }
    }

    /** Ambil merchant/penerima; null bila tidak ketemu pola yang jelas. */
    fun extractMerchant(title: String, text: String): String? {
        val combined = "$title\n$text"
        // Pola "di MERCHANT" — potong di kata kunci akhir.
        merchantDiRe.find(combined)?.let { m ->
            val raw = m.groupValues[1].trim()
            return cleanMerchant(raw)
        }
        merchantKeRe.find(combined)?.let { m ->
            val raw = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
            return cleanMerchant(raw)
        }
        return null
    }

    private val stopWords = listOf(
        "rp", "sebesar", "senilai", "berhasil", "sukses", "telah", "pada",
        "tanggal", "pukul", "dengan", "nomor", "sejumlah"
    )

    private fun cleanMerchant(raw: String): String? {
        if (raw.length < 2) return null
        // Potong pada kata berhenti pertama ("ALFA ... Rp" -> "ALFA ...").
        val words = raw.split(Regex("\\s+"))
        val kept = words.takeWhile { w -> w.lowercase().trimEnd('.', ',') !in stopWords }
        var name = kept.joinToString(" ").trim().trimEnd('.', ',', '-', '/')
        // "ke BCA 123" kadang masih berekor "Rp" yang lolos — potong lagi.
        name = name.split(Regex("""\s+[Rr][Pp]\b""")).first().trim()
        if (name.length < 2) return null
        // Hindari menangkap kalimat panjang yang jelas bukan nama merchant.
        if (name.length > 42) name = name.take(42).trim()
        return name.ifEmpty { null }
    }

    /**
     * Parse judul + isi notifikasi. Mengembalikan null bila tidak ada nominal
     * yang terdeteksi (bukan notifikasi transaksi) — caller sebaiknya
     * mengabaikan notifikasi tersebut.
     */
    fun parse(title: String, text: String): ParsedTxn? {
        val combined = "$title\n$text"
        val amount = extractAmount(combined) ?: return null
        val kind = detectKind(combined)
        val direction = detectDirection(combined)
        val merchant = extractMerchant(title, text)

        var conf = 0.5f          // dasar
        conf += 0.20f            // nominal ditemukan (sudah pasti di sini)
        if (kind != TxnKind.LAINNYA) conf += 0.15f
        if (merchant != null) conf += 0.15f
        if (direction != Direction.UNKNOWN) conf += 0.05f

        return ParsedTxn(
            amount = amount,
            merchant = merchant,
            kind = kind,
            direction = direction,
            confidence = conf.coerceAtMost(1.0f),
        )
    }
}
