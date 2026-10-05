#!/bin/bash
#
# Upload DompetNotif ke GitHub untuk build APK via Actions.
#   Repo  : https://github.com/robprian/mt
#   Hasil : tiap push ke main -> Actions -> artifact "dompetnotif-debug" (APK)
#
# Cara pakai:
#   1. Buat repo KOSONG di https://github.com/new  (nama: mt)
#   2. Jalankan:  bash push-to-github.sh
#   3. Login GitHub saat diminta (browser / token).
#
set -euo pipefail

REPO="robprian/mt"
cd "$(dirname "$0")"

echo "== DompetNotif -> github.com/$REPO =="
echo

# 1. Pastikan repo sudah dibuat
CODE=$(curl -sSL -o /dev/null -w "%{http_code}" "https://github.com/$REPO" || true)
if [ "$CODE" != "200" ]; then
  echo "❌ Repo https://github.com/$REPO belum ada (HTTP $CODE)."
  echo
  echo "   Buat dulu:"
  echo "     1. Buka https://github.com/new"
  echo "     2. Repository name : mt.gthit"
  echo "     3. Public/Private  : terserah"
  echo "     4. JANGAN centang 'Add a README' (repo harus kosong)"
  echo "     5. Create repository, lalu jalankan script ini lagi."
  exit 1
fi
echo "✓ Repo ditemukan."

# 2. Pastikan file penting ada
for f in gradlew "gradle/wrapper/gradle-wrapper.jar" "app/build.gradle.kts" \
         "app/src/main/AndroidManifest.xml" ".github/workflows/build-apk.yml"; do
  [ -e "$f" ] || { echo "❌ File wajib hilang: $f"; exit 1; }
done
echo "✓ File proyek lengkap."

# 3. Git init + commit
if [ ! -d .git ]; then
  git init -b main -q
  echo "✓ git init (branch main)."
fi
git add -A
if git diff --cached --quiet; then
  echo "• Tidak ada perubahan baru untuk di-commit."
else
  git commit -q -m "DompetNotif v1.1 — smart personal money tracker (auto-capture notifikasi + smart kategori)"
  echo "✓ Perubahan di-commit."
fi

# 4. Remote + push
git remote remove origin 2>/dev/null || true
git remote add origin "https://github.com/$REPO.git"
echo "→ Push ke origin/main ..."
git push -u origin main

echo
echo "✅ Selesai!"
echo "   Pantau build: https://github.com/$REPO/actions"
echo "   APK jadi    : Actions -> workflow 'Build APK' -> artifact 'dompetnotif-debug'"
