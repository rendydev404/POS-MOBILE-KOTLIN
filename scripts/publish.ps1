param(
    [Parameter(Mandatory = $true)][string]$ServiceRoleKey,
    [Parameter(Mandatory = $true)][string]$NewApkPath,
    [Parameter(Mandatory = $true)][string]$PatchFilePath,
    [Parameter(Mandatory = $true)][int]$NewVersionCode,
    [Parameter(Mandatory = $true)][string]$NewVersionName,
    [Parameter(Mandatory = $true)][int]$BaseVersionCode
)

$ErrorActionPreference = 'Stop'

$baseUrl = "https://khpkoreaaucvyqfhynfq.supabase.co"

$headers = @{
    "apikey" = $ServiceRoleKey
    "Authorization" = "Bearer $ServiceRoleKey"
    "Prefer" = "resolution=merge-duplicates"
    "Content-Type" = "application/json"
}

# Upload APK
Write-Host "Uploading APK..."
$apkFileName = Split-Path $NewApkPath -Leaf
$apkUploadUrl = "$baseUrl/storage/v1/object/app-releases/$apkFileName"
$apkBytes = [System.IO.File]::ReadAllBytes($NewApkPath)
$uploadApkHeaders = $headers.Clone()
$uploadApkHeaders["Content-Type"] = "application/vnd.android.package-archive"
$uploadApkHeaders["x-upsert"] = "true"
Invoke-RestMethod -Uri $apkUploadUrl -Method Post -Headers $uploadApkHeaders -Body $apkBytes -UseBasicParsing | Out-Null
Write-Host "APK uploaded."

# Upload Patch
Write-Host "Uploading Patch..."
$patchFileName = Split-Path $PatchFilePath -Leaf
$patchUploadUrl = "$baseUrl/storage/v1/object/app-releases/$patchFileName"
$patchBytes = [System.IO.File]::ReadAllBytes($PatchFilePath)
$uploadPatchHeaders = $headers.Clone()
$uploadPatchHeaders["Content-Type"] = "application/octet-stream"
$uploadPatchHeaders["x-upsert"] = "true"
Invoke-RestMethod -Uri $patchUploadUrl -Method Post -Headers $uploadPatchHeaders -Body $patchBytes -UseBasicParsing | Out-Null
Write-Host "Patch uploaded."

# Get file details for JSON
$apkHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $NewApkPath).Hash
$apkSize = (Get-Item -LiteralPath $NewApkPath).Length
$patchHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $PatchFilePath).Hash
$patchSize = (Get-Item -LiteralPath $PatchFilePath).Length

$apkUrl = "$baseUrl/storage/v1/object/public/app-releases/$apkFileName"
$patchUrl = "$baseUrl/storage/v1/object/public/app-releases/$patchFileName"

# Update global_settings
Write-Host "Updating global_settings in DB..."
$jsonObj = @{
    version_code = $NewVersionCode
    version_name = $NewVersionName
    apk_url = $apkUrl
    apk_sha256 = $apkHash
    apk_size_bytes = $apkSize
    deltas = @(
        @{
            base_version_code = $BaseVersionCode
            patch_url = $patchUrl
            patch_sha256 = $patchHash
            patch_size_bytes = $patchSize
        }
    )
}

$jsonBody = $jsonObj | ConvertTo-Json -Depth 5 -Compress

$dbUpdateObj = @{
    key = "app_update"
    value = $jsonObj
    updated_at = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
}

Invoke-RestMethod -Uri "$baseUrl/rest/v1/global_settings?on_conflict=key" -Method Post -Headers $headers -Body ($dbUpdateObj | ConvertTo-Json -Depth 5 -Compress) -UseBasicParsing | Out-Null

Write-Host "Publish completed successfully!"
