package net.robrion.dompetnotif.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.robrion.dompetnotif.parser.TxnParser

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val monitored by vm.monitoredPkgs.collectAsState(initial = emptySet())
    val autoConfirm by vm.autoConfirm.collectAsState(initial = true)
    val autoSync by vm.autoSync.collectAsState(initial = false)
    val baseUrl by vm.baseUrl.collectAsState(initial = "")
    val apiToken by vm.apiToken.collectAsState(initial = "")

    var showPicker by remember { mutableStateOf(false) }
    var showParserTest by remember { mutableStateOf(false) }
    var baseUrlEdit by remember(baseUrl) { mutableStateOf(baseUrl) }
    var monitoredLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Label aplikasi terpilih (untuk ringkasan)
    LaunchedEffect(monitored) {
        withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val map = monitored.associateWith { pkg ->
                try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) { pkg }
            }
            withContext(Dispatchers.Main) { monitoredLabels = map }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Pengaturan", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        // ---- Aplikasi yang dipantau ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Aplikasi yang dipantau", fontWeight = FontWeight.Bold)
                Text(
                    "Hanya notifikasi dari aplikasi ini yang dibaca. " +
                        "Pilih m-banking / e-wallet yang kamu pakai (blu, BCA, BRImo, DANA, GoPay, dsb).",
                    fontSize = 13.sp
                )
                if (monitoredLabels.isNotEmpty()) {
                    Text(
                        monitoredLabels.values.sorted().joinToString(", "),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text("Belum ada aplikasi dipilih.", fontSize = 13.sp)
                }
                Button(onClick = { showPicker = true }) {
                    Text("Pilih aplikasi (${monitored.size})")
                }
            }
        }

        // ---- Konfirmasi otomatis ----
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Konfirmasi otomatis", fontWeight = FontWeight.Bold)
                    Text(
                        "Hasil parsing dengan keyakinan ≥ 95% langsung dikonfirmasi " +
                            "tanpa review manual.",
                        fontSize = 13.sp
                    )
                }
                Switch(checked = autoConfirm, onCheckedChange = { vm.setAutoConfirm(it) })
            }
        }

        // ---- Sinkronisasi server (opsional) ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sinkronisasi server (opsional)", fontWeight = FontWeight.Bold)
                Text(
                    "Kirim transaksi terkonfirmasi ke server money tracker pribadimu " +
                        "(kompatibel API DompetKu: GET/PUT /api/state). Kosongkan untuk mode lokal saja.",
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = baseUrlEdit,
                    onValueChange = { baseUrlEdit = it },
                    label = { Text("URL server (cth. https://dompet.contoh.id)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (baseUrlEdit != baseUrl) {
                    OutlinedButton(onClick = { vm.setBaseUrl(baseUrlEdit) }) {
                        Text("Simpan URL")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (apiToken.isNotEmpty()) "Status: terhubung ✓" else "Status: belum terhubung",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            ctx.startActivity(Intent(ctx, WebLoginActivity::class.java))
                        },
                        enabled = baseUrl.isNotEmpty()
                    ) {
                        Text("Hubungkan")
                    }
                    if (apiToken.isNotEmpty()) {
                        OutlinedButton(onClick = { vm.setApiToken("") }) {
                            Text("Putuskan")
                        }
                    }
                }
                Text(
                    "\"Hubungkan\" membuka halaman login server di dalam aplikasi. " +
                        "Login seperti biasa, token sesi diambil otomatis dan hanya " +
                        "dipakai ke server milikmu.",
                    fontSize = 12.sp
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Sync otomatis saat transaksi ditangkap", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Hanya untuk transaksi yang terkonfirmasi otomatis.",
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = autoSync,
                        onCheckedChange = { vm.setAutoSync(it) },
                        enabled = apiToken.isNotEmpty() && baseUrl.isNotEmpty()
                    )
                }
            }
        }

        // ---- Uji parser ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Uji parser", fontWeight = FontWeight.Bold)
                Text(
                    "Tempel judul + isi notifikasi untuk melihat hasil parsing. " +
                        "Berguna saat format notifikasi bank berubah.",
                    fontSize = 13.sp
                )
                OutlinedButton(onClick = { showParserTest = true }) {
                    Text("Buka alat uji parser")
                }
            }
        }

        // ---- Export ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Export data", fontWeight = FontWeight.Bold)
                val txns by vm.txns.collectAsState()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val f = exportCsv(ctx, txns)
                        shareFile(ctx, f, "text/csv")
                    }) { Text("Export CSV") }
                    OutlinedButton(onClick = {
                        val f = exportJson(ctx, txns)
                        shareFile(ctx, f, "application/json")
                    }) { Text("Export JSON") }
                }
            }
        }

        // ---- Tentang ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tentang", fontWeight = FontWeight.Bold)
                Text(
                    "DompetNotif v1.1.0 — pencatat transaksi otomatis.\n" +
                        "• Semua data tersimpan lokal di HP (tidak ada server pihak ketiga).\n" +
                        "• Aplikasi hanya MEMBACA notifikasi, tidak bisa mengakses " +
                        "isi aplikasi bank atau melakukan transaksi.\n" +
                        "• Butuh Android 8+ dan izin \"Notification access\".",
                    fontSize = 13.sp
                )
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            current = monitored,
            onDismiss = { showPicker = false },
            onSave = { vm.setMonitored(it) }
        )
    }
    if (showParserTest) {
        ParserTestDialog(onDismiss = { showParserTest = false })
    }
}

// ---------- Dialog pilih aplikasi ----------

private data class InstalledApp(val pkg: String, val label: String)

@Composable
private fun AppPickerDialog(
    current: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(current) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            @Suppress("DEPRECATION")
            val list = pm.getInstalledApplications(0)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
                .sortedBy { it.label.lowercase() }
            withContext(Dispatchers.Main) { apps = list }
        }
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.pkg.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih aplikasi yang dipantau") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Cari aplikasi...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.heightIn(max = 380.dp)) {
                    LazyColumn {
                        items(filtered, key = { it.pkg }) { app ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(app.pkg, size = 36.dp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(app.pkg, fontSize = 11.sp)
                                }
                                Checkbox(
                                    checked = selected.contains(app.pkg),
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + app.pkg
                                        else selected - app.pkg
                                    }
                                )
                            }
                        }
                    }
                }
                Text("${selected.size} aplikasi dipilih", fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected); onDismiss() }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// ---------- Dialog uji parser ----------

@Composable
private fun ParserTestDialog(onDismiss: () -> Unit) {
    var title by remember {
        mutableStateOf("Yes, Transaksi QRIS Berhasil!")
    }
    var text by remember {
        mutableStateOf("Transaksi di ALFA_1M4T_JLMAMPANGVI Rp 40.600 berhasil")
    }
    var result by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uji parser notifikasi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Judul notifikasi") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Isi notifikasi") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val p = TxnParser.parse(title, text)
                        result = if (p == null) {
                            "⛔ Bukan notifikasi transaksi (tidak ada nominal) — akan diabaikan."
                        } else {
                            "✓ Nominal: ${formatRp(p.amount)}\n" +
                                "✓ Merchant: ${p.merchant ?: "(tidak terdeteksi)"}\n" +
                                "✓ Jenis: ${p.kind}\n" +
                                "✓ Arah: ${p.direction}\n" +
                                "✓ Keyakinan: ${(p.confidence * 100).toInt()}%"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Jalankan parser") }
                result?.let { Text(it, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}
