package net.inertiamc.anythinginabucket.storage;

import net.inertiamc.core.storage.DatabaseSettings;
import net.inertiamc.core.storage.TokenLedger;
import net.inertiamc.core.storage.WriteQueue;

import java.io.File;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The database and the single thread that writes to it. Every change is queued as it happens. A failed write is
 * retried once a second (up to 30 times), so a short database outage does not lose a caught mob. {@link #close()}
 * drains the writer before closing the pool.
 */
public final class BucketStorage {

    /** One database write. */
    @FunctionalInterface
    public interface Write {
        void run(BucketDatabase database) throws SQLException;
    }

    private static final int RETRIES = 30;

    private final BucketDatabase database;
    private final Logger logger;
    /** A single thread, so a bucket's catch is always written before its release. */
    private final WriteQueue writer;
    private volatile boolean failing;

    private BucketStorage(BucketDatabase database, Logger logger) {
        this.database = database;
        this.logger = logger;
        this.writer = new WriteQueue("AnythingInABucket-storage-writer", logger);
    }

    /** Opens the database and creates the tables. Throws when it cannot be reached. */
    public static BucketStorage open(File dataFolder, DatabaseSettings settings, Logger logger) throws SQLException {
        BucketDatabase database = BucketDatabase.open(dataFolder, settings);
        try {
            database.createTables();
        } catch (SQLException e) {
            database.close();
            throw e;
        }
        logger.info("Storage: " + database.type() + ".");
        return new BucketStorage(database, logger);
    }

    /** For reads. Runs on the calling thread, so never call it from the server thread. */
    public BucketDatabase database() {
        return database;
    }

    /** The item code ledger, loaded at enable and written by the same thread. */
    public TokenLedger itemTokens() throws SQLException {
        return database.itemTokens(writer, logger);
    }

    /**
     * Runs {@code read} on the writer thread, after every write queued before it, so a release right after a catch
     * still finds the mob.
     */
    public void afterWrites(Runnable read) {
        writer.submit(read);
    }

    public void write(Write write) {
        writer.submit(() -> attempt(write));
    }

    private void attempt(Write write) {
        for (int tries = 0; tries <= RETRIES; tries++) {
            try {
                write.run(database);
                if (failing) {
                    failing = false;
                    logger.info("The " + database.type() + " database is reachable again.");
                }
                return;
            } catch (SQLException e) {
                if (!failing) {
                    failing = true;
                    logger.log(Level.SEVERE, "Could not write to the " + database.type() + " database; trying again.", e);
                }
                if (!writer.isAccepting()) return;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException stop) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        logger.severe("A write to the " + database.type() + " database was given up after " + RETRIES + " tries.");
    }

    /** Drains the writer, then closes the pool. Idempotent. */
    public void close() {
        if (!writer.isAccepting()) return;
        writer.drain();
        database.close();
    }
}
