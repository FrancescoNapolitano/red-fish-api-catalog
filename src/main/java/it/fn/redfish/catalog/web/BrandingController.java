package it.fn.redfish.catalog.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import it.fn.redfish.catalog.service.FileStorageService;
import it.fn.redfish.catalog.service.SettingsService;

@Controller
public class BrandingController {

    private final SettingsService settings;
    private final FileStorageService storage;

    public BrandingController(SettingsService settings, FileStorageService storage) {
        this.settings = settings;
        this.storage = storage;
    }

    @GetMapping("/branding/logo")
    public ResponseEntity<Resource> logo() throws IOException {
        String relative = settings.logoPath();
        if (relative == null || relative.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        Path file = storage.resolve(relative);
        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storage.contentTypeOf(relative)))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)))
                .contentLength(Files.size(file))
                .body(new FileSystemResource(file));
    }
}
