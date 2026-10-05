package net.robrion.dompetnotif.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.robrion.dompetnotif.DompetNotifApp
import net.robrion.dompetnotif.data.Prefs
import net.robrion.dompetnotif.data.TxnEntity
import net.robrion.dompetnotif.data.TxnStatus
import net.robrion.dompetnotif.parser.TxnParser
import net.robrion.dompetnotif.sync.DompetSync

/**
 * Inti aplikasi: menangkap notifikasi dari aplikasi m-banking/e-wallet
 * yang dipilih user, mem-parsing-nya menjadi data transaksi, lalu
 * menyimpannya ke database lokal.
 *
 * Android hanya memanggil service ini bila user sudah memberikan izin
 * "Notification access" (diaktifkan manual di Pengaturan sistem).
 */
class TxnListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Jangan proses notifikasi dari aplikasi ini sendiri.
        if (sbn.packageName == packageName) return
        // Abaikan notifikasi ongoing (musik, navigasi, dsb).
        if (sbn.isOngoing) return

        val notif: Notification = sbn.notification ?: return
        val title = notif.extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString().orEmpty().trim()
        // EXTRA_BIG_TEXT memuat isi lengkap; fallback ke EXTRA_TEXT.
        val text = notif.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?.toString()
            ?: notif.extras.getCharSequence(Notification.EXTRA_TEXT)
                ?.toString().orEmpty()
        val cleanText = text.trim()
        if (title.isEmpty() && cleanText.isEmpty()) return

        scope.launch {
            val app = applicationContext as DompetNotifApp

            // 1. Hanya proses aplikasi yang dipantau user.
            if (!app.prefs.isMonitored(sbn.packageName)) return@launch

            // 2. Parse. null = tidak ada nominal -> bukan notif transaksi.
            val parsed = TxnParser.parse(title, cleanText) ?: return@launch

            val dao = DompetNotifApp.db.txnDao()
            val now = System.currentTimeMillis()

            // 3. Dedup: satu transaksi sering memicu >1 notifikasi
            //    (cth: blu mengirim notif email + notif "QRIS Berhasil").
            val similar = dao.findSimilar(
                sbn.packageName, parsed.amount, now - Prefs.DEDUP_WINDOW_MS
            )
            if (similar != null) return@launch

            val snap = app.prefs.snapshot()
            val autoConfirm =
                snap.autoConfirm && parsed.confidence >= snap.confirmThreshold

            val appLabel = appLabelOf(sbn.packageName)
            val category = net.robrion.dompetnotif.parser.SmartCategorizer.suggest(
                merchant = parsed.merchant,
                appName = appLabel,
                kind = parsed.kind,
                direction = parsed.direction,
                learned = snap.categoryMemory,
            )

            val entity = TxnEntity(
                packageName = sbn.packageName,
                appName = appLabel,
                title = title,
                rawText = cleanText,
                amount = parsed.amount,
                merchant = parsed.merchant,
                kind = parsed.kind,
                direction = parsed.direction,
                confidence = parsed.confidence,
                timestamp = now,
                status = if (autoConfirm) TxnStatus.CONFIRMED else TxnStatus.PENDING,
                category = category,
            )
            val id = dao.insert(entity)

            // 4. Sinkronisasi opsional ke DompetKu (hanya yg terkonfirmasi).
            if (autoConfirm && snap.autoSync && snap.apiToken.isNotEmpty()) {
                val saved = dao.byId(id) ?: return@launch
                val res = DompetSync.pushExpense(
                    baseUrl = snap.baseUrl,
                    token = snap.apiToken,
                    txn = saved,
                )
                if (res.isSuccess) {
                    dao.update(saved.copy(synced = true))
                }
                // Gagal sync -> biarkan synced=false; user bisa sync ulang
                // manual dari layar Transaksi. Tidak ada retry agresif agar
                // tidak membanjiri server.
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Tidak ada aksi: data yang sudah ditangkap tetap tersimpan.
    }

    private fun appLabelOf(pkg: String): String {
        return try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            pkg
        }
    }
}
