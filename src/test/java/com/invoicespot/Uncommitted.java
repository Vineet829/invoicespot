package com.invoicespot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

// A change another request makes to a row, made by a transaction of the
// test's own and left uncommitted while the request under test runs: once the
// request waits for it, the change commits, and the request carries on.
public final class Uncommitted {

    private Uncommitted() {}

    public static <T> T change(DataSource dataSource, String change, Object row, Callable<T> request)
            throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection holder = dataSource.getConnection(); Connection watcher = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            // Each look is a transaction of its own: one sees the sessions as
            // they were when it began.
            watcher.setAutoCommit(true);
            try (PreparedStatement statement = holder.prepareStatement(change)) {
                statement.setObject(1, row);
                statement.executeUpdate();
            }
            int holderPid = pid(holder);
            Future<T> result = pool.submit(request);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!blocks(watcher, holderPid)) {
                if (result.isDone()) {
                    holder.rollback();
                    throw new AssertionError("The request finished without waiting for the change: " + result.get());
                }
                if (System.nanoTime() > deadline) {
                    throw new AssertionError("The request never waited for the change");
                }
                Thread.sleep(20);
            }
            holder.commit();
            return result.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private static int pid(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select pg_backend_pid()");
                ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getInt(1);
        }
    }

    // Whether some other session waits on a lock the holder has.
    private static boolean blocks(Connection watcher, int holderPid) throws SQLException {
        try (PreparedStatement statement = watcher.prepareStatement(
                "select count(*) from pg_stat_activity where ? = any(pg_blocking_pids(pid))")) {
            statement.setInt(1, holderPid);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1) > 0;
            }
        }
    }
}
