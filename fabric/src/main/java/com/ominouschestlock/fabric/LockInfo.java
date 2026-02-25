package com.ominouschestlock.fabric;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class LockInfo {
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

    LockInfo(String keyName, String creatorName, UUID creatorUuid, String lastUserName, UUID lastUserUuid,
             boolean normalKey, boolean normalArmed,
             String lastPickUserName, UUID lastPickUserUuid, String lastPickType, long lastPickTimestamp,
             int rustyLimit, int rustyAttempts,
             int normalLimit, int normalAttempts,
             int silenceLimit, int silenceAttempts,
             int silenceOverLimitAttempts, long silencePenaltyTimestamp,
             Map<UUID, PickState> playerPickStates) {
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
    }

    String keyName() { return keyName; }
    String creatorName() { return creatorName; }
    UUID creatorUuid() { return creatorUuid; }
    String lastUserName() { return lastUserName; }
    UUID lastUserUuid() { return lastUserUuid; }
    boolean normalKey() { return normalKey; }
    boolean normalArmed() { return normalArmed; }
    String lastPickUserName() { return lastPickUserName; }
    UUID lastPickUserUuid() { return lastPickUserUuid; }
    String lastPickType() { return lastPickType; }
    long lastPickTimestamp() { return lastPickTimestamp; }
    int rustyLimit() { return rustyLimit; }
    int rustyAttempts() { return rustyAttempts; }
    int normalLimit() { return normalLimit; }
    int normalAttempts() { return normalAttempts; }
    int silenceLimit() { return silenceLimit; }
    int silenceAttempts() { return silenceAttempts; }
    int silenceOverLimitAttempts() { return silenceOverLimitAttempts; }
    long silencePenaltyTimestamp() { return silencePenaltyTimestamp; }
    Map<UUID, PickState> playerPickStates() { return playerPickStates; }

    PickState toPickState() {
        return new PickState(rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp);
    }

    LockInfo withLastUser(ServerPlayer player) {
        return new LockInfo(keyName, creatorName, creatorUuid, player.getName().getString(), player.getUUID(), normalKey, normalArmed,
                lastPickUserName, lastPickUserUuid, lastPickType, lastPickTimestamp,
                rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp, playerPickStates);
    }

    LockInfo withNormalArmed(boolean armed) {
        return new LockInfo(keyName, creatorName, creatorUuid, lastUserName, lastUserUuid, normalKey, armed,
                lastPickUserName, lastPickUserUuid, lastPickType, lastPickTimestamp,
                rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp, playerPickStates);
    }

    LockInfo withPickState(PickState state) {
        if (state == null) {
            return this;
        }
        return new LockInfo(keyName, creatorName, creatorUuid, lastUserName, lastUserUuid, normalKey, normalArmed,
                lastPickUserName, lastPickUserUuid, lastPickType, lastPickTimestamp,
                state.rustyLimit(), state.rustyAttempts(), state.normalLimit(), state.normalAttempts(),
                state.silenceLimit(), state.silenceAttempts(), state.silenceOverLimitAttempts(),
                state.silencePenaltyTimestamp(), playerPickStates);
    }

    LockInfo withLastPick(ServerPlayer player, PickType pickType, long timestamp) {
        if (player == null || pickType == null) {
            return this;
        }
        return new LockInfo(keyName, creatorName, creatorUuid, lastUserName, lastUserUuid, normalKey, normalArmed,
                player.getName().getString(), player.getUUID(), pickType.id, timestamp,
                rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp, playerPickStates);
    }

    LockInfo withPlayerPickState(UUID playerId, PickState state) {
        if (playerId == null || state == null) {
            return this;
        }
        Map<UUID, PickState> updatedStates = new HashMap<>(playerPickStates);
        updatedStates.put(playerId, state);
        return new LockInfo(keyName, creatorName, creatorUuid, lastUserName, lastUserUuid, normalKey, normalArmed,
                lastPickUserName, lastPickUserUuid, lastPickType, lastPickTimestamp,
                rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp, updatedStates);
    }
}

