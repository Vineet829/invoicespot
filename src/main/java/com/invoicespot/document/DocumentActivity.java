package com.invoicespot.document;

import com.invoicespot.common.MoneyFormat;
import com.invoicespot.user.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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

    // What happened to a document, and the member who did it: null when it
    // was the client or an automatic run.
    void record(Document document, DocumentEventType type, String detail, User actor) {
        record(document, type, detail, null, actor);
    }

    void record(Document document, DocumentEventType type, String detail, Long paymentPkid, User actor) {
        DocumentEvent event = new DocumentEvent();
        event.setDocument(document);
        event.setType(type);
        event.setDetail(detail);
        event.setPaymentPkid(paymentPkid);
        event.setActor(actor);
        event.setOccurredAt(Instant.now(clock));
        eventRepository.save(event);
    }

    List<DocumentEvent> history(Document document) {
        return eventRepository.findByDocumentPkidOrderByOccurredAtDescPkidDesc(document.getPkid());
    }

    // "USD 40.00 · Cash", with the tax the client withheld after it when there
    // is some.
    static String paymentDetail(Document document, Payment payment) {
        String detail = amount(document, payment.getAmountPaid()) + " · " + payment.getPaymentMethod().getValue();
        BigDecimal withheld = payment.getTdsAmount();
        return withheld == null || withheld.signum() == 0
                ? detail
                : detail + " · tax withheld " + amount(document, withheld);
    }

    // An amount in the document's currency, such as "USD 1,250.00" or
    // "INR 1,25,000.00".
    static String amount(Document document, BigDecimal value) {
        String currency = Objects.requireNonNullElse(document.getCurrency(), "");
        return (currency + " " + MoneyFormat.format(value, currency)).trim();
    }
}
