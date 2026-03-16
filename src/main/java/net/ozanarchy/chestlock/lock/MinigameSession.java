package net.ozanarchy.chestlock.lock;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class MinigameSession {
    public final UUID playerId;
    public final String containerId;
    public final List<Location> locations;
    public final String keyName;
    public final PickType pickType;
    public final Inventory inventory;
    public final LockMinigameData minigameData;
    public final boolean[][] eliminated;
    public final int[] selectedDepths;
    public double feedbackProgress;
    public FeedbackColor feedbackColor;

    public MinigameSession(UUID playerId, String containerId, List<Location> locations, String keyName,
                           PickType pickType, Inventory inventory, LockMinigameData minigameData) {
        this.playerId = playerId;
        this.containerId = containerId;
        this.locations = locations;
        this.keyName = keyName;
        this.pickType = pickType;
        this.inventory = inventory;
        this.minigameData = minigameData;
        this.eliminated = new boolean[minigameData.pins()][minigameData.depths()];
        this.selectedDepths = new int[minigameData.pins()];
        Arrays.fill(this.selectedDepths, -1);
        this.feedbackProgress = 0.0;
        this.feedbackColor = FeedbackColor.OFF;
    }

    public enum FeedbackColor {
        OFF,
        YELLOW,
        RED,
        GREEN
    }
}
