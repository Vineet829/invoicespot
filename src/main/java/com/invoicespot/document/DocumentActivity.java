package com.invoicespot.document;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class DocumentActivity {

    private final DocumentEventRepository eventRepository;
    private final Clock clock;

    public DocumentActivity(DocumentEventRepository eventRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    void record(Document document, DocumentEventType type, String detail) {
        record(document, type, detail, null);
    }

    void record(Document document, DocumentEventType type, String detail, Long paymentPkid) {
        DocumentEvent event = new DocumentEvent();
        event.setDocument(document);
        event.setType(type);
        event.setDetail(detail);
        event.setPaymentPkid(paymentPkid);
        event.setOccurredAt(Instant.now(clock));
        eventRepository.save(event);
    }

    List<DocumentEvent> history(Document document) {
        return eventRepository.findByDocumentPkidOrderByOccurredAtDescPkidDesc(document.getPkid());
    }

    static String paymentDetail(Document document, Payment payment) {
        return amount(document, payment.getAmountPaid()) + " · " + payment.getPaymentMethod().getValue();
    }

    // An amount in the document's currency, such as "USD 1,250.00".
    static String amount(Document document, BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        format.setRoundingMode(RoundingMode.HALF_UP);
        String currency = Objects.requireNonNullElse(document.getCurrency(), "");
        return (currency + " " + format.format(Objects.requireNonNullElse(value, BigDecimal.ZERO))).trim();
    }
}
