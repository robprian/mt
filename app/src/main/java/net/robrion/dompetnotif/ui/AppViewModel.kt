package net.robrion.dompetnotif.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.robrion.dompetnotif.DompetNotifApp
import net.robrion.dompetnotif.data.TxnEntity
import net.robrion.dompetnotif.data.TxnStatus
import net.robrion.dompetnotif.sync.DompetSync
import java.time.LocalDate
import java.time.ZoneId

val WIB: ZoneId = ZoneId.of("Asia/Jakarta")

/** ViewModel tunggal untuk seluruh layar. */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = DompetNotifApp.db.txnDao()
    private val prefs = (app as DompetNotifApp).prefs

    val txns: StateFlow<List<TxnEntity>> =
        dao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingCount: StateFlow<Int> =
        dao.pendingCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayExpense: StateFlow<Long> = run {
        val startOfDay = LocalDate.now(WIB).atStartOfDay(WIB).toInstant().toEpochMilli()
        val endOfDay = startOfDay + 24 * 60 * 60 * 1000L
        dao.confirmedExpenseSum(startOfDay, endOfDay)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    }

    val monitoredPkgs = prefs.monitoredPkgs
    val autoConfirm = prefs.autoConfirm
    val autoSync = prefs.autoSync
    val baseUrl = prefs.baseUrl
    val apiToken = prefs.apiToken

    fun confirm(id: Long) = updateStatus(id, TxnStatus.CONFIRMED)
    fun ignore(id: Long) = updateStatus(id, TxnStatus.IGNORED)
    fun reopen(id: Long) = updateStatus(id, TxnStatus.PENDING)

    private fun updateStatus(id: Long, status: String) {
        viewModelScope.launch {
            dao.byId(id)?.let { dao.update(it.copy(status = status)) }
        }
    }

    fun saveEdited(updated: TxnEntity) {
        viewModelScope.launch { dao.update(updated) }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            dao.byId(id)?.let { dao.delete(it) }
        }
    }

    fun setMonitored(pkgs: Set<String>) {
        viewModelScope.launch { prefs.setMonitored(pkgs) }
    }

    fun setAutoConfirm(v: Boolean) {
        viewModelScope.launch { prefs.setAutoConfirm(v) }
    }

    fun setAutoSync(v: Boolean) {
        viewModelScope.launch { prefs.setAutoSync(v) }
    }

    fun setBaseUrl(v: String) {
        viewModelScope.launch { prefs.setBaseUrl(v) }
    }

    fun setApiToken(v: String) {
        viewModelScope.launch { prefs.setApiToken(v) }
    }

    /** Kirim satu transaksi ke DompetKu. Callback menerima pesan hasil. */
    fun syncOne(id: Long, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val txn = dao.byId(id)
            if (txn == null) {
                onDone("Transaksi tidak ditemukan")
                return@launch
            }
            val snap = prefs.snapshot()
            if (snap.apiToken.isEmpty()) {
                onDone("Token API belum diisi (lihat Pengaturan).")
                return@launch
            }
            val res = DompetSync.pushExpense(snap.baseUrl, snap.apiToken, txn)
            if (res.isSuccess) {
                dao.update(txn.copy(synced = true))
                onDone("Terkirim ke server ✓")
            } else {
                onDone(res.exceptionOrNull()?.message ?: "Sync gagal")
            }
        }
    }

    /**
     * Simpan hasil edit (termasuk learning kategori) LALU sync dalam satu
     * coroutine — jadi tidak ada race antara simpan vs kirim.
     */
    fun saveAndSync(updated: TxnEntity, originalCategory: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            dao.update(updated)
            val m = updated.merchant
            if (m != null && updated.category.isNotBlank() &&
                updated.category != originalCategory
            ) {
                prefs.learnCategory(m, updated.category)
            }
            val snap = prefs.snapshot()
            if (snap.apiToken.isEmpty() || snap.baseUrl.isEmpty()) {
                onDone("Isi URL server & hubungkan dulu (lihat Pengaturan).")
                return@launch
            }
            val res = DompetSync.pushExpense(snap.baseUrl, snap.apiToken, updated)
            if (res.isSuccess) {
                dao.update(updated.copy(synced = true))
                onDone("Terkirim ke server ✓")
            } else {
                onDone(res.exceptionOrNull()?.message ?: "Sync gagal")
            }
        }
    }

    /** Ringkasan pengeluaran per kategori untuk dasbor. */
    val summaryByCategory: StateFlow<Map<String, Long>> =
        txns.map { list ->
            list.filter { it.status == TxnStatus.CONFIRMED && it.direction == "KELUAR" }
                .groupBy { it.category.ifBlank { "Lainnya" } }
                .mapValues { (_, v) -> v.sumOf { it.amount } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Simpan hasil edit + ajari memori kategori bila user mengubahnya. */
    fun saveEditedWithLearning(updated: TxnEntity, originalCategory: String) {
        viewModelScope.launch {
            dao.update(updated)
            val m = updated.merchant
            if (m != null && updated.category.isNotBlank() &&
                updated.category != originalCategory
            ) {
                prefs.learnCategory(m, updated.category)
            }
        }
    }
}
