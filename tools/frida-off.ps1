# frida-off.ps1 — 停止 frida-server 并移除端口转发
$serial = "dd697c28"
adb -s $serial shell "su -c 'killall fsd'" 2>$null
adb -s $serial forward --remove tcp:8899 2>$null
Write-Host "frida 已停" -ForegroundColor Green
