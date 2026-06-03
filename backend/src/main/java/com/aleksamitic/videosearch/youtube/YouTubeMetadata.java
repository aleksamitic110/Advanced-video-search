package com.aleksamitic.videosearch.youtube;

public record YouTubeMetadata(
        String videoId,
        String title,
        String description,
        String thumbnailUrl,
        String channelTitle,
        String duration
) {
}
