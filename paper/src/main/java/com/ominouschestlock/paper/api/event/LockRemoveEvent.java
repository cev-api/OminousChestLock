package com.ominouschestlock.paper.api.event;

import com.ominouschestlock.paper.api.LockSnapshot;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;

public final class LockRemoveEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player actor;
    private final String keyName;
    private final List<Location> locations;
    private final LockSnapshot removedLock;
    private final LockActionCause cause;
    private boolean cancelled;

    public LockRemoveEvent(Player actor, String keyName, List<Location> locations, LockSnapshot removedLock, LockActionCause cause) {
        this.actor = actor;
        this.keyName = keyName;
        this.locations = List.copyOf(locations);
        this.removedLock = removedLock;
        this.cause = cause;
    }

    public Player getActor() { return actor; }
    public String getKeyName() { return keyName; }
    public List<Location> getLocations() { return locations; }
    public LockSnapshot getRemovedLock() { return removedLock; }
    public LockActionCause getCause() { return cause; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
