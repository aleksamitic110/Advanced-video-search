package com.aleksamitic.videosearch.embedding;

import com.aleksamitic.videosearch.config.RuntimeConfigService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class ImageEmbeddingClient {
    private final RuntimeConfigService runtimeConfigService;
    private final RestTemplate restTemplate = new RestTemplate();

    public ImageEmbeddingClient(RuntimeConfigService runtimeConfigService) {
        this.runtimeConfigService = runtimeConfigService;
    }

    public List<Double> embedImage(Path imagePath) {
        try {
            return embedImage(Files.readAllBytes(imagePath), imagePath.getFileName().toString());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read image file for embedding.", exception);
        }
    }

    public List<Double> embedImage(byte[] imageBytes, String filename) {
        ByteArrayResource imageResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return filename == null || filename.isBlank() ? "image.jpg" : filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", imageResource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<EmbeddingResponse> response = restTemplate.postForEntity(
                runtimeConfigService.embeddingServiceUrl() + "/api/embed/image",
                new HttpEntity<>(body, headers),
                EmbeddingResponse.class
        );

        EmbeddingResponse embeddingResponse = response.getBody();
        if (embeddingResponse == null || embeddingResponse.embedding() == null || embeddingResponse.embedding().isEmpty()) {
            throw new IllegalStateException("Embedding service returned an empty embedding.");
        }
        return embeddingResponse.embedding();
    }

    public List<Double> embedText(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<EmbeddingResponse> response = restTemplate.postForEntity(
                runtimeConfigService.embeddingServiceUrl() + "/api/embed/text",
                new HttpEntity<>(Map.of("text", text), headers),
                EmbeddingResponse.class
        );

        EmbeddingResponse embeddingResponse = response.getBody();
        if (embeddingResponse == null || embeddingResponse.embedding() == null || embeddingResponse.embedding().isEmpty()) {
            throw new IllegalStateException("Embedding service returned an empty text embedding.");
        }
        return embeddingResponse.embedding();
    }

    public String toPgVector(List<Double> embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < embedding.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(embedding.get(index));
        }
        return builder.append(']').toString();
    }

    private record EmbeddingResponse(
            List<Double> embedding,
            int dimensions,
            String model
    ) {
    }

}
