import re
from urllib.parse import parse_qs, urlparse


TOKEN_RE = re.compile(r"[\w']+", re.UNICODE)


def tokenize(text: str) -> list[str]:
    return [token.lower() for token in TOKEN_RE.findall(text or "")]


def parse_youtube_video_id(url: str) -> str | None:
    parsed = urlparse(url.strip())
    host = parsed.netloc.lower().removeprefix("www.")
    if host in {"youtube.com", "m.youtube.com"}:
        if parsed.path == "/watch":
            return parse_qs(parsed.query).get("v", [None])[0]
        if parsed.path.startswith("/shorts/") or parsed.path.startswith("/embed/"):
            parts = [part for part in parsed.path.split("/") if part]
            return parts[1] if len(parts) > 1 else None
    if host == "youtu.be":
        return parsed.path.strip("/") or None
    return None


def youtube_watch_url(video_id: str, timestamp_seconds: int | None = None) -> str:
    base = f"https://www.youtube.com/watch?v={video_id}"
    if timestamp_seconds is None:
        return base
    return f"{base}&t={max(0, int(timestamp_seconds))}s"


def make_snippet(content: str, query: str, max_length: int = 220) -> str:
    content = " ".join((content or "").split())
    if len(content) <= max_length:
        return content
    terms = tokenize(query)
    lower = content.lower()
    hit_index = min((lower.find(term) for term in terms if lower.find(term) >= 0), default=0)
    start = max(0, hit_index - max_length // 3)
    end = min(len(content), start + max_length)
    prefix = "..." if start > 0 else ""
    suffix = "..." if end < len(content) else ""
    return prefix + content[start:end] + suffix
