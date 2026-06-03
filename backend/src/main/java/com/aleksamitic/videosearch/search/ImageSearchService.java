package com.aleksamitic.videosearch.search;

import com.aleksamitic.videosearch.embedding.ImageEmbeddingClient;
import com.aleksamitic.videosearch.util.YouTubeUrls;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ImageSearchService {
    private final ImageEmbeddingClient imageEmbeddingClient;
    private final JdbcTemplate jdbcTemplate;

    public ImageSearchService(ImageEmbeddingClient imageEmbeddingClient, JdbcTemplate jdbcTemplate) {
        this.imageEmbeddingClient = imageEmbeddingClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ImageSearchResult> search(byte[] imageBytes, String filename, int limit) {
        String vector = imageEmbeddingClient.toPgVector(imageEmbeddingClient.embedImage(imageBytes, filename));
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
                    return new ImageSearchResult(
                            videoId,
                            rs.getString("title"),
                            "frame",
                            String.valueOf(frameId),
                            timestamp,
                            "Visual frame match at " + timestamp + "s",
                            "Visual frame match at " + timestamp + "s",
                            score,
                            YouTubeUrls.watchUrl(videoId, timestamp),
                            rs.getString("thumbnail_url"),
                            "/api/frames/" + frameId,
                            List.of()
                    );
                },
                vector,
                vector,
                Math.max(1, limit)
        );
    }
}
