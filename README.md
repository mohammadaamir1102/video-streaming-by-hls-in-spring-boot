# Video Streaming by HLS in Spring Boot

A complete, step-by-step project for uploading, transcoding, and streaming videos using HLS (HTTP Live Streaming) and FFmpeg, built with Spring Boot. This guide is designed for a technical blog or LinkedIn post, with detailed explanations, code snippets, and process flow.

---

## What is HLS?

**HLS (HTTP Live Streaming)** is a streaming protocol developed by Apple. It breaks video files into small segments and serves them over HTTP. The client (browser or player) downloads and plays these segments, allowing adaptive bitrate streaming and smooth playback even on unstable networks.

- **Advantages:**
  - Adaptive streaming (quality changes based on bandwidth)
  - Works over standard HTTP
  - Supported by most browsers and devices (with hls.js for non-Safari browsers)

## What is FFmpeg?

**FFmpeg** is a powerful open-source tool for processing video and audio files. In this project, FFmpeg is used to transcode uploaded videos into HLS format, generating `.m3u8` playlists and `.ts` segment files.

---

## Project Structure & Process Flow

1. **User uploads a video** via a drag-and-drop web interface (`index.html`).
2. **Backend saves the file** and uses FFmpeg to transcode it into HLS segments and a playlist (`VideoUploadController.java`).
3. **User is redirected to a player page** that streams the video using hls.js (`player.html`).
4. **All video files are stored** in a `videos/` directory, with each upload getting a unique folder.

```
[User] --(upload)--> [Spring Boot Backend] --(save file)--> [videos/] --(FFmpeg)--> [HLS segments + playlist]
   |                                                                                      |
   +-------------------(redirect to /player)----------------------------------------------+
                                                                                         |
                                                                                  [hls.js Player]
```

---

## Java Controller Code (with Explanatory Comments)

### VideoUploadController.java
```java
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

    // Directory where videos and HLS outputs are stored
    private final Path storageLocation = Paths.get("videos");

    // Windows path (will be used only if OS is Windows)
    private static final String WINDOWS_FFMPEG =
            "C:\\ffmpeg-master-latest-win64-gpl-shared\\bin\\ffmpeg.exe";

    /**
     * Handles video file upload, saves it, and transcodes to HLS using FFmpeg.
     * @param file The uploaded video file
     * @return ResponseEntity with status and HLS output path
     */
    @PostMapping("/upload")
    public ResponseEntity<String> handleUpload(
            @RequestParam("videoFile") MultipartFile file)
            throws IOException, InterruptedException {

        // 1. Create storage folder if it doesn't exist
        if (Files.notExists(storageLocation)) {
            Files.createDirectories(storageLocation);
        }

        // 2. Save the uploaded file
        Path inputFile = storageLocation.resolve(
                Objects.requireNonNull(file.getOriginalFilename()));
        Files.copy(file.getInputStream(), inputFile,
                StandardCopyOption.REPLACE_EXISTING);

        // 3. Create HLS output folder
        Path outputDir = storageLocation.resolve("hls")
                .resolve(UUID.randomUUID().toString());
        Files.createDirectories(outputDir);

        // 4. Run ffmpeg to transcode video to HLS
        ProcessBuilder pb = getProcessBuilder(inputFile, outputDir);
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            return ResponseEntity.status(500)
                    .body("FFmpeg failed with exit code: " + exitCode);
        }

        // 5. Return the HLS playlist URL
        String uuid = outputDir.getFileName().toString();
        String hlsUrl = "/hls/" + uuid + "/index.m3u8";
        return ResponseEntity.ok(hlsUrl);
    }

    /**
     * Builds the correct ffmpeg command for the current OS.
     */
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
```

### VideoPlayerController.java
```java
package com.aamir.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class VideoPlayerController {

    /**
     * Serves the main upload page.
     */
    @GetMapping("/")
    public String home() {
        return "index";
    }

    /**
     * Serves the video player page, passing HLS URL and file name to the view.
     */
    @GetMapping("/player")
    public String player(@RequestParam(required = false) String hls,
                         @RequestParam(required = false) String file,
                         Model model) {
        model.addAttribute("hlsUrl", hls);
        model.addAttribute("fileName", file);
        return "player";
    }
}
```

---

## Frontend (HTML) Code and Flow

### index.html (Upload Page)
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Upload Video</title>
    <style>
        /* ...CSS for layout, colors, drag-and-drop, progress bar... */
    </style>
</head>
<body>
<div class="container">
    <h1>Upload video</h1>
    <p>Video will be processed into HLS</p>
    <div class="upload-box" id="uploadBox">
        <div class="icon" id="icon">📁</div>
        <div id="text">Click or drag video here</div>
        <div class="file-name" id="fileName"></div>
    </div>
    <button class="upload-btn" id="uploadBtn">⬆ Upload</button>
    <div class="progress" id="progress">
        <div class="progress-bar" id="progressBar"></div>
    </div>
    <div class="loader" id="loader"></div>
    <div class="status" id="status"></div>
</div>
<input type="file" id="fileInput" accept="video/*" hidden>
<script>
    // Handles file selection and drag-and-drop
    // Shows progress, status, and triggers upload
    // On upload success, redirects to /player with HLS URL
    // ...existing code from your index.html JS...
</script>
</body>
</html>
```

**How it works:**
- User selects or drags a video file.
- Progress bar and status are updated during upload.
- After upload and FFmpeg processing, user is redirected to the player page with the HLS URL.

### player.html (HLS Video Player)
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Video Streaming Player</title>
    <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
    <style>
        /* ...CSS for player layout... */
    </style>
</head>
<body>
<h1>🎬 HLS Video Streaming</h1>
<video id="videoPlayer" controls crossorigin="anonymous"></video>
<script th:inline="javascript">
    // Reads the HLS URL from query parameter
    // Uses hls.js to play the stream if supported
    // Fallback for browsers with native HLS support
    // ...existing code from your player.html JS...
</script>
</body>
</html>
```

**How it works:**
- Loads the HLS stream using hls.js if supported.
- Plays the video in the browser with adaptive streaming.

---

## How Streaming Works in This Project

- **Upload:** The user uploads a video file via the web UI (`index.html`).
- **Transcoding:** The backend saves the file and runs FFmpeg to convert it to HLS (segments + playlist).
- **Redirect:** After processing, the user is redirected to `/player` with the HLS URL as a parameter.
- **Playback:** The player page (`player.html`) uses hls.js to stream the video in the browser.
- **Adaptive Streaming:** HLS allows the player to switch quality based on network conditions.

---

## Example Frontend Flow

- `index.html` provides a drag-and-drop upload box, progress bar, and status updates.
- After upload and processing, the user is redirected to the player page.
- `player.html` loads the HLS stream using hls.js, providing smooth, adaptive playback.

---

## Prerequisites

- Java 17+
- Maven
- FFmpeg installed and available in the system PATH (or update the Windows path in the controller)

---

## Setup & Run

1. **Clone the repository:**
   ```bash
   git clone https://github.com/mohammadaamir1102/video-streaming-by-hls-in-spring-boot
   cd video-streaming-by-hls-in-spring-boot
   ```
2. **Build the project:**
   ```bash
   ./mvnw clean package
   ```
3. **Run the application:**
   ```bash
   ./mvnw spring-boot:run
   ```
4. **Access the app:**
   Open [http://localhost:9090](http://localhost:9090) in your browser.

---

## License

This project is for demonstration and educational purposes.

