package net.ozanarchy.chestlock.events;

import net.kyori.adventure.text.Component;
import net.ozanarchy.chestlock.ChestLockPlugin;
import net.ozanarchy.chestlock.lock.LockInfo;
import net.ozanarchy.chestlock.lock.LockService;
import net.ozanarchy.chestlock.model.KeyMatch;
import net.ozanarchy.chestlock.model.PickMatch;
import net.ozanarchy.chestlock.util.ItemUtil;
import net.ozanarchy.chestlock.util.LocationUtil;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ChestInteractListener implements Listener {

    private final ChestLockPlugin plugin;
    private final LockService lockService;

    public ChestInteractListener(ChestLockPlugin plugin, LockService lockService) {
        this.plugin = plugin;
        this.lockService = lockService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.hasBlock()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !LocationUtil.isLockable(block)) {
            return;
        }

        switch (event.getAction()) {
            case RIGHT_CLICK_BLOCK, LEFT_CLICK_BLOCK -> {
                KeyMatch heldKey = lockService.findAnyHeldKey(event.getPlayer());
                String heldKeyName = heldKey == null ? null : heldKey.name();
                LockInfo existingLock = lockService.getLockInfo(block);

                if (existingLock != null) {
                    if (heldKeyName == null || !existingLock.keyName().equals(heldKeyName)) {
                        PickMatch pickMatch = lockService.findHeldPick(event.getPlayer());
                        if (pickMatch != null) {
                            lockService.handlePickAttempt(event, block, existingLock, pickMatch);
                            return;
                        }
                        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && net.ozanarchy.chestlock.util.ItemUtil.isDecorationItem(event.getItem())) {
                            event.setCancelled(false);
                            event.setUseInteractedBlock(Event.Result.ALLOW);
                            event.setUseItemInHand(Event.Result.ALLOW);
                        } else {
                            event.setCancelled(true);
                            lockService.playFail(event.getPlayer(), block.getLocation());
                            lockService.logLockEvent("INTERACT_DENY", event.getPlayer().getName(), heldKeyName, block.getLocation(), existingLock, null);
                        }
                    }
                    return;
                }

                // If not locked and player holds a key, try to lock it (only on right click)
                if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && heldKeyName != null) {
                    if (!lockService.tryLock(block, heldKeyName, event.getPlayer(), heldKey != null && heldKey.normal())) {
                        lockService.playFail(event.getPlayer(), block.getLocation());
                        lockService.logLockEvent("LOCK_DENY", event.getPlayer().getName(), heldKeyName, block.getLocation(), null, "key already used or locked by another key");
                        return;
                    }

                    lockService.playInsert(event.getPlayer(), block.getLocation());
                    lockService.logLockEvent("LOCK_CREATED", event.getPlayer().getName(), heldKeyName, block.getLocation(), lockService.getLockInfo(block), null);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();
        List<org.bukkit.Location> locations = LocationUtil.resolveLockLocations(holder);
        if (locations.isEmpty()) {
            return;
        }

        LockInfo lockInfo = lockService.getLockInfo(locations);
        if (lockInfo == null) {
            return;
        }

        KeyMatch keyMatch = lockService.findHeldKey(player, lockInfo.keyName());
        String heldKeyName = keyMatch == null ? null : keyMatch.name();
        if (heldKeyName == null || !lockInfo.keyName().equals(heldKeyName)) {
            event.setCancelled(true);
            lockService.playFail(player, locations.getFirst());
            lockService.logLockEvent("OPEN_DENY", player.getName(), heldKeyName, locations.getFirst(), lockInfo, "wrong or missing key");
            return;
        }

        if (hasOtherViewers(inventory, player)) {
            event.setCancelled(true);
            lockService.playFail(player, locations.getFirst());
            lockService.logLockEvent("OPEN_DENY", player.getName(), heldKeyName, locations.getFirst(), lockInfo, "in use by another player");
            return;
        }

        lockService.updateLastUser(locations, player);
        lockService.playSuccess(player, locations.getFirst());
        lockService.logLockEvent("OPEN_ALLOWED", player.getName(), heldKeyName, locations.getFirst(), lockInfo, null);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (lockService.handleMinigameClick(event)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top == null) {
            return;
        }
        String lockName = lockService.getLockKeyName(top);
        if (lockName == null) {
            return;
        }
        if (!lockName.equals(lockService.findAnyHeldKey(player) != null ? lockService.findAnyHeldKey(player).name() : null)) {
            event.setCancelled(true);
            lockService.playFail(player, lockService.lockLocation(top));
            lockService.logLockEvent("INVENTORY_CLICK_DENY", player.getName(), lockService.findAnyHeldKey(player) != null ? lockService.findAnyHeldKey(player).name() : null, lockService.lockLocation(top), lockService.getLockInfo(lockService.lockLocation(top)), null);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top == null) {
            return;
        }
        String lockName = lockService.getLockKeyName(top);
        if (lockName == null) {
            return;
        }
        if (!lockName.equals(lockService.findAnyHeldKey(player) != null ? lockService.findAnyHeldKey(player).name() : null)) {
            event.setCancelled(true);
            lockService.playFail(player, lockService.lockLocation(top));
            lockService.logLockEvent("INVENTORY_DRAG_DENY", player.getName(), lockService.findAnyHeldKey(player) != null ? lockService.findAnyHeldKey(player).name() : null, lockService.lockLocation(top), lockService.getLockInfo(lockService.lockLocation(top)), null);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        lockService.handleMinigameClose(event);
    }

    private boolean hasOtherViewers(Inventory inventory, Player player) {
        for (HumanEntity viewer : inventory.getViewers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId())) {
                continue; // Skip the current player
            }
            if (viewer instanceof Player) { // Only count actual players as "other viewers"
                return true;
            }
        }
        return false;
    }
}
