package com.ominouschestlock.paper;

import org.sqlite.SQLiteDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class SqliteLockRepository extends SqlLockRepository {
    SqliteLockRepository(File sqliteFile) {
        super(createDataSource(sqliteFile), "sqlite");
    }

    private static SQLiteDataSource createDataSource(File sqliteFile) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
        return dataSource;
    }

    @Override
    protected void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS ocl_locks ("
                            + "location_key TEXT PRIMARY KEY,"
                            + "key_name TEXT NOT NULL,"
                            + "creator_name TEXT NULL,"
                            + "creator_uuid TEXT NULL,"
                            + "last_user_name TEXT NULL,"
                            + "last_user_uuid TEXT NULL,"
                            + "normal_key INTEGER NOT NULL,"
                            + "normal_armed INTEGER NOT NULL,"
                            + "last_pick_user_name TEXT NULL,"
                            + "last_pick_user_uuid TEXT NULL,"
                            + "last_pick_type TEXT NULL,"
                            + "last_pick_timestamp INTEGER NOT NULL,"
                            + "rusty_limit INTEGER NOT NULL,"
                            + "rusty_attempts INTEGER NOT NULL,"
                            + "normal_limit INTEGER NOT NULL,"
                            + "normal_attempts INTEGER NOT NULL,"
                            + "silence_limit INTEGER NOT NULL,"
                            + "silence_attempts INTEGER NOT NULL,"
                            + "silence_over_limit_attempts INTEGER NOT NULL,"
                            + "silence_penalty_timestamp INTEGER NOT NULL,"
                            + "world_name TEXT NULL,"
                            + "x INTEGER NULL,"
                            + "y INTEGER NULL,"
                            + "z INTEGER NULL,"
                            + "world_realm TEXT NULL,"
                            + "world_uuid TEXT NULL"
                            + ")"
            );
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS ocl_lock_pick_states ("
                            + "location_key TEXT NOT NULL,"
                            + "player_uuid TEXT NOT NULL,"
                            + "rusty_limit INTEGER NOT NULL,"
                            + "rusty_attempts INTEGER NOT NULL,"
                            + "normal_limit INTEGER NOT NULL,"
                            + "normal_attempts INTEGER NOT NULL,"
                            + "silence_limit INTEGER NOT NULL,"
                            + "silence_attempts INTEGER NOT NULL,"
                            + "silence_over_limit_attempts INTEGER NOT NULL,"
                            + "silence_penalty_timestamp INTEGER NOT NULL,"
                            + "PRIMARY KEY (location_key, player_uuid)"
                            + ")"
            );
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS ocl_lock_minigame ("
                            + "location_key TEXT PRIMARY KEY,"
                            + "type TEXT NOT NULL,"
                            + "pins INTEGER NOT NULL,"
                            + "depths INTEGER NOT NULL,"
                            + "secret TEXT NOT NULL,"
                            + "created_timestamp INTEGER NOT NULL,"
                            + "salt_version INTEGER NOT NULL"
                            + ")"
            );
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ocl_locks_key_name ON ocl_locks (key_name)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ocl_locks_world_xyz ON ocl_locks (world_name, x, y, z)");
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
                + "ON CONFLICT(location_key) DO UPDATE SET "
                + "key_name=excluded.key_name,"
                + "creator_name=excluded.creator_name,"
                + "creator_uuid=excluded.creator_uuid,"
                + "last_user_name=excluded.last_user_name,"
                + "last_user_uuid=excluded.last_user_uuid,"
                + "normal_key=excluded.normal_key,"
                + "normal_armed=excluded.normal_armed,"
                + "last_pick_user_name=excluded.last_pick_user_name,"
                + "last_pick_user_uuid=excluded.last_pick_user_uuid,"
                + "last_pick_type=excluded.last_pick_type,"
                + "last_pick_timestamp=excluded.last_pick_timestamp,"
                + "rusty_limit=excluded.rusty_limit,"
                + "rusty_attempts=excluded.rusty_attempts,"
                + "normal_limit=excluded.normal_limit,"
                + "normal_attempts=excluded.normal_attempts,"
                + "silence_limit=excluded.silence_limit,"
                + "silence_attempts=excluded.silence_attempts,"
                + "silence_over_limit_attempts=excluded.silence_over_limit_attempts,"
                + "silence_penalty_timestamp=excluded.silence_penalty_timestamp,"
                + "world_name=excluded.world_name,"
                + "x=excluded.x,"
                + "y=excluded.y,"
                + "z=excluded.z,"
                + "world_realm=excluded.world_realm,"
                + "world_uuid=excluded.world_uuid";
    }
}
