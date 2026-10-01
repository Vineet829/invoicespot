package com.invoicespot.settings;

import java.util.List;

// The kinds of email a business sends, each with its standard subject and
// message, and the placeholders its emails fill. The standard texts are the
// ones these emails had before businesses could write their own.
public enum EmailTemplateKind {
    INVOICE("Invoices", "invoice", Text.SUBJECT, Text.PAY_BODY, List.of(
            Text.CLIENT, Text.DOCUMENT, Text.NUMBER, Text.AMOUNT, Text.BALANCE, Text.DUE, Text.PAY_LINK,
            Text.PAYMENT, Text.BUSINESS)),
    QUOTATION("Quotations", "quotation", Text.SUBJECT, Text.ACCEPT_BODY, List.of(
            Text.CLIENT, Text.DOCUMENT, Text.NUMBER, Text.AMOUNT,
            new Placeholder("due", "The day it is valid until"),
            new Placeholder("link", "A link to view and accept it online"), Text.BUSINESS)),
    RECEIPT("Receipts", "receipt", Text.SUBJECT, Text.VIEW_BODY, List.of(
            Text.CLIENT, Text.DOCUMENT, Text.NUMBER, Text.AMOUNT, Text.VIEW_LINK, Text.BUSINESS)),
    CREDIT_NOTE("Credit notes", "credit note", Text.SUBJECT, Text.VIEW_BODY, List.of(
            Text.CLIENT, Text.DOCUMENT, Text.NUMBER, Text.AMOUNT, Text.VIEW_LINK, Text.BUSINESS)),
    REMINDER("Reminders", "reminder", Text.REMINDER_SUBJECT, Text.REMINDER_BODY, List.of(
            Text.CLIENT, Text.DOCUMENT, Text.NUMBER, Text.AMOUNT, Text.BALANCE, Text.DUE,
            new Placeholder("due_status", "When it is or was due, such as “is due on Oct 15, 2026”"),
            Text.PAY_LINK, Text.PAYMENT, Text.BUSINESS)),
    PAYMENT_RECEIPT("Payment receipts", "payment receipt", Text.SUBJECT, Text.BODY, List.of(
            Text.CLIENT,
            new Placeholder("document", "The receipt's title, such as Payment receipt INV-0042-P1"),
            new Placeholder("number", "The receipt's number"),
            new Placeholder("amount", "The amount paid"),
            new Placeholder("balance", "What is still to pay on the invoice"),
            Text.BUSINESS)),
    STATEMENT("Statements", "statement", Text.SUBJECT, Text.BODY, List.of(
            Text.CLIENT, new Placeholder("document", "The title, Statement of account"), Text.BUSINESS));

    // A placeholder an email of this kind fills, and what it is filled with.
    public record Placeholder(String name, String meaning) {}

    private final String label;
    private final String noun;
    private final String standardSubject;
    private final String standardBody;
    private final List<Placeholder> placeholders;

    EmailTemplateKind(
            String label, String noun, String standardSubject, String standardBody, List<Placeholder> placeholders) {
        this.label = label;
        this.noun = noun;
        this.standardSubject = standardSubject;
        this.standardBody = standardBody;
        this.placeholders = placeholders;
    }

    public String label() {
        return label;
    }

    public String noun() {
        return noun;
    }

    public String standardSubject() {
        return standardSubject;
    }

    public String standardBody() {
        return standardBody;
    }

    public List<Placeholder> placeholders() {
        return placeholders;
    }

    public boolean fills(String placeholder) {
        return placeholders.stream().anyMatch(known -> known.name().equals(placeholder));
    }

    // A line whose placeholders come out empty, such as how to pay a paid
    // invoice, is left out of the email.
    private static final class Text {
        static final String SUBJECT = "{document} from {business}";
        static final String ATTACHED = "Hello {client},\n\nPlease find {document} from {business} attached.\n\n";
        static final String BODY = ATTACHED + "{business}";
        static final String PAY_BODY = ATTACHED + "View and pay it online: {link}\n\n{payment}\n\n{business}";
        static final String ACCEPT_BODY = ATTACHED + "View and accept it online: {link}\n\n{business}";
        static final String VIEW_BODY = ATTACHED + "View it online: {link}\n\n{business}";
        static final String REMINDER_SUBJECT = "Reminder: {document} from {business}";
        static final String REMINDER_BODY = "Hello {client},\n\nA friendly reminder that {document} for {balance}"
                + " {due_status}. A copy is attached.\n\nView and pay it online: {link}\n\n{payment}\n\n"
                + "Thank you,\n{business}";
        static final Placeholder PAY_LINK = new Placeholder("link", "A link to view and pay it online");
        static final Placeholder VIEW_LINK = new Placeholder("link", "A link to view it online");
        static final Placeholder PAYMENT = new Placeholder(
                "payment", "How to pay, from Settings → Getting paid, while it is unpaid");
        static final Placeholder CLIENT = new Placeholder("client", "The client's name");
        static final Placeholder DOCUMENT = new Placeholder("document", "The title, such as Invoice INV-0042");
        static final Placeholder NUMBER = new Placeholder("number", "The number, such as INV-0042");
        static final Placeholder AMOUNT = new Placeholder("amount", "The total");
        static final Placeholder BALANCE = new Placeholder("balance", "What is still to pay");
        static final Placeholder DUE = new Placeholder("due", "The due date");
        static final Placeholder BUSINESS = new Placeholder("business", "Your business's name");
    }
}
