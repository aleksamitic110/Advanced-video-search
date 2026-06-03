package com.aleksamitic.videosearch.api;

import com.aleksamitic.videosearch.frames.FrameExtractionService;
import com.aleksamitic.videosearch.search.LuceneTextSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class VideoController {
    private final JdbcTemplate jdbcTemplate;
    private final LuceneTextSearchService luceneTextSearchService;
    private final FrameExtractionService frameExtractionService;

    public VideoController(
            JdbcTemplate jdbcTemplate,
            LuceneTextSearchService luceneTextSearchService,
            FrameExtractionService frameExtractionService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.luceneTextSearchService = luceneTextSearchService;
        this.frameExtractionService = frameExtractionService;
    }

    @GetMapping("/api/videos")
    public Map<String, Object> listVideos() {
        List<Map<String, Object>> videos = jdbcTemplate.query(
                """
                SELECT video_id, url, title, thumbnail_url, channel_title, duration, status, error,
                       comment_count, transcript_count, frame_count, text_doc_count, created_at, updated_at
                FROM videos
                ORDER BY updated_at DESC
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("video_id", rs.getString("video_id"));
                    row.put("url", rs.getString("url"));
                    row.put("title", rs.getString("title"));
                    row.put("thumbnail_url", rs.getString("thumbnail_url"));
                    row.put("channel_title", rs.getString("channel_title"));
                    row.put("duration", rs.getString("duration"));
                    row.put("status", rs.getString("status"));
                    row.put("error", rs.getString("error"));
                    row.put("comment_count", rs.getInt("comment_count"));
                    row.put("transcript_count", rs.getInt("transcript_count"));
                    row.put("frame_count", rs.getInt("frame_count"));
                    row.put("text_doc_count", rs.getInt("text_doc_count"));
                    row.put("created_at", rs.getTimestamp("created_at").toString());
                    row.put("updated_at", rs.getTimestamp("updated_at").toString());
                    return row;
                }
        );
        return Map.of("videos", videos);
    }

    @DeleteMapping("/api/videos/{videoId}")
    public Map<String, Object> deleteVideo(@PathVariable String videoId) {
        int deleted = jdbcTemplate.update("DELETE FROM videos WHERE video_id = ?", videoId);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found");
        }
        luceneTextSearchService.deleteVideo(videoId);
        frameExtractionService.deleteVideoMedia(videoId);
        return Map.of("deleted", true, "video_id", videoId);
    }
}
