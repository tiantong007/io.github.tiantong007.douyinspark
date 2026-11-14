# frida-on.ps1 — 启动手机端 frida-server（改名 fsd + 端口 8899，绕抖音检测）
$ErrorActionPreference = "Stop"
$serial = "dd697c28"
$fsd = "/data/local/tmp/fsd"

if (-not (adb -s $serial shell "ls $fsd" 2>$null)) {
    Write-Host "手机上没找到 $fsd，先 push：" -ForegroundColor Yellow
    Write-Host "  adb -s $serial push D:\czj\gj\frida-server\bin\fsd-16.7.19-android-arm64 $fsd"
    exit 1
}
adb -s $serial shell "su -c 'chmod 755 $fsd'"
adb -s $serial shell "su -c 'nohup $fsd -D -l 127.0.0.1:8899 >/dev/null 2>&1 &'"
Start-Sleep -Seconds 2
adb -s $serial forward tcp:8899 tcp:8899
Write-Host "frida 就绪：frida-ps -H 127.0.0.1:8899" -ForegroundColor Green
