package com.invoicespot.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicespot.common.ApiException;
import com.invoicespot.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class UploadServiceTest {

    private static UploadService serviceFor(Path dir) {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("a", "b", Duration.ofMinutes(10), Duration.ofDays(1)),
                new AppProperties.Cookie("jwt", "/", "None", true),
                new AppProperties.Site("Invoice Spot", "http://localhost:3000", "http://localhost:8080", "from@invoicespot.local"),
                new AppProperties.Verification(Duration.ofMinutes(15)),
                new AppProperties.Upload(dir.toString()));
        return new UploadService(properties);
    }

    @Test
    @DisplayName("an image is stored under /uploads and written to disk")
    void storesImage(@TempDir Path dir) throws Exception {
        UploadService service = serviceFor(dir);
        MockMultipartFile logo =
                new MockMultipartFile("logo", "brand.png", "image/png", new byte[] {1, 2, 3, 4});

        String url = service.store(logo);

        assertThat(url).startsWith("/uploads/").endsWith(".png");
        String fileName = url.substring("/uploads/".length());
        assertThat(Files.exists(dir.resolve(fileName))).isTrue();
    }

    @Test
    @DisplayName("an empty upload is rejected")
    void rejectsEmptyFile(@TempDir Path dir) {
        UploadService service = serviceFor(dir);
        MockMultipartFile empty = new MockMultipartFile("logo", "e.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.store(empty))
                .isInstanceOf(ApiException.class)
                .hasMessage("A logo file is required");
    }

    @Test
    @DisplayName("a non-image upload is rejected")
    void rejectsNonImage(@TempDir Path dir) {
        UploadService service = serviceFor(dir);
        MockMultipartFile text =
                new MockMultipartFile("logo", "note.txt", "text/plain", new byte[] {9, 9});

        assertThatThrownBy(() -> service.store(text))
                .isInstanceOf(ApiException.class)
                .hasMessage("Only image files are allowed")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("a stored upload's public URL resolves back to its file")
    void resolvesStoredFile(@TempDir Path dir) throws Exception {
        UploadService service = serviceFor(dir);
        String url = service.store(new MockMultipartFile("logo", "brand.png", "image/png", new byte[] {1, 2}));

        assertThat(service.localFile(url)).contains(dir.resolve(url.substring("/uploads/".length())).toAbsolutePath());
    }

    @Test
    @DisplayName("remote URLs, missing files and paths outside the upload folder never resolve")
    void refusesOtherPaths(@TempDir Path dir) throws Exception {
        UploadService service = serviceFor(dir.resolve("uploads"));
        Files.writeString(dir.resolve("secret.txt"), "secret");

        assertThat(service.localFile("https://example.com/logo.png")).isEmpty();
        assertThat(service.localFile("/uploads/missing.png")).isEmpty();
        assertThat(service.localFile("/uploads/../secret.txt")).isEmpty();
        assertThat(service.localFile(null)).isEmpty();
    }

    @Test
    @DisplayName("a logo over 2 MB is refused before anything is written")
    void refusesLargeFiles(@TempDir Path dir) {
        UploadService service = serviceFor(dir);
        MockMultipartFile large = new MockMultipartFile(
                "logo", "big.png", "image/png", new byte[(int) UploadService.MAX_BYTES + 1]);

        assertThatThrownBy(() -> service.store(large))
                .isInstanceOf(ApiException.class)
                .hasMessage("Logos can be at most 2 MB")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(dir.toFile().listFiles()).isEmpty();
    }
}
