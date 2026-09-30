package com.invoicespot.export;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Requires;
import com.invoicespot.business.Permission;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<byte[]> all(@Requires(Permission.EXPORT_ARCHIVE) BusinessAccess access) {
        DataExport.Archive archive = dataExport.all(access);
        return ResponseEntity.ok()
                .contentType(ZIP)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archive.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(archive.content());
    }
}
