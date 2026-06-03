# YT Video Search

Faculty Information Retrieval project for searching indexed YouTube videos from a web client and Brave/Chrome extension.

The backend is a Java/Spring Boot service. The architecture is:

- Spring Boot backend for REST API and orchestration
- PostgreSQL + pgvector in Docker for video history, config, frame metadata, and image vectors
- Apache Lucene in the Java backend for text indexing/search
- separate Python embedding service for CLIP image embeddings
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
Embedding API:    http://127.0.0.1:8081
```

On first run, `start.ps1` creates `.env` from `.env.example`. Add `YOUTUBE_API_KEY` there before importing videos.

## Current Milestone

The project is in Java rewrite milestone 5:

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
- CLIP image embedding service in `embedding-service/`
- pgvector frame similarity search at `/api/search/image`
- existing web client and extension still served by Docker

Hybrid search is the next milestone.

Transcript import is best-effort through YouTube timed text endpoints. Some videos do not expose transcripts.

The first Docker build/start for image search is large because it installs PyTorch and downloads the CLIP model. Docker stores model cache in the `embedding-cache` volume.

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
