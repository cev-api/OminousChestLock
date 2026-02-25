package com.ominouschestlock.paper;

import java.util.Arrays;

final class LockMinigameData {
    private final String type;
    private final int pins;
    private final int depths;
    private final int[] secret;
    private final long createdTimestamp;
    private final int saltVersion;

    LockMinigameData(String type, int pins, int depths, int[] secret, long createdTimestamp, int saltVersion) {
        this.type = type;
        this.pins = pins;
        this.depths = depths;
        this.secret = secret == null ? new int[0] : Arrays.copyOf(secret, secret.length);
        this.createdTimestamp = createdTimestamp;
        this.saltVersion = saltVersion;
    }

    String type() {
        return type;
    }

    int pins() {
        return pins;
    }

    int depths() {
        return depths;
    }

    int[] secret() {
        return Arrays.copyOf(secret, secret.length);
    }

    long createdTimestamp() {
        return createdTimestamp;
    }

    int saltVersion() {
        return saltVersion;
    }
}
