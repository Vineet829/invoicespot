package com.invoicespot.document;

import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.BusinessSettingsRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DocumentNumbering {

    private final BusinessSettingsRepository settingsRepository;
    private final DocumentRepository documentRepository;

    public DocumentNumbering(BusinessSettingsRepository settingsRepository, DocumentRepository documentRepository) {
        this.settingsRepository = settingsRepository;
        this.documentRepository = documentRepository;
    }

    // Each business numbers its own documents. Its settings row is locked
    // while a number is handed out, so members creating documents at once
    // take turns.
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Long businessPkid, DocumentType type) {
        settingsRepository.insertDefaultsIfMissing(businessPkid);
        BusinessSettings settings = settingsRepository.findForUpdateByBusinessPkid(businessPkid).orElseThrow();
        String prefix = switch (type) {
            case INVOICE -> settings.getInvoicePrefix();
            case QUOTATION -> settings.getQuotationPrefix();
            case RECEIPT -> settings.getReceiptPrefix();
        };
        int number = switch (type) {
            case INVOICE -> settings.getNextInvoiceNumber();
            case QUOTATION -> settings.getNextQuotationNumber();
            case RECEIPT -> settings.getNextReceiptNumber();
        };
        while (documentRepository.existsByBusinessPkidAndDocumentNumber(businessPkid, format(prefix, number))) {
            number++;
        }
        switch (type) {
            case INVOICE -> settings.setNextInvoiceNumber(number + 1);
            case QUOTATION -> settings.setNextQuotationNumber(number + 1);
            case RECEIPT -> settings.setNextReceiptNumber(number + 1);
        }
        return format(prefix, number);
    }

    static String format(String prefix, int number) {
        return prefix + String.format("%04d", number);
    }
}
