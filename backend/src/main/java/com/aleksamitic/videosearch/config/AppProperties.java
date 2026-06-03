package com.aleksamitic.videosearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String dataDir;
    private String youtubeApiKey;
    private int frameIntervalSeconds;
    private String embeddingServiceUrl;

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getYoutubeApiKey() {
        return youtubeApiKey;
    }

    public void setYoutubeApiKey(String youtubeApiKey) {
        this.youtubeApiKey = youtubeApiKey;
    }

    public int getFrameIntervalSeconds() {
        return frameIntervalSeconds;
    }

    public void setFrameIntervalSeconds(int frameIntervalSeconds) {
        this.frameIntervalSeconds = frameIntervalSeconds;
    }

    public String getEmbeddingServiceUrl() {
        return embeddingServiceUrl;
    }

    public void setEmbeddingServiceUrl(String embeddingServiceUrl) {
        this.embeddingServiceUrl = embeddingServiceUrl;
    }
}
