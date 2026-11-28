# build.ps1 — 一键：编译 → zipalign → 签名 → 输出到 release\
#
# 用法:
#   .\build.ps1                arm64-v8a 与 armeabi-v7a 都签
#   .\build.ps1 -Arm64Only     只签 arm64-v8a（日常真机联调用这个）
#
# 本机专有配置（不入库）：keystore\local.env.ps1，示例：
#   $env:JAVA_HOME         = 'C:\path\to\jdk21'
#   $env:ANDROID_HOME      = 'C:\path\to\android-sdk'
#   $env:SPARK_KS_FILE     = 'C:\path\to\keystore\release.keystore'
#   $env:SPARK_KS_ALIAS    = 'release'
#   $env:SPARK_BUILD_TOOLS = '36.0.0'    # 可省略
#
# 签名口令（同样不入库，任选一种）:
#   1) 环境变量  SPARK_KS_PASS / SPARK_KEY_PASS
#   2) 文件      keystore\signing.txt —— 第一行 store 口令，第二行 key 口令
#
# 产物命名: release\douyinspark-arm64-v8a.apk
param(
    [switch]$Arm64Only,
    [switch]$SkipCheck
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

# keystore\ 已被 .gitignore 排除，本机路径与口令都放这里
$localEnv = Join-Path $root "keystore\local.env.ps1"
if (Test-Path $localEnv) { . $localEnv }

if (-not $env:JAVA_HOME)    { throw "缺少 JAVA_HOME：设置环境变量，或在 keystore\local.env.ps1 里定义" }
if (-not $env:ANDROID_HOME) { throw "缺少 ANDROID_HOME：设置环境变量，或在 keystore\local.env.ps1 里定义" }

$btVersion = if ($env:SPARK_BUILD_TOOLS) { $env:SPARK_BUILD_TOOLS } else { "36.0.0" }
$bt    = Join-Path $env:ANDROID_HOME "build-tools\$btVersion"
$ks    = if ($env:SPARK_KS_FILE)  { $env:SPARK_KS_FILE }  else { Join-Path $root "keystore\release.keystore" }
$alias = if ($env:SPARK_KS_ALIAS) { $env:SPARK_KS_ALIAS } else { "release" }

$store = $env:SPARK_KS_PASS
$key   = $env:SPARK_KEY_PASS
if (-not $store) {
    $f = Join-Path $root "keystore\signing.txt"
    if (Test-Path $f) {
        $lines = Get-Content $f | Where-Object { $_.Trim() -ne "" }
        $store = $lines[0].Trim()
        $key   = if ($lines.Count -gt 1) { $lines[1].Trim() } else { $store }
    }
}
if (-not $store) { throw "缺少签名口令：设置环境变量 SPARK_KS_PASS，或建 keystore\signing.txt（两行：store 口令 / key 口令）" }
if (-not $key)   { $key = $store }

if (-not (Test-Path $ks)) { throw "找不到 keystore: $ks（用 SPARK_KS_FILE 指定）" }

$abis = if ($Arm64Only) { @("arm64-v8a") } else { @("arm64-v8a", "armeabi-v7a") }

# 构建前自检：版本号同步（tools\prebuild-check.ps1；-SkipCheck 可跳过）
$precheck = Join-Path $root "tools\prebuild-check.ps1"
if (-not $SkipCheck -and (Test-Path $precheck)) {
    Write-Host "[0/4] 版本号同步自检..." -ForegroundColor Cyan
    & powershell -NoProfile -ExecutionPolicy Bypass -File $precheck
    if ($LASTEXITCODE -ne 0) { throw "构建前自检未通过：版本号未同步，已中止。改完再构建，或用 -SkipCheck 跳过。" }
}

Write-Host "[1/4] 编译 (assembleRelease)..." -ForegroundColor Cyan
Push-Location "$root\android"
& .\gradlew.bat :app:assembleRelease --console=plain --no-daemon
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "编译失败" }
Pop-Location

$outDir = "$root\android\app\build\outputs\apk\release"

foreach ($abi in $abis) {
    $unsigned = "$outDir\app-$abi-release-unsigned.apk"
    if (-not (Test-Path $unsigned)) { Write-Warning "跳过 $abi（没产物）"; continue }
    $aligned = "$root\.tmp\aligned-$abi.apk"
    $signed  = "$root\release\douyinspark-$abi.apk"
    New-Item -ItemType Directory -Force "$root\.tmp" | Out-Null
    New-Item -ItemType Directory -Force "$root\release" | Out-Null

    Write-Host "[2/4] 对齐 $abi ..." -ForegroundColor Cyan
    & "$bt\zipalign.exe" -p -f 4 $unsigned $aligned

    Write-Host "[3/4] 签名 $abi ..." -ForegroundColor Cyan
    & "$bt\apksigner.bat" sign --ks $ks --ks-pass "pass:$store" --key-pass "pass:$key" --out $signed $aligned

    Write-Host "[4/4] 校验 $abi ..." -ForegroundColor Cyan
    & "$bt\apksigner.bat" verify --print-certs $signed | Select-Object -First 3
    Get-Item $signed | Select-Object Name, Length
}

Write-Host "`n完成，产物在 $root\release\" -ForegroundColor Green
