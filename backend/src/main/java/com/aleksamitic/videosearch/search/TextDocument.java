package com.aleksamitic.videosearch.search;

public record TextDocument(
        String videoId,
        String sourceType,
        String sourceId,
        Integer timestampSeconds,
        String title,
        String content,
        String thumbnailUrl
) {
}
