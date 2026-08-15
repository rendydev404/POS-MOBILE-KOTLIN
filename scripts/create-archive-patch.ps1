param(
    [Parameter(Mandatory = $true)][string]$OldApk,
    [Parameter(Mandatory = $true)][string]$NewApk,
    [Parameter(Mandatory = $true)][string]$PatchFile,
    [string]$VerifyOutput
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$cacheRoot = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\com.eidu\archive-patcher\3.0.0'
$library = Get-ChildItem -LiteralPath $cacheRoot -Recurse -Filter 'archive-patcher-3.0.0.jar' |
    Select-Object -First 1
if ($null -eq $library) {
    throw 'archive-patcher 3.0.0 belum ada di Gradle cache; jalankan build app terlebih dahulu.'
}

$classes = Join-Path $projectRoot 'build\archive-patch-cli'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& javac -cp $library.FullName -d $classes (Join-Path $PSScriptRoot 'ArchivePatchCli.java')
if ($LASTEXITCODE -ne 0) { throw 'Gagal compile ArchivePatchCli' }

$classpath = "$classes;$($library.FullName)"
& java -cp $classpath ArchivePatchCli generate $OldApk $NewApk $PatchFile
if ($LASTEXITCODE -ne 0) { throw 'Gagal membuat archive patch' }

if (-not [string]::IsNullOrWhiteSpace($VerifyOutput)) {
    & java -cp $classpath ArchivePatchCli apply $OldApk $PatchFile $VerifyOutput
    if ($LASTEXITCODE -ne 0) { throw 'Gagal menerapkan archive patch' }
    $expected = (Get-FileHash -Algorithm SHA256 -LiteralPath $NewApk).Hash
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $VerifyOutput).Hash
    if ($expected -ne $actual) { throw 'APK hasil patch tidak identik dengan target signed' }
    Write-Output "RECONSTRUCTED_MATCH=True"
}

Get-Item -LiteralPath $PatchFile | Select-Object FullName, Length
Get-FileHash -Algorithm SHA256 -LiteralPath $PatchFile | Select-Object Algorithm, Hash
