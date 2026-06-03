package com.aleksamitic.videosearch.api;

import com.aleksamitic.videosearch.frames.FrameExtractionService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
public class FrameController {
    private final FrameExtractionService frameExtractionService;

    public FrameController(FrameExtractionService frameExtractionService) {
        this.frameExtractionService = frameExtractionService;
    }

    @GetMapping("/api/frames/{frameId}")
    public ResponseEntity<Resource> frame(@PathVariable long frameId) {
        Path path = frameExtractionService.framePath(frameId);
        if (path == null || !Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Frame not found");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(new FileSystemResource(path));
    }
}
