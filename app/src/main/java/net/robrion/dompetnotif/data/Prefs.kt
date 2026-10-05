package net.robrion.dompetnotif.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dompetnotif_prefs"
)

/**
 * Pengaturan aplikasi (DataStore). Sengaja TIDAK ada daftar paket bawaan:
 * user memilih sendiri aplikasi yang dipantau dari daftar aplikasi
 * terinstal, supaya tidak bergantung pada tebakan nama paket bank.
 */
class Prefs(private val context: Context) {

    companion object {
        private val KEY_MONITORED = stringSetPreferencesKey("monitored_pkgs")
        private val KEY_AUTO_CONFIRM = booleanPreferencesKey("auto_confirm")
        private val KEY_CONF_THRESHOLD = floatPreferencesKey("confirm_threshold")
        private val KEY_AUTO_SYNC = booleanPreferencesKey("auto_sync")
        private val KEY_BASE_URL = stringPreferencesKey("dompet_base_url")
        private val KEY_API_TOKEN = stringPreferencesKey("dompet_api_token")
        private val KEY_CAT_MEMORY = stringPreferencesKey("category_memory")

        /** Kosong = mode lokal saja. User isi sendiri bila punya server. */
        const val DEFAULT_BASE_URL = ""
        const val DEFAULT_THRESHOLD = 0.95f
        const val DEDUP_WINDOW_MS = 15 * 60 * 1000L
        const val MAX_CAT_MEMORY = 300
    }

    val monitoredPkgs: Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_MONITORED] ?: emptySet() }

    val autoConfirm: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUTO_CONFIRM] ?: true }

    val confirmThreshold: Flow<Float> =
        context.dataStore.data.map { it[KEY_CONF_THRESHOLD] ?: DEFAULT_THRESHOLD }

    val autoSync: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUTO_SYNC] ?: false }

    val baseUrl: Flow<String> =
        context.dataStore.data.map { it[KEY_BASE_URL] ?: DEFAULT_BASE_URL }

    val apiToken: Flow<String> =
        context.dataStore.data.map { it[KEY_API_TOKEN] ?: "" }

    suspend fun isMonitored(pkg: String): Boolean =
        context.dataStore.data.map { it[KEY_MONITORED] ?: emptySet() }.first().contains(pkg)

    suspend fun setMonitored(pkgs: Set<String>) {
        context.dataStore.edit { it[KEY_MONITORED] = pkgs }
    }

    suspend fun setAutoConfirm(v: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_CONFIRM] = v }
    }

    suspend fun setAutoSync(v: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SYNC] = v }
    }

    suspend fun setBaseUrl(v: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = v.trim().trimEnd('/') }
    }

    suspend fun setApiToken(v: String) {
        context.dataStore.edit { it[KEY_API_TOKEN] = v.trim() }
    }

    /** Nilai sekali baca untuk dipakai dari background service. */
    suspend fun snapshot(): PrefsSnapshot {
        val d = context.dataStore.data.first()
        return PrefsSnapshot(
            autoConfirm = d[KEY_AUTO_CONFIRM] ?: true,
            confirmThreshold = d[KEY_CONF_THRESHOLD] ?: DEFAULT_THRESHOLD,
            autoSync = d[KEY_AUTO_SYNC] ?: false,
            baseUrl = d[KEY_BASE_URL] ?: DEFAULT_BASE_URL,
            apiToken = d[KEY_API_TOKEN] ?: "",
            categoryMemory = parseCatMemory(d[KEY_CAT_MEMORY]),
        )
    }

    // ---------- Memori kategori (aplikasi "belajar" dari koreksi user) ----------

    /** Peta "merchant lowercase" -> kategori pilihan user. */
    val categoryMemory: Flow<Map<String, String>> =
        context.dataStore.data.map { parseCatMemory(it[KEY_CAT_MEMORY]) }

    private fun parseCatMemory(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(raw)
            buildMap {
                obj.keys().forEach { k -> put(k, obj.getString(k)) }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** Simpan koreksi kategori user agar dipakai untuk transaksi berikutnya. */
    suspend fun learnCategory(merchant: String, category: String) {
        val key = merchant.trim().lowercase()
        if (key.isBlank() || category.isBlank()) return
        context.dataStore.edit { prefs ->
            val cur = parseCatMemory(prefs[KEY_CAT_MEMORY]).toMutableMap()
            cur[key] = category
            // Batasi ukuran: buang yang paling lama bila kepenuhan.
            while (cur.size > MAX_CAT_MEMORY) {
                cur.remove(cur.keys.first())
            }
            prefs[KEY_CAT_MEMORY] = org.json.JSONObject(cur as Map<*, *>).toString()
        }
    }
}

data class PrefsSnapshot(
    val autoConfirm: Boolean,
    val confirmThreshold: Float,
    val autoSync: Boolean,
    val baseUrl: String,
    val apiToken: String,
    val categoryMemory: Map<String, String>,
)
