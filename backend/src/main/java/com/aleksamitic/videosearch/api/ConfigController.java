package com.aleksamitic.videosearch.api;

import com.aleksamitic.videosearch.config.RuntimeConfigService;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ConfigController {
    private final RuntimeConfigService runtimeConfigService;

    public ConfigController(RuntimeConfigService runtimeConfigService) {
        this.runtimeConfigService = runtimeConfigService;
    }

    @GetMapping("/api/config")
    public Map<String, Object> currentConfig() {
        String apiKey = runtimeConfigService.youtubeApiKey();

        return Map.of(
                "youtube_api_key_configured", apiKey != null && !apiKey.isBlank(),
                "frame_interval_seconds", runtimeConfigService.frameIntervalSeconds(),
                "enable_ytdlp", runtimeConfigService.enableYtdlp(),
                "data_dir", runtimeConfigService.dataDir()
        );
    }

    @PostMapping("/api/config")
    public Map<String, Object> updateConfig(@RequestBody RuntimeConfigRequest request) {
        if (request.youtubeApiKey() != null) {
            runtimeConfigService.updateYoutubeApiKey(request.youtubeApiKey());
        }
        if (request.frameIntervalSeconds() != null) {
            runtimeConfigService.updateFrameIntervalSeconds(request.frameIntervalSeconds());
        }
        if (request.enableYtdlp() != null) {
            runtimeConfigService.updateEnableYtdlp(request.enableYtdlp());
        }
        return currentConfig();
    }

    public record RuntimeConfigRequest(
            @JsonProperty("youtube_api_key") String youtubeApiKey,
            @JsonProperty("frame_interval_seconds") @Min(1) Integer frameIntervalSeconds,
            @JsonProperty("enable_ytdlp") Boolean enableYtdlp
    ) {
    }
}
