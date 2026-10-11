package com.invoicespot.demo;

import com.invoicespot.document.PaymentMethod;
import com.invoicespot.document.RecurringFrequency;
import java.math.BigDecimal;
import java.util.List;

final class DemoData {

    record Item(String name, String description, BigDecimal price, boolean countable) {}

    record Client(String name, String email, String phone, String address, String city) {}

    // Paid the given number of days before the sample data is added: what it
    // cost before tax, and the tax paid on it, which can be claimed back or not.
    record SampleExpense(
            int daysAgo,
            String vendor,
            String category,
            BigDecimal amount,
            BigDecimal tax,
            boolean taxReclaimable,
            PaymentMethod method) {}

    // A sample invoice repeated on a schedule: the item it bills and how many
    // of it, the client it bills, how often it repeats, and how many days
    // after the sample data is added the next one is made.
    record SampleSchedule(String item, int quantity, String client, RecurringFrequency frequency, int daysAhead) {}

    static final List<Item> ITEMS = List.of(
            item("Brand strategy workshop", "Half-day session with the leadership team", "1800", false),
            item("Logo and identity system", "Logo, colour, type and usage guide", "3200", false),
            item("Website design (per page)", "Responsive layouts for one page", "450", true),
            item("Front-end build (per page)", "Accessible, production-ready code", "380", true),
            item("Copywriting (per 500 words)", null, "150", true),
            item("Product photography (half day)", "Studio, lighting and retouching", "900", false),
            item("Motion graphics (per 15 seconds)", null, "650", true),
            item("Social media kit", "Templates for posts, stories and covers", "700", false),
            item("Hosting and maintenance (monthly)", "Updates, backups and uptime checks", "120", true),
            item("Consulting (hourly)", null, "140", true));

    static final List<Client> CLIENTS = List.of(
            new Client("Harbor & Pine Architects", "accounts@harborandpine.example", "+1 415 555 0142",
                    "88 Bay Street", "San Francisco"),
            new Client("Bluefin Analytics", "billing@bluefin.example", "+1 206 555 0187", "1200 Western Avenue",
                    "Seattle"),
            new Client("Oak Street Bakery", "hello@oakstreetbakery.example", "+1 510 555 0119", "402 Oak Street",
                    "Oakland"),
            new Client("Lumen Health", "finance@lumenhealth.example", "+1 617 555 0133", "75 State Street",
                    "Boston"),
            new Client("Tidewater Logistics", "ap@tidewater.example", "+1 757 555 0164", "9 Harbor Road",
                    "Norfolk"),
            new Client("Atlas Robotics", "payables@atlasrobotics.example", "+1 512 555 0171", "300 Congress Avenue",
                    "Austin"),
            new Client("Juniper Films", "studio@juniperfilms.example", "+1 323 555 0158", "6100 Sunset Boulevard",
                    "Los Angeles"),
            new Client("Kestrel Outdoor", "accounts@kestreloutdoor.example", "+1 303 555 0126", "1550 Wynkoop Street",
                    "Denver"),
            new Client("Marigold Events", "team@marigoldevents.example", "+1 312 555 0149", "220 North Wells Street",
                    "Chicago"),
            new Client("Quill & Co. Publishing", "billing@quillandco.example", "+1 212 555 0192", "11 Bond Street",
                    "New York"),
            new Client("Sable Fitness", "ops@sablefitness.example", "+1 305 555 0137", "1450 Brickell Avenue",
                    "Miami"),
            new Client("Verdant Farms", "office@verdantfarms.example", "+1 503 555 0110", "88 NW Hoyt Street",
                    "Portland"));

    private static final String SOFTWARE = "Software and subscriptions";

    // A year of what the studio spent, in dollars, as its documents are. US
    // sales tax isn't claimed back, so none of the tax paid can be.
    static final List<SampleExpense> EXPENSES = List.of(
            expense(3, "Mission Street Office Supply", "Office", "86.25", "6.25", false, PaymentMethod.CASH),
            expense(9, "Canvasly", SOFTWARE, "599.88", "43.49", false, PaymentMethod.CREDIT_CARD),
            expense(15, "Harborview Hotel", "Travel", "412.00", "49.44", false, PaymentMethod.CREDIT_CARD),
            expense(16, "Pacific Coast Air", "Travel", "386.40", "0", false, PaymentMethod.CREDIT_CARD),
            expense(24, "Rivera Front-End Studio", "Contractors", "2400.00", "0", false, PaymentMethod.BANK_TRANSFER),
            expense(38, "Bayline Print Co.", "Marketing", "420.00", "30.45", false, PaymentMethod.CREDIT_CARD),
            expense(52, "Framewise", SOFTWARE, "144.00", "0", false, PaymentMethod.CREDIT_CARD),
            expense(67, "Mae Lin Illustration", "Contractors", "1200.00", "0", false, PaymentMethod.BANK_TRANSFER),
            expense(81, "Brightdesk Coworking", "Office", "180.00", "0", false, PaymentMethod.BANK_TRANSFER),
            expense(121, "Brightreach Ads", "Marketing", "560.00", "0", false, PaymentMethod.CREDIT_CARD),
            expense(148, "Cloudcrate Hosting", SOFTWARE, "240.00", "0", false, PaymentMethod.CREDIT_CARD),
            expense(184, "Metro Rideshare", "Travel", "67.00", "0", false, PaymentMethod.CREDIT_CARD),
            expense(205, "Typeforge Fonts", SOFTWARE, "129.00", "9.35", false, PaymentMethod.CREDIT_CARD),
            expense(310, "Ergoworks Furniture", "Office", "489.00", "35.45", false, PaymentMethod.CREDIT_CARD));

    // Who the sample expenses were paid to, which tells them from the
    // account's own.
    static final List<String> EXPENSE_VENDORS = EXPENSES.stream().map(SampleExpense::vendor).toList();

    // One month of hosting billed every month, a monthly retainer of ten
    // consulting hours, and a strategy workshop every quarter, each next made
    // in the coming weeks.
    static final List<SampleSchedule> SCHEDULES = List.of(
            new SampleSchedule(
                    "Hosting and maintenance (monthly)", 1, "Oak Street Bakery", RecurringFrequency.MONTHLY, 5),
            new SampleSchedule("Consulting (hourly)", 10, "Bluefin Analytics", RecurringFrequency.MONTHLY, 12),
            new SampleSchedule("Brand strategy workshop", 1, "Lumen Health", RecurringFrequency.QUARTERLY, 26));

    static final String NOTES = "Thank you for working with us. It's a pleasure to be part of your project.";
    static final String TERMS =
            "Payment is due within 14 days of the issue date. Bank transfer details are on request.";
    static final String EMAIL_MESSAGE = "Hi {client},\n\nPlease find {document} attached. Let me know if you have any"
            + " questions.\n\nThanks,\n{business}";

    private static Item item(String name, String description, String price, boolean countable) {
        return new Item(name, description, new BigDecimal(price), countable);
    }

    private static SampleExpense expense(
            int daysAgo,
            String vendor,
            String category,
            String amount,
            String tax,
            boolean taxReclaimable,
            PaymentMethod method) {
        return new SampleExpense(
                daysAgo, vendor, category, new BigDecimal(amount), new BigDecimal(tax), taxReclaimable, method);
    }

    private DemoData() {}
}
