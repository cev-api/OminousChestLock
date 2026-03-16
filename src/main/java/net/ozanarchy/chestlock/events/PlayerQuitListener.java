package net.ozanarchy.chestlock.events;

import net.ozanarchy.chestlock.ChestLockPlugin;
import net.ozanarchy.chestlock.lock.LockService;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final ChestLockPlugin plugin;
    private final LockService lockService;

    public PlayerQuitListener(ChestLockPlugin plugin, LockService lockService) {
        this.plugin = plugin;
        this.lockService = lockService;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lockService.handlePlayerQuit(event.getPlayer());
    }
}
