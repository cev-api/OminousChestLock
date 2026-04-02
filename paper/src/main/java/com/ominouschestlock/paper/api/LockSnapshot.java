package com.ominouschestlock.paper.api;

import org.bukkit.Location;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record LockSnapshot(
        String keyName,
        String creatorName,
        UUID creatorUuid,
        String lastUserName,
        UUID lastUserUuid,
        boolean normalKey,
        boolean normalArmed,
        String lastPickUserName,
        UUID lastPickUserUuid,
        String lastPickType,
        long lastPickTimestamp,
        int rustyLimit,
        int rustyAttempts,
        int normalLimit,
        int normalAttempts,
        int silenceLimit,
        int silenceAttempts,
        int silenceOverLimitAttempts,
        long silencePenaltyTimestamp,
        String minigameType,
        int minigamePins,
        int minigameDepths,
        long minigameCreatedTimestamp,
        int minigameSaltVersion,
        List<Location> locations
) {
    public LockSnapshot {
        locations = locations == null ? List.of() : Collections.unmodifiableList(locations);
    }
}
