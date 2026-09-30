package com.invoicespot.upload;

import com.invoicespot.common.ApiException;
import com.invoicespot.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {

    public static final long MAX_BYTES = 2L * 1024 * 1024;
    public static final String TOO_LARGE = "Logos can be at most 2 MB";

    private static final String PUBLIC_PREFIX = "/uploads/";

    private final Path uploadDir;

    public UploadService(AppProperties properties) {
        this.uploadDir = Path.of(properties.upload().dir());
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A logo file is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only image files are allowed");
        }
        if (contentType.toLowerCase(Locale.ROOT).contains("svg")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SVG files are not allowed");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, TOO_LARGE);
        }

        String fileName = UUID.randomUUID() + extension(contentType, file.getOriginalFilename());
        try {
            Files.createDirectories(uploadDir);
            file.transferTo(uploadDir.resolve(fileName).toAbsolutePath());
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "The logo could not be uploaded");
        }
        return PUBLIC_PREFIX + fileName;
    }

    public Optional<Path> localFile(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith(PUBLIC_PREFIX)) {
            return Optional.empty();
        }
        Path root = uploadDir.toAbsolutePath().normalize();
        Path file = root.resolve(publicUrl.substring(PUBLIC_PREFIX.length())).normalize();
        if (!file.getParent().equals(root) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    private static String extension(String contentType, String originalName) {
        if (originalName != null && originalName.contains(".")) {
            return originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }
}
