package com.invoicespot.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

// A spreadsheet-safe CSV download. A text cell that a spreadsheet would read
// as a formula gets a leading apostrophe, so opening the file never runs
// anything typed into the app; so does text that already starts with
// apostrophes ahead of such a character, so an import that takes one away
// gives back exactly what was typed. Numbers and dates are written as they
// are. The byte order mark makes Excel read the file as UTF-8.
public record CsvFile(String fileName, byte[] content) {

    private static final Pattern GUARDED = Pattern.compile("'*[=+\\-@\t\r]");
    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    public static CsvFile of(String fileName, List<String> header, List<List<Object>> rows) {
        StringBuilder csv = new StringBuilder("﻿");
        CSVFormat format = CSVFormat.RFC4180.builder().setHeader(header.toArray(String[]::new)).get();
        try (CSVPrinter printer = new CSVPrinter(csv, format)) {
            for (List<Object> row : rows) {
                printer.printRecord(row.stream().map(CsvFile::cell).toList());
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return new CsvFile(fileName, csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Object cell(Object value) {
        if (value instanceof String text && GUARDED.matcher(text).lookingAt()) {
            return "'" + text;
        }
        return value instanceof BigDecimal amount ? amount.toPlainString() : value;
    }

    public ResponseEntity<byte[]> download() {
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(content);
    }
}
