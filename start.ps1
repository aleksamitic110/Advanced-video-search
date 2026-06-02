$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "Created .env from .env.example. Add YOUTUBE_API_KEY there or save it from the web client/options page."
}

Write-Host "Building and starting Docker services..."
docker compose up --build -d

Write-Host ""
Write-Host "Services:"
Write-Host "  Backend:          http://127.0.0.1:8000"
Write-Host "  Web client:       http://127.0.0.1:5173"
Write-Host "  Extension files:  http://127.0.0.1:5174"
Write-Host ""
Write-Host "For Brave/Chrome extension use: brave://extensions -> Load unpacked -> extension/"
