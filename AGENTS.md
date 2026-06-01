# Agent Handoff

## Project Goal

This project is a Brave/Chrome extension plus local FastAPI backend for a faculty Information Retrieval project. It indexes pasted YouTube links and supports:

- BM25 text search over title, description, comments, and transcript chunks
- image/frame similarity search over extracted video frames
- hybrid text + image search
- direct YouTube timestamp links

The project lives in:

```text
YT video search extension/
```

## Current Structure

```text
backend/
  app/
    main.py                 FastAPI routes
    config.py               local settings and data/tool paths
    database.py             SQLite helpers
    schema.sql              DB schema
    ingestion/
      pipeline.py           YouTube import, yt-dlp download, ffmpeg frame extraction
      youtube_client.py     YouTube Data API comments/metadata and transcript fallback
    indexing/
      text_index.py         BM25 implementation and result formatting
      vector_index.py       lightweight image descriptor and cosine search
    search/
      service.py            text/image/hybrid orchestration
  scripts/
    setup_tools.ps1         downloads yt-dlp.exe and FFmpeg into backend/tools
    start_backend.ps1       creates venv, installs deps, starts uvicorn
  requirements.txt
extension/
  manifest.json             Manifest V3 extension
  popup.*                   import/search UI
  options.*                 backend URL, API key, ytdlp/ffmpeg settings
Plan.md                     original implementation plan
README.md                   setup and usage
```

Ignored local runtime folders:

```text
backend/.venv/
backend/data/
backend/tools/
```

Do not commit API keys, downloaded videos, extracted frames, SQLite data, virtualenvs, or FFmpeg binaries.

## What Works

- Backend starts at `http://127.0.0.1:8000`.
- YouTube API key is saved once through extension options or `POST /api/config`.
- `yt-dlp.exe` and FFmpeg can be installed into `backend/tools` with `backend/scripts/setup_tools.ps1`.
- Frame extraction works when `enable_ytdlp=true` and tools are available.
- Delete video from extension history is implemented with `DELETE /api/videos/{video_id}`.
- Comment search results are more readable: full-ish comments, highlighted terms, source labels, and show-more toggle.

## Important Behavior

- YouTube Data API key is required for reliable title/description/comments.
- Transcripts are best-effort via `youtube-transcript-api`; many videos have no accessible transcript.
- Frame extraction requires downloading the video with yt-dlp. Some videos may fail due to YouTube restrictions, age checks, or bot checks.
- Current image search uses a lightweight RGB histogram/grid descriptor, not CLIP. This is acceptable as a functional fallback but should be upgraded for stronger neural-search grading.

## Suggested Next Work

- Add CLIP embeddings for image/text-to-frame search, ideally via `sentence-transformers` or OpenCLIP.
- Add clearer error display in the extension for failed imports.
- Add progress/status updates for long imports instead of synchronous waiting.
- Add pagination or grouping for comment results.
- Add export/import of indexed video lists for demonstrations.
- Add automated API tests for import, text search, delete, and image search.

## Run Commands

First setup on a new laptop:

```powershell
cd backend
.\scripts\setup_tools.ps1
.\scripts\start_backend.ps1
```

Load extension:

```text
brave://extensions -> Developer mode -> Load unpacked -> extension/
```

Backend health check:

```powershell
Invoke-RestMethod http://127.0.0.1:8000/api/health
```
