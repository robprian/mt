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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.robrion.dompetnotif.data.TxnEntity
import net.robrion.dompetnotif.data.TxnStatus
import net.robrion.dompetnotif.parser.Direction
import net.robrion.dompetnotif.parser.TxnKind

private enum class Filter(val label: String, val status: String?) {
    ALL("Semua", null),
    PENDING("Menunggu", TxnStatus.PENDING),
    CONFIRMED("Dikonfirmasi", TxnStatus.CONFIRMED),
    IGNORED("Diabaikan", TxnStatus.IGNORED),
}

@Composable
fun TxnListScreen(vm: AppViewModel) {
    val txns by vm.txns.collectAsState()
    var filter by remember { mutableStateOf(Filter.ALL) }
    var editing by remember { mutableStateOf<TxnEntity?>(null) }

    val shown = remember(txns, filter) {
        if (filter.status == null) txns else txns.filter { it.status == filter.status }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Transaksi", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filter.values().forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(f.label, fontSize = 12.sp) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (shown.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "Tidak ada transaksi pada filter ini.",
                    Modifier.padding(24.dp),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { t ->
                    TxnRow(t, onClick = { editing = t })
                }
            }
        }
    }

    editing?.let { t ->
        TxnEditDialog(
            txn = t,
            vm = vm,
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun TxnEditDialog(txn: TxnEntity, vm: AppViewModel, onDismiss: () -> Unit) {
    var merchant by remember(txn.id) { mutableStateOf(txn.merchant ?: "") }
    var amountStr by remember(txn.id) { mutableStateOf(txn.amount.toString()) }
    var kind by remember(txn.id) { mutableStateOf(txn.kind) }
    var direction by remember(txn.id) { mutableStateOf(txn.direction) }
    var note by remember(txn.id) { mutableStateOf(txn.note) }
    var category by remember(txn.id) { mutableStateOf(txn.category) }
    var syncMsg by remember(txn.id) { mutableStateOf("") }
    var syncing by remember(txn.id) { mutableStateOf(false) }
    val originalCategory = remember(txn.id) { txn.category }

    fun buildEdited(status: String = txn.status) = txn.copy(
        merchant = merchant.ifBlank { null },
        amount = amountStr.toLongOrNull() ?: txn.amount,
        kind = kind,
        direction = direction,
        note = note,
        category = category.ifBlank { "Lainnya" },
        status = status,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detail transaksi", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    AppIcon(txn.packageName, size = 36.dp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(txn.appName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            formatDate(txn.timestamp),
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "Keyakinan parser: ${(txn.confidence * 100).toInt()}% • Status: ${txn.status}" +
                        (if (txn.synced) " • sudah sync" else ""),
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant / penerima") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.filter { c -> c.isDigit() } },
                    label = { Text("Nominal (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Jenis", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                ChipRow(
                    options = listOf(
                        TxnKind.QRIS, TxnKind.TRANSFER, TxnKind.TOPUP,
                        TxnKind.TARIK_TUNAI, TxnKind.BIAYA_ADMIN, TxnKind.LAINNYA
                    ),
                    selected = kind,
                    onSelect = { kind = it }
                )
                Text("Arah", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                ChipRow(
                    options = listOf(Direction.KELUAR, Direction.MASUK, Direction.UNKNOWN),
                    selected = direction,
                    onSelect = { direction = it }
                )
                Text("Kategori (smart — koreksimu diingat aplikasi)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Kategori") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ChipRow(
                    options = net.robrion.dompetnotif.parser.SmartCategorizer.DEFAULT_CATEGORIES,
                    selected = category,
                    onSelect = { category = it }
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Catatan") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Notifikasi asli: \"${txn.title}\"",
                    fontSize = 11.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (syncMsg.isNotEmpty()) {
                    Text(syncMsg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Column {
                Row {
                    TextButton(onClick = {
                        vm.saveEditedWithLearning(buildEdited(), originalCategory)
                        onDismiss()
                    }) { Text("Simpan") }
                    TextButton(onClick = {
                        vm.saveEditedWithLearning(
                            buildEdited(TxnStatus.CONFIRMED), originalCategory
                        )
                        onDismiss()
                    }) { Text("Konfirmasi") }
                }
                Row {
                    TextButton(onClick = { vm.ignore(txn.id); onDismiss() }) {
                        Text("Abaikan")
                    }
                    TextButton(onClick = { vm.delete(txn.id); onDismiss() }) {
                        Text("Hapus", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                    }
                }
                Row {
                    Button(
                        onClick = {
                            syncing = true
                            syncMsg = "Mengirim..."
                            vm.syncOne(txn.id) { msg ->
                                syncing = false
                                syncMsg = msg
                            }
                        },
                        enabled = !syncing,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sync ke server") }
                }
                Row {
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Tutup")
                    }
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { opt ->
                    FilterChip(
                        selected = selected == opt,
                        onClick = { onSelect(opt) },
                        label = { Text(opt, fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}
