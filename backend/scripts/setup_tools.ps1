$ErrorActionPreference = "Stop"

$BackendRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$ToolsDir = Join-Path $BackendRoot "tools"
$FfmpegBinDir = Join-Path $ToolsDir "ffmpeg\bin"
$TempDir = Join-Path $env:TEMP "yt-video-search-tools"

New-Item -ItemType Directory -Force -Path $ToolsDir, $FfmpegBinDir, $TempDir | Out-Null

$YtDlpPath = Join-Path $ToolsDir "yt-dlp.exe"
if (-not (Test-Path $YtDlpPath)) {
    Write-Host "Downloading yt-dlp.exe..."
    Invoke-WebRequest `
        -Uri "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe" `
        -OutFile $YtDlpPath
} else {
    Write-Host "yt-dlp.exe already exists."
}

$FfmpegPath = Join-Path $FfmpegBinDir "ffmpeg.exe"
if (-not (Test-Path $FfmpegPath)) {
    Write-Host "Downloading FFmpeg full build..."
    $ZipPath = Join-Path $TempDir "ffmpeg-full.zip"
    $ExtractDir = Join-Path $TempDir "ffmpeg"
    if (Test-Path $ExtractDir) {
        Remove-Item -Recurse -Force $ExtractDir
    }
    Invoke-WebRequest `
        -Uri "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-full.zip" `
        -OutFile $ZipPath
    Expand-Archive -LiteralPath $ZipPath -DestinationPath $ExtractDir -Force

    $Executables = Get-ChildItem -Recurse -File -Path $ExtractDir -Include ffmpeg.exe,ffprobe.exe,ffplay.exe
    foreach ($Executable in $Executables) {
        Copy-Item -LiteralPath $Executable.FullName -Destination $FfmpegBinDir -Force
    }
} else {
    Write-Host "FFmpeg already exists."
}

Write-Host ""
Write-Host "Tool versions:"
& $YtDlpPath --version
& $FfmpegPath -version | Select-Object -First 1

Write-Host ""
Write-Host "Tools ready in $ToolsDir"
