import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

from ..database import connect, json_dumps, json_loads, rows_to_dicts
from ..utils import youtube_watch_url


class ImageEmbeddingService:
    """Small local image descriptor used as a no-GPU fallback.

    The class boundary is intentional: replace `embed_image` with CLIP later while
    keeping the database and API stable.
    """

    def embed_image(self, image_path: str | Path) -> list[float]:
        image = Image.open(image_path).convert("RGB")
        image = ImageOps.fit(image, (224, 224))
        histogram_features = []
        for channel in image.split():
            histogram = channel.histogram()
            bins = np.array(histogram, dtype=np.float32).reshape(16, 16).sum(axis=1)
            histogram_features.extend(bins.tolist())

        resized = image.resize((8, 8))
        color_grid = np.asarray(resized, dtype=np.float32).reshape(-1)
        features = np.array(histogram_features + color_grid.tolist(), dtype=np.float32)
        norm = np.linalg.norm(features)
        if norm == 0:
            return features.tolist()
        return (features / norm).tolist()


def cosine_similarity(left: list[float], right: list[float]) -> float:
    if not left or not right or len(left) != len(right):
        return 0.0
    dot = sum(a * b for a, b in zip(left, right))
    left_norm = math.sqrt(sum(a * a for a in left))
    right_norm = math.sqrt(sum(b * b for b in right))
    if left_norm == 0 or right_norm == 0:
        return 0.0
    return dot / (left_norm * right_norm)


class VectorIndex:
    def __init__(self) -> None:
        self.embedding_service = ImageEmbeddingService()

    def add_frame(self, video_id: str, timestamp_seconds: int, frame_path: Path) -> None:
        embedding = self.embedding_service.embed_image(frame_path)
        with connect() as connection:
            connection.execute(
                """
                INSERT INTO frames(video_id, timestamp_seconds, frame_path, embedding)
                VALUES (?, ?, ?, ?)
                """,
                (video_id, timestamp_seconds, str(frame_path), json_dumps(embedding)),
            )

    def search_image(self, image_path: Path, limit: int = 10) -> list[dict]:
        query_embedding = self.embedding_service.embed_image(image_path)
        with connect() as connection:
            rows = connection.execute(
                """
                SELECT f.*, v.title, v.url, v.thumbnail_url
                FROM frames f
                JOIN videos v ON v.video_id = f.video_id
                """
            ).fetchall()

        results = []
        for row in rows_to_dicts(rows):
            embedding = json_loads(row.get("embedding"), [])
            score = cosine_similarity(query_embedding, embedding)
            if score <= 0:
                continue
            timestamp = row["timestamp_seconds"]
            results.append(
                {
                    "video_id": row["video_id"],
                    "title": row.get("title") or row["video_id"],
                    "url": row["url"],
                    "timestamp_seconds": timestamp,
                    "youtube_timestamp_url": youtube_watch_url(row["video_id"], timestamp),
                    "score": score,
                    "bm25_score": None,
                    "vector_score": score,
                    "source_type": "frame",
                    "snippet": f"Visual match at {timestamp}s",
                    "thumbnail_url": row.get("thumbnail_url", ""),
                    "frame_path": row.get("frame_path", ""),
                    "frame_url": f"/api/frames/{row['id']}",
                }
            )

        results.sort(key=lambda item: item["score"], reverse=True)
        return self._collapse_near_duplicates(results)[:limit]

    def _collapse_near_duplicates(self, results: list[dict], window_seconds: int = 10) -> list[dict]:
        accepted: list[dict] = []
        for result in results:
            too_close = any(
                existing["video_id"] == result["video_id"]
                and abs((existing.get("timestamp_seconds") or 0) - (result.get("timestamp_seconds") or 0))
                <= window_seconds
                for existing in accepted
            )
            if not too_close:
                accepted.append(result)
        return accepted
