package com.invoicespot.document;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DocumentStatusConverter implements AttributeConverter<DocumentStatus, String> {

    @Override
    public String convertToDatabaseColumn(DocumentStatus attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public DocumentStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DocumentStatus.fromValue(dbData);
    }
}
