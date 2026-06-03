package com.aleksamitic.videosearch.video;

import com.aleksamitic.videosearch.config.RuntimeConfigService;
import com.aleksamitic.videosearch.frames.FrameExtractionService;
import com.aleksamitic.videosearch.search.LuceneTextSearchService;
import com.aleksamitic.videosearch.search.TextDocument;
import com.aleksamitic.videosearch.util.YouTubeUrls;
import com.aleksamitic.videosearch.youtube.TranscriptChunk;
import com.aleksamitic.videosearch.youtube.YouTubeClient;
import com.aleksamitic.videosearch.youtube.YouTubeMetadata;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class VideoImportService {
    private final JdbcTemplate jdbcTemplate;
    private final RuntimeConfigService runtimeConfigService;
    private final YouTubeClient youTubeClient;
    private final LuceneTextSearchService luceneTextSearchService;
    private final FrameExtractionService frameExtractionService;

    public VideoImportService(
            JdbcTemplate jdbcTemplate,
            RuntimeConfigService runtimeConfigService,
            YouTubeClient youTubeClient,
            LuceneTextSearchService luceneTextSearchService,
            FrameExtractionService frameExtractionService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.runtimeConfigService = runtimeConfigService;
        this.youTubeClient = youTubeClient;
        this.luceneTextSearchService = luceneTextSearchService;
        this.frameExtractionService = frameExtractionService;
    }

    public List<ImportResult> importUrls(List<String> urls) {
        List<ImportResult> results = new ArrayList<>();
        for (String url : urls) {
            String videoId = YouTubeUrls.parseVideoId(url);
            if (videoId.isBlank()) {
                results.add(new ImportResult("", url, "failed", "Invalid YouTube URL"));
                continue;
            }
            try {
                importVideo(videoId, YouTubeUrls.watchUrl(videoId, null));
                results.add(new ImportResult(videoId, YouTubeUrls.watchUrl(videoId, null), "indexed", ""));
            } catch (RuntimeException exception) {
                markFailed(videoId, YouTubeUrls.watchUrl(videoId, null), exception.getMessage());
                results.add(new ImportResult(videoId, YouTubeUrls.watchUrl(videoId, null), "failed", exception.getMessage()));
            }
        }
        return results;
    }

    private void importVideo(String videoId, String url) {
        upsertVideo(videoId, url, "fetching_metadata");
        String apiKey = runtimeConfigService.youtubeApiKey();
        YouTubeMetadata metadata = youTubeClient.fetchMetadata(videoId, apiKey);
        List<String> comments = youTubeClient.fetchComments(videoId, apiKey, 3);
        List<TranscriptChunk> transcriptChunks = youTubeClient.fetchTranscriptChunks(videoId);

        List<TextDocument> textDocuments = new ArrayList<>();
        addDocument(textDocuments, videoId, "metadata", "title", null, metadata.title(), metadata.title(), metadata.thumbnailUrl());
        addDocument(textDocuments, videoId, "description", "description", null, metadata.title(), metadata.description(), metadata.thumbnailUrl());
        for (int index = 0; index < comments.size(); index++) {
            addDocument(textDocuments, videoId, "comment", String.valueOf(index), null, metadata.title(), comments.get(index), metadata.thumbnailUrl());
        }
        for (int index = 0; index < transcriptChunks.size(); index++) {
            TranscriptChunk chunk = transcriptChunks.get(index);
            addDocument(textDocuments, videoId, "transcript", String.valueOf(index), chunk.timestampSeconds(), metadata.title(), chunk.content(), metadata.thumbnailUrl());
        }

        luceneTextSearchService.replaceVideoDocuments(videoId, textDocuments);
        int frameCount = 0;
        if (runtimeConfigService.enableYtdlp()) {
            updateStatus(videoId, "extracting_frames");
            frameCount = frameExtractionService.extractFrames(videoId, url);
        }
        jdbcTemplate.update(
                """
                UPDATE videos
                SET title = ?,
                    description = ?,
                    thumbnail_url = ?,
                    channel_title = ?,
                    duration = ?,
                    status = 'indexed',
                    error = '',
                    comment_count = ?,
                    transcript_count = ?,
                    text_doc_count = ?,
                    frame_count = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE video_id = ?
                """,
                metadata.title(),
                metadata.description(),
                metadata.thumbnailUrl(),
                metadata.channelTitle(),
                metadata.duration(),
                comments.size(),
                transcriptChunks.size(),
                textDocuments.size(),
                frameCount,
                videoId
        );
    }

    private void addDocument(
            List<TextDocument> documents,
            String videoId,
            String sourceType,
            String sourceId,
            Integer timestampSeconds,
            String title,
            String content,
            String thumbnailUrl
    ) {
        if (content != null && !content.isBlank()) {
            documents.add(new TextDocument(videoId, sourceType, sourceId, timestampSeconds, title, content, thumbnailUrl));
        }
    }

    private void upsertVideo(String videoId, String url, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO videos(video_id, url, status)
                VALUES (?, ?, ?)
                ON CONFLICT (video_id) DO UPDATE SET
                    url = EXCLUDED.url,
                    status = EXCLUDED.status,
                    error = '',
                    updated_at = CURRENT_TIMESTAMP
                """,
                videoId,
                url,
                status
        );
    }

    private void markFailed(String videoId, String url, String error) {
        jdbcTemplate.update(
                """
                INSERT INTO videos(video_id, url, status, error)
                VALUES (?, ?, 'failed', ?)
                ON CONFLICT (video_id) DO UPDATE SET
                    status = 'failed',
                    error = EXCLUDED.error,
                    updated_at = CURRENT_TIMESTAMP
                """,
                videoId,
                url,
                error == null ? "" : error
        );
    }

    private void updateStatus(String videoId, String status) {
        jdbcTemplate.update(
                "UPDATE videos SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE video_id = ?",
                status,
                videoId
        );
    }

    public record ImportResult(
            @JsonProperty("video_id") String videoId,
            String url,
            String status,
            String error
    ) {
    }
}
