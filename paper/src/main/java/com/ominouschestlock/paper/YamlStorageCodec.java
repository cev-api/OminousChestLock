package com.ominouschestlock.paper;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class YamlStorageCodec {
    private YamlStorageCodec() {
    }

    static Map<String, LockInfo> load(File dataFile, int maxPins, int maxDepths) {
        Map<String, LockInfo> result = new LinkedHashMap<>();
        if (dataFile == null || !dataFile.exists()) {
            return result;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = config.getConfigurationSection("locked-chests");
        if (section == null) {
            return result;
        }

        for (String locationKey : section.getKeys(false)) {
            LockInfo lockInfo = readLockInfo(section, locationKey, maxPins, maxDepths);
            if (lockInfo != null) {
                result.put(locationKey, lockInfo);
            }
        }
        return result;
    }

    private static LockInfo readLockInfo(ConfigurationSection section, String locationKey, int maxPins, int maxDepths) {
        String keyName = null;
        String creatorName = null;
        UUID creatorUuid = null;
        String lastUserName = null;
        UUID lastUserUuid = null;
        boolean normalKey = false;
        boolean normalArmed = false;
        String lastPickUserName = null;
        UUID lastPickUserUuid = null;
        String lastPickType = null;
        long lastPickTimestamp = 0L;
        Map<UUID, PickState> playerPickStates = new HashMap<>();
        int rustyLimit = -1;
        int rustyAttempts = 0;
        int normalLimit = -1;
        int normalAttempts = 0;
        int silenceLimit = -1;
        int silenceAttempts = 0;
        int silenceOverLimitAttempts = 0;
        long silencePenaltyTimestamp = 0L;
        LockMinigameData minigameData = null;

        if (section.isString(locationKey)) {
            keyName = section.getString(locationKey);
        } else {
            ConfigurationSection lockSection = section.getConfigurationSection(locationKey);
            if (lockSection != null) {
                keyName = lockSection.getString("key");
                creatorName = lockSection.getString("creator.name");
                creatorUuid = parseUuid(lockSection.getString("creator.uuid"));
                lastUserName = lockSection.getString("last-user.name");
                lastUserUuid = parseUuid(lockSection.getString("last-user.uuid"));
                normalKey = lockSection.getBoolean("normal.key", false);
                normalArmed = lockSection.getBoolean("normal.armed", false);
                lastPickUserName = lockSection.getString("pick.last.name");
                lastPickUserUuid = parseUuid(lockSection.getString("pick.last.uuid"));
                lastPickType = lockSection.getString("pick.last.type");
                lastPickTimestamp = lockSection.getLong("pick.last.timestamp", 0L);
                if (lastPickUserName != null && lastPickUserName.isBlank()) {
                    lastPickUserName = null;
                }
                if (lastPickType != null && lastPickType.isBlank()) {
                    lastPickType = null;
                }
                ConfigurationSection pickPlayers = lockSection.getConfigurationSection("pick.players");
                if (pickPlayers != null) {
                    for (String playerId : pickPlayers.getKeys(false)) {
                        UUID playerUuid = parseUuid(playerId);
                        if (playerUuid == null) {
                            continue;
                        }
                        ConfigurationSection pickStateSection = pickPlayers.getConfigurationSection(playerId);
                        if (pickStateSection == null) {
                            continue;
                        }
                        playerPickStates.put(playerUuid,
                                new PickState(
                                        pickStateSection.getInt("rusty.limit", -1),
                                        pickStateSection.getInt("rusty.attempts", 0),
                                        pickStateSection.getInt("normal.limit", -1),
                                        pickStateSection.getInt("normal.attempts", 0),
                                        pickStateSection.getInt("silence.limit", -1),
                                        pickStateSection.getInt("silence.attempts", 0),
                                        pickStateSection.getInt("silence.over-limit-attempts", 0),
                                        pickStateSection.getLong("silence.penalty-timestamp", 0L)
                                ));
                    }
                }
                rustyLimit = lockSection.getInt("pick.rusty.limit", -1);
                rustyAttempts = lockSection.getInt("pick.rusty.attempts", 0);
                normalLimit = lockSection.getInt("pick.normal.limit", -1);
                normalAttempts = lockSection.getInt("pick.normal.attempts", 0);
                silenceLimit = lockSection.getInt("pick.silence.limit", -1);
                silenceAttempts = lockSection.getInt("pick.silence.attempts", 0);
                silenceOverLimitAttempts = lockSection.getInt("pick.silence.over-limit-attempts", 0);
                silencePenaltyTimestamp = lockSection.getLong("pick.silence.penalty-timestamp", 0L);
                ConfigurationSection minigameSection = lockSection.getConfigurationSection("minigame");
                if (minigameSection != null) {
                    String type = minigameSection.getString("type");
                    int pins = minigameSection.getInt("pins", 0);
                    int depths = minigameSection.getInt("depths", 0);
                    List<Integer> secretList = minigameSection.getIntegerList("secret");
                    int safePins = Math.max(1, Math.min(maxPins, pins));
                    int safeDepths = Math.max(1, Math.min(maxDepths, depths));
                    int[] secret = new int[Math.min(secretList.size(), safePins)];
                    boolean oneBased = true;
                    for (Integer value : secretList) {
                        if (value == null || value <= 0) {
                            oneBased = false;
                            break;
                        }
                    }
                    for (int i = 0; i < secret.length; i++) {
                        int raw = secretList.get(i);
                        int normalized = oneBased ? (raw - 1) : raw;
                        secret[i] = Math.max(0, Math.min(safeDepths - 1, normalized));
                    }
                    long created = minigameSection.getLong("created", 0L);
                    int saltVersion = minigameSection.getInt("salt-version", 1);
                    if (type != null && !type.isBlank() && secret.length == safePins) {
                        minigameData = new LockMinigameData(type, safePins, safeDepths, secret, created, saltVersion);
                    }
                }
            }
        }

        if (keyName == null || keyName.isBlank()) {
            return null;
        }
        return new LockInfo(
                keyName, creatorName, creatorUuid, lastUserName, lastUserUuid, normalKey, normalArmed,
                lastPickUserName, lastPickUserUuid, lastPickType, lastPickTimestamp,
                rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp, playerPickStates, minigameData
        );
    }

    static void save(File dataFile, Map<String, LockInfo> locks, LocationMetadataResolver locationMetadataResolver) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection section = config.createSection("locked-chests");
        for (Map.Entry<String, LockInfo> entry : locks.entrySet()) {
            String locationKey = entry.getKey();
            LockInfo info = entry.getValue();
            if (info == null) {
                continue;
            }
            ConfigurationSection lockSection = section.createSection(locationKey);
            lockSection.set("key", info.keyName());
            LocationData locationData = locationMetadataResolver.parseLocationKey(locationKey);
            if (locationData != null) {
                lockSection.set("world.name", locationData.worldName());
                if (locationData.realm() != null) {
                    lockSection.set("world.realm", locationData.realm());
                }
                if (locationData.worldUuid() != null) {
                    lockSection.set("world.uuid", locationData.worldUuid().toString());
                }
            }
            if (info.creatorName() != null) {
                lockSection.set("creator.name", info.creatorName());
            }
            if (info.creatorUuid() != null) {
                lockSection.set("creator.uuid", info.creatorUuid().toString());
            }
            if (info.lastUserName() != null) {
                lockSection.set("last-user.name", info.lastUserName());
            }
            if (info.lastUserUuid() != null) {
                lockSection.set("last-user.uuid", info.lastUserUuid().toString());
            }
            if (info.lastPickUserName() != null) {
                lockSection.set("pick.last.name", info.lastPickUserName());
            }
            if (info.lastPickUserUuid() != null) {
                lockSection.set("pick.last.uuid", info.lastPickUserUuid().toString());
            }
            if (info.lastPickType() != null) {
                lockSection.set("pick.last.type", info.lastPickType());
            }
            if (info.lastPickTimestamp() > 0L) {
                lockSection.set("pick.last.timestamp", info.lastPickTimestamp());
            }
            if (!info.playerPickStates().isEmpty()) {
                ConfigurationSection pickPlayers = lockSection.createSection("pick.players");
                for (Map.Entry<UUID, PickState> stateEntry : info.playerPickStates().entrySet()) {
                    UUID playerId = stateEntry.getKey();
                    PickState state = stateEntry.getValue();
                    if (playerId == null || state == null) {
                        continue;
                    }
                    ConfigurationSection pickStateSection = pickPlayers.createSection(playerId.toString());
                    pickStateSection.set("rusty.limit", state.rustyLimit());
                    pickStateSection.set("rusty.attempts", state.rustyAttempts());
                    pickStateSection.set("normal.limit", state.normalLimit());
                    pickStateSection.set("normal.attempts", state.normalAttempts());
                    pickStateSection.set("silence.limit", state.silenceLimit());
                    pickStateSection.set("silence.attempts", state.silenceAttempts());
                    pickStateSection.set("silence.over-limit-attempts", state.silenceOverLimitAttempts());
                    pickStateSection.set("silence.penalty-timestamp", state.silencePenaltyTimestamp());
                }
            }
            if (info.normalKey()) {
                lockSection.set("normal.key", true);
                lockSection.set("normal.armed", info.normalArmed());
            }
            if (info.rustyLimit() >= 0 || info.rustyAttempts() > 0) {
                lockSection.set("pick.rusty.limit", info.rustyLimit());
                lockSection.set("pick.rusty.attempts", info.rustyAttempts());
            }
            if (info.normalLimit() >= 0 || info.normalAttempts() > 0) {
                lockSection.set("pick.normal.limit", info.normalLimit());
                lockSection.set("pick.normal.attempts", info.normalAttempts());
            }
            if (info.silenceLimit() >= 0 || info.silenceAttempts() > 0 || info.silenceOverLimitAttempts() > 0
                    || info.silencePenaltyTimestamp() > 0L) {
                lockSection.set("pick.silence.limit", info.silenceLimit());
                lockSection.set("pick.silence.attempts", info.silenceAttempts());
                lockSection.set("pick.silence.over-limit-attempts", info.silenceOverLimitAttempts());
                lockSection.set("pick.silence.penalty-timestamp", info.silencePenaltyTimestamp());
            }
            if (info.minigameData() != null) {
                LockMinigameData mg = info.minigameData();
                lockSection.set("minigame.type", mg.type());
                lockSection.set("minigame.pins", mg.pins());
                lockSection.set("minigame.depths", mg.depths());
                List<Integer> secretList = new ArrayList<>(mg.pins());
                for (int value : mg.secret()) {
                    secretList.add(Math.max(1, value + 1));
                }
                lockSection.set("minigame.secret", secretList);
                lockSection.set("minigame.created", mg.createdTimestamp());
                lockSection.set("minigame.salt-version", mg.saltVersion());
            }
        }
        config.save(dataFile);
    }

    static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    interface LocationMetadataResolver {
        LocationData parseLocationKey(String locationKey);
    }
}
