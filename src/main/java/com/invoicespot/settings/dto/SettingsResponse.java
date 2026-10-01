package com.invoicespot.settings.dto;

import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.PdfTemplate;
import com.invoicespot.tax.TaxRegime;
import java.math.BigDecimal;

public record SettingsResponse(
        String defaultCurrency,
        String baseCurrency,
        int paymentTermsDays,
        BigDecimal defaultTaxRate,
        String taxLabel,
        String defaultNotes,
        String defaultTerms,
        String timeZone,
        String invoicePrefix,
        String quotationPrefix,
        String receiptPrefix,
        String creditNotePrefix,
        int nextInvoiceNumber,
        int nextQuotationNumber,
        int nextReceiptNumber,
        int nextCreditNoteNumber,
        boolean remindersEnabled,
        int reminderDaysBefore,
        boolean reminderOnDue,
        int reminderEveryDays,
        int reminderMax,
        TaxRegime taxRegime,
        String lutReference,
        PdfTemplate pdfTemplate,
        boolean saved) {

    public static SettingsResponse from(BusinessSettings settings) {
        return new SettingsResponse(
                settings.getDefaultCurrency(),
                settings.getBaseCurrency(),
                settings.getPaymentTermsDays(),
                settings.getDefaultTaxRate().stripTrailingZeros(),
                settings.getTaxLabel(),
                settings.getDefaultNotes(),
                settings.getDefaultTerms(),
                settings.getTimeZone(),
                settings.getInvoicePrefix(),
                settings.getQuotationPrefix(),
                settings.getReceiptPrefix(),
                settings.getCreditNotePrefix(),
                settings.getNextInvoiceNumber(),
                settings.getNextQuotationNumber(),
                settings.getNextReceiptNumber(),
                settings.getNextCreditNoteNumber(),
                settings.isRemindersEnabled(),
                settings.getReminderDaysBefore(),
                settings.isReminderOnDue(),
                settings.getReminderEveryDays(),
                settings.getReminderMax(),
                settings.getTaxRegime(),
                settings.getLutReference(),
                settings.getPdfTemplate(),
                settings.getPkid() != null);
    }
}
