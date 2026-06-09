$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI was not found. Install Docker Desktop and make sure docker is available in PATH."
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Docker is not running. Start Docker Desktop first, then run .\start.ps1 again."
}

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "Created .env from .env.example. Add YOUTUBE_API_KEY there before importing videos."
}

Write-Host "Building and starting Docker services..."
docker compose up --build -d
if ($LASTEXITCODE -ne 0) {
    throw "docker compose up failed."
}

docker compose ps

Write-Host ""
Write-Host "Services:"
Write-Host "  Backend:          http://127.0.0.1:8000"
Write-Host "  Web client:       http://127.0.0.1:5173"
Write-Host "  Extension files:  http://127.0.0.1:5174"
Write-Host "  Embedding API:    http://127.0.0.1:8081"
Write-Host ""
Write-Host "Search endpoints:"
Write-Host "  Text:             POST http://127.0.0.1:8000/api/search/text"
Write-Host "  Image:            POST http://127.0.0.1:8000/api/search/image"
Write-Host "  Visual semantic:  POST http://127.0.0.1:8000/api/search/visual-semantic"
Write-Host ""
Write-Host "For Brave/Chrome extension use: brave://extensions -> Load unpacked -> extension/"
