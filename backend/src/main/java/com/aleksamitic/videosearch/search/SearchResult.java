package com.aleksamitic.videosearch.search;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SearchResult(
        @JsonProperty("video_id") String videoId,
        String title,
        @JsonProperty("source_type") String sourceType,
        @JsonProperty("source_id") String sourceId,
        @JsonProperty("timestamp_seconds") Integer timestampSeconds,
        String snippet,
        @JsonProperty("full_text") String fullText,
        float score,
        @JsonProperty("youtube_timestamp_url") String youtubeTimestampUrl,
        @JsonProperty("thumbnail_url") String thumbnailUrl,
        @JsonProperty("matched_terms") List<String> matchedTerms
) {
}
