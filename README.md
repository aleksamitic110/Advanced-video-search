# YT Video Search Extension

Brave/Chrome extension and local FastAPI backend for multimodal YouTube video search.

It indexes pasted YouTube links and supports:

- BM25 text search over titles, descriptions, comments, and transcripts
- image/frame similarity search with timestamps
- hybrid text + image search
- direct links that open YouTube at the matched timestamp

## Docker Quick Start On A New Laptop

Clone the repo, then run:

```powershell
.\start.ps1
```

This starts:

```text
Backend:          http://127.0.0.1:8000
Web client:       http://127.0.0.1:5173
Extension files:  http://127.0.0.1:5174
```

The backend Docker image installs Python dependencies and FFmpeg. It uses the Python `yt-dlp` package from `requirements.txt`, so you do not need to install `yt-dlp` or FFmpeg manually for Docker.

Optional: copy `.env.example` to `.env` and set `YOUTUBE_API_KEY` before starting Docker. You can also save the API key from the web client or extension options page.

## Manual Backend Quick Start

Use this if you do not want Docker:

```powershell
cd backend
.\scripts\setup_tools.ps1
.\scripts\start_backend.ps1
```

Backend runs at:

```text
http://127.0.0.1:8000
```

## Web Client

When Docker is running, open:

```text
http://127.0.0.1:5173
```

The web client supports importing videos, setting the API key, text/image/hybrid search, viewing indexed videos, and deleting indexed videos.

## Browser Extension

Open Brave or Chrome:

```text
brave://extensions
```

or:

```text
chrome://extensions
```

Enable Developer Mode, choose **Load unpacked**, and select the `extension/` folder.

Docker also serves the extension files at `http://127.0.0.1:5174`, but browser extensions still need to be loaded from the local `extension/` folder in Developer Mode.

## Configuration

The web client and extension options page let you set:

- backend URL, default `http://127.0.0.1:8000`
- YouTube API key
- frame interval in seconds
- whether local video download/frame extraction is enabled

The YouTube API key is saved in the backend SQLite config. You enter it once unless you delete `backend/data/`.

## Local Tools

The repo does not commit `yt-dlp.exe`, FFmpeg, downloaded videos, extracted frames, or the SQLite database.

Run this to download tools into the expected local paths:

```powershell
cd backend
.\scripts\setup_tools.ps1
```

Expected generated paths:

```text
backend/tools/yt-dlp.exe
backend/tools/ffmpeg/bin/ffmpeg.exe
backend/tools/ffmpeg/bin/ffprobe.exe
backend/tools/ffmpeg/bin/ffplay.exe
```

These paths are ignored by git because FFmpeg binaries are too large for normal GitHub pushes.

Docker does not use these Windows executables. Docker installs Linux FFmpeg inside the backend image.

## Important Demo Notes

Metadata and comments require a YouTube Data API key. Transcripts are best-effort and depend on whether a video has available captions. Frame extraction requires `yt-dlp` and `ffmpeg`, and is disabled unless explicitly enabled.

The current local image embedding is lightweight so the demo works on normal laptops. The backend isolates embedding logic in `app/indexing/vector_index.py`, where CLIP can replace the fallback descriptor.

## Useful Checks

```powershell
Invoke-RestMethod http://127.0.0.1:8000/api/health
.\tools\yt-dlp.exe --version
.\tools\ffmpeg\bin\ffmpeg.exe -version
```
