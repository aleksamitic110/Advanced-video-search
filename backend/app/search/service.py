from pathlib import Path

from ..indexing.text_index import BM25Index
from ..indexing.vector_index import VectorIndex


class SearchService:
    def __init__(self) -> None:
        self.text_index = BM25Index()
        self.vector_index = VectorIndex()

    def text_search(self, query: str, fields: list[str], limit: int) -> list[dict]:
        return self.text_index.search(query=query, fields=fields, limit=limit)

    def image_search(self, image_path: Path, limit: int) -> list[dict]:
        return self.vector_index.search_image(image_path=image_path, limit=limit)

    def hybrid_search(
        self,
        query: str | None,
        image_path: Path | None,
        text_weight: float,
        vector_weight: float,
        limit: int,
    ) -> list[dict]:
        text_results = self.text_search(query, ["title", "description", "comments", "transcript"], limit * 3) if query else []
        vector_results = self.image_search(image_path, limit * 3) if image_path else []

        max_bm25 = max((result["bm25_score"] or 0 for result in text_results), default=1.0)
        max_vector = max((result["vector_score"] or 0 for result in vector_results), default=1.0)

        merged: dict[tuple[str, int | None], dict] = {}
        for result in text_results:
            key = (result["video_id"], result.get("timestamp_seconds"))
            normalized = (result["bm25_score"] or 0) / max(max_bm25, 0.000001)
            merged[key] = {
                **result,
                "bm25_score": normalized,
                "score": normalized * text_weight,
            }

        for result in vector_results:
            key = self._nearest_existing_key(merged, result) or (result["video_id"], result.get("timestamp_seconds"))
            normalized = (result["vector_score"] or 0) / max(max_vector, 0.000001)
            if key in merged:
                merged[key]["vector_score"] = normalized
                merged[key]["score"] += normalized * vector_weight
                if result.get("frame_path"):
                    merged[key]["frame_path"] = result["frame_path"]
                if result.get("timestamp_seconds") is not None:
                    merged[key]["timestamp_seconds"] = result["timestamp_seconds"]
                    merged[key]["youtube_timestamp_url"] = result["youtube_timestamp_url"]
                merged[key]["source_type"] = "hybrid"
            else:
                merged[key] = {
                    **result,
                    "vector_score": normalized,
                    "score": normalized * vector_weight,
                }

        results = list(merged.values())
        results.sort(key=lambda item: item["score"], reverse=True)
        return results[:limit]

    def _nearest_existing_key(self, merged: dict[tuple[str, int | None], dict], result: dict) -> tuple[str, int | None] | None:
        timestamp = result.get("timestamp_seconds")
        if timestamp is None:
            return None
        for key, existing in merged.items():
            if existing["video_id"] != result["video_id"]:
                continue
            existing_timestamp = existing.get("timestamp_seconds")
            if existing_timestamp is not None and abs(existing_timestamp - timestamp) <= 30:
                return key
        return None
