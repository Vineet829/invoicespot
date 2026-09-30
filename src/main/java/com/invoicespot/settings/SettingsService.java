package com.invoicespot.settings;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessRepository;
import com.invoicespot.common.ApiException;
import com.invoicespot.settings.dto.SettingsResponse;
import com.invoicespot.settings.dto.UpdateSettingsRequest;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

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
        BusinessSettings settings = settingsRepository.findByBusinessPkid(access.businessPkid()).orElseGet(() -> {
            BusinessSettings created = new BusinessSettings();
            created.setBusiness(businessRepository.getReferenceById(access.businessPkid()));
            return created;
        });
        settings.setDefaultCurrency(request.defaultCurrency());
        settings.setPaymentTermsDays(request.paymentTermsDays());
        settings.setDefaultTaxRate(request.defaultTaxRate());
        settings.setTaxLabel(request.taxLabel().trim());
        settings.setDefaultNotes(blankToNull(request.defaultNotes()));
        settings.setDefaultTerms(blankToNull(request.defaultTerms()));
        settings.setEmailMessage(blankToNull(request.emailMessage()));
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
        settingsRepository.save(settings);
        return SettingsResponse.from(settings);
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
