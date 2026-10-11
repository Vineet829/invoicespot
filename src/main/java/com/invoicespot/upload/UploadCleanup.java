package com.invoicespot.upload;

import com.invoicespot.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// For the daily jobs: removes logos nothing uses any more, such as one
// replaced by another, one uploaded but never saved, or one whose business is
// gone. A logo stays while a business shows it or a document was made with
// it, and for a day after it was uploaded, so that one just uploaded isn't
// removed before its business is saved.
@Component
public class UploadCleanup {

    private static final Duration KEEP_UNUSED = Duration.ofDays(1);
    private static final String IN_USE = "select logo from businesses where logo like '/uploads/%'"
            + " union select seller_logo from documents where seller_logo like '/uploads/%'";

    private final Path uploadDir;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public UploadCleanup(AppProperties properties, JdbcTemplate jdbcTemplate, Clock clock) {
        this.uploadDir = Path.of(properties.upload().dir());
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    // How many files were removed.
    public int removeUnused() {
        if (!Files.isDirectory(uploadDir)) {
            return 0;
        }
        Set<String> inUse = new HashSet<>(jdbcTemplate.queryForList(IN_USE, String.class));
        Instant uploadedBefore = clock.instant().minus(KEEP_UNUSED);
        int removed = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(uploadDir)) {
            for (Path file : files) {
                if (Files.isRegularFile(file)
                        && !inUse.contains("/uploads/" + file.getFileName())
                        && Files.getLastModifiedTime(file).toInstant().isBefore(uploadedBefore)
                        && removed(file)) {
                    removed++;
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return removed;
    }

    private static boolean removed(Path file) throws IOException {
        try {
            return Files.deleteIfExists(file);
        } catch (NoSuchFileException exception) {
            return false;
        }
    }
}
