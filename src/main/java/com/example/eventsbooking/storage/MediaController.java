package com.example.eventsbooking.storage;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Serves uploaded pictures to everybody (they are shown on public event pages). The content type comes from the stored
 * extension, which was chosen from the verified content at upload time; {@code nosniff} stops browsers from guessing.
 * File names are unguessable UUIDs and never change their content, so they can be cached for a year.
 */
@RestController
@RequestMapping("/api/media")
class MediaController {

    private final FileStorage storage;

    MediaController(FileStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/{name}")
    ResponseEntity<byte[]> get(@PathVariable String name) {
        if (!LocalFileStorage.isStoredName(name)) {
            return ResponseEntity.notFound().build();
        }
        return storage.read(name)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(ImageType.fromStoredName(name).orElseThrow().contentType()))
                        .header("X-Content-Type-Options", "nosniff")
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
