# YT Video Search Extension Plan

## Summary

Build a Chrome Manifest V3 extension plus a local Python backend for searching a selected set of YouTube videos. The main grading value is multimodal information retrieval: BM25 text search over metadata, comments, and transcripts; vector search over extracted video frames; and hybrid ranking that combines both signals.

The project intentionally focuses on pasted YouTube links instead of crawling all of YouTube. That keeps the implementation realistic for a course project and makes evaluation repeatable.

## Architecture

- `extension/` is the Chrome extension. It lets the user configure the backend URL and YouTube API key, import video links, run text/image/hybrid searches, and open results at exact timestamps.
- `backend/` is a FastAPI service. It handles ingestion, local storage, frame extraction, embedding, BM25 scoring, vector similarity, and hybrid result merging.
- `backend/data/` stores local SQLite data, extracted frames, optional downloaded videos, and uploaded query images.

## Retrieval Features

- Text indexing:
  - Index title, description, comments, and transcript chunks as separate searchable documents.
  - Rank text results with BM25.
  - Return matched source type, snippet, score, video title, thumbnail, and timestamp when available.
- Frame/image indexing:
  - Extract frames every 5-10 seconds when local video download is enabled.
  - Store one embedding per frame with `video_id`, frame path, and timestamp.
  - Search uploaded images against frame embeddings using cosine similarity.
  - The default implementation uses a lightweight local image descriptor, and the code is isolated so CLIP can replace it.
- Hybrid search:
  - Combine normalized BM25 and vector scores.
  - Prefer results where text and visual evidence point to the same video/timestamp.
  - Return direct YouTube timestamp links.

## API

- `POST /api/config`
  - Stores runtime settings such as YouTube API key and frame extraction interval.
- `POST /api/videos/import`
  - Input: `{ "urls": ["https://www.youtube.com/watch?v=..."] }`
  - Imports videos synchronously for a small demo dataset.
- `GET /api/videos`
  - Lists indexed videos and ingestion status.
- `POST /api/search/text`
  - Input: `{ "query": "dog", "limit": 10, "fields": ["title", "description", "comments", "transcript"] }`
  - Returns BM25-ranked text matches.
- `POST /api/search/image`
  - Input: multipart upload with `image`.
  - Returns visually similar frame matches.
- `POST /api/search/hybrid`
  - Input: multipart upload with optional `query`, optional `image`, and optional weights.
  - Returns merged text/vector results.

## Implementation Notes

- Use FastAPI because Python has the best tooling for YouTube ingestion, ML embeddings, image processing, and video frame extraction.
- Use SQLite for the local demo database.
- Use a simple built-in BM25 implementation instead of requiring Elasticsearch/Solr during the first version.
- Use YouTube Data API for metadata and comments when an API key is provided.
- Use `youtube-transcript-api` as a best-effort transcript fallback.
- Use `yt-dlp` and `ffmpeg` only when `ENABLE_YTDLP=true`; otherwise the app still indexes metadata/comments/transcripts.
- Keep audio search as an optional future extension, not part of the first graded version.

## Test Plan

- Import at least 2-3 demo YouTube videos.
- Verify videos appear in the extension after import.
- Run a text query that appears in a title, description, comment, or transcript.
- Upload an extracted frame as an image query and verify the same video/timestamp is near the top.
- Run hybrid search and compare BM25-only, vector-only, and final merged scores.
- Open a search result and verify it navigates to `youtube.com/watch?v={video_id}&t={seconds}s`.

## Grading Talking Points

- Inverted index idea and BM25 ranking from course labs.
- Difference between lexical search and neural/vector search.
- Why frames need timestamps to make video search useful.
- Why hybrid ranking improves results over only text or only image matching.
- Limitations: YouTube quota, caption availability, video download permissions, and lightweight fallback embeddings.
