package com.invoicespot.settings.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateSettingsRequest(
        @NotNull(message = "Choose a default currency")
                @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a three-letter code such as USD")
                String defaultCurrency,
        @NotNull(message = "Enter the payment terms in days")
                @Min(value = 0, message = "Payment terms can't be negative")
                @Max(value = 365, message = "Payment terms can be at most 365 days")
                Integer paymentTermsDays,
        @NotNull(message = "Enter a default tax rate")
                @DecimalMin(value = "0", message = "The tax rate must be from 0 to 100")
                @DecimalMax(value = "100", message = "The tax rate must be from 0 to 100")
                @Digits(integer = 3, fraction = 4, message = "The tax rate can have at most 4 decimals")
                BigDecimal defaultTaxRate,
        @NotBlank(message = "Enter a name for your tax, such as VAT or GST")
                @Size(max = 20, message = "The tax name can be at most 20 characters")
                String taxLabel,
        @Size(max = 5000, message = "Default notes can be at most 5,000 characters") String defaultNotes,
        @Size(max = 5000, message = "Default terms can be at most 5,000 characters") String defaultTerms,
        @Size(max = 5000, message = "The email message can be at most 5,000 characters") String emailMessage,
        @NotBlank(message = "Choose a time zone") @Size(max = 64, message = "Choose a time zone") String timeZone,
        @Size(max = 20, message = "Prefixes can be at most 20 characters")
                @Pattern(
                        regexp = "[\\p{L}\\p{N} ._/#-]*",
                        message = "Prefixes can use letters, numbers, spaces and . _ / # -")
                String invoicePrefix,
        @Size(max = 20, message = "Prefixes can be at most 20 characters")
                @Pattern(
                        regexp = "[\\p{L}\\p{N} ._/#-]*",
                        message = "Prefixes can use letters, numbers, spaces and . _ / # -")
                String quotationPrefix,
        @Size(max = 20, message = "Prefixes can be at most 20 characters")
                @Pattern(
                        regexp = "[\\p{L}\\p{N} ._/#-]*",
                        message = "Prefixes can use letters, numbers, spaces and . _ / # -")
                String receiptPrefix,
        @Min(value = 1, message = "The next number must be at least 1")
                @Max(value = 999_999_999, message = "The next number can be at most 999,999,999")
                Integer nextInvoiceNumber,
        @Min(value = 1, message = "The next number must be at least 1")
                @Max(value = 999_999_999, message = "The next number can be at most 999,999,999")
                Integer nextQuotationNumber,
        @Min(value = 1, message = "The next number must be at least 1")
                @Max(value = 999_999_999, message = "The next number can be at most 999,999,999")
                Integer nextReceiptNumber,
        Boolean remindersEnabled,
        @Min(value = 0, message = "Remind from 0 to 60 days before the due date")
                @Max(value = 60, message = "Remind from 0 to 60 days before the due date")
                Integer reminderDaysBefore,
        Boolean reminderOnDue,
        @Min(value = 0, message = "Repeat reminders every 0 to 60 days")
                @Max(value = 60, message = "Repeat reminders every 0 to 60 days")
                Integer reminderEveryDays,
        @Min(value = 1, message = "Send from 1 to 10 reminders an invoice")
                @Max(value = 10, message = "Send from 1 to 10 reminders an invoice")
                Integer reminderMax) {}
