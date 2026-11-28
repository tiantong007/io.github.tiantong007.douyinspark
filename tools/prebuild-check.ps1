# prebuild-check.ps1 - pre-build version-sync self check
# Usage: powershell -ExecutionPolicy Bypass -File tools\prebuild-check.ps1
# Verifies the single source of truth (android/app/build.gradle.kts) matches
# every mirrored version string: README.md, in-app manual.md, docs/ui/index.html.
# Exit code 0 = pass, 1 = mismatch.
#
# IMPORTANT: this file is intentionally 100% ASCII (comments included).
# Windows PowerShell 5.1 reads a BOM-less .ps1 as the ANSI/GBK codepage; a
# full-width char in a comment can swallow the following newline and break
# parsing. Keep it ASCII; build full-width parens at runtime via char codes.

param([switch]$Quiet)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot   # tools dir -> repo root

# Full-width parentheses used in the UI/manual banners (U+FF08 / U+FF09)
$lp = [char]0xFF08
$rp = [char]0xFF09

$gradle = Join-Path $root "android\app\build.gradle.kts"
if (-not (Test-Path $gradle)) { Write-Host "[FAIL] not found: $gradle" -ForegroundColor Red; exit 1 }
$txt = Get-Content $gradle -Raw -Encoding UTF8
$mCode = [regex]::Match($txt, 'versionCode\s*=\s*(\d+)')
$mName = [regex]::Match($txt, 'versionName\s*=\s*"([^"]+)"')
if (-not $mCode.Success -or -not $mName.Success) {
    Write-Host "[FAIL] android/app/build.gradle.kts missing versionCode / versionName" -ForegroundColor Red
    exit 1
}
$code = $mCode.Groups[1].Value
$name = $mName.Groups[1].Value

$vcWide = "$lp" + "versionCode $code" + "$rp"   # (full-width) versionCode NNNNN (full-width)
$uiWide = "$name$lp$code$rp"                    # vX.Y.Z (full-width) NNNNN (full-width)
$anchor = "version-anchor: $name ($code)"       # version-anchor: vX.Y.Z (NNNNN)

$checks = @(
    @{ File = "README.md";                                         Need = @($anchor, $vcWide) },
    @{ File = "android\core-spark\src\main\assets\docs\manual.md"; Need = @($anchor, $vcWide) },
    @{ File = "docs\ui\index.html";                                Need = @($uiWide) }
)

if (-not $Quiet) { Write-Host "version source: versionCode=$code  versionName=$name" -ForegroundColor Cyan }
Write-Host ""

$fail = 0
foreach ($c in $checks) {
    $p = Join-Path $root $c.File
    if (-not (Test-Path $p)) {
        Write-Host ("[MISS] " + $c.File + " not found") -ForegroundColor Yellow
        $fail++
        continue
    }
    $body = Get-Content $p -Raw -Encoding UTF8
    foreach ($need in $c.Need) {
        if ($body.Contains($need)) {
            if (-not $Quiet) { Write-Host ("[ OK ] " + $c.File + "  <<" + $need + ">>") -ForegroundColor Green }
        } else {
            Write-Host ("[FAIL] " + $c.File + "  missing <<" + $need + ">>") -ForegroundColor Red
            $fail++
        }
    }
}

# Working tree cleanliness - advisory only (dev builds may be dirty)
Push-Location $root
$dirty = @(git status --porcelain 2>$null)
Pop-Location
if ($dirty.Count -gt 0) {
    Write-Host ""
    Write-Host "[WARN] working tree has uncommitted changes - commit/confirm before a release build:" -ForegroundColor Yellow
    $dirty | ForEach-Object { Write-Host ("        " + $_) -ForegroundColor Yellow }
} elseif (-not $Quiet) {
    Write-Host ""
    Write-Host "[ OK ] working tree clean (all changes committed)" -ForegroundColor Green
}

if ($fail -gt 0) {
    Write-Host ""
    Write-Host "PREBUILD CHECK FAILED: $fail version string(s) out of sync. Abort." -ForegroundColor Red
    exit 1
}
if (-not $Quiet) { Write-Host ""; Write-Host "PREBUILD CHECK PASSED - version $name ($code) consistent." -ForegroundColor Green }
exit 0
