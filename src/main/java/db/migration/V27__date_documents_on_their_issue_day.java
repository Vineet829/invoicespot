package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

// The day each document is dated. Until now it was the day the document was
// created, read in its business's time zone; existing documents keep exactly
// that day. From now on the day is chosen when a document is made, and may be
// earlier than the day it was typed in.
//
// The day is worked out here in Java rather than in SQL because time zones
// are saved as the names Java accepts: the database doesn't know some of them
// (Asia/Calcutta, the name Chrome gives India) and reads others the other way
// round (UTC+05:30).
public class V27__date_documents_on_their_issue_day extends BaseJavaMigration {

    private static final int BATCH = 1000;

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE documents ADD COLUMN issue_date DATE");
        }

        Map<Long, ZoneId> zones = new HashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet settings = statement.executeQuery("SELECT business_pkid, time_zone FROM business_settings")) {
            while (settings.next()) {
                zones.put(settings.getLong("business_pkid"), ZoneId.of(settings.getString("time_zone")));
            }
        }

        try (Statement statement = connection.createStatement();
                ResultSet documents = statement.executeQuery("SELECT pkid, business_pkid, created_at FROM documents");
                PreparedStatement update =
                        connection.prepareStatement("UPDATE documents SET issue_date = ? WHERE pkid = ?")) {
            int pending = 0;
            while (documents.next()) {
                ZoneId zone = zones.getOrDefault(documents.getLong("business_pkid"), ZoneOffset.UTC);
                OffsetDateTime created = documents.getObject("created_at", OffsetDateTime.class);
                update.setObject(1, created.atZoneSameInstant(zone).toLocalDate());
                update.setLong(2, documents.getLong("pkid"));
                update.addBatch();
                if (++pending == BATCH) {
                    update.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                update.executeBatch();
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE documents ALTER COLUMN issue_date SET NOT NULL");
            statement.execute(
                    "CREATE INDEX idx_documents_business_issue_date ON documents (business_pkid, issue_date)");
        }
    }
}
