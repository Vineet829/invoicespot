package com.invoicespot.export;

import com.invoicespot.user.User;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final DataExport dataExport;

    public ExportController(DataExport dataExport) {
        this.dataExport = dataExport;
    }

    @GetMapping("/all")
    public ResponseEntity<byte[]> all(@AuthenticationPrincipal User principal) {
        DataExport.Archive archive = dataExport.all(principal);
        return ResponseEntity.ok()
                .contentType(ZIP)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archive.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(archive.content());
    }
}
