from dataclasses import dataclass, field
from typing import Any

import requests
from youtube_transcript_api import YouTubeTranscriptApi


YOUTUBE_API_BASE = "https://www.googleapis.com/youtube/v3"


@dataclass
class YouTubeMetadata:
    video_id: str
    title: str = ""
    description: str = ""
    thumbnail_url: str = ""
    channel_title: str = ""
    duration: str = ""
    raw: dict[str, Any] = field(default_factory=dict)


class YouTubeClient:
    def __init__(self, api_key: str = "") -> None:
        self.api_key = api_key

    def fetch_metadata(self, video_id: str) -> YouTubeMetadata:
        if not self.api_key:
            return YouTubeMetadata(video_id=video_id, title=f"YouTube video {video_id}")

        response = requests.get(
            f"{YOUTUBE_API_BASE}/videos",
            params={
                "key": self.api_key,
                "id": video_id,
                "part": "snippet,contentDetails,statistics",
            },
            timeout=20,
        )
        response.raise_for_status()
        items = response.json().get("items", [])
        if not items:
            raise ValueError(f"YouTube video not found: {video_id}")

        item = items[0]
        snippet = item.get("snippet", {})
        thumbnails = snippet.get("thumbnails", {})
        thumbnail = (
            thumbnails.get("maxres")
            or thumbnails.get("high")
            or thumbnails.get("medium")
            or thumbnails.get("default")
            or {}
        )
        return YouTubeMetadata(
            video_id=video_id,
            title=snippet.get("title", ""),
            description=snippet.get("description", ""),
            thumbnail_url=thumbnail.get("url", ""),
            channel_title=snippet.get("channelTitle", ""),
            duration=item.get("contentDetails", {}).get("duration", ""),
            raw=item,
        )

    def fetch_comments(self, video_id: str, max_pages: int = 3) -> list[str]:
        if not self.api_key:
            return []

        comments: list[str] = []
        page_token = None
        for _ in range(max_pages):
            params = {
                "key": self.api_key,
                "videoId": video_id,
                "part": "snippet",
                "maxResults": 100,
                "textFormat": "plainText",
            }
            if page_token:
                params["pageToken"] = page_token

            response = requests.get(f"{YOUTUBE_API_BASE}/commentThreads", params=params, timeout=20)
            if response.status_code == 403:
                return comments
            response.raise_for_status()
            data = response.json()
            for item in data.get("items", []):
                top_comment = item.get("snippet", {}).get("topLevelComment", {})
                text = top_comment.get("snippet", {}).get("textDisplay", "")
                if text:
                    comments.append(text)
            page_token = data.get("nextPageToken")
            if not page_token:
                break
        return comments

    def fetch_transcript_chunks(self, video_id: str) -> list[dict[str, Any]]:
        try:
            transcript = YouTubeTranscriptApi.get_transcript(video_id)
        except Exception:
            return []

        chunks: list[dict[str, Any]] = []
        current_text: list[str] = []
        current_start: int | None = None

        for item in transcript:
            start = int(item.get("start", 0))
            if current_start is None:
                current_start = start
            current_text.append(item.get("text", ""))
            if start - current_start >= 30:
                chunks.append({"timestamp_seconds": current_start, "content": " ".join(current_text)})
                current_text = []
                current_start = None

        if current_text and current_start is not None:
            chunks.append({"timestamp_seconds": current_start, "content": " ".join(current_text)})
        return chunks
