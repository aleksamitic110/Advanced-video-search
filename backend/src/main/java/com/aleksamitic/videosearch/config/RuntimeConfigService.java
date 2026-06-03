package com.aleksamitic.videosearch.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RuntimeConfigService {
    private final JdbcTemplate jdbcTemplate;
    private final AppProperties appProperties;

    public RuntimeConfigService(JdbcTemplate jdbcTemplate, AppProperties appProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.appProperties = appProperties;
    }

    public String youtubeApiKey() {
        return read("youtube_api_key", appProperties.getYoutubeApiKey());
    }

    public int frameIntervalSeconds() {
        return Integer.parseInt(read("frame_interval_seconds", String.valueOf(appProperties.getFrameIntervalSeconds())));
    }

    public boolean enableYtdlp() {
        return Boolean.parseBoolean(read("enable_ytdlp", "true"));
    }

    public String dataDir() {
        return appProperties.getDataDir();
    }

    public String embeddingServiceUrl() {
        return appProperties.getEmbeddingServiceUrl();
    }

    public void updateYoutubeApiKey(String value) {
        write("youtube_api_key", value.trim());
    }

    public void updateFrameIntervalSeconds(int value) {
        write("frame_interval_seconds", String.valueOf(value));
    }

    public void updateEnableYtdlp(boolean value) {
        write("enable_ytdlp", String.valueOf(value));
    }

    private String read(String key, String fallback) {
        return jdbcTemplate.query(
                "SELECT value FROM runtime_config WHERE key = ?",
                rs -> rs.next() ? rs.getString("value") : fallback,
                key
        );
    }

    private void write(String key, String value) {
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
}
