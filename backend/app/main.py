from pathlib import Path
import shutil
from uuid import uuid4

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse

from .config import get_settings
from .database import connect, init_db, row_to_dict, rows_to_dicts
from .ingestion.pipeline import IngestionPipeline
from .models import ImportRequest, RuntimeConfig, TextSearchRequest
from .search.service import SearchService


settings = get_settings()
app = FastAPI(title="YT Video Search Extension API", version="0.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
def startup() -> None:
    settings.ensure_dirs()
    init_db()
    _load_runtime_config()


@app.get("/api/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/api/config")
def update_config(payload: RuntimeConfig) -> dict:
    with connect() as connection:
        if payload.youtube_api_key is not None:
            settings.youtube_api_key = payload.youtube_api_key.strip()
            connection.execute(
                "INSERT OR REPLACE INTO runtime_config(key, value) VALUES ('youtube_api_key', ?)",
                (settings.youtube_api_key,),
            )
        if payload.frame_interval_seconds is not None:
            settings.frame_interval_seconds = payload.frame_interval_seconds
            connection.execute(
                "INSERT OR REPLACE INTO runtime_config(key, value) VALUES ('frame_interval_seconds', ?)",
                (str(settings.frame_interval_seconds),),
            )
        if payload.enable_ytdlp is not None:
            settings.enable_ytdlp = payload.enable_ytdlp
            connection.execute(
                "INSERT OR REPLACE INTO runtime_config(key, value) VALUES ('enable_ytdlp', ?)",
                ("true" if settings.enable_ytdlp else "false",),
            )
    return current_config()


@app.get("/api/config")
def current_config() -> dict:
    return {
        "youtube_api_key_configured": bool(settings.youtube_api_key),
        "frame_interval_seconds": settings.frame_interval_seconds,
        "enable_ytdlp": settings.enable_ytdlp,
        "data_dir": str(settings.data_dir),
    }


@app.post("/api/videos/import")
def import_videos(payload: ImportRequest) -> dict:
    pipeline = IngestionPipeline(settings)
    return {"results": pipeline.import_urls(payload.urls)}


@app.get("/api/videos")
def list_videos() -> dict:
    with connect() as connection:
        rows = connection.execute(
            """
            SELECT video_id, url, title, thumbnail_url, channel_title, duration, status, error,
                   comment_count, transcript_count, frame_count, created_at, updated_at,
                   (
                       SELECT COUNT(*)
                       FROM text_docs td
                       WHERE td.video_id = videos.video_id
                   ) AS text_doc_count
            FROM videos
            ORDER BY updated_at DESC
            """
        ).fetchall()
    return {"videos": rows_to_dicts(rows)}


@app.delete("/api/videos/{video_id}")
def delete_video(video_id: str) -> dict:
    with connect() as connection:
        existing = connection.execute("SELECT video_id FROM videos WHERE video_id = ?", (video_id,)).fetchone()
        if not existing:
            raise HTTPException(status_code=404, detail="Video not found")
        connection.execute("DELETE FROM videos WHERE video_id = ?", (video_id,))

    frame_dir = settings.frames_dir / video_id
    video_file = settings.videos_dir / f"{video_id}.mp4"
    if frame_dir.exists():
        shutil.rmtree(frame_dir)
    if video_file.exists():
        video_file.unlink()

    return {"deleted": True, "video_id": video_id}


@app.post("/api/search/text")
def text_search(payload: TextSearchRequest) -> dict:
    service = SearchService()
    return {"results": service.text_search(payload.query, payload.fields, payload.limit)}


@app.post("/api/search/image")
async def image_search(image: UploadFile = File(...), limit: int = Form(10)) -> dict:
    image_path = await _save_upload(image)
    service = SearchService()
    return {"results": service.image_search(image_path, limit)}


@app.post("/api/search/hybrid")
async def hybrid_search(
    query: str = Form(""),
    image: UploadFile | None = File(default=None),
    limit: int = Form(10),
    text_weight: float = Form(0.4),
    vector_weight: float = Form(0.6),
) -> dict:
    if not query.strip() and image is None:
        raise HTTPException(status_code=400, detail="Hybrid search needs a text query or an image.")
    image_path = await _save_upload(image) if image else None
    service = SearchService()
    return {
        "results": service.hybrid_search(
            query=query.strip() or None,
            image_path=image_path,
            text_weight=text_weight,
            vector_weight=vector_weight,
            limit=limit,
        )
    }


@app.get("/api/frames/{frame_id}")
def get_frame(frame_id: int) -> FileResponse:
    with connect() as connection:
        row = connection.execute("SELECT frame_path FROM frames WHERE id = ?", (frame_id,)).fetchone()
    frame = row_to_dict(row)
    if not frame:
        raise HTTPException(status_code=404, detail="Frame not found")
    frame_path = Path(frame["frame_path"])
    if not frame_path.exists():
        raise HTTPException(status_code=404, detail="Frame file not found")
    return FileResponse(frame_path)


async def _save_upload(upload: UploadFile) -> Path:
    suffix = Path(upload.filename or "query.jpg").suffix or ".jpg"
    path = settings.uploads_dir / f"{uuid4().hex}{suffix}"
    content = await upload.read()
    path.write_bytes(content)
    return path


def _load_runtime_config() -> None:
    with connect() as connection:
        rows = connection.execute("SELECT key, value FROM runtime_config").fetchall()
    values = {row["key"]: row["value"] for row in rows}
    if "youtube_api_key" in values:
        settings.youtube_api_key = values["youtube_api_key"]
    if "frame_interval_seconds" in values:
        settings.frame_interval_seconds = int(values["frame_interval_seconds"])
    if "enable_ytdlp" in values:
        settings.enable_ytdlp = values["enable_ytdlp"].lower() == "true"


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="127.0.0.1", port=8000, reload=True)
