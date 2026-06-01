import math
from collections import Counter
from dataclasses import dataclass

from ..database import connect, rows_to_dicts
from ..utils import make_snippet, tokenize, youtube_watch_url


@dataclass
class BM25Hit:
    doc: dict
    score: float


class BM25Index:
    def __init__(self, k1: float = 1.5, b: float = 0.75) -> None:
        self.k1 = k1
        self.b = b

    def search(self, query: str, fields: list[str], limit: int) -> list[dict]:
        source_map = {
            "title": "metadata",
            "description": "description",
            "comments": "comment",
            "comment": "comment",
            "transcript": "transcript",
        }
        allowed_sources = {source_map[field] for field in fields if field in source_map}
        if not allowed_sources:
            allowed_sources = {"metadata", "description", "comment", "transcript"}
        with connect() as connection:
            rows = connection.execute(
                """
                SELECT td.*, v.title AS video_title, v.url, v.thumbnail_url
                FROM text_docs td
                JOIN videos v ON v.video_id = td.video_id
                WHERE td.source_type IN ({})
                """.format(",".join("?" for _ in allowed_sources)),
                tuple(allowed_sources),
            ).fetchall()

        docs = rows_to_dicts(rows)
        query_terms = tokenize(query)
        if not query_terms or not docs:
            return []

        doc_tokens = [tokenize(f"{doc.get('title', '')} {doc.get('content', '')}") for doc in docs]
        doc_lengths = [len(tokens) for tokens in doc_tokens]
        avg_doc_length = sum(doc_lengths) / max(len(doc_lengths), 1)
        document_frequency = Counter()
        for tokens in doc_tokens:
            document_frequency.update(set(tokens))

        hits: list[BM25Hit] = []
        total_docs = len(docs)
        for doc, tokens, doc_length in zip(docs, doc_tokens, doc_lengths):
            frequencies = Counter(tokens)
            score = 0.0
            for term in query_terms:
                tf = frequencies.get(term, 0)
                if not tf:
                    continue
                df = document_frequency.get(term, 0)
                idf = math.log(1 + (total_docs - df + 0.5) / (df + 0.5))
                denominator = tf + self.k1 * (1 - self.b + self.b * doc_length / max(avg_doc_length, 1))
                score += idf * (tf * (self.k1 + 1)) / denominator
            if score > 0:
                hits.append(BM25Hit(doc=doc, score=score))

        hits.sort(key=lambda hit: hit.score, reverse=True)
        return [self._format_hit(hit, query) for hit in hits[:limit]]

    def _format_hit(self, hit: BM25Hit, query: str) -> dict:
        doc = hit.doc
        timestamp = doc.get("timestamp_seconds")
        content = doc.get("content", "")
        matched_terms = sorted({term for term in tokenize(query) if term in set(tokenize(content))})
        source_type = doc["source_type"]
        if source_type == "comment":
            snippet = content if len(content) <= 1200 else make_snippet(content, query, 520)
        else:
            snippet = make_snippet(content, query)
        return {
            "video_id": doc["video_id"],
            "title": doc.get("video_title") or doc.get("title") or doc["video_id"],
            "url": doc["url"],
            "timestamp_seconds": timestamp,
            "youtube_timestamp_url": youtube_watch_url(doc["video_id"], timestamp),
            "score": hit.score,
            "bm25_score": hit.score,
            "vector_score": None,
            "source_type": source_type,
            "source_id": doc.get("source_id", ""),
            "snippet": snippet,
            "full_text": content,
            "matched_terms": matched_terms,
            "thumbnail_url": doc.get("thumbnail_url", ""),
            "frame_path": "",
        }
