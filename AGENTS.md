# Agent Handoff

## Goal

This is a faculty Information Retrieval project for indexing YouTube videos and searching them from both:

- `client/` standalone web client
- `extension/` Brave/Chrome extension

The project is being rewritten from Python to Java. Do not restore the old Python backend.

## Accepted Architecture

- Java Spring Boot backend in `backend/`
- PostgreSQL in Docker for persistence
- Apache Lucene in the Java backend for text indexing/search
- separate image embedding service later, not DJL inside Java
- one startup script: `start.ps1`
- Docker Compose is the supported runtime path

## Current Structure

```text
backend/
  pom.xml
  Dockerfile
  src/main/java/com/aleksamitic/videosearch/
    VideoSearchApplication.java
    api/
    config/
  src/main/resources/application.yml
client/
  index.html
  app.js
  styles.css
  Dockerfile
extension/
  manifest.json
  popup.*
  options.*
  Dockerfile
docker-compose.yml
start.ps1
README.md
AGENTS.md
```

## Milestones

1. Java backend skeleton + PostgreSQL + Docker health check. Done in commit `c6ae9bb`.
2. PostgreSQL video history/config API. Done in commit `487081b`.
3. YouTube import + Lucene text indexing/search. Done.
4. Frame extraction with yt-dlp/ffmpeg.
5. Separate embedding service + image search with pgvector.
6. Hybrid search.

Push after each stable milestone.

## Important Rules

- Keep frontend and extension API responses compatible where practical.
- Do not reintroduce Python virtualenvs or manual backend tool scripts.
- Runtime data, local `.env`, videos, frames, Lucene indexes, and build output must not be committed.
- The Java package is `com.aleksamitic.videosearch`.
