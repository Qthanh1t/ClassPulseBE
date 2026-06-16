# Chạy LiveKit SFU native trên Windows (thay cho bản Docker — Docker/WSL chặn media 2 thiết bị).
# Đọc LIVEKIT_NODE_IP từ .env làm nguồn sự thật (phải khớp LIVEKIT_URL trong .env).
# Dùng: .\start-livekit.ps1            (lấy IP từ .env)
#       .\start-livekit.ps1 192.168.1.50   (override IP nếu mạng đổi — nhớ sửa .env cho khớp)

param([string]$NodeIp)

$root = $PSScriptRoot
$exe  = Join-Path $root 'livekit-server.exe'
$cfg  = Join-Path $root 'livekit.yaml'
$envFile = Join-Path $root '.env'

if (-not (Test-Path $exe)) {
    Write-Error "Khong tim thay $exe. Tai livekit_*_windows_amd64.zip tu https://github.com/livekit/livekit/releases va giai nen vao $root"
    exit 1
}

if (-not $NodeIp) {
    if (Test-Path $envFile) {
        $line = Get-Content $envFile | Where-Object { $_ -match '^\s*LIVEKIT_NODE_IP\s*=' } | Select-Object -First 1
        if ($line) { $NodeIp = ($line -replace '^\s*LIVEKIT_NODE_IP\s*=', '').Trim() }
    }
}

if (-not $NodeIp) {
    Write-Error "Khong doc duoc LIVEKIT_NODE_IP tu .env. Truyen tay: .\start-livekit.ps1 192.168.1.122"
    exit 1
}

Write-Host "Starting LiveKit native | node-ip = $NodeIp" -ForegroundColor Cyan
Write-Host "(Dam bao LIVEKIT_URL trong .env la ws://${NodeIp}:7880)" -ForegroundColor DarkGray
& $exe --config $cfg --node-ip $NodeIp
