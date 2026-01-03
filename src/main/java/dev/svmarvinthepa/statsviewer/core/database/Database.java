package dev.svmarvinthepa.statsviewer.core.database;

import java.sql.Connection;

public interface Database {
    void connect();
    void setup();
    void close();
    Connection getConnection();
}
