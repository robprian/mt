package net.robrion.dompetnotif.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.robrion.dompetnotif.data.TxnEntity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Sinkronisasi opsional ke server money tracker pribadi
 * (kompatibel API DompetKu: GET/PUT /api/state).
 *
 * Cara kerja: baca seluruh state user via GET /api/state, tambahkan satu
 * entri ke array "expenses", lalu tulis balik via PUT /api/state.
 * Bentuk entri mengikuti skema state v7/v8 DompetKu:
 * {id, date, amount, fee, accountId, catId, note, cycle}
 *
 * BUTUH TOKEN API. Endpoint /api/login di server dilindungi Cloudflare
 * Turnstile sehingga aplikasi tidak bisa login otomatis — token harus
 * dimasukkan manual di Pengaturan (lihat README bagian "Sinkronisasi").
 * Token yang kedaluwarsa akan menyebabkan hasil failure("HTTP 401").
 *
 * CATATAN KONKURENSI: PUT /api/state menimpa seluruh state. Bila web
 * DompetKu sedang dibuka dan ada perubahan yang belum tersimpan di sana,
 * perubahan itu bisa tertimpa. Untuk v1 ini, sync dilakukan secepatnya
 * setelah GET untuk memperkecil jendela konflik.
 */
object DompetSync {

    private val WIB = ZoneId.of("Asia/Jakarta")

    suspend fun pushExpense(
        baseUrl: String,
        token: String,
        txn: TxnEntity,
        accountId: String = "",
        catId: String = "",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val base = baseUrl.trimEnd('/')

            // 1. GET state terbaru
            val getConn = (URL("$base/api/state").openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Authorization", "Bearer $token")
                }
            val getCode = getConn.responseCode
            if (getCode == 401) {
                return@withContext Result.failure(Exception("Token kedaluwarsa/invalid (HTTP 401). Perbarui token di Pengaturan."))
            }
            if (getCode != 200) {
                return@withContext Result.failure(Exception("GET /api/state gagal: HTTP $getCode"))
            }
            val stateJson = JSONObject(getConn.inputStream.bufferedReader().readText())
            if (!stateJson.optBoolean("ok")) {
                return@withContext Result.failure(Exception("GET /api/state: respons tidak ok"))
            }
            val data = stateJson.optJSONObject("data") ?: JSONObject()
            val salaryDay = data.optInt("salaryDay", 28)

            // 2. Tambahkan entri expense
            val expenses: JSONArray = data.optJSONArray("expenses") ?: JSONArray().also {
                data.put("expenses", it)
            }
            val entry = JSONObject()
            entry.put("id", "auto-${UUID.randomUUID()}")
            entry.put("date", LocalDate.now(WIB).toString())
            entry.put("amount", txn.amount.toDouble())
            entry.put("fee", 0.0)
            entry.put("accountId", accountId)
            entry.put("catId", catId)
            val merchantPart = txn.merchant?.let { " — $it" } ?: ""
            entry.put("note", "🤖 ${txn.appName}$merchantPart [${txn.kind}]")
            entry.put("cycle", cycleKey(salaryDay))
            expenses.put(entry)

            // 3. PUT state kembali
            val payload = JSONObject().put("data", data).toString()
            val putConn = (URL("$base/api/state").openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = "PUT"
                    doOutput = true
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $token")
                }
            putConn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val putCode = putConn.responseCode
            if (putCode == 401) {
                return@withContext Result.failure(Exception("Token kedaluwarsa/invalid (HTTP 401). Perbarui token di Pengaturan."))
            }
            if (putCode != 200) {
                return@withContext Result.failure(Exception("PUT /api/state gagal: HTTP $putCode"))
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(Exception("Sync gagal: ${t.message}"))
        }
    }

    /**
     * Kunci siklus mengikuti logika DompetKu: siklus berjalan dari
     * salaryDay bulan ini (atau bulan lalu bila hari ini < salaryDay).
     */
    fun cycleKey(salaryDay: Int): String {
        var date = LocalDate.now(WIB)
        if (date.dayOfMonth < salaryDay) {
            date = date.minusMonths(1)
        }
        return "%04d-%02d".format(date.year, date.monthValue)
    }
}
