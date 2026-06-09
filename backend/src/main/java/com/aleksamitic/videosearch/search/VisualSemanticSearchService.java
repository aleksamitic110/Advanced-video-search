package com.aleksamitic.videosearch.search;

import com.aleksamitic.videosearch.embedding.ImageEmbeddingClient;
import com.aleksamitic.videosearch.util.YouTubeUrls;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class VisualSemanticSearchService {
    private final ImageEmbeddingClient imageEmbeddingClient;
    private final JdbcTemplate jdbcTemplate;

    public VisualSemanticSearchService(ImageEmbeddingClient imageEmbeddingClient, JdbcTemplate jdbcTemplate) {
        this.imageEmbeddingClient = imageEmbeddingClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ImageSearchResult> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String normalizedQuery = query.trim();
        String vector = imageEmbeddingClient.toPgVector(imageEmbeddingClient.embedText(normalizedQuery));
        List<String> queryTerms = tokenizeQuery(normalizedQuery);

        return jdbcTemplate.query(
                """
                SELECT f.id, f.video_id, f.timestamp_seconds, v.title, v.thumbnail_url,
                       (f.embedding <=> ?::vector) AS distance
                FROM frames f
                JOIN videos v ON v.video_id = f.video_id
                WHERE f.embedding IS NOT NULL
                ORDER BY f.embedding <=> ?::vector
                LIMIT ?
                """,
                (rs, rowNum) -> {
                    long frameId = rs.getLong("id");
                    String videoId = rs.getString("video_id");
                    int timestamp = rs.getInt("timestamp_seconds");
                    double distance = rs.getDouble("distance");
                    double score = 1.0 / (1.0 + distance);
                    String explanation = "Visual semantic match for \"" + normalizedQuery + "\" at " + timestamp + "s";
                    return new ImageSearchResult(
                            videoId,
                            rs.getString("title"),
                            "visual_semantic",
                            String.valueOf(frameId),
                            timestamp,
                            explanation,
                            explanation,
                            score,
                            YouTubeUrls.watchUrl(videoId, timestamp),
                            rs.getString("thumbnail_url"),
                            "/api/frames/" + frameId,
                            queryTerms
                    );
                },
                vector,
                vector,
                Math.max(1, limit)
        );
    }

    private List<String> tokenizeQuery(String queryText) {
        return List.of(queryText.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_-]+"))
                .stream()
                .filter(term -> !term.isBlank())
                .distinct()
                .toList();
    }
}
