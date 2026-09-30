package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatementsTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final Instant START_OF_TODAY = LocalDate.of(2026, 9, 30).atStartOfDay(ZONE).toInstant();

    private static Document invoice(String number, String currency, String total, String issued, String due) {
        Document invoice = new Document();
        invoice.setDocumentType(DocumentType.INVOICE);
        invoice.setDocumentNumber(number);
        invoice.setCurrency(currency);
        invoice.setTotal(new BigDecimal(total));
        invoice.setTotalAmountReceived(BigDecimal.ZERO);
        invoice.setStatus(DocumentStatus.NOT_PAID);
        invoice.setCreatedAt(LocalDate.parse(issued).atTime(10, 0).atZone(ZONE).toInstant());
        invoice.setDueDate(LocalDate.parse(due).atTime(12, 0).atZone(ZONE).toInstant());
        return invoice;
    }

    private static void pay(Document invoice, String amount, String day, PaymentMethod method) {
        Payment payment = new Payment();
        payment.setDocument(invoice);
        payment.setAmountPaid(new BigDecimal(amount));
        payment.setDatePaid(LocalDate.parse(day));
        payment.setPaymentMethod(method);
        invoice.getPayments().add(payment);
        BigDecimal received = invoice.getTotalAmountReceived().add(payment.getAmountPaid());
        invoice.setTotalAmountReceived(received);
        invoice.setStatus(DocumentTotals.status(invoice.getTotal(), received));
    }

    @Test
    @DisplayName("invoices and payments run in date order, with the balance after each")
    void ledger() {
        Document first = invoice("INV-1", "USD", "100.00", "2026-08-01", "2026-08-15");
        Document second = invoice("INV-2", "USD", "50.00", "2026-09-10", "2026-10-10");
        pay(first, "40.00", "2026-09-10", PaymentMethod.BANK_TRANSFER);

        Statements.Account account =
                Statements.accounts(List.of(first, second), ZONE, START_OF_TODAY).get(0);

        assertThat(account.lines())
                .extracting(Statements.Line::description)
                .containsExactly("Invoice INV-1", "Invoice INV-2", "Payment for INV-1 (Bank Transfer)");
        assertThat(account.lines())
                .extracting(Statements.Line::balance)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("150.00"), new BigDecimal("110.00"));
        assertThat(account.charged()).isEqualByComparingTo("150");
        assertThat(account.paid()).isEqualByComparingTo("40");
        assertThat(account.due()).isEqualByComparingTo("110");
        assertThat(account.overdue()).isEqualByComparingTo("60");
    }

    @Test
    @DisplayName("each currency gets its own account, and a paid invoice is never overdue")
    void currencies() {
        Document dollars = invoice("INV-1", "USD", "80.00", "2026-07-01", "2026-07-15");
        pay(dollars, "80.00", "2026-07-20", PaymentMethod.CASH);
        Document euros = invoice("INV-2", "EUR", "30.00", "2026-07-02", "2026-07-16");

        List<Statements.Account> accounts = Statements.accounts(List.of(dollars, euros), ZONE, START_OF_TODAY);

        assertThat(accounts).extracting(Statements.Account::currency).containsExactly("USD", "EUR");
        assertThat(accounts.get(0).due()).isEqualByComparingTo("0");
        assertThat(accounts.get(0).overdue()).isEqualByComparingTo("0");
        assertThat(accounts.get(1).due()).isEqualByComparingTo("30");
        assertThat(accounts.get(1).overdue()).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("an invoice is dated by the business's calendar, not UTC")
    void businessCalendar() {
        Document late = invoice("INV-1", "USD", "10.00", "2026-09-01", "2026-09-30");
        late.setCreatedAt(Instant.parse("2026-08-31T20:00:00Z"));

        Statements.Line line = Statements.accounts(List.of(late), ZONE, START_OF_TODAY)
                .get(0)
                .lines()
                .get(0);

        assertThat(line.date()).isEqualTo(LocalDate.of(2026, 9, 1));
    }
}
