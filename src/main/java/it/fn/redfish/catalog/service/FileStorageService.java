package it.fn.redfish.catalog.service;

import it.fn.redfish.catalog.support.I18n;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import it.fn.redfish.catalog.config.CatalogProperties;
import it.fn.redfish.catalog.support.BusinessException;

@Service
public class FileStorageService {

    private static final Set<String> LOGO_EXTENSIONS = Set.of("png", "jpg", "jpeg", "svg", "gif", "webp");
    private static final long MAX_LOGO_BYTES = 1024L * 1024;

    private final Path root;

    public FileStorageService(CatalogProperties properties) {
        this.root = properties.getStorageDir().toAbsolutePath().normalize();
    }

    public String storeLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(I18n.text("message.no.file.selected"));
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw new BusinessException(I18n.text("message.the.logo.must.not.exceed.1.mb"));
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!LOGO_EXTENSIONS.contains(extension)) {
            throw new BusinessException(I18n.text("message.unsupported.logo.format.allowed") + String.join(", ", LOGO_EXTENSIONS) + ")");
        }
        Path target = root.resolve("branding").resolve("logo." + extension);
        try {
            Files.createDirectories(target.getParent());

            try (var stream = Files.list(target.getParent())) {
                stream.filter(p -> p.getFileName().toString().startsWith("logo."))
                        .filter(p -> !p.equals(target))
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException ignored) {

                            }
                        });
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException(I18n.text("message.cannot.save.the.logo") + e.getMessage(), e);
        }
        return "branding/logo." + extension;
    }

    public Path resolve(String relativePath) {
        Path candidate = root.resolve(relativePath).normalize();
        if (!candidate.startsWith(root)) {
            throw new BusinessException(I18n.text("message.path.not.allowed"));
        }
        return candidate;
    }

    public void deleteQuietly(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException | BusinessException ignored) {

        }
    }

    public String contentTypeOf(String relativePath) {
        return switch (extensionOf(relativePath)) {
            case "svg" -> "image/svg+xml";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/png";
        };
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
