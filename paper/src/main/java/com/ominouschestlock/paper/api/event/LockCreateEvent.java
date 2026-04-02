package com.ominouschestlock.paper.api.event;

import com.ominouschestlock.paper.api.LockSnapshot;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;

public final class LockCreateEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String keyName;
    private final boolean normalKey;
    private final List<Location> locations;
    private final LockSnapshot existingLock;
    private boolean cancelled;

    public LockCreateEvent(Player player, String keyName, boolean normalKey, List<Location> locations, LockSnapshot existingLock) {
        this.player = player;
        this.keyName = keyName;
        this.normalKey = normalKey;
        this.locations = List.copyOf(locations);
        this.existingLock = existingLock;
    }

    public Player getPlayer() { return player; }
    public String getKeyName() { return keyName; }
    public boolean isNormalKey() { return normalKey; }
    public List<Location> getLocations() { return locations; }
    public LockSnapshot getExistingLock() { return existingLock; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
