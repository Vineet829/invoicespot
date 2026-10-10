package com.invoicespot.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicespot.document.Document;
import com.invoicespot.document.DocumentState;
import com.invoicespot.document.DocumentType;
import java.util.UUID;

public record DocumentLink(
        @JsonProperty("_id") UUID id, String documentNumber, DocumentType documentType, DocumentState state) {

    public static DocumentLink from(Document document) {
        return new DocumentLink(
                document.getId(), document.getDocumentNumber(), document.getDocumentType(), document.state());
    }
}
