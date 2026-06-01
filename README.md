# YT Video Search Extension

Brave/Chrome extension and local FastAPI backend for multimodal YouTube video search.

It indexes pasted YouTube links and supports:

- BM25 text search over titles, descriptions, comments, and transcripts
- image/frame similarity search with timestamps
- hybrid text + image search
- direct links that open YouTube at the matched timestamp

## Quick Start On A New Laptop

Clone the repo, then run:

```powershell
cd backend
.\scripts\setup_tools.ps1
.\scripts\start_backend.ps1
```

Backend runs at:

```text
http://127.0.0.1:8000
```

Then open Brave or Chrome:

```text
brave://extensions
```

or:

```text
chrome://extensions
```

Enable Developer Mode, choose **Load unpacked**, and select the `extension/` folder.

## Configuration

The extension options page lets you set:

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

## Important Demo Notes

Metadata and comments require a YouTube Data API key. Transcripts are best-effort and depend on whether a video has available captions. Frame extraction requires `yt-dlp` and `ffmpeg`, and is disabled unless explicitly enabled.

The current local image embedding is lightweight so the demo works on normal laptops. The backend isolates embedding logic in `app/indexing/vector_index.py`, where CLIP can replace the fallback descriptor.

## Useful Checks

```powershell
Invoke-RestMethod http://127.0.0.1:8000/api/health
.\tools\yt-dlp.exe --version
.\tools\ffmpeg\bin\ffmpeg.exe -version
```
