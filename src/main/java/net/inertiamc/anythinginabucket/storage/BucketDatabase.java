package net.inertiamc.anythinginabucket.storage;

import net.inertiamc.core.storage.ConnectionPool;
import net.inertiamc.core.storage.DatabaseSettings;
import net.inertiamc.core.storage.TokenLedger;
import net.inertiamc.core.storage.WriteQueue;
import net.inertiamc.anythinginabucket.models.StoredMob;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * SQL access. Two tables: {@code mobs} (every caught mob not yet released) and {@code item_tokens} (core's item codes).
 * {@code REPLACE INTO} works the same on SQLite and MySQL.
 */
public final class BucketDatabase {

    private final ConnectionPool pool;
    private final boolean mysql;
    private final String prefix;

    BucketDatabase(ConnectionPool pool, boolean mysql, String prefix) {
        this.pool = pool;
        this.mysql = mysql;
        this.prefix = prefix;
    }

    /** SQLite at data/database.db. A pool of 3 on MySQL: the writer, the item codes and the reads. */
    public static BucketDatabase open(File dataFolder, DatabaseSettings s) {
        return new BucketDatabase(s.open(dataFolder, "AnythingInABucket", 3), s.mysql(), s.activePrefix());
    }

    public String type() {
        return mysql ? "MySQL" : "SQLite";
    }

    private String table(String name) {
        return prefix + name;
    }

    public void createTables() throws SQLException {
        // MEDIUMTEXT on MySQL, since a chest donkey full of shulker boxes can exceed TEXT's 64 KB.
        String text = mysql ? "MEDIUMTEXT" : "TEXT";
        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("mobs") + " ("
                    + "id VARCHAR(36) NOT NULL PRIMARY KEY, type VARCHAR(64) NOT NULL, format VARCHAR(16) NOT NULL, "
                    + "data " + text + " NOT NULL, data_version INT NOT NULL, bucket VARCHAR(16) NOT NULL, "
                    + "caught_by VARCHAR(36) NOT NULL, caught_at BIGINT NOT NULL)");
        }
    }

    public TokenLedger itemTokens(WriteQueue writes, Logger logger) throws SQLException {
        return new TokenLedger(pool, prefix + "item_tokens", writes, logger);
    }

    /** The mob in the bucket, or null when there is none (released, or lost with its item). */
    public StoredMob mob(UUID id) throws SQLException {
        String sql = "SELECT id, type, format, data, data_version, bucket, caught_by, caught_at FROM " + table("mobs")
                + " WHERE id = ?";
        try (Connection connection = pool.getConnection(); PreparedStatement select = connection.prepareStatement(sql)) {
            select.setString(1, id.toString());
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) return null;
                return new StoredMob(UUID.fromString(rows.getString("id")), rows.getString("type"), rows.getString("format"),
                        rows.getString("data"), rows.getInt("data_version"), rows.getString("bucket"),
                        UUID.fromString(rows.getString("caught_by")), rows.getLong("caught_at"));
            }
        }
    }

    public void save(StoredMob mob) throws SQLException {
        String sql = "REPLACE INTO " + table("mobs")
                + " (id, type, format, data, data_version, bucket, caught_by, caught_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = pool.getConnection(); PreparedStatement insert = connection.prepareStatement(sql)) {
            insert.setString(1, mob.id().toString());
            insert.setString(2, mob.type());
            insert.setString(3, mob.format());
            insert.setString(4, mob.data());
            insert.setInt(5, mob.dataVersion());
            insert.setString(6, mob.bucket());
            insert.setString(7, mob.caughtBy().toString());
            insert.setLong(8, mob.caughtAt());
            insert.executeUpdate();
        }
    }

    public void delete(UUID id) throws SQLException {
        try (Connection connection = pool.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM " + table("mobs") + " WHERE id = ?")) {
            delete.setString(1, id.toString());
            delete.executeUpdate();
        }
    }

    public void close() {
        pool.shutdown();
    }
}
