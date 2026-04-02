package com.ominouschestlock.paper.api.event;

import com.ominouschestlock.paper.api.LockSnapshot;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class LockoutAppliedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Location location;
    private final String pickTypeId;
    private final LockPickMode mode;
    private final long lockoutEndsAtMs;
    private final LockSnapshot lockSnapshot;

    public LockoutAppliedEvent(Player player, Location location, String pickTypeId, LockPickMode mode, long lockoutEndsAtMs, LockSnapshot lockSnapshot) {
        this.player = player;
        this.location = location;
        this.pickTypeId = pickTypeId;
        this.mode = mode;
        this.lockoutEndsAtMs = lockoutEndsAtMs;
        this.lockSnapshot = lockSnapshot;
    }

    public Player getPlayer() { return player; }
    public Location getLocation() { return location; }
    public String getPickTypeId() { return pickTypeId; }
    public LockPickMode getMode() { return mode; }
    public long getLockoutEndsAtMs() { return lockoutEndsAtMs; }
    public LockSnapshot getLockSnapshot() { return lockSnapshot; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
