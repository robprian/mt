package net.robrion.dompetnotif.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Status review oleh user. */
object TxnStatus {
    const val PENDING = "PENDING"       // baru ditangkap, menunggu dikonfirmasi
    const val CONFIRMED = "CONFIRMED"   // sudah dikonfirmasi user (masuk pembukuan)
    const val IGNORED = "IGNORED"       // diabaikan (bukan transaksi / duplikat)
}

/**
 * Satu transaksi yang ditangkap dari notifikasi.
 * Semua data tersimpan LOKAL di HP (Room/SQLite). Tidak dikirim ke mana-mana
 * kecuali user mengaktifkan sinkronisasi di Pengaturan.
 */
@Entity(tableName = "transactions")
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,      // cth: id.co.bca... (paket aplikasi sumber)
    val appName: String,          // label aplikasi, cth: "blu"
    val title: String,            // judul notifikasi asli
    val rawText: String,          // isi notifikasi asli (untuk audit)
    val amount: Long,             // nominal rupiah (tanpa desimal)
    val merchant: String?,        // merchant/penerima hasil parsing, bisa null
    val kind: String,             // QRIS / TRANSFER / TOPUP / TARIK_TUNAI / BIAYA_ADMIN / LAINNYA
    val direction: String,        // KELUAR / MASUK / UNKNOWN
    val confidence: Float,        // 0..1 keyakinan parser
    val timestamp: Long,          // epoch millis (WIB saat notifikasi masuk)
    val status: String = TxnStatus.PENDING,
    val synced: Boolean = false,  // sudah terkirim ke server?
    val note: String = "",
    val category: String = "",    // kategori smart (diisi SmartCategorizer)
)

@Dao
interface TxnDao {
    @Insert
    suspend fun insert(txn: TxnEntity): Long

    @Update
    suspend fun update(txn: TxnEntity)

    @Delete
    suspend fun delete(txn: TxnEntity)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun all(): Flow<List<TxnEntity>>

    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY timestamp DESC")
    fun byStatus(status: String): Flow<List<TxnEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): TxnEntity?

    /**
     * Dedup: cari transaksi mirip (paket + nominal sama) dalam [since, now].
     * Menangani kasus aplikasi mengirim 2 notifikasi untuk 1 transaksi
     * (cth: blu mengirim notif email + notif "QRIS Berhasil").
     */
    @Query(
        """SELECT * FROM transactions
           WHERE packageName = :pkg AND amount = :amount AND timestamp > :since
           ORDER BY timestamp DESC LIMIT 1"""
    )
    suspend fun findSimilar(pkg: String, amount: Long, since: Long): TxnEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING'")
    fun pendingCount(): Flow<Int>

    @Query(
        """SELECT COALESCE(SUM(amount),0) FROM transactions
           WHERE direction = 'KELUAR' AND status = 'CONFIRMED'
           AND timestamp >= :fromTs AND timestamp < :toTs"""
    )
    fun confirmedExpenseSum(fromTs: Long, toTs: Long): Flow<Long>
}

@Database(entities = [TxnEntity::class], version = 2, exportSchema = false)
abstract class TxnDatabase : RoomDatabase() {
    abstract fun txnDao(): TxnDao
}
