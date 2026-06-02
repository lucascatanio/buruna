package com.buruna.shared.storage;

import com.buruna.shared.exception.StorageException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@RestController
@RequestMapping("/local-storage")
@Profile("local")
public class LocalStorageController {

    private final Path storagePath;

    public LocalStorageController(@Value("${app.storage.local.path}") Path storagePath) {
        this.storagePath = storagePath;
    }

    private Path resolve(String objectName) {
        String safe = objectName.startsWith("/") ? objectName.substring(1) : objectName;
        Path file = storagePath.resolve(safe).normalize();
        if (!file.startsWith(storagePath)) {
            throw new StorageException("Path traversal detectado: " + objectName, null);
        }
        return file;
    }

    @PutMapping("/upload/{*objectName}")
    public ResponseEntity<Void> upload(@PathVariable String objectName, HttpServletRequest request) {
        Path target = resolve(objectName);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(request.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            throw new StorageException("Falha ao receber upload local: " + objectName, e);
        }
    }

    @GetMapping("/files/{*objectName}")
    public ResponseEntity<Resource> download(@PathVariable String objectName, HttpServletRequest request) {
        Path target = resolve(objectName);
        if (!Files.exists(target)) {
            return ResponseEntity.notFound().build();
        }

        try {
            String contentType = Files.probeContentType(target);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            long fileLength = Files.size(target);
            String rangeHeader = request.getHeader("Range");

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                return handleRangeRequest(target, contentType, fileLength, rangeHeader);
            }

            Resource resource = new FileSystemResource(target);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(fileLength)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(resource);
        } catch (IOException e) {
            throw new StorageException("Falha ao servir arquivo local: " + objectName, e);
        }
    }

    private ResponseEntity<Resource> handleRangeRequest(Path target, String contentType,
                                                         long fileLength, String rangeHeader) throws IOException {
        String[] ranges = rangeHeader.substring("bytes=".length()).split("-");
        long start = Long.parseLong(ranges[0]);
        long end = ranges.length > 1 && !ranges[1].isEmpty()
                ? Long.parseLong(ranges[1])
                : fileLength - 1;

        if (start >= fileLength || end >= fileLength || start > end) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
        }

        long contentLength = end - start + 1;
        var resource = new RangeResource(target, start, contentLength);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(contentLength)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength)
                .body(resource);
    }

    private static class RangeResource extends FileSystemResource {
        private final long start;
        private final long contentLength;

        RangeResource(Path path, long start, long contentLength) {
            super(path.toFile());
            this.start = start;
            this.contentLength = contentLength;
        }

        @Override
        public long contentLength() {
            return contentLength;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            InputStream is = super.getInputStream();
            is.skipNBytes(start);
            return is;
        }
    }
}
