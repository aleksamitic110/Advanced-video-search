package com.aleksamitic.videosearch.frames;

import com.aleksamitic.videosearch.config.RuntimeConfigService;
import com.aleksamitic.videosearch.embedding.ImageEmbeddingClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class FrameExtractionService {
    private final RuntimeConfigService runtimeConfigService;
    private final JdbcTemplate jdbcTemplate;
    private final ImageEmbeddingClient imageEmbeddingClient;

    public FrameExtractionService(
            RuntimeConfigService runtimeConfigService,
            JdbcTemplate jdbcTemplate,
            ImageEmbeddingClient imageEmbeddingClient
    ) {
        this.runtimeConfigService = runtimeConfigService;
        this.jdbcTemplate = jdbcTemplate;
        this.imageEmbeddingClient = imageEmbeddingClient;
    }

    public int extractFrames(String videoId, String url) {
        Path videoPath = downloadVideo(videoId, url);
        Path frameDirectory = framesDirectory(videoId);
        clearFrames(videoId, frameDirectory);
        runCommand(List.of(
                "ffmpeg",
                "-y",
                "-i",
                videoPath.toString(),
                "-vf",
                "fps=1/" + runtimeConfigService.frameIntervalSeconds(),
                "-q:v",
                "3",
                frameDirectory.resolve("frame_%05d.jpg").toString()
        ), "ffmpeg frame extraction failed");

        List<Path> frames = listFrames(frameDirectory);
        int count = 0;
        for (Path frame : frames) {
            int timestamp = count * runtimeConfigService.frameIntervalSeconds();
            String embedding = imageEmbeddingClient.toPgVector(imageEmbeddingClient.embedImage(frame));
            jdbcTemplate.update(
                    """
                    INSERT INTO frames(video_id, timestamp_seconds, frame_path, embedding)
                    VALUES (?, ?, ?, ?::vector)
                    """,
                    videoId,
                    timestamp,
                    frame.toString(),
                    embedding
            );
            count++;
        }
        return count;
    }

    public Path framePath(long frameId) {
        return jdbcTemplate.query(
                "SELECT frame_path FROM frames WHERE id = ?",
                rs -> rs.next() ? Path.of(rs.getString("frame_path")) : null,
                frameId
        );
    }

    public void deleteVideoMedia(String videoId) {
        Path frameDirectory = framesDirectory(videoId);
        try {
            if (Files.exists(frameDirectory)) {
                try (Stream<Path> paths = Files.walk(frameDirectory)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(this::deletePath);
                }
            }
            if (Files.exists(videosDirectory())) {
                try (Stream<Path> paths = Files.list(videosDirectory())) {
                    paths
                            .filter(path -> path.getFileName().toString().startsWith(videoId + "."))
                            .forEach(this::deletePath);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete media files for video " + videoId, exception);
        }
    }

    private Path downloadVideo(String videoId, String url) {
        Path existing = findDownloadedVideo(videoId);
        if (existing != null) {
            return existing;
        }
        Path outputTemplate = videosDirectory().resolve(videoId + ".%(ext)s");
        runCommand(List.of(
                "yt-dlp",
                "--no-playlist",
                "-f",
                "bv*[height<=480][ext=mp4]+ba[ext=m4a]/b[height<=480][ext=mp4]/bv*[height<=480]+ba/b[height<=480]/best[height<=480]/best",
                "--merge-output-format",
                "mp4",
                "-o",
                outputTemplate.toString(),
                url
        ), "yt-dlp download failed");
        Path downloaded = findDownloadedVideo(videoId);
        if (downloaded == null) {
            throw new IllegalStateException("yt-dlp finished but no downloaded video file was found.");
        }
        return downloaded;
    }

    private void clearFrames(String videoId, Path frameDirectory) {
        try {
            jdbcTemplate.update("DELETE FROM frames WHERE video_id = ?", videoId);
            if (Files.exists(frameDirectory)) {
                try (Stream<Path> paths = Files.walk(frameDirectory)) {
                    paths.sorted(Comparator.reverseOrder())
                            .filter(path -> !path.equals(frameDirectory))
                            .forEach(this::deletePath);
                }
            }
            Files.createDirectories(frameDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not prepare frame directory.", exception);
        }
    }

    private void runCommand(List<String> command, String label) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(label + ": " + output.strip());
            }
        } catch (IOException exception) {
            throw new IllegalStateException(label + ": " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(label + ": interrupted", exception);
        }
    }

    private Path findDownloadedVideo(String videoId) {
        try {
            Files.createDirectories(videosDirectory());
            try (Stream<Path> paths = Files.list(videosDirectory())) {
                return paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().startsWith(videoId + "."))
                        .filter(path -> !path.getFileName().toString().contains(".part"))
                        .findFirst()
                        .orElse(null);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect downloaded videos.", exception);
        }
    }

    private List<Path> listFrames(Path frameDirectory) {
        try (Stream<Path> paths = Files.list(frameDirectory)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".jpg"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not list extracted frames.", exception);
        }
    }

    private void deletePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete " + path, exception);
        }
    }

    private Path videosDirectory() {
        return Path.of(runtimeConfigService.dataDir(), "videos");
    }

    private Path framesDirectory(String videoId) {
        return Path.of(runtimeConfigService.dataDir(), "frames", videoId);
    }
}
