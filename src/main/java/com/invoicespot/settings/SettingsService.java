package com.invoicespot.settings;

import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessRepository;
import com.invoicespot.common.ApiException;
import com.invoicespot.settings.dto.PaymentDetailsRequest;
import com.invoicespot.settings.dto.PaymentDetailsResponse;
import com.invoicespot.settings.dto.SettingsResponse;
import com.invoicespot.settings.dto.UpdateSettingsRequest;
import com.invoicespot.tax.Gstin;
import com.invoicespot.tax.TaxRegime;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private static final String GST_PREFIX = "[A-Za-z0-9/-]{0,12}";

    private final BusinessSettingsRepository settingsRepository;
    private final BusinessRepository businessRepository;

    public SettingsService(BusinessSettingsRepository settingsRepository, BusinessRepository businessRepository) {
        this.settingsRepository = settingsRepository;
        this.businessRepository = businessRepository;
    }

    // A business that never saved its settings works with the defaults.
    @Transactional(readOnly = true)
    public BusinessSettings forBusiness(Long businessPkid) {
        return settingsRepository.findByBusinessPkid(businessPkid).orElseGet(BusinessSettings::new);
    }

    @Transactional(readOnly = true)
    public SettingsResponse get(BusinessAccess access) {
        return SettingsResponse.from(forBusiness(access.businessPkid()));
    }

    @Transactional
    public SettingsResponse update(BusinessAccess access, UpdateSettingsRequest request) {
        BusinessSettings settings = saved(access);
        // The reporting currency follows the default currency until it is
        // set to another one.
        if (request.baseCurrency() != null) {
            settings.setBaseCurrency(request.baseCurrency());
        } else if (settings.getBaseCurrency().equals(settings.getDefaultCurrency())) {
            settings.setBaseCurrency(request.defaultCurrency());
        }
        settings.setDefaultCurrency(request.defaultCurrency());
        settings.setPaymentTermsDays(request.paymentTermsDays());
        settings.setDefaultTaxRate(request.defaultTaxRate());
        settings.setTaxLabel(request.taxLabel().trim());
        settings.setDefaultNotes(blankToNull(request.defaultNotes()));
        settings.setDefaultTerms(blankToNull(request.defaultTerms()));
        settings.setTimeZone(zone(request.timeZone()));
        if (request.invoicePrefix() != null) {
            settings.setInvoicePrefix(request.invoicePrefix());
        }
        if (request.quotationPrefix() != null) {
            settings.setQuotationPrefix(request.quotationPrefix());
        }
        if (request.receiptPrefix() != null) {
            settings.setReceiptPrefix(request.receiptPrefix());
        }
        if (request.nextInvoiceNumber() != null) {
            settings.setNextInvoiceNumber(request.nextInvoiceNumber());
        }
        if (request.nextQuotationNumber() != null) {
            settings.setNextQuotationNumber(request.nextQuotationNumber());
        }
        if (request.nextReceiptNumber() != null) {
            settings.setNextReceiptNumber(request.nextReceiptNumber());
        }
        if (request.creditNotePrefix() != null) {
            settings.setCreditNotePrefix(request.creditNotePrefix());
        }
        if (request.nextCreditNoteNumber() != null) {
            settings.setNextCreditNoteNumber(request.nextCreditNoteNumber());
        }
        if (request.remindersEnabled() != null) {
            settings.setRemindersEnabled(request.remindersEnabled());
        }
        if (request.reminderDaysBefore() != null) {
            settings.setReminderDaysBefore(request.reminderDaysBefore());
        }
        if (request.reminderOnDue() != null) {
            settings.setReminderOnDue(request.reminderOnDue());
        }
        if (request.reminderEveryDays() != null) {
            settings.setReminderEveryDays(request.reminderEveryDays());
        }
        if (request.reminderMax() != null) {
            settings.setReminderMax(request.reminderMax());
        }
        if (request.lutReference() != null) {
            settings.setLutReference(blankToNull(request.lutReference().trim()));
        }
        if (request.taxRegime() != null) {
            settings.setTaxRegime(regime(request.taxRegime()));
        }
        if (request.pdfTemplate() != null) {
            settings.setPdfTemplate(pdfTemplate(request.pdfTemplate()));
        }
        if (settings.getTaxRegime() == TaxRegime.GST_INDIA) {
            requireGst(access.businessPkid(), settings);
        }
        settingsRepository.save(settings);
        return SettingsResponse.from(settings);
    }

    @Transactional(readOnly = true)
    public PaymentDetailsResponse paymentDetails(BusinessAccess access) {
        return PaymentDetailsResponse.from(forBusiness(access.businessPkid()).getPaymentDetails());
    }

    @Transactional
    public PaymentDetailsResponse updatePaymentDetails(BusinessAccess access, PaymentDetailsRequest request) {
        PaymentDetails details = PaymentDetailsValidator.validate(request);
        BusinessSettings settings = saved(access);
        settings.setPaymentDetails(details);
        settingsRepository.save(settings);
        return PaymentDetailsResponse.from(settings.getPaymentDetails());
    }

    // The business's settings row, made with the defaults if it has none yet.
    // It is locked, as numbering locks it, so a save that waits for another
    // save or for a document's number starts from what that one wrote.
    private BusinessSettings saved(BusinessAccess access) {
        return settingsRepository.findForUpdateByBusinessPkid(access.businessPkid()).orElseGet(() -> {
            BusinessSettings created = new BusinessSettings();
            created.setBusiness(businessRepository.getReferenceById(access.businessPkid()));
            return created;
        });
    }

    // India GST mode needs the business's GSTIN, reports in rupees, and
    // numbers invoices only with letters, digits, / and -, at most 16 long.
    private void requireGst(Long businessPkid, BusinessSettings settings) {
        Business business = businessRepository.findById(businessPkid).orElseThrow();
        String gstin = business.getTaxId() == null ? null : business.getTaxId().trim().toUpperCase(Locale.ROOT);
        if (!Gstin.isValid(gstin)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Add your business's GSTIN under Settings → Business before turning on GST mode");
        }
        business.setTaxId(gstin);
        settings.setBaseCurrency("INR");
        for (String prefix : List.of(
                settings.getInvoicePrefix(), settings.getReceiptPrefix(), settings.getCreditNotePrefix())) {
            if (!prefix.matches(GST_PREFIX)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "In GST mode, invoice, receipt and credit note prefixes can use only letters, digits, "
                                + "/ and -, up to 12 characters");
            }
        }
    }

    private static TaxRegime regime(String value) {
        try {
            return TaxRegime.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose standard tax or India GST");
        }
    }

    private static PdfTemplate pdfTemplate(String value) {
        try {
            return PdfTemplate.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose the Classic, Modern or Compact template");
        }
    }

    private static String zone(String value) {
        try {
            return ZoneId.of(value.trim()).getId();
        } catch (DateTimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a time zone from the list");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
