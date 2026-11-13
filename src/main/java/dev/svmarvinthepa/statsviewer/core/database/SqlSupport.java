package dev.svmarvinthepa.statsviewer.core.database;

import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

/**
 * Small helpers for modules that own their table instead of having it
 * declared in {@link SQLiteDatabase} / {@link MariaDBDatabase}.
 */
public final class SqlSupport {

    private SqlSupport() {}

    public static boolean isSqlite(DatabaseManager databaseManager) {
        return databaseManager.getDatabase() instanceof SQLiteDatabase;
    }

    /**
     * Creates the table (idempotent) in the dialect of the active database,
     * then the indexes. A failing index is only logged: the table itself is
     * what a module needs to work.
     *
     * @return false when the table could not be created
     */
    public static boolean createTable(
            DatabaseManager databaseManager,
            String table,
            String sqliteDdl,
            String mariaDbDdl,
            String... indexedColumns
    ) {
        try (Connection c = databaseManager.getDatabase().getConnection();
             Statement st = c.createStatement()) {

            st.execute(isSqlite(databaseManager) ? sqliteDdl : mariaDbDdl);

            for (String column : indexedColumns) {
                try {
                    st.execute("CREATE INDEX IF NOT EXISTS idx_" + table + "_" + column
                            + " ON " + table + " (" + column + ")");
                } catch (SQLException e) {
                    DebugLogger.warn("Could not create index on " + table + "." + column
                            + ": " + e.getMessage());
                }
            }
            return true;

        } catch (Exception e) {
            DebugLogger.error("Failed to create table " + table, e);
            return false;
        }
    }

    /** Cuts a value to the column size so an unexpectedly long name never fails the insert. */
    public static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    public static void setString(PreparedStatement ps, int index, String value, int maxLength)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, limit(value, maxLength));
        }
    }

    public static void setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    public static void setLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    public static void setBool(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value ? 1 : 0);
        }
    }
}
