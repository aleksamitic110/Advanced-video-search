package com.aleksamitic.videosearch.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class YouTubeClient {
    private static final String API_BASE = "https://www.googleapis.com/youtube/v3";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    public YouTubeClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public YouTubeMetadata fetchMetadata(String videoId, String apiKey) {
        requireApiKey(apiKey);
        JsonNode root = getJson(API_BASE + "/videos?part=snippet,contentDetails&id=" + encode(videoId) + "&key=" + encode(apiKey));
        JsonNode items = root.path("items");
        if (!items.isArray() || items.isEmpty()) {
            throw new IllegalArgumentException("YouTube video not found: " + videoId);
        }

        JsonNode item = items.get(0);
        JsonNode snippet = item.path("snippet");
        JsonNode thumbnails = snippet.path("thumbnails");
        String thumbnail = firstThumbnail(thumbnails);
        return new YouTubeMetadata(
                videoId,
                text(snippet.path("title")),
                text(snippet.path("description")),
                thumbnail,
                text(snippet.path("channelTitle")),
                text(item.path("contentDetails").path("duration"))
        );
    }

    public List<String> fetchComments(String videoId, String apiKey, int maxPages) {
        requireApiKey(apiKey);
        List<String> comments = new ArrayList<>();
        String pageToken = "";
        for (int page = 0; page < maxPages; page++) {
            String url = API_BASE + "/commentThreads?part=snippet&textFormat=plainText&maxResults=100"
                    + "&videoId=" + encode(videoId)
                    + "&key=" + encode(apiKey)
                    + (pageToken.isBlank() ? "" : "&pageToken=" + encode(pageToken));
            JsonNode root = getJsonAllowingDisabledComments(url);
            if (root == null) {
                return comments;
            }
            for (JsonNode item : root.path("items")) {
                String comment = text(item.path("snippet").path("topLevelComment").path("snippet").path("textDisplay"));
                if (!comment.isBlank()) {
                    comments.add(comment);
                }
            }
            pageToken = text(root.path("nextPageToken"));
            if (pageToken.isBlank()) {
                break;
            }
        }
        return comments;
    }

    public List<TranscriptChunk> fetchTranscriptChunks(String videoId) {
        for (String lang : List.of("en", "sr", "hr", "bs")) {
            List<TranscriptChunk> chunks = fetchTranscriptForLanguage(videoId, lang);
            if (!chunks.isEmpty()) {
                return chunks;
            }
        }
        return List.of();
    }

    private List<TranscriptChunk> fetchTranscriptForLanguage(String videoId, String language) {
        String url = "https://www.youtube.com/api/timedtext?v=" + encode(videoId)
                + "&lang=" + encode(language)
                + "&fmt=json3";
        try {
            JsonNode root = getJson(url);
            List<TranscriptChunk> chunks = new ArrayList<>();
            List<String> current = new ArrayList<>();
            int currentStart = -1;
            for (JsonNode event : root.path("events")) {
                if (!event.has("segs")) {
                    continue;
                }
                int startSeconds = event.path("tStartMs").asInt(0) / 1000;
                if (currentStart < 0) {
                    currentStart = startSeconds;
                }
                for (JsonNode segment : event.path("segs")) {
                    String text = text(segment.path("utf8")).replace('\n', ' ').trim();
                    if (!text.isBlank()) {
                        current.add(text);
                    }
                }
                if (startSeconds - currentStart >= 30 && !current.isEmpty()) {
                    chunks.add(new TranscriptChunk(currentStart, String.join(" ", current)));
                    current.clear();
                    currentStart = -1;
                }
            }
            if (!current.isEmpty() && currentStart >= 0) {
                chunks.add(new TranscriptChunk(currentStart, String.join(" ", current)));
            }
            return chunks;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private JsonNode getJson(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("YouTube request failed with HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not parse YouTube response.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("YouTube request was interrupted.", exception);
        }
    }

    private JsonNode getJsonAllowingDisabledComments(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 403) {
                return null;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("YouTube comments request failed with HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not parse YouTube comments response.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("YouTube comments request was interrupted.", exception);
        }
    }

    private void requireApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("YouTube API key is required for metadata and comments.");
        }
    }

    private String firstThumbnail(JsonNode thumbnails) {
        for (String key : List.of("maxres", "high", "medium", "default")) {
            String url = text(thumbnails.path(key).path("url"));
            if (!url.isBlank()) {
                return url;
            }
        }
        return "";
    }

    private String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? "" : node.asText("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
