package com.aleksamitic.videosearch.api;

import com.aleksamitic.videosearch.video.VideoImportService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ImportController {
    private final VideoImportService videoImportService;

    public ImportController(VideoImportService videoImportService) {
        this.videoImportService = videoImportService;
    }

    @PostMapping("/api/videos/import")
    public Map<String, Object> importVideos(@RequestBody ImportRequest request) {
        return Map.of("results", videoImportService.importUrls(request.urls() == null ? List.of() : request.urls()));
    }

    public record ImportRequest(List<String> urls) {
    }
}
