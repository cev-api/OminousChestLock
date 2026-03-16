package net.ozanarchy.chestlock.lock;

import java.util.Arrays;

public final class LockMinigameData {
    private final String type;
    private final int pins;
    private final int depths;
    private final int[] secret;
    private final long createdTimestamp;
    private final int saltVersion;

    public LockMinigameData(String type, int pins, int depths, int[] secret, long createdTimestamp, int saltVersion) {
        this.type = type;
        this.pins = pins;
        this.depths = depths;
        this.secret = secret == null ? new int[0] : Arrays.copyOf(secret, secret.length);
        this.createdTimestamp = createdTimestamp;
        this.saltVersion = saltVersion;
    }

    public String type() { return type; }
    public int pins() { return pins; }
    public int depths() { return depths; }
    public int[] secret() { return Arrays.copyOf(secret, secret.length); }
    public long createdTimestamp() { return createdTimestamp; }
    public int saltVersion() { return saltVersion; }
}
