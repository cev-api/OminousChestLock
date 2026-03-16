package net.ozanarchy.chestlock.lock;

import net.ozanarchy.chestlock.lock.PickState;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LockInfo {
    private final String keyName;
    private final String creatorName;
    private final UUID creatorUuid;
    private final String lastUserName;
    private final UUID lastUserUuid;
    private final boolean normalKey;
    private final boolean normalArmed;
    private final String lastPickUserName;
    private final UUID lastPickUserUuid;
    private final String lastPickType;
    private final long lastPickTimestamp;
    private final int rustyLimit;
    private final int rustyAttempts;
    private final int normalLimit;
    private final int normalAttempts;
    private final int silenceLimit;
    private final int silenceAttempts;
    private final int silenceOverLimitAttempts;
    private final long silencePenaltyTimestamp;
    private final Map<UUID, PickState> playerPickStates;
    private final LockMinigameData minigameData;

    public LockInfo(String keyName, String creatorName, UUID creatorUuid, String lastUserName, UUID lastUserUuid,
                     boolean normalKey, boolean normalArmed,
                     String lastPickUserName, UUID lastPickUserUuid, String lastPickType, long lastPickTimestamp,
                     int rustyLimit, int rustyAttempts,
                     int normalLimit, int normalAttempts,
                     int silenceLimit, int silenceAttempts,
                     int silenceOverLimitAttempts, long silencePenaltyTimestamp,
                     Map<UUID, PickState> playerPickStates,
                     LockMinigameData minigameData) {
        this.keyName = keyName;
        this.creatorName = creatorName;
        this.creatorUuid = creatorUuid;
        this.lastUserName = lastUserName;
        this.lastUserUuid = lastUserUuid;
        this.normalKey = normalKey;
        this.normalArmed = normalArmed;
        this.lastPickUserName = lastPickUserName;
        this.lastPickUserUuid = lastPickUserUuid;
        this.lastPickType = lastPickType;
        this.lastPickTimestamp = lastPickTimestamp;
        this.rustyLimit = rustyLimit;
        this.rustyAttempts = rustyAttempts;
        this.normalLimit = normalLimit;
        this.normalAttempts = normalAttempts;
        this.silenceLimit = silenceLimit;
        this.silenceAttempts = silenceAttempts;
        this.silenceOverLimitAttempts = silenceOverLimitAttempts;
        this.silencePenaltyTimestamp = silencePenaltyTimestamp;
        this.playerPickStates = playerPickStates == null ? new HashMap<>() : new HashMap<>(playerPickStates);
        this.minigameData = minigameData;
    }

    public String keyName() {
        return keyName;
    }

    public String creatorName() {
        return creatorName;
    }

    public UUID creatorUuid() {
        return creatorUuid;
    }

    public String lastUserName() {
        return lastUserName;
    }

    public UUID lastUserUuid() {
        return lastUserUuid;
    }

    public boolean normalKey() {
        return normalKey;
    }

    public boolean normalArmed() {
        return normalArmed;
    }

    public String lastPickUserName() {
        return lastPickUserName;
    }

    public UUID lastPickUserUuid() {
        return lastPickUserUuid;
    }

    public String lastPickType() {
        return lastPickType;
    }

    public long lastPickTimestamp() {
        return lastPickTimestamp;
    }

    public int rustyLimit() {
        return rustyLimit;
    }

    public int rustyAttempts() {
        return rustyAttempts;
    }

    public int normalLimit() {
        return normalLimit;
    }

    public int normalAttempts() {
        return normalAttempts;
    }

    public int silenceLimit() {
        return silenceLimit;
    }

    public int silenceAttempts() {
        return silenceAttempts;
    }

    public int silenceOverLimitAttempts() {
        return silenceOverLimitAttempts;
    }

    public long silencePenaltyTimestamp() {
        return silencePenaltyTimestamp;
    }

    public Map<UUID, PickState> playerPickStates() {
        return playerPickStates;
    }

    public LockMinigameData minigameData() {
        return minigameData;
    }

    public PickState toPickState() {
        return new PickState(rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp);
    }

    public LockInfo withLastUser(Player player) {
        return new LockInfo(keyName, creatorName, creatorUuid, player.getName(), player.getUniqueId(), normalKey, normalArmed,
                lastPickUserName, lastPickUserUuid, lastPickType, lastPickTimestamp,
                rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp, playerPickStates, minigameData);
    }

    public LockInfo withNormalArmed(boolean armed) {
        return new LockInfo(keyName, creatorName, creatorUuid, lastUserName, lastUserUuid, normalKey, armed,
                lastPickUserName, lastPickUserUuid, lastPickType, lastPickTimestamp,
                rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp, playerPickStates, minigameData);
    }

    public LockInfo withLastPick(Player player, PickType pickType, long timestamp) {
        return new LockInfo(keyName, creatorName, creatorUuid, lastUserName, lastUserUuid, normalKey, normalArmed,
                player.getName(), player.getUniqueId(), pickType.id(), timestamp,
                rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp, playerPickStates, minigameData);
    }

    public LockInfo withPickState(PickState state) {
        if (state == null) {
            return this;
        }
        return new LockInfo(keyName, creatorName, creatorUuid, lastUserName, lastUserUuid, normalKey, normalArmed,
                lastPickUserName, lastPickUserUuid, lastPickType, lastPickTimestamp,
                state.rustyLimit(), state.rustyAttempts(), state.normalLimit(), state.normalAttempts(),
                state.silenceLimit(), state.silenceAttempts(), state.silenceOverLimitAttempts(),
                state.silencePenaltyTimestamp(), playerPickStates, minigameData);
    }

    public LockInfo withPlayerPickState(UUID playerId, PickState state) {
        if (playerId == null || state == null) {
            return this;
        }
        Map<UUID, PickState> updatedStates = new HashMap<>(playerPickStates);
        updatedStates.put(playerId, state);
        return new LockInfo(keyName, creatorName, creatorUuid, lastUserName, lastUserUuid, normalKey, normalArmed,
                lastPickUserName, lastPickUserUuid, lastPickType, lastPickTimestamp,
                rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp, updatedStates, minigameData);
    }

    public LockInfo withMinigameData(LockMinigameData data) {
        return new LockInfo(keyName, creatorName, creatorUuid, lastUserName, lastUserUuid, normalKey, normalArmed,
                lastPickUserName, lastPickUserUuid, lastPickType, lastPickTimestamp,
                rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp, playerPickStates, data);
    }
}
