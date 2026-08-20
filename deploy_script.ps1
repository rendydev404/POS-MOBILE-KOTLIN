$ErrorActionPreference = 'Stop'
$ServiceKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtocGtvcmVhYXVjdnlxZmh5bmZxIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4MDk2MzI5MiwiZXhwIjoyMDk2NTM5MjkyfQ.Dy0QMAHfB8EU9BK-JuyRrBidpG6iM94t9RtiJ_viZz8"

Copy-Item "app\build\outputs\apk\release\app-release.apk" "release\Suka-Shawarma-POS-v1.0.69.apk" -Force
Write-Host "Creating delta patch..."
.\scripts\create-archive-patch.ps1 -OldApk "release\Suka-Shawarma-POS-v1.0.68.apk" -NewApk "release\Suka-Shawarma-POS-v1.0.69.apk" -PatchFile "release\Suka-Shawarma-POS-v1.0.68-to-v1.0.69.fbf" -VerifyOutput "release\Suka-Shawarma-POS-v1.0.69-archive-reconstructed.apk"

Write-Host "Publishing update..."
.\scripts\publish.ps1 -ServiceRoleKey $ServiceKey -NewApkPath "release\Suka-Shawarma-POS-v1.0.69.apk" -PatchFilePath "release\Suka-Shawarma-POS-v1.0.68-to-v1.0.69.fbf" -NewVersionCode 70 -NewVersionName "1.0.69" -BaseVersionCode 69
