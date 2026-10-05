# DompetNotif — Smart Personal Money Tracker

**Pencatat keuangan otomatis yang membaca notifikasi pembayaran HP-mu.**
Bayar QRIS di Alfamart, transfer via m-banking, top-up e-wallet — semuanya
tercatat sendiri lengkap dengan kategori. Tanpa buka aplikasi. Tanpa catat manual.

> *Yes, Transaksi QRIS Berhasil! — Transaksi di ALFA_1M4T_JLMAMPANGVI Rp 40.600*
> → 1 detik kemudian sudah tercatat: **Rp 40.600 • Belanja • QRIS** ✓

## Kenapa "smart"?

| Fitur | Cara kerja |
|---|---|
| 🧠 **Auto-capture** | `NotificationListenerService` menangkap notifikasi m-banking/e-wallet yang kamu pilih, real-time |
| 🏷️ **Smart kategori** | `SmartCategorizer`: aturan keyword Indonesia (Alfamart→Belanja, Kopi Kenangan→Makanan & Minum, …) + fallback jenis transaksi |
| 🎓 **Belajar dari koreksi** | Setiap koreksi kategori yang kamu lakukan **diingat per merchant** — makin dipakai, makin akurat |
| 🎯 **Skor keyakinan** | Tiap hasil parsing punya skor 0–100%; ≥95% otomatis terkonfirmasi, sisanya antre review 1 ketukan |
| 🔁 **Anti-duplikat** | Satu transaksi yang memicu 2 notifikasi (mis. notif email + notif sukses) hanya dicatat sekali |
| 📊 **Insight instan** | Pengeluaran hari ini, antrean review, pengeluaran per kategori |

## Untuk siapa?

- Pekerja/karyawan yang mau tahu uangnya lari ke mana **tanpa effort**
- Freelancer yang butuh bukti pengeluaran rapi (export CSV/JSON)
- Siapa pun yang pakai banyak e-wallet & m-banking dan malas rekap manual

**Privasi = nilai jual utama:** 100% lokal-first. Tidak ada akun wajib, tidak ada
server pihak ketiga, tidak ada analytics, tidak ada iklan. Data tidak pernah
keluar dari HP kecuali kamu sendiri yang mengaktifkan sinkronisasi ke server
pribadimu.

## Fitur lengkap

- 📲 Tangkap notifikasi pembayaran real-time (Android 8+)
- 🧠 Parser Bahasa Indonesia generik (tidak bergantung nama paket bank tertentu)
- ✅ Review manual: edit merchant/nominal/jenis/kategori/catatan
- 📊 Dasbor: pengeluaran hari ini, menunggu review, per kategori
- 🔗 Sinkronisasi opsional ke server pribadi (kompatibel API DompetKu: `GET/PUT /api/state`)
- 📤 Export CSV / JSON + share
- 🧪 Alat uji parser bawaan (di aplikasi maupun `tools/test_parser.py`)
- 🔋 Panduan pengecualian baterai + autostart (Xiaomi/Oppo/Vivo)

## Cara build (GitHub Actions — tanpa Android Studio)

```bash
# 1. Buat repo KOSONG di https://github.com/new  (mis. robprian/mt.gthit)
# 2. Jalankan script ini:
bash push-to-github.sh
# 3. Buka tab Actions -> workflow "Build APK" -> download artifact
#    "dompetnotif-debug" -> install di HP
```

Atau manual:

```bash
git init -b main && git add -A && git commit -m "DompetNotif v1.1" \
  && git remote add origin git@github.com:USER/REPO.git && git push -u origin main
```

Workflow `.github/workflows/build-apk.yml`: JDK 17 + Gradle wrapper (sudah
termasuk di repo), `assembleDebug` tiap push ke `main`/`master`.

**Android Studio (alternatif):** Open folder ini → tunggu Gradle sync → Run.

Stack: Kotlin 1.9.24, AGP 8.5.2, Compose Material3, Room 2.6.1, DataStore,
minSdk 26 / targetSdk 34.

## Cara pakai (3 langkah)

1. **Izin**: buka aplikasi → *Buka Pengaturan Izin* → aktifkan DompetNotif di
   *Notification access*.
2. **Pilih aplikasi**: Pengaturan → *Pilih aplikasi* → centang m-banking /
   e-wallet yang dipakai.
3. **Bayar seperti biasa.** Transaksi tercatat otomatis.

Review: tab *Transaksi* → filter *Menunggu* → koreksi → *Konfirmasi* / *Abaikan*.
Koreksi kategori **diingat** untuk merchant tersebut selamanya.

## Sinkronisasi server (opsional)

Punya server money tracker sendiri (kompatibel API DompetKu)?

1. Pengaturan → isi **URL server** → *Simpan URL*
2. *Hubungkan* → login di WebView dalam aplikasi → token sesi diambil otomatis
3. Aktifkan *Sync otomatis*, atau kirim per transaksi via tombol *Sync ke server*

Catatan: login server yang dilindungi Turnstile/CAPTCHA tidak bisa otomatis —
itu sebabnya dipakai WebView. Token hanya dipakai ke server milikmu.

## Cara kerja parser

`app/src/main/java/net/robrion/dompetnotif/parser/TxnParser.kt`

| Langkah | Aturan |
|---|---|
| Nominal | Regex `Rp\s?[\d.,]+`; bila beberapa angka (mis. sisa saldo), pilih konteks paling transaksional, penalti konteks `saldo`/`sisa` |
| Merchant | Pola `di MERCHANT` / `ke MERCHANT` / `kepada MERCHANT` |
| Jenis | Keyword: `qris`, `transfer`, `top up`, `tarik tunai`, `biaya admin` |
| Arah | `diterima`/`dana masuk` → MASUK; `berhasil`/`dipotong` → KELUAR |
| Keyakinan | 50 + 20 (nominal) + 15 (jenis) + 15 (merchant) + 5 (arah) |

Uji: `python3 tools/test_parser.py` → **22/22 lulus** (12 kasus notifikasi +
10 kasus kategori).

## Batasan jujur

- **Hanya Android.** iOS tidak mengizinkan aplikasi membaca notifikasi aplikasi
  lain — tidak ada cara resmi.
- **Hanya sebagus notifikasinya.** Tanpa nominal di notifikasi = tidak tertangkap.
- **Format bank bisa berubah** — parser generik + alat uji bawaan; pola baru
  tinggal tambah di `TxnParser.kt` / `SmartCategorizer.kt`.
- **Bukan akses rekening.** Tidak bisa lihat mutasi/saldo real atau transaksi —
  hanya teks notifikasi.

## Struktur proyek

```
dompet-notif-android/
├── push-to-github.sh            # upload ke GitHub (satu perintah)
├── .github/workflows/build-apk.yml
├── gradlew / gradle/wrapper/    # Gradle wrapper (build tanpa install)
├── preview.html                 # mockup UI interaktif (buka di browser)
├── README.md
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── res/mipmap-*/ic_launcher.png   # ikon aplikasi
│   └── java/net/robrion/dompetnotif/
│       ├── DompetNotifApp.kt
│       ├── data/ (Room entity/DAO, Prefs DataStore + memori kategori)
│       ├── parser/ (TxnParser.kt, SmartCategorizer.kt)  ★
│       ├── service/TxnListenerService.kt                 ★
│       ├── sync/DompetSync.kt
│       └── ui/ (Dashboard, Transaksi, Pengaturan, WebLogin)
└── tools/test_parser.py         # 22/22 PASS
```

## Roadmap

- Notifikasi harian: rekap pengeluaran otomatis jam 21:00
- Budget per kategori + peringatan mendekati limit
- Widget home-screen: pengeluaran hari ini
- Backup terenkripsi ke file / cloud pribadi
- Mode multi-bahasa (EN) untuk pasar lebih luas
