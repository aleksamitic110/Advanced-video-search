from pydantic import BaseModel, Field


class RuntimeConfig(BaseModel):
    youtube_api_key: str | None = None
    frame_interval_seconds: int | None = Field(default=None, ge=1, le=120)
    enable_ytdlp: bool | None = None


class ImportRequest(BaseModel):
    urls: list[str] = Field(min_length=1)


class ImportResult(BaseModel):
    video_id: str
    url: str
    status: str
    error: str = ""


class TextSearchRequest(BaseModel):
    query: str = Field(min_length=1)
    limit: int = Field(default=10, ge=1, le=50)
    fields: list[str] = Field(default_factory=lambda: ["title", "description", "comments", "transcript"])


class SearchResult(BaseModel):
    video_id: str
    title: str
    url: str
    timestamp_seconds: int | None = None
    youtube_timestamp_url: str
    score: float
    bm25_score: float | None = None
    vector_score: float | None = None
    source_type: str
    snippet: str = ""
    thumbnail_url: str = ""
    frame_path: str = ""
    frame_url: str = ""
