# MH Tour 3.0 — Hotspot Wi‑Fi Only

Versi ini dirancang untuk komunikasi audio **tanpa internet**. Semua HP Guide/Jemaah harus tersambung ke satu hotspot Wi‑Fi lokal. Server lokal menjalankan LiveKit + token service.

## Arsitektur

`Guide Hotspot/Laptop Server (192.168.43.1) ← Wi‑Fi hotspot → HP Jemaah`

- Token API: `http://192.168.43.1:8080`
- LiveKit lokal: `ws://192.168.43.1:7880`
- Tidak menggunakan LiveKit Cloud.
- Aplikasi menolak operasi ketika koneksi aktif bukan Wi‑Fi saja.
- Data seluler harus dimatikan selama sesi.

## Cara menjalankan server lokal

### Windows + Docker Desktop
1. Nyalakan hotspot Windows dan pastikan IP perangkat server adalah `192.168.43.1`.
2. Sambungkan HP Guide/Jemaah ke hotspot tersebut.
3. Di folder `server`, salin `.env.example` menjadi `.env`.
4. Jalankan:

```bash
docker compose up -d --build
```

5. Tes dari browser HP: `http://192.168.43.1:8080/health`.
6. Respons harus JSON dengan `ok: true`.

Jika hotspot Anda memakai subnet berbeda, ubah `TOKEN_BASE_URL`, `LIVEKIT_URL`, dan `ALLOWED_SUBNET` lalu rebuild APK/server.

## Build APK di GitHub Actions

Project sudah disiapkan untuk GitHub Actions. **Jangan upload ZIP sebagai satu file** karena folder `.github` adalah folder tersembunyi. Gunakan GitHub Desktop atau Git Bash agar `.github/workflows/build-release-signed.yml` ikut ter-upload. Lihat `GITHUB_UPLOAD_INSTRUCTIONS.md`.

Setelah project masuk GitHub: **Actions → Build MH Tour Android → Run workflow**.

- Debug: `MH-Tour-Debug-APK` — untuk pengujian instalasi.
- Release: `MH-Tour-Hotspot-WiFi-Only-Release` — untuk verifikasi build release.
- Release signed: isi 4 GitHub Secrets sesuai `BUILD_RELEASE_SIGNED.md`.

Untuk build lokal, buka folder `android` dengan Android Studio atau jalankan Gradle 8.11.1 dari folder `android`.

## Alur pemakaian

1. Server laptop/mini-PC terhubung ke hotspot.
2. Guide dan Jemaah terhubung ke hotspot yang sama.
3. Guide buka aplikasi → **Mulai sebagai Guide** → **Buat Sesi Rombongan**.
4. Guide bagikan kode/QR secara lokal.
5. Jemaah scan QR atau masukkan kode.
6. Guide tekan **Mulai Bicara**.

## Catatan jaringan

- `192.168.43.1` adalah default hotspot Android/Windows yang digunakan paket ini; jika perangkat hotspot memakai alamat lain, konfigurasi harus disesuaikan.
- Firewall perangkat server harus mengizinkan TCP 8080, TCP 7880/7881, dan UDP 50000–50020 pada jaringan private/hotspot.
- Untuk benar-benar offline, jangan aktifkan VPN atau proxy yang mengalihkan trafik keluar.

## GitHub Actions build

Use `.github/workflows/build-release-signed.yml`. It is a manual `workflow_dispatch` build and does not require a committed keystore or GitHub Secrets for the test release. Download `MH-Tour-APK` from the completed Actions run.


Build CI fix: Android BuildConfig is explicitly enabled and the GitHub Actions workflow uses a valid workflow_dispatch trigger.
