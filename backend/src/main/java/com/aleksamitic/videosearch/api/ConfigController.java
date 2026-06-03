package com.aleksamitic.videosearch.api;

import com.aleksamitic.videosearch.config.AppProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ConfigController {
    private final JdbcTemplate jdbcTemplate;
    private final AppProperties appProperties;

    public ConfigController(JdbcTemplate jdbcTemplate, AppProperties appProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.appProperties = appProperties;
    }

    @GetMapping("/api/config")
    public Map<String, Object> currentConfig() {
        String apiKey = readConfig("youtube_api_key", appProperties.getYoutubeApiKey());
        int frameInterval = Integer.parseInt(readConfig(
                "frame_interval_seconds",
                String.valueOf(appProperties.getFrameIntervalSeconds())
        ));
        boolean enableYtdlp = Boolean.parseBoolean(readConfig("enable_ytdlp", "true"));

        return Map.of(
                "youtube_api_key_configured", apiKey != null && !apiKey.isBlank(),
                "frame_interval_seconds", frameInterval,
                "enable_ytdlp", enableYtdlp,
                "data_dir", appProperties.getDataDir()
        );
    }

    @PostMapping("/api/config")
    public Map<String, Object> updateConfig(@RequestBody RuntimeConfigRequest request) {
        if (request.youtubeApiKey() != null) {
            writeConfig("youtube_api_key", request.youtubeApiKey().trim());
        }
        if (request.frameIntervalSeconds() != null) {
            writeConfig("frame_interval_seconds", String.valueOf(request.frameIntervalSeconds()));
        }
        if (request.enableYtdlp() != null) {
            writeConfig("enable_ytdlp", request.enableYtdlp().toString());
        }
        return currentConfig();
    }

    private String readConfig(String key, String fallback) {
        return jdbcTemplate.query(
                "SELECT value FROM runtime_config WHERE key = ?",
                rs -> rs.next() ? rs.getString("value") : fallback,
                key
        );
    }

    private void writeConfig(String key, String value) {
        jdbcTemplate.update(
                """
                INSERT INTO runtime_config(key, value)
                VALUES (?, ?)
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = CURRENT_TIMESTAMP
                """,
                key,
                value
        );
    }

    public record RuntimeConfigRequest(
            @JsonProperty("youtube_api_key") String youtubeApiKey,
            @JsonProperty("frame_interval_seconds") @Min(1) Integer frameIntervalSeconds,
            @JsonProperty("enable_ytdlp") Boolean enableYtdlp
    ) {
    }
}
