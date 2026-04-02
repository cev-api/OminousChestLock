package com.ominouschestlock.paper.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;

public interface OminousChestLockApi {
    boolean isLocked(Location location);

    Optional<LockSnapshot> getLockSnapshot(Location location);

    boolean createLock(Location location, String keyName, Player creator, boolean normalKey);

    boolean removeLock(Location location, Player actor);

    boolean unlock(Location location, Player actor);

    Collection<String> getRegisteredPickTypes();

    Collection<String> getRegisteredLockTypes();

    boolean registerLockType(String lockTypeId);

    boolean registerPickType(String pickTypeId);
}
