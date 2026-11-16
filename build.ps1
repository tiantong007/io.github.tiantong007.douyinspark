# build.ps1 — 一键：编译 → zipalign → 签名 → 输出到 release\
# 用法: .\build.ps1            (arm64 + armeabi 都签)
#       .\build.ps1 -Arm64Only (只签 arm64)
param([switch]$Arm64Only)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:JAVA_HOME     = "D:\czj\xxrj\jdk21"
$env:ANDROID_HOME  = "D:\czj\xxrj\android-sdk"
$bt  = "D:\czj\xxrj\android-sdk\build-tools\36.0.0"
$ks  = "$root\keystore\tiantong-release.keystore"
$alias = "tiantong"
$store = "cc123456"     # keystore 口令
$key   = "cc123456"     # key 口令

if (-not (Test-Path $ks)) { throw "找不到 keystore: $ks" }

Write-Host "[1/4] 编译..." -ForegroundColor Cyan
Push-Location "$root\src"
& .\gradlew.bat :app:assembleRelease --console=plain --no-daemon
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "编译失败" }
Pop-Location

$outDir = "$root\src\app\build\outputs\apk\release"
$abis = @("arm64-v8a")
if (-not $Arm64Only) { $abis += "armeabi-v7a" }

foreach ($abi in $abis) {
    $unsigned = "$outDir\app-$abi-release-unsigned.apk"
    if (-not (Test-Path $unsigned)) { Write-Warning "跳过 $abi（没产物）"; continue }
    $aligned = "$root\.tmp\aligned-$abi.apk"
    $signed  = "$root\release\douyinspark-$abi.apk"
    New-Item -ItemType Directory -Force "$root\.tmp" | Out-Null

    Write-Host "[2/4] 对齐 $abi ..." -ForegroundColor Cyan
    & "$bt\zipalign.exe" -p -f 4 $unsigned $aligned

    Write-Host "[3/4] 签名 $abi ..." -ForegroundColor Cyan
    & "$bt\apksigner.bat" sign --ks $ks --ks-pass "pass:$store" --key-pass "pass:$key" --out $signed $aligned

    Write-Host "[4/4] 校验 $abi ..." -ForegroundColor Cyan
    & "$bt\apksigner.bat" verify --print-certs $signed | Select-Object -First 3
    Get-Item $signed | Select-Object Name, Length
}
Write-Host "`n完成，产物在 $root\release\" -ForegroundColor Green
