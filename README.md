# YT Video Search

Faculty Information Retrieval project for searching indexed YouTube videos from a web client and Brave/Chrome extension.

The backend is being rewritten as a Java/Spring Boot service. The target architecture is:

- Spring Boot backend for REST API and orchestration
- PostgreSQL in Docker for video history, config, frame metadata, and later pgvector
- Apache Lucene in the Java backend for text indexing/search
- separate embedding service later for CLIP/OpenCLIP image embeddings
- static web client in `client/`
- Brave/Chrome extension in `extension/`

## Start

Run from the project root:

```powershell
.\start.ps1
```

This starts:

```text
Backend:          http://127.0.0.1:8000
Web client:       http://127.0.0.1:5173
Extension files:  http://127.0.0.1:5174
```

On first run, `start.ps1` creates `.env` from `.env.example`. Add `YOUTUBE_API_KEY` there before importing videos.

## Current Milestone

The project is in Java rewrite milestone 4:

- Java/Spring Boot backend skeleton
- PostgreSQL Docker service
- backend health endpoint at `/api/health`
- database-backed config endpoint at `/api/config`
- video history endpoint at `/api/videos`
- delete endpoint at `/api/videos/{videoId}`
- YouTube metadata/comment import at `/api/videos/import`
- Lucene text indexing/search at `/api/search/text`
- Java-managed frame extraction with `yt-dlp` and `ffmpeg`
- frame serving endpoint at `/api/frames/{frameId}`
- existing web client and extension still served by Docker

Image embedding/search and hybrid search are the next milestones.

Transcript import is best-effort through YouTube timed text endpoints. Some videos do not expose transcripts.

## Extension

For real extension usage, load it unpacked:

```text
brave://extensions -> Developer mode -> Load unpacked -> extension/
```

Docker serves extension files at `http://127.0.0.1:5174` only for static inspection. Browsers still require loading the local `extension/` folder.

## Useful Checks

```powershell
docker compose ps
Invoke-RestMethod http://127.0.0.1:8000/api/health
```
