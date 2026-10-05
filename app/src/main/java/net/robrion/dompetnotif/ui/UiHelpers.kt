package net.robrion.dompetnotif.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import net.robrion.dompetnotif.data.TxnEntity
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Rp 40.600 */
fun formatRp(v: Long): String {
    val neg = v < 0
    val s = kotlin.math.abs(v).toString().reversed().chunked(3).joinToString(".").reversed()
    return (if (neg) "-Rp " else "Rp ") + s
}

private val timeFmt = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("id"))
private val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale("id"))

fun formatTime(ts: Long): String =
    Instant.ofEpochMilli(ts).atZone(WIB).format(timeFmt)

fun formatDate(ts: Long): String =
    Instant.ofEpochMilli(ts).atZone(WIB).format(dateFmt)

/** Ikon aplikasi sumber transaksi (diambil dari PackageManager). */
@Composable
fun AppIcon(pkg: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val ctx = LocalContext.current
    val bitmap: Bitmap? = remember(pkg) {
        try {
            ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96)
        } catch (_: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(size).clip(CircleShape)
        )
    } else {
        Box(
            modifier = modifier.size(size).clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = pkg.firstOrNull()?.uppercase() ?: "?",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// ---------- Izin sistem ----------

fun isNotifAccessGranted(ctx: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

fun openNotifAccessSettings(ctx: Context) {
    ctx.startActivity(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun isBatteryIgnored(ctx: Context): Boolean {
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(ctx.packageName)
}

fun requestIgnoreBatteryOptimizations(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${ctx.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        // Beberapa vendor memblokir intent ini; user bisa atur manual.
    }
}

// ---------- Export ----------

private fun exportDir(ctx: Context): File =
    File(ctx.cacheDir, "exports").apply { mkdirs() }

fun exportCsv(ctx: Context, txns: List<TxnEntity>): File {
    val sb = StringBuilder("tanggal,waktu,aplikasi,merchant,kategori,jenis,arah,nominal,status,catatan\n")
    txns.forEach { t ->
        val dt = Instant.ofEpochMilli(t.timestamp).atZone(WIB)
        sb.append(
            listOf(
                dt.toLocalDate().toString(),
                dt.toLocalTime().toString().take(8),
                csv(t.appName), csv(t.merchant ?: ""), csv(t.category),
                t.kind, t.direction,
                t.amount.toString(), t.status, csv(t.note)
            ).joinToString(",")
        ).append('\n')
    }
    return File(exportDir(ctx), "dompetnotif.csv").apply { writeText(sb.toString()) }
}

fun exportJson(ctx: Context, txns: List<TxnEntity>): File {
    val arr = txns.joinToString(",\n") { t ->
        """  {"id":${t.id},"app":${json(t.appName)},"merchant":${json(t.merchant ?: "")},""" +
            """"kind":"${t.kind}","direction":"${t.direction}","amount":${t.amount},""" +
            """"timestamp":${t.timestamp},"status":"${t.status}","synced":${t.synced},""" +
            """"note":${json(t.note)},"title":${json(t.title)},"raw":${json(t.rawText)}}"""
    }
    return File(exportDir(ctx), "dompetnotif.json").apply {
        writeText("[\n$arr\n]")
    }
}

private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
private fun json(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\n", "\\n") + "\""

fun shareFile(ctx: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    ctx.startActivity(
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}
