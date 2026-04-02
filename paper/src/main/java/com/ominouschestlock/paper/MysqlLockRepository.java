package com.ominouschestlock.paper;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class MysqlLockRepository extends SqlLockRepository {
    private final HikariDataSource dataSource;

    MysqlLockRepository(StorageConfig.MysqlSettings mysql) {
        this(createDataSource(mysql));
    }

    private MysqlLockRepository(HikariDataSource dataSource) {
        super(dataSource, "mysql");
        this.dataSource = dataSource;
    }

    private static HikariDataSource createDataSource(StorageConfig.MysqlSettings mysql) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + mysql.host() + ":" + mysql.port() + "/" + mysql.database() + mysql.parameters());
        config.setUsername(mysql.username());
        config.setPassword(mysql.password());
        config.setMaximumPoolSize(mysql.maximumPoolSize());
        config.setMinimumIdle(mysql.minimumIdle());
        config.setConnectionTimeout(mysql.connectionTimeoutMs());
        config.setPoolName("OCL-MySQL");
        return new HikariDataSource(config);
    }

    @Override
    protected void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS ocl_locks ("
                            + "location_key VARCHAR(255) NOT NULL PRIMARY KEY,"
                            + "key_name VARCHAR(255) NOT NULL,"
                            + "creator_name VARCHAR(255) NULL,"
                            + "creator_uuid CHAR(36) NULL,"
                            + "last_user_name VARCHAR(255) NULL,"
                            + "last_user_uuid CHAR(36) NULL,"
                            + "normal_key BOOLEAN NOT NULL,"
                            + "normal_armed BOOLEAN NOT NULL,"
                            + "last_pick_user_name VARCHAR(255) NULL,"
                            + "last_pick_user_uuid CHAR(36) NULL,"
                            + "last_pick_type VARCHAR(64) NULL,"
                            + "last_pick_timestamp BIGINT NOT NULL,"
                            + "rusty_limit INT NOT NULL,"
                            + "rusty_attempts INT NOT NULL,"
                            + "normal_limit INT NOT NULL,"
                            + "normal_attempts INT NOT NULL,"
                            + "silence_limit INT NOT NULL,"
                            + "silence_attempts INT NOT NULL,"
                            + "silence_over_limit_attempts INT NOT NULL,"
                            + "silence_penalty_timestamp BIGINT NOT NULL,"
                            + "world_name VARCHAR(128) NULL,"
                            + "x INT NULL,"
                            + "y INT NULL,"
                            + "z INT NULL,"
                            + "world_realm VARCHAR(32) NULL,"
                            + "world_uuid CHAR(36) NULL,"
                            + "INDEX idx_ocl_locks_key_name (key_name),"
                            + "INDEX idx_ocl_locks_world_xyz (world_name, x, y, z)"
                            + ") ENGINE=InnoDB"
            );
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS ocl_lock_pick_states ("
                            + "location_key VARCHAR(255) NOT NULL,"
                            + "player_uuid CHAR(36) NOT NULL,"
                            + "rusty_limit INT NOT NULL,"
                            + "rusty_attempts INT NOT NULL,"
                            + "normal_limit INT NOT NULL,"
                            + "normal_attempts INT NOT NULL,"
                            + "silence_limit INT NOT NULL,"
                            + "silence_attempts INT NOT NULL,"
                            + "silence_over_limit_attempts INT NOT NULL,"
                            + "silence_penalty_timestamp BIGINT NOT NULL,"
                            + "PRIMARY KEY (location_key, player_uuid),"
                            + "CONSTRAINT fk_pick_states_lock FOREIGN KEY (location_key) REFERENCES ocl_locks(location_key) ON DELETE CASCADE"
                            + ") ENGINE=InnoDB"
            );
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS ocl_lock_minigame ("
                            + "location_key VARCHAR(255) NOT NULL PRIMARY KEY,"
                            + "type VARCHAR(32) NOT NULL,"
                            + "pins INT NOT NULL,"
                            + "depths INT NOT NULL,"
                            + "secret VARCHAR(255) NOT NULL,"
                            + "created_timestamp BIGINT NOT NULL,"
                            + "salt_version INT NOT NULL,"
                            + "CONSTRAINT fk_minigame_lock FOREIGN KEY (location_key) REFERENCES ocl_locks(location_key) ON DELETE CASCADE"
                            + ") ENGINE=InnoDB"
            );
        }
    }

    @Override
    protected String upsertLockSql() {
        return "INSERT INTO ocl_locks ("
                + "location_key,key_name,creator_name,creator_uuid,last_user_name,last_user_uuid,normal_key,normal_armed,"
                + "last_pick_user_name,last_pick_user_uuid,last_pick_type,last_pick_timestamp,"
                + "rusty_limit,rusty_attempts,normal_limit,normal_attempts,silence_limit,silence_attempts,"
                + "silence_over_limit_attempts,silence_penalty_timestamp,world_name,x,y,z,world_realm,world_uuid"
                + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE "
                + "key_name=VALUES(key_name),"
                + "creator_name=VALUES(creator_name),"
                + "creator_uuid=VALUES(creator_uuid),"
                + "last_user_name=VALUES(last_user_name),"
                + "last_user_uuid=VALUES(last_user_uuid),"
                + "normal_key=VALUES(normal_key),"
                + "normal_armed=VALUES(normal_armed),"
                + "last_pick_user_name=VALUES(last_pick_user_name),"
                + "last_pick_user_uuid=VALUES(last_pick_user_uuid),"
                + "last_pick_type=VALUES(last_pick_type),"
                + "last_pick_timestamp=VALUES(last_pick_timestamp),"
                + "rusty_limit=VALUES(rusty_limit),"
                + "rusty_attempts=VALUES(rusty_attempts),"
                + "normal_limit=VALUES(normal_limit),"
                + "normal_attempts=VALUES(normal_attempts),"
                + "silence_limit=VALUES(silence_limit),"
                + "silence_attempts=VALUES(silence_attempts),"
                + "silence_over_limit_attempts=VALUES(silence_over_limit_attempts),"
                + "silence_penalty_timestamp=VALUES(silence_penalty_timestamp),"
                + "world_name=VALUES(world_name),"
                + "x=VALUES(x),"
                + "y=VALUES(y),"
                + "z=VALUES(z),"
                + "world_realm=VALUES(world_realm),"
                + "world_uuid=VALUES(world_uuid)";
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
