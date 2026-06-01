import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.database import init_db
from app.utils import parse_youtube_video_id, youtube_watch_url


def test_youtube_url_parser() -> None:
    assert parse_youtube_video_id("https://www.youtube.com/watch?v=abc123") == "abc123"
    assert parse_youtube_video_id("https://youtu.be/abc123") == "abc123"
    assert parse_youtube_video_id("https://www.youtube.com/shorts/abc123") == "abc123"
    assert parse_youtube_video_id("https://example.com/watch?v=abc123") is None


def test_timestamp_url() -> None:
    assert youtube_watch_url("abc123") == "https://www.youtube.com/watch?v=abc123"
    assert youtube_watch_url("abc123", 42) == "https://www.youtube.com/watch?v=abc123&t=42s"


if __name__ == "__main__":
    init_db()
    test_youtube_url_parser()
    test_timestamp_url()
    print("smoke tests passed")
