package com.aleksamitic.videosearch.util;

import java.net.URI;
import java.util.Arrays;

public final class YouTubeUrls {
    private YouTubeUrls() {
    }

    public static String parseVideoId(String url) {
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().replaceFirst("^www\\.", "");
            if (host.equals("youtu.be")) {
                return cleanVideoId(uri.getPath().replaceFirst("^/", ""));
            }
            if (host.equals("youtube.com") || host.equals("m.youtube.com")) {
                if (uri.getPath().equals("/watch")) {
                    return Arrays.stream((uri.getQuery() == null ? "" : uri.getQuery()).split("&"))
                            .filter(part -> part.startsWith("v="))
                            .map(part -> part.substring(2))
                            .map(YouTubeUrls::cleanVideoId)
                            .filter(id -> !id.isBlank())
                            .findFirst()
                            .orElse("");
                }
                if (uri.getPath().startsWith("/shorts/")) {
                    return cleanVideoId(uri.getPath().substring("/shorts/".length()));
                }
            }
        } catch (IllegalArgumentException ignored) {
            return "";
        }
        return "";
    }

    public static String watchUrl(String videoId, Integer timestampSeconds) {
        String url = "https://www.youtube.com/watch?v=" + videoId;
        return timestampSeconds == null ? url : url + "&t=" + timestampSeconds + "s";
    }

    private static String cleanVideoId(String value) {
        int separator = value.indexOf('?');
        String cleaned = separator >= 0 ? value.substring(0, separator) : value;
        return cleaned.replaceAll("[^A-Za-z0-9_-]", "");
    }
}
