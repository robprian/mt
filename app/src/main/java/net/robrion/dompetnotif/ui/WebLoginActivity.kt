package net.robrion.dompetnotif.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.robrion.dompetnotif.DompetNotifApp
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * WebView untuk menghubungkan DompetKu.
 *
 * Masalah: endpoint /api/login di server dilindungi Cloudflare Turnstile,
 * jadi aplikasi tidak bisa login otomatis dengan username+password.
 * Solusi: user login manual di WebView ini (Turnstile tetap jalan normal),
 * lalu aplikasi membaca token sesi dari sessionStorage (kunci "mt2tok"
 * yang dipakai web DompetKu) dan menyimpannya untuk sinkronisasi.
 *
 * Token TIDAK pernah ditampilkan atau dikirim ke mana pun selain
 * server DompetKu milik user sendiri.
 */
class WebLoginActivity : ComponentActivity() {

    private var webViewRef: WebView? = null
    private var done by mutableStateOf(false)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DompetNotifTheme {
                Column(Modifier.fillMaxSize()) {
                    if (!done) {
                        LinearProgressIndicator(Modifier.padding(16.dp))
                        Text(
                            "Login ke DompetKu di bawah. Setelah berhasil, token " +
                                "akan diambil otomatis.",
                            Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = WebViewClient()
                                val prefs = (application as DompetNotifApp).prefs
                                lifecycleScope.launch {
                                    val base = prefs.snapshot().baseUrl
                                    if (base.isBlank()) {
                                        Toast.makeText(
                                            this@WebLoginActivity,
                                            "Isi URL server dulu di Pengaturan.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        finish()
                                    } else {
                                        loadUrl(base)
                                    }
                                }
                                lifecycleScope.launch(Dispatchers.IO) {
                                    pollForToken()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    /** Poll sessionStorage sampai token muncul (user selesai login). */
    private suspend fun pollForToken() {
        val app = application as DompetNotifApp
        repeat(120) { // maks ~4 menit
            delay(2000)
            if (done) return
            var token: String? = null
            val latch = CountDownLatch(1)
            runOnUiThread {
                webViewRef?.evaluateJavascript("sessionStorage.getItem('mt2tok')") { value ->
                    token = value?.takeIf { it != "null" }?.trim('"')
                    latch.countDown()
                } ?: latch.countDown()
            }
            latch.await(5, TimeUnit.SECONDS)
            if (!token.isNullOrEmpty()) {
                app.prefs.setApiToken(token!!)
                withContext(Dispatchers.Main) {
                    done = true
                    Toast.makeText(this@WebLoginActivity, "DompetKu terhubung ✓", Toast.LENGTH_LONG).show()
                    finish()
                }
                return
            }
        }
    }
}
