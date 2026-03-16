package net.ozanarchy.chestlock.config;

import net.ozanarchy.chestlock.ChestLockPlugin;
import net.ozanarchy.chestlock.lock.LockInfo;
import net.ozanarchy.chestlock.lock.LockMinigameData;
import net.ozanarchy.chestlock.lock.PickState;
import net.ozanarchy.chestlock.model.HopperOwner;
import net.ozanarchy.chestlock.model.LocationData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DataStore {
    private final ChestLockPlugin plugin;
    private final File dataFile;
    private final Map<String, LockInfo> lockedChests;
    private final Map<String, String> keyToChest;
    private final Map<String, HopperOwner> hopperOwners;

    public DataStore(ChestLockPlugin plugin, File dataFile, Map<String, LockInfo> lockedChests, Map<String, String> keyToChest, Map<String, HopperOwner> hopperOwners) {
        this.plugin = plugin;
        this.dataFile = dataFile;
        this.lockedChests = lockedChests;
        this.keyToChest = keyToChest;
        this.hopperOwners = hopperOwners;
    }

    public Map<String, LockInfo> getLockedChests() {
        return lockedChests;
    }

    public Map<String, String> getKeyToChest() {
        return keyToChest;
    }

    public Map<String, HopperOwner> getHopperOwners() {
        return hopperOwners;
    }

    public void loadData() {
        lockedChests.clear();
        keyToChest.clear();
        hopperOwners.clear(); // Clear hopper owners too on load

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create data folder.");
        }

        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = config.getConfigurationSection("locked-chests");
        if (section == null) {
            return;
        }

        for (String locationKey : section.getKeys(false)) {
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
                    String creatorId = lockSection.getString("creator.uuid");
                    creatorUuid = parseUuid(creatorId);
                    lastUserName = lockSection.getString("last-user.name");
                    String lastUserId = lockSection.getString("last-user.uuid");
                    lastUserUuid = parseUuid(lastUserId);
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
                            int rLimit = pickStateSection.getInt("rusty.limit", -1);
                            int rAttempts = pickStateSection.getInt("rusty.attempts", 0);
                            int nLimit = pickStateSection.getInt("normal.limit", -1);
                            int nAttempts = pickStateSection.getInt("normal.attempts", 0);
                            int sLimit = pickStateSection.getInt("silence.limit", -1);
                            int sAttempts = pickStateSection.getInt("silence.attempts", 0);
                            int sOver = pickStateSection.getInt("silence.over-limit-attempts", 0);
                            long sPenalty = pickStateSection.getLong("silence.penalty-timestamp", 0L);
                            playerPickStates.put(playerUuid, new PickState(rLimit, rAttempts, nLimit, nAttempts, sLimit, sAttempts, sOver, sPenalty));
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
                        String type = minigameSection.getString("type", "trial");
                        int pins = minigameSection.getInt("pins", 4);
                        int depths = minigameSection.getInt("depths", 4);
                        java.util.List<Integer> secretList = minigameSection.getIntegerList("secret");
                        int[] secret = new int[secretList.size()];
                        for (int i = 0; i < secretList.size(); i++) {
                            secret[i] = secretList.get(i);
                        }
                        long created = minigameSection.getLong("created", 0L);
                        int saltVersion = minigameSection.getInt("salt-version", 1);
                        minigameData = new LockMinigameData(type, pins, depths, secret, created, saltVersion);
                    }
                }
            }

            if (keyName == null || keyName.isBlank()) {
                continue;
            }
            LockInfo info = new LockInfo(keyName, creatorName, creatorUuid, lastUserName, lastUserUuid, normalKey, normalArmed,
                    lastPickUserName, lastPickUserUuid, lastPickType, lastPickTimestamp,
                    rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                    silenceOverLimitAttempts, silencePenaltyTimestamp, playerPickStates, minigameData);
            lockedChests.put(locationKey, info);
            keyToChest.putIfAbsent(keyName, locationKey);
        }
    }

    public void saveData() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection section = config.createSection("locked-chests");
        for (Map.Entry<String, LockInfo> entry : lockedChests.entrySet()) {
            String locationKey = entry.getKey();
            LockInfo info = entry.getValue();
            if (info == null) {
                continue;
            }
            ConfigurationSection lockSection = section.createSection(locationKey);
            lockSection.set("key", info.keyName());
            LocationData locationData = parseLocationKey(locationKey);
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
                ConfigurationSection minigame = lockSection.createSection("minigame");
                minigame.set("type", info.minigameData().type());
                minigame.set("pins", info.minigameData().pins());
                minigame.set("depths", info.minigameData().depths());
                java.util.List<Integer> secret = new java.util.ArrayList<>();
                for (int v : info.minigameData().secret()) {
                    secret.add(v);
                }
                minigame.set("secret", secret);
                minigame.set("created", info.minigameData().createdTimestamp());
                minigame.set("salt-version", info.minigameData().saltVersion());
            }
        }
        try {
            config.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save data.yml: " + exception.getMessage());
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // Moved from ChestLockPlugin for serializing LocationData
    private LocationData parseLocationKey(String locationKey) {
        if (locationKey == null || locationKey.isBlank()) {
            return null;
        }
        String[] parts = locationKey.split(":");
        if (parts.length < 2) {
            // Log warning? Or handle gracefully
            return null;
        }
        String worldName = parts[0];
        String[] coords = parts[1].split(",");
        if (coords.length != 3) {
            // Log warning? Or handle gracefully
            return null;
        }
        try {
            int x = Integer.parseInt(coords[0]);
            int y = Integer.parseInt(coords[1]);
            int z = Integer.parseInt(coords[2]);
            // No realm or uuid info in locationKey string itself, so pass nulls for now
            // If needed, this would require a richer locationKey format or separate storage
            return new LocationData(worldName, (UUID) null, (String) null, x, y, z);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Failed to parse location coordinates from key: " + locationKey + " - " + e.getMessage());
            return null;
        }
    }

    // Moved from ChestLockPlugin for serializing LocationData
    private String mapRealm(World.Environment environment) {
        return switch (environment) {
            case NORMAL -> "overworld";
            case NETHER -> "nether";
            case THE_END -> "the_end";
            default -> "unknown";
        };
    }
}
