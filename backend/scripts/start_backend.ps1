$ErrorActionPreference = "Stop"

$BackendRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $BackendRoot

$Python = Join-Path $BackendRoot ".venv\Scripts\python.exe"
if (-not (Test-Path $Python)) {
    Write-Host "Creating virtual environment..."
    python -m venv .venv
}

Write-Host "Installing backend dependencies..."
& $Python -m pip install -r requirements.txt

Write-Host "Starting backend on http://127.0.0.1:8000"
& $Python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
