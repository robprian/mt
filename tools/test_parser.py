#!/usr/bin/env python3
"""Mirror 1:1 dari TxnParser.kt — untuk verifikasi logika parsing.

Menjalankan seluruh kasus uji notifikasi bank/e-wallet Indonesia dan
memastikan hasil ekstraksi (nominal, merchant, jenis, arah) benar.
"""
import re
import sys
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP

MERCHANT_END = (r"(?=\s+(?:[Rr][Pp]\b|sebesar\b|senilai\b|berhasil\b|sukses\b"
                 r"|telah\b|pada\b|dengan\b|sejumlah\b|ke\b|dari\b)|\s*$)")
AMOUNT_RE = re.compile(r"Rp\.?\s*([\d.]+(?:,\d{1,2})?)", re.IGNORECASE)
MERCHANT_DI_RE = re.compile(r"\b[Dd]i\s+([A-Za-z0-9][A-Za-z0-9_\-. ,]{1,42}?)" + MERCHANT_END)
MERCHANT_KE_RE = re.compile(
    r"\b[Kk]epada\s+([A-Za-z0-9][A-Za-z0-9_\-. ,]{1,42}?)" + MERCHANT_END
    + r"|\b[Kk]e\s+([A-Za-z0-9][A-Za-z0-9_\-. ,]{1,42}?)" + MERCHANT_END
)
STOP_WORDS = {"rp", "sebesar", "senilai", "berhasil", "sukses", "telah", "pada",
              "tanggal", "pukul", "dengan", "nomor", "sejumlah"}


def parse_rp(raw: str) -> int:
    # Samakan dengan Kotlin Math.round(): half-up, bukan banker's rounding.
    try:
        return int(Decimal(raw.replace(".", "").replace(",", "."))
                   .to_integral_value(rounding=ROUND_HALF_UP))
    except (InvalidOperation, ValueError):
        return 0


def extract_amount(text: str):
    cands = []
    for m in AMOUNT_RE.finditer(text):
        value = parse_rp(m.group(1))
        if value <= 0:
            continue
        before = text[max(0, m.start() - 40):m.start()].lower()
        score = 0
        if any(w in before for w in ("sebesar", "senilai", "nominal", "total")):
            score += 3
        if any(w in before for w in ("transaksi", "bayar", "transfer", "top up", "topup", "tarik")):
            score += 2
        if "saldo" in before or "sisa" in before:
            score -= 4
        cands.append((value, score, m.start()))
    if not cands:
        return None
    cands.sort(key=lambda c: (-c[1], c[2]))
    return cands[0][0]


def detect_kind(text: str) -> str:
    l = text.lower()
    if "qris" in l:
        return "QRIS"
    if "tarik tunai" in l or "penarikan tunai" in l:
        return "TARIK_TUNAI"
    if "top up" in l or "topup" in l or "isi saldo" in l:
        return "TOPUP"
    if "biaya admin" in l or "admin fee" in l:
        return "BIAYA_ADMIN"
    if "transfer" in l:
        return "TRANSFER"
    return "LAINNYA"


def detect_direction(text: str) -> str:
    l = text.lower()
    if "diterima" in l:
        return "MASUK"
    if "dana masuk" in l or "saldo masuk" in l or "uang masuk" in l:
        return "MASUK"
    if "dari " in l and "transfer" in l:
        return "MASUK"
    if "penarikan" in l or "tarik tunai" in l:
        return "KELUAR"
    if "dipotong" in l or "terpotong" in l or "dibebankan" in l:
        return "KELUAR"
    if "berhasil" in l or "sukses" in l:
        return "KELUAR"
    return "UNKNOWN"


def clean_merchant(raw: str):
    if len(raw) < 2:
        return None
    words = re.split(r"\s+", raw)
    kept = []
    for w in words:
        if w.lower().rstrip(".,") in STOP_WORDS:
            break
        kept.append(w)
    name = " ".join(kept).strip().rstrip(".,-/")
    name = re.split(r"\s+[Rr][Pp]\b", name)[0].strip()
    if len(name) < 2:
        return None
    if len(name) > 42:
        name = name[:42].strip()
    return name or None


def extract_merchant(title: str, text: str):
    combined = f"{title}\n{text}"
    m = MERCHANT_DI_RE.search(combined)
    if m:
        return clean_merchant(m.group(1).strip())
    m = MERCHANT_KE_RE.search(combined)
    if m:
        raw = m.group(1) or m.group(2) or ""
        return clean_merchant(raw.strip())
    return None


def parse(title: str, text: str):
    combined = f"{title}\n{text}"
    amount = extract_amount(combined)
    if amount is None:
        return None
    kind = detect_kind(combined)
    direction = detect_direction(combined)
    merchant = extract_merchant(title, text)
    conf = 0.5 + 0.20
    if kind != "LAINNYA":
        conf += 0.15
    if merchant is not None:
        conf += 0.15
    if direction != "UNKNOWN":
        conf += 0.05
    return {"amount": amount, "merchant": merchant, "kind": kind,
            "direction": direction, "confidence": min(conf, 1.0)}


CASES = [
    # (nama, title, text, expected)
    ("blu QRIS (screenshot user)",
     "Yes, Transaksi QRIS Berhasil!",
     "Transaksi di ALFA_1M4T_JLMAMPANGVI Rp 40.600 berhasil",
     {"amount": 40600, "merchant": "ALFA_1M4T_JLMAMPANGVI",
      "kind": "QRIS", "direction": "KELUAR"}),
    ("blu email duplikat (screenshot user) -> diabaikan",
     "Transaksimu Pakai blu Berhasil!",
     "Hai Robby, Terima kasih sudah menggunakan blu untuk tr...",
     None),
    ("transfer BCA",
     "Transfer Berhasil",
     "Transfer ke BCA 1234567890 sebesar Rp 1.500.000 berhasil",
     {"amount": 1500000, "merchant": "BCA 1234567890",
      "kind": "TRANSFER", "direction": "KELUAR"}),
    ("DANA masuk",
     "DANA",
     "Dana masuk Rp 250.000 dari Budi Santoso",
     {"amount": 250000, "merchant": None,
      "kind": "LAINNYA", "direction": "MASUK"}),
    ("jebakan sisa saldo",
     "Info Transaksi",
     "Transaksi Rp 50.000 berhasil. Sisa saldo Rp 2.345.678",
     {"amount": 50000, "merchant": None,
      "kind": "LAINNYA", "direction": "KELUAR"}),
    ("top up GoPay",
     "GoPay",
     "Top up GoPay Rp 100.000 berhasil",
     {"amount": 100000, "merchant": None,
      "kind": "TOPUP", "direction": "KELUAR"}),
    ("biaya admin",
     "Info Transaksi",
     "Biaya admin Rp 6.500 telah dipotong dari saldo Anda",
     {"amount": 6500, "merchant": None,
      "kind": "BIAYA_ADMIN", "direction": "KELUAR"}),
    ("tarik tunai",
     "BRImo",
     "Tarik tunai Rp 500.000 di ATM BRI GAMBIR berhasil",
     {"amount": 500000, "merchant": "ATM BRI GAMBIR",
      "kind": "TARIK_TUNAI", "direction": "KELUAR"}),
    ("transfer masuk",
     "Livin'",
     "Transfer dari SINTA Rp 1.000.000 telah diterima",
     {"amount": 1000000, "merchant": None,
      "kind": "TRANSFER", "direction": "MASUK"}),
    ("nominal desimal koma",
     "blu",
     "Pembayaran QRIS di KOPI KENANGAN Rp 58.000,50 berhasil",
     {"amount": 58001, "merchant": "KOPI KENANGAN",
      "kind": "QRIS", "direction": "KELUAR"}),
    ("bukan transaksi",
     "blu",
     "Promo spesial: cashback 20% untuk pengguna baru!",
     None),
    ("merchant mengandung awalan ke/dari",
     "QRIS",
     "Bayar di Toko Kedaung Rp 20.000 berhasil",
     {"amount": 20000, "merchant": "Toko Kedaung",
      "kind": "QRIS", "direction": "KELUAR"}),
]


# ---------------- SmartCategorizer (mirror dari SmartCategorizer.kt) ----------------

CAT_RULES = [
    (re.compile(r"alfamart|indomaret|superindo|hypermart|transmart|lottemart|giant|carrefour|yogya|borma"), "Belanja"),
    (re.compile(r"kopi|starbucks|janji jiwa|kenangan|mcd|kfc|pizza|resto|rumah makan|warteg|dapur|cafe|kafe|bakso|soto|ayam|martabak|gofood|grabfood|shopeefood|kantin|warkop"), "Makanan & Minum"),
    (re.compile(r"gojek|grab|ojek|kai|tiket\.com|traveloka|tiket|travel|bensin|shell|pertamina|parkir|tol |tol_"), "Transportasi"),
    (re.compile(r"pln|token listrik|indihome|telkom|pdam|bpjs|pajak|pbb"), "Tagihan"),
    (re.compile(r"apotek|klinik|rumah sakit|\brs\b|dokter|halodoc|alodokter|puskesmas|lab klinik"), "Kesehatan"),
    (re.compile(r"xxi|cgv|netflix|spotify|disney|steam|playstation|game|karaoke"), "Hiburan"),
    (re.compile(r"gopay|dana|ovo|shopeepay|linkaja|isaku"), "Top Up"),
]

CAT_FALLBACK = {"QRIS": "Belanja", "TRANSFER": "Transfer", "TOPUP": "Top Up",
                "TARIK_TUNAI": "Tarik Tunai", "BIAYA_ADMIN": "Biaya Bank"}


def categorize(merchant, app, kind, direction, learned: dict) -> str:
    if direction == "MASUK":
        return "Pemasukan"
    key = (merchant or app).strip().lower()
    if key in learned:
        return learned[key]
    hay = f"{key} {app.lower()}"
    for rx, cat in CAT_RULES:
        if rx.search(hay):
            return cat
    return CAT_FALLBACK.get(kind, "Lainnya")


def main() -> int:
    fails = 0
    for name, title, text, expected in CASES:
        got = parse(title, text)
        if expected is None:
            ok = got is None
        else:
            ok = (got is not None
                  and got["amount"] == expected["amount"]
                  and got["merchant"] == expected["merchant"]
                  and got["kind"] == expected["kind"]
                  and got["direction"] == expected["direction"])
        status = "PASS" if ok else "FAIL"
        print(f"[{status}] {name}")
        if not ok:
            fails += 1
            print(f"   expected: {expected}")
            print(f"   got     : {got}")
        elif got is not None:
            print(f"   -> Rp{got['amount']:,} | {got['merchant']} | "
                  f"{got['kind']} | {got['direction']} | conf={got['confidence']:.2f}")

    # ---- Uji SmartCategorizer (mirror dari SmartCategorizer.kt) ----
    print("\n-- SmartCategorizer --")
    cat_cases = [
        # (merchant, app, kind, direction, learned, expected)
        ("ALFA_1M4T_JLMAMPANGVI", "blu", "QRIS", "KELUAR", {}, "Belanja"),
        ("KOPI KENANGAN", "blu", "QRIS", "KELUAR", {}, "Makanan & Minum"),
        ("BCA 1234567890", "BCA mobile", "TRANSFER", "KELUAR", {}, "Transfer"),
        (None, "GoPay", "TOPUP", "KELUAR", {}, "Top Up"),
        (None, "BRImo", "BIAYA_ADMIN", "KELUAR", {}, "Biaya Bank"),
        ("Dana masuk", "DANA", "LAINNYA", "MASUK", {}, "Pemasukan"),
        # memori belajar menang atas aturan keyword:
        ("TOKO SAHABAT", "blu", "QRIS", "KELUAR",
         {"toko sahabat": "Hiburan"}, "Hiburan"),
        ("WARTEG BAROKAH", "blu", "QRIS", "KELUAR", {}, "Makanan & Minum"),
        ("SHELL", "blu", "QRIS", "KELUAR", {}, "Transportasi"),
        ("PLN TOKEN", "Tokopedia", "LAINNYA", "KELUAR", {}, "Tagihan"),
    ]
    for merchant, app, kind, direction, learned, expected in cat_cases:
        got = categorize(merchant, app, kind, direction, learned)
        ok = got == expected
        print(f"[{'PASS' if ok else 'FAIL'}] {merchant or app} -> {got}")
        if not ok:
            fails += 1
            print(f"   expected: {expected}")

    total = len(CASES) + len(cat_cases)
    print(f"\n{total-fails}/{total} lulus")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
