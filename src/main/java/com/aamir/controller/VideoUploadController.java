package com.aamir.controller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/videos")
public class VideoUploadController {

    private final Path storageLocation = Paths.get("videos");

    // Windows path (will be used only if OS is Windows)
    private static final String WINDOWS_FFMPEG =
            "C:\\ffmpeg-master-latest-win64-gpl-shared\\bin\\ffmpeg.exe";

    @PostMapping("/upload")
    public ResponseEntity<String> handleUpload(
            @RequestParam("videoFile") MultipartFile file)
            throws IOException, InterruptedException {

        // create storage folder
        if (Files.notExists(storageLocation)) {
            Files.createDirectories(storageLocation);
        }

        // save uploaded file
        Path inputFile = storageLocation.resolve(
                Objects.requireNonNull(file.getOriginalFilename()));

        Files.copy(file.getInputStream(), inputFile,
                StandardCopyOption.REPLACE_EXISTING);

        // create HLS output folder
        Path outputDir = storageLocation.resolve("hls")
                .resolve(UUID.randomUUID().toString());

        Files.createDirectories(outputDir);

        // run ffmpeg
        ProcessBuilder pb = getProcessBuilder(inputFile, outputDir);

        pb.inheritIO();

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            return ResponseEntity.status(500)
                    .body("FFmpeg failed with exit code: " + exitCode);
        }

        String uuid = outputDir.getFileName().toString();

        String hlsUrl = "/hls/" + uuid + "/index.m3u8";

        return ResponseEntity.ok(hlsUrl);
    }

    private ProcessBuilder getProcessBuilder(Path inputFile, Path outputDir) {

        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().contains("win");

        String ffmpeg = isWindows ? WINDOWS_FFMPEG : "ffmpeg";

        Path outputFile = outputDir.resolve("index.m3u8");
        Path segmentFile = outputDir.resolve("segment_%03d.ts");

        return new ProcessBuilder(
                ffmpeg,
                "-i", inputFile.toAbsolutePath().toString(),

                "-c:v", "libx264",
                "-c:a", "aac",

                "-g", "48",
                "-keyint_min", "48",
                "-sc_threshold", "0",

                "-hls_time", "2",
                "-hls_list_size", "0",

                "-hls_segment_filename",
                segmentFile.toAbsolutePath().toString(),

                "-f", "hls",
                outputFile.toAbsolutePath().toString()
        );
    }
}