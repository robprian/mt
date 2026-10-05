package net.robrion.dompetnotif.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import net.robrion.dompetnotif.data.TxnStatus

/** Refresh status izin setiap kali layar kembali tampil (onResume). */
@Composable
fun rememberResumeTick(): Int {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    return tick
}

@Composable
fun DashboardScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    val notifGranted = remember(tick) { isNotifAccessGranted(ctx) }
    val batteryOk = remember(tick) { isBatteryIgnored(ctx) }

    val todayExpense by vm.todayExpense.collectAsState()
    val pending by vm.pendingCount.collectAsState()
    val txns by vm.txns.collectAsState()
    val byCategory by vm.summaryByCategory.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "DompetNotif",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Pencatat transaksi otomatis dari notifikasi m-banking & e-wallet.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // --- Kartu izin ---
        if (!notifGranted) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.NotificationsOff, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Izin akses notifikasi belum diberikan", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tanpa izin ini aplikasi tidak bisa membaca notifikasi " +
                                "pembayaran. Ketuk tombol, lalu aktifkan DompetNotif.",
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { openNotifAccessSettings(ctx) }) {
                            Text("Buka Pengaturan Izin")
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Akses notifikasi aktif — siap menangkap transaksi.")
                    }
                }
            }
        }

        if (notifGranted && !batteryOk) {
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Baterai: aplikasi belum dikecualikan", fontWeight = FontWeight.Bold)
                        Text(
                            "Agar pencatatan tetap jalan saat HP idle, kecualikan " +
                                "DompetNotif dari optimasi baterai.",
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { requestIgnoreBatteryOptimizations(ctx) }) {
                            Text("Kecualikan dari optimasi baterai")
                        }
                    }
                }
            }
        }

        // --- Statistik ---
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Keluar hari ini", fontSize = 12.sp)
                        Text(
                            formatRp(todayExpense),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Menunggu review", fontSize = 12.sp)
                        Text(
                            "$pending",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }

        if (byCategory.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Pengeluaran per kategori", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        byCategory.entries.sortedByDescending { it.value }.take(5)
                            .forEach { (k, v) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(k, fontSize = 13.sp)
                                    Text(formatRp(v), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                    }
                }
            }
        }

        item {
            Text("Terbaru", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        val recent = txns.take(5)
        if (recent.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Belum ada transaksi tertangkap.")
                        Text(
                            "Lakukan pembayaran QRIS/transfer lalu cek kembali.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recent, key = { it.id }) { t ->
                TxnRow(t, onClick = {})
            }
        }
    }
}

/** Baris transaksi ringkas, dipakai di dasbor & daftar. */
@Composable
fun TxnRow(
    t: net.robrion.dompetnotif.data.TxnEntity,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(t.packageName)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t.merchant ?: t.appName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                val cat = t.category.ifBlank { null }
                Text(
                    listOfNotNull(cat, "${t.kind} • ${formatTime(t.timestamp)}")
                        .joinToString(" • ") +
                        if (t.status == TxnStatus.PENDING) " • perlu review" else "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                (if (t.direction == "MASUK") "+" else "-") + formatRp(t.amount),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (t.direction == "MASUK")
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
        }
    }
}
