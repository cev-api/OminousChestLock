package com.ominouschestlock.paper;

import org.bukkit.Bukkit;
import org.bukkit.World;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

abstract class SqlLockRepository implements LockRepository {
    private final DataSource dataSource;
    private final String backendName;

    SqlLockRepository(DataSource dataSource, String backendName) {
        this.dataSource = dataSource;
        this.backendName = backendName;
    }

    @Override
    public String backendName() {
        return backendName;
    }

    @Override
    public void initialize() throws LockRepositoryException {
        try (Connection connection = dataSource.getConnection()) {
            createSchema(connection);
        } catch (SQLException exception) {
            throw new LockRepositoryException("Failed to initialize " + backendName + " storage schema.", exception);
        }
    }

    @Override
    public Map<String, LockInfo> loadAll() throws LockRepositoryException {
        Map<String, LockInfoBuilder> builders = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT location_key,key_name,creator_name,creator_uuid,last_user_name,last_user_uuid,normal_key,normal_armed,"
                            + "last_pick_user_name,last_pick_user_uuid,last_pick_type,last_pick_timestamp,"
                            + "rusty_limit,rusty_attempts,normal_limit,normal_attempts,silence_limit,silence_attempts,"
                            + "silence_over_limit_attempts,silence_penalty_timestamp "
                            + "FROM ocl_locks")) {
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        String locationKey = rs.getString("location_key");
                        if (locationKey == null || locationKey.isBlank()) {
                            continue;
                        }
                        String keyName = rs.getString("key_name");
                        if (keyName == null || keyName.isBlank()) {
                            continue;
                        }
                        LockInfoBuilder builder = new LockInfoBuilder();
                        builder.keyName = keyName;
                        builder.creatorName = rs.getString("creator_name");
                        builder.creatorUuid = YamlStorageCodec.parseUuid(rs.getString("creator_uuid"));
                        builder.lastUserName = rs.getString("last_user_name");
                        builder.lastUserUuid = YamlStorageCodec.parseUuid(rs.getString("last_user_uuid"));
                        builder.normalKey = rs.getBoolean("normal_key");
                        builder.normalArmed = rs.getBoolean("normal_armed");
                        builder.lastPickUserName = rs.getString("last_pick_user_name");
                        if (builder.lastPickUserName != null && builder.lastPickUserName.isBlank()) {
                            builder.lastPickUserName = null;
                        }
                        builder.lastPickUserUuid = YamlStorageCodec.parseUuid(rs.getString("last_pick_user_uuid"));
                        builder.lastPickType = rs.getString("last_pick_type");
                        if (builder.lastPickType != null && builder.lastPickType.isBlank()) {
                            builder.lastPickType = null;
                        }
                        builder.lastPickTimestamp = rs.getLong("last_pick_timestamp");
                        builder.rustyLimit = rs.getInt("rusty_limit");
                        builder.rustyAttempts = rs.getInt("rusty_attempts");
                        builder.normalLimit = rs.getInt("normal_limit");
                        builder.normalAttempts = rs.getInt("normal_attempts");
                        builder.silenceLimit = rs.getInt("silence_limit");
                        builder.silenceAttempts = rs.getInt("silence_attempts");
                        builder.silenceOverLimitAttempts = rs.getInt("silence_over_limit_attempts");
                        builder.silencePenaltyTimestamp = rs.getLong("silence_penalty_timestamp");
                        builders.put(locationKey, builder);
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT location_key,player_uuid,rusty_limit,rusty_attempts,normal_limit,normal_attempts,"
                            + "silence_limit,silence_attempts,silence_over_limit_attempts,silence_penalty_timestamp "
                            + "FROM ocl_lock_pick_states")) {
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        String locationKey = rs.getString("location_key");
                        LockInfoBuilder builder = builders.get(locationKey);
                        if (builder == null) {
                            continue;
                        }
                        UUID playerUuid = YamlStorageCodec.parseUuid(rs.getString("player_uuid"));
                        if (playerUuid == null) {
                            continue;
                        }
                        builder.playerPickStates.put(playerUuid, new PickState(
                                rs.getInt("rusty_limit"),
                                rs.getInt("rusty_attempts"),
                                rs.getInt("normal_limit"),
                                rs.getInt("normal_attempts"),
                                rs.getInt("silence_limit"),
                                rs.getInt("silence_attempts"),
                                rs.getInt("silence_over_limit_attempts"),
                                rs.getLong("silence_penalty_timestamp")
                        ));
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT location_key,type,pins,depths,secret,created_timestamp,salt_version FROM ocl_lock_minigame")) {
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        String locationKey = rs.getString("location_key");
                        LockInfoBuilder builder = builders.get(locationKey);
                        if (builder == null) {
                            continue;
                        }
                        String type = rs.getString("type");
                        int pins = rs.getInt("pins");
                        int depths = rs.getInt("depths");
                        int[] secret = decodeSecret(rs.getString("secret"), pins, depths);
                        if (type != null && !type.isBlank() && secret.length == Math.max(1, pins)) {
                            builder.minigameData = new LockMinigameData(
                                    type,
                                    Math.max(1, pins),
                                    Math.max(1, depths),
                                    secret,
                                    rs.getLong("created_timestamp"),
                                    rs.getInt("salt_version")
                            );
                        }
                    }
                }
            }
        } catch (SQLException exception) {
            throw new LockRepositoryException("Failed to load lock data from " + backendName + ".", exception);
        }

        Map<String, LockInfo> result = new LinkedHashMap<>();
        for (Map.Entry<String, LockInfoBuilder> entry : builders.entrySet()) {
            result.put(entry.getKey(), entry.getValue().build());
        }
        return result;
    }

    @Override
    public void saveAll(Map<String, LockInfo> locks) throws LockRepositoryException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM ocl_lock_pick_states");
                    statement.executeUpdate("DELETE FROM ocl_lock_minigame");
                    statement.executeUpdate("DELETE FROM ocl_locks");
                }
                insertOrUpdateAll(connection, locks);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new LockRepositoryException("Failed to save lock data to " + backendName + ".", exception);
        }
    }

    @Override
    public void upsertAll(Map<String, LockInfo> locks) throws LockRepositoryException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertOrUpdateAll(connection, locks);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new LockRepositoryException("Failed to upsert lock data to " + backendName + ".", exception);
        }
    }

    private void insertOrUpdateAll(Connection connection, Map<String, LockInfo> locks) throws SQLException {
        try (PreparedStatement lockStatement = connection.prepareStatement(upsertLockSql());
             PreparedStatement deletePickStates = connection.prepareStatement("DELETE FROM ocl_lock_pick_states WHERE location_key = ?");
             PreparedStatement deleteMinigame = connection.prepareStatement("DELETE FROM ocl_lock_minigame WHERE location_key = ?");
             PreparedStatement pickStateStatement = connection.prepareStatement(
                     "INSERT INTO ocl_lock_pick_states (location_key,player_uuid,rusty_limit,rusty_attempts,normal_limit,normal_attempts,"
                             + "silence_limit,silence_attempts,silence_over_limit_attempts,silence_penalty_timestamp) "
                             + "VALUES (?,?,?,?,?,?,?,?,?,?)");
             PreparedStatement minigameStatement = connection.prepareStatement(
                     "INSERT INTO ocl_lock_minigame (location_key,type,pins,depths,secret,created_timestamp,salt_version) "
                             + "VALUES (?,?,?,?,?,?,?)")) {
            for (Map.Entry<String, LockInfo> entry : locks.entrySet()) {
                String locationKey = entry.getKey();
                LockInfo info = entry.getValue();
                if (locationKey == null || locationKey.isBlank() || info == null || info.keyName() == null || info.keyName().isBlank()) {
                    continue;
                }
                LocationData locationData = parseLocationKey(locationKey);

                lockStatement.setString(1, locationKey);
                lockStatement.setString(2, info.keyName());
                lockStatement.setString(3, valueOrNull(info.creatorName()));
                lockStatement.setString(4, uuidOrNull(info.creatorUuid()));
                lockStatement.setString(5, valueOrNull(info.lastUserName()));
                lockStatement.setString(6, uuidOrNull(info.lastUserUuid()));
                lockStatement.setBoolean(7, info.normalKey());
                lockStatement.setBoolean(8, info.normalArmed());
                lockStatement.setString(9, valueOrNull(info.lastPickUserName()));
                lockStatement.setString(10, uuidOrNull(info.lastPickUserUuid()));
                lockStatement.setString(11, valueOrNull(info.lastPickType()));
                lockStatement.setLong(12, info.lastPickTimestamp());
                lockStatement.setInt(13, info.rustyLimit());
                lockStatement.setInt(14, info.rustyAttempts());
                lockStatement.setInt(15, info.normalLimit());
                lockStatement.setInt(16, info.normalAttempts());
                lockStatement.setInt(17, info.silenceLimit());
                lockStatement.setInt(18, info.silenceAttempts());
                lockStatement.setInt(19, info.silenceOverLimitAttempts());
                lockStatement.setLong(20, info.silencePenaltyTimestamp());
                if (locationData != null) {
                    lockStatement.setString(21, valueOrNull(locationData.worldName()));
                    lockStatement.setInt(22, locationData.x());
                    lockStatement.setInt(23, locationData.y());
                    lockStatement.setInt(24, locationData.z());
                    lockStatement.setString(25, valueOrNull(locationData.realm()));
                    lockStatement.setString(26, uuidOrNull(locationData.worldUuid()));
                } else {
                    lockStatement.setNull(21, Types.VARCHAR);
                    lockStatement.setNull(22, Types.INTEGER);
                    lockStatement.setNull(23, Types.INTEGER);
                    lockStatement.setNull(24, Types.INTEGER);
                    lockStatement.setNull(25, Types.VARCHAR);
                    lockStatement.setNull(26, Types.VARCHAR);
                }
                lockStatement.executeUpdate();

                deletePickStates.setString(1, locationKey);
                deletePickStates.executeUpdate();
                deleteMinigame.setString(1, locationKey);
                deleteMinigame.executeUpdate();

                for (Map.Entry<UUID, PickState> stateEntry : info.playerPickStates().entrySet()) {
                    UUID playerUuid = stateEntry.getKey();
                    PickState state = stateEntry.getValue();
                    if (playerUuid == null || state == null) {
                        continue;
                    }
                    pickStateStatement.setString(1, locationKey);
                    pickStateStatement.setString(2, playerUuid.toString());
                    pickStateStatement.setInt(3, state.rustyLimit());
                    pickStateStatement.setInt(4, state.rustyAttempts());
                    pickStateStatement.setInt(5, state.normalLimit());
                    pickStateStatement.setInt(6, state.normalAttempts());
                    pickStateStatement.setInt(7, state.silenceLimit());
                    pickStateStatement.setInt(8, state.silenceAttempts());
                    pickStateStatement.setInt(9, state.silenceOverLimitAttempts());
                    pickStateStatement.setLong(10, state.silencePenaltyTimestamp());
                    pickStateStatement.executeUpdate();
                }

                if (info.minigameData() != null) {
                    LockMinigameData mg = info.minigameData();
                    minigameStatement.setString(1, locationKey);
                    minigameStatement.setString(2, mg.type());
                    minigameStatement.setInt(3, mg.pins());
                    minigameStatement.setInt(4, mg.depths());
                    minigameStatement.setString(5, encodeSecret(mg.secret()));
                    minigameStatement.setLong(6, mg.createdTimestamp());
                    minigameStatement.setInt(7, mg.saltVersion());
                    minigameStatement.executeUpdate();
                }
            }
        }
    }

    private static String valueOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String uuidOrNull(UUID value) {
        return value == null ? null : value.toString();
    }

    private LocationData parseLocationKey(String locationKey) {
        if (locationKey == null) {
            return null;
        }
        int idx = locationKey.indexOf(':');
        if (idx <= 0 || idx >= locationKey.length() - 1) {
            return null;
        }
        String worldName = locationKey.substring(0, idx);
        String[] coords = locationKey.substring(idx + 1).split(",");
        if (coords.length != 3) {
            return null;
        }
        try {
            int x = Integer.parseInt(coords[0]);
            int y = Integer.parseInt(coords[1]);
            int z = Integer.parseInt(coords[2]);
            World world = Bukkit.getWorld(worldName);
            String realm = world != null ? mapRealm(world.getEnvironment()) : null;
            UUID worldUuid = world != null ? world.getUID() : null;
            return new LocationData(worldName, x, y, z, realm, worldUuid);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String mapRealm(World.Environment environment) {
        if (environment == null) {
            return null;
        }
        return switch (environment) {
            case NORMAL -> "OVERWORLD";
            case NETHER -> "NETHER";
            case THE_END -> "END";
            default -> environment.name();
        };
    }

    private static String encodeSecret(int[] secret) {
        if (secret == null || secret.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < secret.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(Math.max(0, secret[i]));
        }
        return builder.toString();
    }

    private static int[] decodeSecret(String encoded, int pins, int depths) {
        int safePins = Math.max(1, pins);
        int safeDepths = Math.max(1, depths);
        if (encoded == null || encoded.isBlank()) {
            return new int[0];
        }
        String[] parts = encoded.split(",");
        int[] secret = new int[Math.min(parts.length, safePins)];
        for (int i = 0; i < secret.length; i++) {
            try {
                int value = Integer.parseInt(parts[i]);
                secret[i] = Math.max(0, Math.min(safeDepths - 1, value));
            } catch (NumberFormatException ignored) {
                secret[i] = 0;
            }
        }
        return secret;
    }

    @Override
    public void close() {
        // no-op for plain JDBC data sources
    }

    private static final class LockInfoBuilder {
        private String keyName;
        private String creatorName;
        private UUID creatorUuid;
        private String lastUserName;
        private UUID lastUserUuid;
        private boolean normalKey;
        private boolean normalArmed;
        private String lastPickUserName;
        private UUID lastPickUserUuid;
        private String lastPickType;
        private long lastPickTimestamp;
        private int rustyLimit = -1;
        private int rustyAttempts = 0;
        private int normalLimit = -1;
        private int normalAttempts = 0;
        private int silenceLimit = -1;
        private int silenceAttempts = 0;
        private int silenceOverLimitAttempts = 0;
        private long silencePenaltyTimestamp = 0L;
        private final Map<UUID, PickState> playerPickStates = new HashMap<>();
        private LockMinigameData minigameData;

        private LockInfo build() {
            return new LockInfo(
                    keyName,
                    creatorName,
                    creatorUuid,
                    lastUserName,
                    lastUserUuid,
                    normalKey,
                    normalArmed,
                    lastPickUserName,
                    lastPickUserUuid,
                    lastPickType,
                    lastPickTimestamp,
                    rustyLimit,
                    rustyAttempts,
                    normalLimit,
                    normalAttempts,
                    silenceLimit,
                    silenceAttempts,
                    silenceOverLimitAttempts,
                    silencePenaltyTimestamp,
                    playerPickStates,
                    minigameData
            );
        }
    }

    protected abstract void createSchema(Connection connection) throws SQLException;

    protected abstract String upsertLockSql();
}
