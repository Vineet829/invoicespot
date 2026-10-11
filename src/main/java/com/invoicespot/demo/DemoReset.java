package com.invoicespot.demo;

import com.invoicespot.business.BusinessService;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Clears the demo account's own business, once it holds the sample data, of
// every record its sample data or a visitor put in it, so that the seeder can
// add a fresh sample set dated from today: documents and all that hangs off
// them, clients, items, expenses, the business's notifications and email
// templates, and its settings, so that numbering starts over. Nothing outside
// that business is touched, and the business itself, its members, the demo
// account, its sessions and the audit log stay. The demo account can't invite
// anyone, so its business has no invitations to clear.
@Component
public class DemoReset {

    // Every table the business's records are in, rows before the rows they
    // point at, so that no foreign key is left pointing at a row already gone.
    // Each statement takes the business as its one value.
    private static final String DOCUMENTS = "(select pkid from documents where business_pkid = ?)";
    private static final List<String> STATEMENTS = List.of(
            "delete from recurring_runs where schedule_pkid in"
                    + " (select pkid from recurring_schedules where business_pkid = ?)",
            "delete from recurring_schedules where business_pkid = ?",
            "delete from payment_claims where business_pkid = ?",
            "delete from notifications where business_pkid = ?",
            "delete from document_events where document_pkid in " + DOCUMENTS,
            "delete from share_links where document_pkid in " + DOCUMENTS,
            "delete from refunds where document_pkid in " + DOCUMENTS,
            "delete from payments where document_pkid in " + DOCUMENTS,
            "delete from document_tax_summaries where document_pkid in " + DOCUMENTS,
            "delete from document_billing_items where document_pkid in " + DOCUMENTS,
            // Credit notes and copies point at documents of the same business,
            // so they all go in one statement.
            "delete from documents where business_pkid = ?",
            "delete from portal_links where business_pkid = ?",
            "delete from customers where business_pkid = ?",
            "delete from catalog_items where business_pkid = ?",
            "delete from expenses where business_pkid = ?",
            "delete from email_templates where business_pkid = ?",
            "delete from business_settings where business_pkid = ?");

    private final UserRepository userRepository;
    private final BusinessService businessService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public DemoReset(
            UserRepository userRepository,
            BusinessService businessService,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.businessService = businessService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    // How many records were cleared, or why none were.
    public record Result(boolean cleared, String reason, int records) {

        static Result skipped(String reason) {
            return new Result(false, reason, 0);
        }
    }

    // All in one transaction, or in the caller's when it has one.
    public Result clear(String demoEmail) {
        Optional<User> demo = userRepository.findByEmailIgnoreCase(demoEmail == null ? "" : demoEmail.trim());
        if (demo.isEmpty()) {
            return Result.skipped("the demo account " + demoEmail + " does not exist");
        }
        Long businessPkid = businessService.ownedBy(demo.get().getPkid()).getPkid();
        // Only the business the sample data was added to is cleared, so a demo
        // email set to someone's real account never wipes it. It was marked
        // then, where nothing in the app changes the mark.
        if (!hasSampleData(businessPkid)) {
            return Result.skipped("the account " + demoEmail + " holds no sample data, so it was left as it was");
        }
        int records = transactionTemplate.execute(status -> STATEMENTS.stream()
                .mapToInt(statement -> jdbcTemplate.update(statement, businessPkid))
                .sum());
        return new Result(true, null, records);
    }

    private boolean hasSampleData(Long businessPkid) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select sample_data from businesses where pkid = ?", Boolean.class, businessPkid));
    }
}
