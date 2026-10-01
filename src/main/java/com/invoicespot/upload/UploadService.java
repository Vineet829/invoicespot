package com.invoicespot.upload;

import com.invoicespot.common.ApiException;
import com.invoicespot.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    public static final String NOT_AN_IMAGE = "Logos can be PNG, JPEG or GIF images";

    private static final String PUBLIC_PREFIX = "/uploads/";
    // What was ever stored here: today's logos, and WebP ones from before.
    private static final List<String> SERVED = List.of(".png", ".jpg", ".jpeg", ".gif", ".webp");

    private final Path uploadDir;

    public UploadService(AppProperties properties) {
        this.uploadDir = Path.of(properties.upload().dir());
    }

    // A logo is kept only when its first bytes show a PNG, JPEG or GIF image,
    // and named for what it is, whatever the browser called it: a page or
    // script sent as an image is refused. WebP is left out because PDFs can't
    // show it.
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A logo file is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, TOO_LARGE);
        }
        try {
            byte[] content = file.getBytes();
            String fileName = UUID.randomUUID() + extension(content)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, NOT_AN_IMAGE));
            Files.createDirectories(uploadDir);
            Files.write(uploadDir.resolve(fileName).toAbsolutePath(), content);
            return PUBLIC_PREFIX + fileName;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The logo could not be uploaded");
        }
    }

    // Only image files are served from /uploads.
    public static boolean servable(String path) {
        String name = path.toLowerCase(Locale.ROOT);
        return SERVED.stream().anyMatch(name::endsWith);
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

    private static Optional<String> extension(byte[] content) {
        if (startsWith(content, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(".png");
        }
        if (startsWith(content, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(".jpg");
        }
        if (startsWith(content, 'G', 'I', 'F', '8', '7', 'a') || startsWith(content, 'G', 'I', 'F', '8', '9', 'a')) {
            return Optional.of(".gif");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int... signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if ((content[index] & 0xFF) != signature[index]) {
                return false;
            }
        }
        return true;
    }
}
