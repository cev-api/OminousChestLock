package com.ominouschestlock.paper;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

final class StorageConfig {
    private final String type;
    private final File sqliteFile;
    private final MysqlSettings mysql;

    private StorageConfig(String type, File sqliteFile, MysqlSettings mysql) {
        this.type = type;
        this.sqliteFile = sqliteFile;
        this.mysql = mysql;
    }

    String type() {
        return type;
    }

    File sqliteFile() {
        return sqliteFile;
    }

    MysqlSettings mysql() {
        return mysql;
    }

    static StorageConfig from(FileConfiguration config, File dataFolder) throws LockRepositoryException {
        String type = config.getString("storage.type", "yaml");
        if (type == null || type.isBlank()) {
            type = "yaml";
        }
        type = type.toLowerCase();
        if (!type.equals("yaml") && !type.equals("sqlite") && !type.equals("mysql")) {
            throw new LockRepositoryException("Invalid storage.type '" + type + "'. Use yaml, sqlite, or mysql.");
        }

        String sqlitePath = config.getString("storage.sqlite.file", "plugins/OminousChestLock/data.db");
        if (sqlitePath == null || sqlitePath.isBlank()) {
            sqlitePath = "plugins/OminousChestLock/data.db";
        }
        File sqliteFile = new File(sqlitePath);
        if (!sqliteFile.isAbsolute()) {
            sqliteFile = new File(dataFolder.getParentFile(), sqlitePath);
        }
        File sqliteParent = sqliteFile.getParentFile();
        if (sqliteParent != null && !sqliteParent.exists() && !sqliteParent.mkdirs()) {
            throw new LockRepositoryException("Could not create SQLite directory: " + sqliteParent.getAbsolutePath());
        }

        String host = required(config, "storage.mysql.host");
        int port = Math.max(1, config.getInt("storage.mysql.port", 3306));
        String database = required(config, "storage.mysql.database");
        String username = required(config, "storage.mysql.username");
        String password = config.getString("storage.mysql.password", "");
        String parameters = config.getString("storage.mysql.parameters", "?useSSL=false&autoReconnect=true");
        if (parameters == null || parameters.isBlank()) {
            parameters = "?useSSL=false&autoReconnect=true";
        }
        if (!parameters.startsWith("?")) {
            parameters = "?" + parameters;
        }
        int maxPool = Math.max(1, config.getInt("storage.mysql.pool.maximumPoolSize", 10));
        int minIdle = Math.max(0, config.getInt("storage.mysql.pool.minimumIdle", 2));
        if (minIdle > maxPool) {
            minIdle = maxPool;
        }
        long timeout = Math.max(1000L, config.getLong("storage.mysql.pool.connectionTimeoutMs", 30000L));
        MysqlSettings mysql = new MysqlSettings(host, port, database, username, password, parameters, maxPool, minIdle, timeout);
        return new StorageConfig(type, sqliteFile, mysql);
    }

    private static String required(FileConfiguration config, String path) {
        String value = config.getString(path, "");
        return value == null ? "" : value.trim();
    }

    record MysqlSettings(String host, int port, String database, String username, String password, String parameters,
                         int maximumPoolSize, int minimumIdle, long connectionTimeoutMs) {
        boolean isConfigured() {
            return !host.isBlank() && !database.isBlank() && !username.isBlank();
        }
    }
}
