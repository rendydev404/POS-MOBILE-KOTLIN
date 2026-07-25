# Script Automatisasi Build & Deploy POS Kasir Suka Shawarma ke HP Fisik (USB Debugging)

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "D:\ANDROID-SDK"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Suka Shawarma POS Kasir - USB Testing & Build Runner " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Cek HP Fisik Terhubung
Write-Host "`n[1/3] Memeriksa Perangkat HP Fisik via ADB..." -ForegroundColor Yellow
$devices = & "$env:ANDROID_HOME\platform-tools\adb.exe" devices

if ($devices -match "device\b") {
    Write-Host "[OK] Perangkat HP Fisik Terdeteksi & Terhubung (USB Debugging Ready)!" -ForegroundColor Green
} else {
    Write-Host "[ERROR] Perangkat HP belum terdeteksi." -ForegroundColor Red
    Write-Host "Pastikan kabel USB terpasang dan opsi 'Izinkan Debugging USB' sudah disetujui di HP Anda." -ForegroundColor Red
    exit
}

# 2. Build & Install APK ke HP Fisik
Write-Host "`n[2/3] Membangun (Build) & Menginstall APK ke HP Fisik..." -ForegroundColor Yellow
.\gradlew.bat installDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n[3/3] Menjalankan Aplikasi di HP Fisik..." -ForegroundColor Green
    & "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n "com.sukashawarma.pos/com.sukashawarma.pos.presentation.MainActivity"
    Write-Host "`n[SUKSES] Aplikasi Suka Shawarma POS Kasir berhasil berjalan di HP Fisik Anda!" -ForegroundColor Green
} else {
    Write-Host "`n[FAIL] Terjadi kendala saat build Gradle." -ForegroundColor Red
}
