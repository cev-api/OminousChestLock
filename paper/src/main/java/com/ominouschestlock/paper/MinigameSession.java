package com.ominouschestlock.paper;

import org.bukkit.Location;
import org.bukkit.boss.BossBar;
import org.bukkit.inventory.Inventory;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

final class MinigameSession {
    final UUID playerId;
    final String ownerName;
    final String containerId;
    final List<Location> locations;
    final String keyName;
    final PickType pickType;
    Inventory inventory;
    LockMinigameData minigameData;
    final boolean[][] eliminated;
    final int[] selectedDepths;
    BossBar bossBar;
    final String baseTitle;
    String feedbackTitle;
    double feedbackProgress;
    FeedbackColor feedbackColor;
    int timeoutTaskId = -1;
    int bossbarTaskId = -1;
    long lastActionMs;

    MinigameSession(UUID playerId, String ownerName, String containerId, List<Location> locations, String keyName,
                    PickType pickType, Inventory inventory, LockMinigameData minigameData) {
        this.playerId = playerId;
        this.ownerName = ownerName;
        this.containerId = containerId;
        this.locations = locations;
        this.keyName = keyName;
        this.pickType = pickType;
        this.inventory = inventory;
        this.baseTitle = "Locked Chest";
        this.feedbackTitle = "";
        this.feedbackProgress = 0.0;
        this.feedbackColor = FeedbackColor.OFF;
        this.minigameData = minigameData;
        this.eliminated = new boolean[minigameData.pins()][minigameData.depths()];
        this.selectedDepths = new int[minigameData.pins()];
        Arrays.fill(this.selectedDepths, -1);
        this.lastActionMs = System.currentTimeMillis();
    }

    enum FeedbackColor {
        OFF,
        YELLOW,
        RED,
        GREEN
    }
}
