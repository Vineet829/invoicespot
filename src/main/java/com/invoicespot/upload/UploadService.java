package com.invoicespot.upload;

import com.invoicespot.common.ApiException;
import com.invoicespot.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {

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

        String fileName = UUID.randomUUID() + extension(contentType, file.getOriginalFilename());
        try {
            Files.createDirectories(uploadDir);
            file.transferTo(uploadDir.resolve(fileName).toAbsolutePath());
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "The logo could not be uploaded");
        }
        return "/uploads/" + fileName;
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
            case "image/svg+xml" -> ".svg";
            default -> "";
        };
    }
}
