package net.ozanarchy.chestlock.lock;

import net.ozanarchy.chestlock.model.HopperOwner;
import net.ozanarchy.chestlock.model.KeyMatch;
import net.ozanarchy.chestlock.model.LocationData;
import net.ozanarchy.chestlock.model.PickMatch;
import net.ozanarchy.chestlock.model.PendingIgnite;
import net.ozanarchy.chestlock.util.ItemUtil;
import net.ozanarchy.chestlock.util.LocationUtil;
import net.ozanarchy.chestlock.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import net.ozanarchy.chestlock.ChestLockPlugin; // For scheduler and logger
import net.ozanarchy.chestlock.config.ConfigManager;
import net.ozanarchy.chestlock.events.LockPickSuccessEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

// This class will hold logic related to managing locks, pick attempts, and recipe updates
public class LockService {
    private static final int MINIGAME_SIZE = 54;
    private static final int GRID_FIRST_COLUMN = 1;
    private static final int GRID_MAX_COLUMNS = 6;
    private static final int GRID_MAX_ROWS = 5;
    private static final int SLOT_RESET_ALL = 17;
    private static final int SLOT_TURN_LOCK = 26;
    private static final int SLOT_CLOSE = 35;

    private final ChestLockPlugin plugin;
    private final Map<String, LockInfo> lockedChests;
    private final Map<String, String> keyToChest;
    private final Map<String, HopperOwner> hopperOwners; // Needs to be shared for hopper logic
    private final Map<String, Long> logCooldowns; // For logging control
    private final Map<String, PendingIgnite> tntIgnites; // For TNT protection
    private final Map<UUID, String> tntSources; // For TNT protection
    private final Map<UUID, PendingIgnite> crystalSources; // For crystal protection

    // Configuration values (will be moved to ConfigManager later)
    private int pickLimitMin;
    private int pickLimitMax;
    private double rustyNormalKeyChance;
    private double rustyOpenChance;
    private double normalNormalKeyChance;
    private double normalOpenChance;
    private double silenceOpenChance;
    private double rustyDamage;
    private double normalDamage;
    private double silenceDamage;
    private double rustyBreakChance;
    private double normalBreakChance;
    private double silenceBreakChance;
    private double lodestoneOpenChance;
    private double lodestoneDamage;
    private double lodestoneBreakChance;
    private long silencePenaltyResetMs;
    private LockoutScope lockoutScope;
    private boolean allowLockpicks;
    private boolean allowNormalKeys;
    private NamespacedKey pickTypeKey;
    private int logLevel; // Also needs to be configured
    private boolean minigameEnabled;
    private int minigameTrialPins;
    private int minigameTrialDepths;
    private int minigameOminousPins;
    private int minigameOminousDepths;
    private Material minigamePinIcon;
    private final Map<UUID, MinigameSession> minigameSessions = new HashMap<>();
    private final Map<String, UUID> minigameOwners = new HashMap<>();

    public LockService(ChestLockPlugin plugin, Map<String, LockInfo> lockedChests, Map<String, String> keyToChest,
                       Map<String, HopperOwner> hopperOwners, Map<String, Long> logCooldowns,
                       Map<String, PendingIgnite> tntIgnites, Map<UUID, String> tntSources,
                       Map<UUID, PendingIgnite> crystalSources) {
        this.plugin = plugin;
        this.lockedChests = lockedChests;
        this.keyToChest = keyToChest;
        this.hopperOwners = hopperOwners;
        this.logCooldowns = logCooldowns;
        this.tntIgnites = tntIgnites;
        this.tntSources = tntSources;
        this.crystalSources = crystalSources;
        // Default values for config that will be loaded from ConfigManager
        this.pickLimitMin = 2;
        this.pickLimitMax = 5;
        this.rustyNormalKeyChance = 0.05;
        this.rustyOpenChance = 0.1;
        this.normalNormalKeyChance = 0.15;
        this.normalOpenChance = 0.25;
        this.silenceOpenChance = 0.35;
        this.rustyDamage = 1.0;
        this.normalDamage = 2.0;
        this.silenceDamage = 3.0;
        this.rustyBreakChance = 0.5;
        this.normalBreakChance = 0.3;
        this.silenceBreakChance = 0.1;
        this.lodestoneOpenChance = 0.2;
        this.lodestoneDamage = 1.5;
        this.lodestoneBreakChance = 0.2;
        this.silencePenaltyResetMs = 15 * 60 * 1000L; // 15 minutes
        this.lockoutScope = LockoutScope.CHEST;
        this.allowLockpicks = true;
        this.allowNormalKeys = true;
        this.pickTypeKey = new NamespacedKey(plugin, "lock_pick_type");
        this.logLevel = 1; // Info level by default
        this.minigameEnabled = true;
        this.minigameTrialPins = 4;
        this.minigameTrialDepths = 4;
        this.minigameOminousPins = 6;
        this.minigameOminousDepths = 5;
        this.minigamePinIcon = Material.END_ROD;
    }

    // Setter for config values, will be called by ConfigManager
    public void setConfigValues(int pickLimitMin, int pickLimitMax, double rustyNormalKeyChance, double rustyOpenChance,
                                double normalNormalKeyChance, double normalOpenChance, double silenceOpenChance,
                                double rustyDamage, double normalDamage, double silenceDamage, double rustyBreakChance,
                                double normalBreakChance, double silenceBreakChance,
                                double lodestoneOpenChance, double lodestoneDamage, double lodestoneBreakChance,
                                long silencePenaltyResetMs,
                                LockoutScope lockoutScope, boolean allowLockpicks, boolean allowNormalKeys, int logLevel,
                                boolean minigameEnabled, int minigameTrialPins, int minigameTrialDepths,
                                int minigameOminousPins, int minigameOminousDepths, String minigamePinIcon) {
        this.pickLimitMin = pickLimitMin;
        this.pickLimitMax = pickLimitMax;
        this.rustyNormalKeyChance = rustyNormalKeyChance;
        this.rustyOpenChance = rustyOpenChance;
        this.normalNormalKeyChance = normalNormalKeyChance;
        this.normalOpenChance = normalOpenChance;
        this.silenceOpenChance = silenceOpenChance;
        this.rustyDamage = rustyDamage;
        this.normalDamage = normalDamage;
        this.silenceDamage = silenceDamage;
        this.rustyBreakChance = rustyBreakChance;
        this.normalBreakChance = normalBreakChance;
        this.silenceBreakChance = silenceBreakChance;
        this.lodestoneOpenChance = lodestoneOpenChance;
        this.lodestoneDamage = lodestoneDamage;
        this.lodestoneBreakChance = lodestoneBreakChance;
        this.silencePenaltyResetMs = silencePenaltyResetMs;
        this.lockoutScope = lockoutScope;
        this.allowLockpicks = allowLockpicks;
        this.allowNormalKeys = allowNormalKeys;
        this.logLevel = logLevel;
        this.minigameEnabled = minigameEnabled;
        this.minigameTrialPins = Math.max(1, minigameTrialPins);
        this.minigameTrialDepths = Math.max(1, Math.min(5, minigameTrialDepths));
        this.minigameOminousPins = Math.max(1, minigameOminousPins);
        this.minigameOminousDepths = Math.max(1, Math.min(5, minigameOminousDepths));
        Material icon = Material.matchMaterial(minigamePinIcon == null ? "END_ROD" : minigamePinIcon);
        this.minigamePinIcon = icon == null ? Material.END_ROD : icon;

        // Update ItemUtil with the correct NamespacedKey
        ItemUtil.setPickTypeKey(pickTypeKey);
    }

    public LockInfo getLockInfo(Block block) {
        return getLockInfo(LocationUtil.resolveLockLocations(block));
    }

    public LockInfo getLockInfo(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return lockedChests.get(LocationUtil.locationKey(location));
    }

    public LockInfo getLockInfo(List<Location> locations) {
        for (Location location : locations) {
            LockInfo info = lockedChests.get(LocationUtil.locationKey(location));
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    public boolean isLockedBlock(Block block) {
        if (!LocationUtil.isLockable(block)) {
            return false;
        }
        return getLockInfo(block) != null;
    }

    public boolean isLockedHolder(InventoryHolder holder) {
        if (holder == null) {
            return false;
        }
        List<Location> locations = LocationUtil.resolveLockLocations(holder);
        if (locations.isEmpty()) {
            return false;
        }
        return getLockInfo(locations) != null;
    }

    public String getLockKeyName(Block block) {
        LockInfo info = getLockInfo(LocationUtil.resolveLockLocations(block));
        return info == null ? null : info.keyName();
    }

    public String getLockKeyName(Inventory inventory) {
        LockInfo info = getLockInfo(LocationUtil.resolveLockLocations(inventory.getHolder()));
        return info == null ? null : info.keyName();
    }

    public KeyMatch findHeldKey(Player player, String requiredName) {
        KeyMatch main = getKeyMatch(player.getInventory().getItemInMainHand(), EquipmentSlot.HAND, requiredName);
        if (main != null) {
            return main;
        }
        return getKeyMatch(player.getInventory().getItemInOffHand(), EquipmentSlot.OFF_HAND, requiredName);
    }

    public KeyMatch findAnyHeldKey(Player player) {
        KeyMatch main = getAnyKeyMatch(player.getInventory().getItemInMainHand(), EquipmentSlot.HAND);
        if (main != null) {
            return main;
        }
        return getAnyKeyMatch(player.getInventory().getItemInOffHand(), EquipmentSlot.OFF_HAND);
    }

    private KeyMatch getKeyMatch(ItemStack itemStack, EquipmentSlot slot, String requiredName) {
        String name = ItemUtil.getKeyName(itemStack, allowNormalKeys);
        if (name == null || !name.equals(requiredName)) {
            return null;
        }
        boolean normal = itemStack.getType() == Material.TRIAL_KEY;
        if (normal && !allowNormalKeys) {
            return null;
        }
        return new KeyMatch(name, slot, normal);
    }

    private KeyMatch getAnyKeyMatch(ItemStack itemStack, EquipmentSlot slot) {
        String name = ItemUtil.getKeyName(itemStack, allowNormalKeys);
        if (name == null) {
            return null;
        }
        boolean normal = itemStack.getType() == Material.TRIAL_KEY;
        if (normal && !allowNormalKeys) {
            return null;
        }
        return new KeyMatch(name, slot, normal);
    }

    public boolean tryLock(Block block, String keyName, Player creator, boolean normalKey) {
        List<Location> locations = LocationUtil.resolveLockLocations(block);
        if (locations.isEmpty()) {
            return false;
        }

        Set<String> locationKeys = new HashSet<>();
        for (Location location : locations) {
            locationKeys.add(LocationUtil.locationKey(location));
        }

        String mappedLocation = keyToChest.get(keyName);
        if (mappedLocation != null && !locationKeys.contains(mappedLocation)) {
            LockInfo mappedInfo = lockedChests.get(mappedLocation);
            if (mappedInfo == null || !mappedInfo.keyName().equals(keyName)) {
                keyToChest.remove(keyName);
            } else {
                return false;
            }
        }

        for (Location location : locations) {
            LockInfo existing = lockedChests.get(LocationUtil.locationKey(location));
            if (existing != null && !existing.keyName().equals(keyName)) {
                return false;
            }
        }

        LockInfo info = new LockInfo(keyName, creator.getName(), creator.getUniqueId(), null, null, normalKey, false,
                null, null, null, 0L,
                -1, 0, -1, 0, -1, 0, 0, 0L, new HashMap<>(), null);
        for (Location location : locations) {
            lockedChests.put(LocationUtil.locationKey(location), info);
        }
        keyToChest.putIfAbsent(keyName, locationKeys.iterator().next());
        // DataStore will handle saving later
        // saveData();
        return true;
    }

    public void unlock(Block block, String keyName) {
        List<Location> locations = LocationUtil.resolveLockLocations(block);
        for (Location location : locations) {
            lockedChests.remove(LocationUtil.locationKey(location));
        }
        keyToChest.remove(keyName);
        // DataStore will handle saving later
        // saveData();
    }

    public PickState getPickState(LockInfo info, Player player) {
        if (info == null) {
            return PickState.empty();
        }
        if (lockoutScope == LockoutScope.PLAYER) {
            if (player == null) {
                return PickState.empty();
            }
            PickState state = info.playerPickStates().get(player.getUniqueId());
            return state == null ? PickState.empty() : state;
        }
        return info.toPickState();
    }

    public LockInfo updatePickState(LockInfo info, Player player, PickState state) {
        if (info == null || state == null) {
            return info;
        }
        if (lockoutScope == LockoutScope.PLAYER) {
            if (player == null) {
                return info;
            }
            return info.withPlayerPickState(player.getUniqueId(), state);
        }
        return info.withPickState(state);
    }

    public void updateLockInfo(List<Location> locations, LockInfo info) {
        for (Location location : locations) {
            lockedChests.put(LocationUtil.locationKey(location), info);
        }
        // DataStore will handle saving later
        // saveData();
    }

    public void updateLastUser(List<Location> locations, Player player) {
        boolean changed = false;
        for (Location location : locations) {
            String key = LocationUtil.locationKey(location);
            LockInfo info = lockedChests.get(key);
            if (info == null) {
                continue;
            }
            if (!player.getUniqueId().equals(info.lastUserUuid()) || !player.getName().equals(info.lastUserName())) {
                lockedChests.put(key, info.withLastUser(player));
                changed = true;
            }
        }
        // DataStore will handle saving later
        // if (changed) { saveData(); }
    }


    public void consumeOnePick(Player player, PickMatch match) {
        ItemStack stack = match.slot() == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        if (stack == null || stack.getType() != Material.TRIPWIRE_HOOK) {
            return;
        }
        int amount = stack.getAmount();
        if (amount <= 1) {
            if (match.slot() == EquipmentSlot.HAND) {
                player.getInventory().setItemInMainHand(null);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
        } else {
            stack.setAmount(amount - 1);
        }
    }

    public PickMatch findHeldPick(Player player) {
        PickType main = ItemUtil.getPickType(player.getInventory().getItemInMainHand());
        if (main != null) {
            return new PickMatch(main, EquipmentSlot.HAND);
        }
        PickType off = ItemUtil.getPickType(player.getInventory().getItemInOffHand());
        if (off != null) {
            return new PickMatch(off, EquipmentSlot.OFF_HAND);
        }
        return null;
    }

    public void handlePickAttempt(PlayerInteractEvent event, Block block, LockInfo lockInfo, PickMatch pickMatch) {
        if (!allowLockpicks) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(net.kyori.adventure.text.Component.text("Lockpicking is disabled."));
            return;
        }
        List<Location> locations = LocationUtil.resolveLockLocations(block);
        if (locations.isEmpty()) {
            return;
        }

        Player player = event.getPlayer();
        PickType pickType = pickMatch.type();

        // Enforce Lodestone restrictions
        if (block.getType() == Material.LODESTONE) {
            if (pickType != PickType.LODESTONE) {
                event.setCancelled(true);
                player.sendMessage(net.kyori.adventure.text.Component.text("Only a Lodestone Lock Pick can be used on this block."));
                return;
            }
        } else if (pickType == PickType.LODESTONE) {
            event.setCancelled(true);
            player.sendMessage(net.kyori.adventure.text.Component.text("A Lodestone Lock Pick can only be used on Lodestones."));
            return;
        }

        if (minigameEnabled) {
            event.setCancelled(true);
            openMinigame(player, locations, lockInfo, pickType);
            return;
        }

        boolean normalKeyLock = lockInfo.normalKey();
        long now = System.currentTimeMillis();
        boolean success;
        boolean overLimit = false;
        boolean lockoutNow = false;
        boolean changed = false;
        LockInfo updated = lockInfo;
        PickState state = getPickState(lockInfo, player);

        switch (pickType) {
            case RUSTY -> {
                int limit = state.rustyLimit();
                if (limit < 0) {
                    limit = ThreadLocalRandom.current().nextInt(pickLimitMin, pickLimitMax + 1);
                    state = state.withRustyLimit(limit);
                    changed = true;
                }
                int attempts = state.rustyAttempts();
                boolean overLimitBefore = attempts >= limit;
                lockoutNow = !overLimitBefore && (attempts + 1) >= limit;
                overLimit = overLimitBefore || lockoutNow;
                state = state.withRustyAttempts(attempts + 1);
                changed = true;
                double chance = normalKeyLock ? rustyNormalKeyChance : rustyOpenChance;
                success = !overLimitBefore && !lockoutNow && ThreadLocalRandom.current().nextDouble() < chance;
                if (!success) {
                    player.damage(rustyDamage);
                    if (lockoutNow) {
                        playWorldSoundDelayed(block, Sound.BLOCK_VAULT_DEACTIVATE);
                        playWorldSoundDelayed(block, Sound.BLOCK_VAULT_HIT);
                    } else if (overLimitBefore) {
                        playWorldSoundDelayed(block, Sound.BLOCK_VAULT_HIT);
                    } else {
                        playWorldSoundDelayed(block, Sound.BLOCK_CHEST_LOCKED);
                    }
                } else {
                    playWorldSoundDelayed(block, Sound.BLOCK_TRIPWIRE_CLICK_ON);
                }
            }
            case NORMAL -> {
                int limit = state.normalLimit();
                if (limit < 0) {
                    limit = ThreadLocalRandom.current().nextInt(pickLimitMin, pickLimitMax + 1);
                    state = state.withNormalLimit(limit);
                    changed = true;
                }
                int attempts = state.normalAttempts();
                boolean overLimitBefore = attempts >= limit;
                lockoutNow = !overLimitBefore && (attempts + 1) >= limit;
                overLimit = overLimitBefore || lockoutNow;
                state = state.withNormalAttempts(attempts + 1);
                changed = true;
                double chance = normalKeyLock ? normalNormalKeyChance : normalOpenChance;
                success = !overLimitBefore && !lockoutNow && ThreadLocalRandom.current().nextDouble() < chance;
                if (!success) {
                    player.damage(normalDamage);
                    if (lockoutNow) {
                        playWorldSoundDelayed(block, Sound.BLOCK_VAULT_DEACTIVATE);
                        playWorldSoundDelayed(block, Sound.BLOCK_VAULT_HIT);
                    } else if (overLimitBefore) {
                        playWorldSoundDelayed(block, Sound.BLOCK_VAULT_HIT);
                    } else {
                        playWorldSoundDelayed(block, Sound.BLOCK_CHEST_LOCKED);
                    }
                } else {
                    playWorldSoundDelayed(block, Sound.BLOCK_TRIPWIRE_CLICK_ON);
                }
            }
            case SILENCE -> {
                int limit = state.silenceLimit();
                if (limit < 0) {
                    limit = ThreadLocalRandom.current().nextInt(pickLimitMin, pickLimitMax + 1);
                    state = state.withSilenceLimit(limit);
                    changed = true;
                }
                int attempts = state.silenceAttempts();
                boolean overLimitBefore = attempts >= limit;
                lockoutNow = !overLimitBefore && (attempts + 1) >= limit;
                overLimit = overLimitBefore || lockoutNow;

                int overLimitAttempts = state.silenceOverLimitAttempts();
                boolean penaltyExpired = state.silencePenaltyTimestamp() > 0
                        && now - state.silencePenaltyTimestamp() >= silencePenaltyResetMs;
                if (penaltyExpired) {
                    overLimitAttempts = 0;
                    state = state.withSilenceOverLimitAttempts(0).withSilencePenaltyTimestamp(0L);
                    changed = true;
                }

                double chance = silenceOpenChance;
                if (overLimit) {
                    boolean criticalOverLimit = lockoutNow || overLimitAttempts == 0;
                    if (criticalOverLimit) {
                        overLimitAttempts = 1;
                    }
                    int penaltySteps = overLimitAttempts;
                    chance = chance / Math.pow(2.0, penaltySteps);
                    player.damage(silenceDamage);
                    if (criticalOverLimit) {
                        playWorldSoundDelayed(block, Sound.BLOCK_VAULT_DEACTIVATE);
                        playWorldSoundDelayed(block, Sound.BLOCK_VAULT_HIT);
                    } else if (overLimitBefore) {
                        playWorldSoundDelayed(block, Sound.BLOCK_VAULT_HIT);
                    }
                    overLimitAttempts += 1;
                    state = state.withSilenceOverLimitAttempts(overLimitAttempts).withSilencePenaltyTimestamp(now);
                    changed = true;
                }
                state = state.withSilenceAttempts(attempts + 1);
                changed = true;
                success = !overLimitBefore && !lockoutNow && ThreadLocalRandom.current().nextDouble() < chance;
            }
            case LODESTONE -> {
                // Lodestone picks don't have a limit/lockout for now, or should they?
                // User didn't specify, let's keep it simple or follow Normal pick logic.
                // Let's give it a limit too.
                int limit = state.normalLimit(); // Reuse normal limit or add a new one?
                if (limit < 0) {
                    limit = ThreadLocalRandom.current().nextInt(pickLimitMin, pickLimitMax + 1);
                    state = state.withNormalLimit(limit);
                    changed = true;
                }
                int attempts = state.normalAttempts();
                boolean overLimitBefore = attempts >= limit;
                lockoutNow = !overLimitBefore && (attempts + 1) >= limit;
                overLimit = overLimitBefore || lockoutNow;
                state = state.withNormalAttempts(attempts + 1);
                changed = true;
                
                double chance = lodestoneOpenChance;
                success = !overLimitBefore && !lockoutNow && ThreadLocalRandom.current().nextDouble() < chance;
                if (!success) {
                    player.damage(lodestoneDamage);
                    if (lockoutNow) {
                        playWorldSoundDelayed(block, Sound.BLOCK_VAULT_DEACTIVATE);
                        playWorldSoundDelayed(block, Sound.BLOCK_VAULT_HIT);
                    } else if (overLimitBefore) {
                        playWorldSoundDelayed(block, Sound.BLOCK_VAULT_HIT);
                    } else {
                        playWorldSoundDelayed(block, Sound.BLOCK_CHEST_LOCKED);
                    }
                } else {
                    // Lodestone success sound is louder and distinct
                    playWorldSoundDelayed(block, Sound.BLOCK_ANVIL_PLACE);
                    playWorldSoundDelayed(block, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR);
                }
            }
            default -> {
                return;
            }
        }

        updated = updated.withLastPick(player, pickType, now);
        updated = updatePickState(updated, player, state);
        changed = true;

        if (changed) {
            updateLockInfo(locations, updated);
        }

        double breakChance = switch (pickType) {
            case RUSTY -> rustyBreakChance;
            case NORMAL -> normalBreakChance;
            case SILENCE -> silenceBreakChance;
            case LODESTONE -> lodestoneBreakChance;
        };
        if (ThreadLocalRandom.current().nextDouble() < breakChance) {
            consumeOnePick(player, pickMatch);
        }

        if (success) {
            logLockEvent("PICK_SUCCESS", player.getName(), null, block.getLocation(), lockInfo, "pick=" + pickType.id());
            
            // Fire custom event
            LockPickSuccessEvent successEvent = new LockPickSuccessEvent(player, block, lockInfo, pickType);
            Bukkit.getPluginManager().callEvent(successEvent);

            if (!successEvent.isCancelled()) {
                unlock(block, lockInfo.keyName()); // This will also save data via dataStore
            }

            if (pickType == PickType.SILENCE) {
                playWorldSoundDelayed(block, Sound.BLOCK_VAULT_OPEN_SHUTTER);
                event.setCancelled(true);
                openSilently(player, block);
            } else {
                event.setCancelled(false);
                event.setUseInteractedBlock(Result.ALLOW);
                event.setUseItemInHand(Result.ALLOW);
            }
        } else {
            event.setCancelled(true);
            logLockEvent("PICK_FAIL", player.getName(), null, block.getLocation(), lockInfo,
                    "pick=" + pickType.id() + (overLimit ? " overLimit=true" : ""));
        }
    }


    public void playSuccess(Player player, Location location) {
        if (location.getWorld() == null) {
            return;
        }
        location.getWorld().playSound(location, Sound.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    public void playFail(Player player, Location location) {
        if (location.getWorld() == null) {
            return;
        }
        location.getWorld().playSound(location, Sound.BLOCK_VAULT_INSERT_ITEM_FAIL, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    public void playInsert(Player player, Location location) {
        if (location.getWorld() == null) {
            return;
        }
        location.getWorld().playSound(location, Sound.BLOCK_VAULT_INSERT_ITEM, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    public void playWorldSoundDelayed(Block block, Sound sound) {
        if (block == null || sound == null) {
            return;
        }
        World world = block.getWorld();
        if (world == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin,
                () -> world.playSound(block.getLocation(), sound, SoundCategory.MASTER, 1.6f, 1.0f));
    }

    public void openSilently(Player player, Block block) {
        if (!(block.getState() instanceof InventoryHolder holder)) {
            return;
        }
        player.openInventory(holder.getInventory());
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.stopSound(Sound.BLOCK_CHEST_OPEN);
            player.stopSound(Sound.BLOCK_CHEST_CLOSE);
            player.stopSound(Sound.BLOCK_BARREL_OPEN);
            player.stopSound(Sound.BLOCK_BARREL_CLOSE);
            player.stopSound(Sound.BLOCK_SHULKER_BOX_OPEN);
            player.stopSound(Sound.BLOCK_SHULKER_BOX_CLOSE);
        });
    }

    public Location lockLocation(Inventory inventory) {
        List<Location> locations = LocationUtil.resolveLockLocations(inventory.getHolder());
        if (locations.isEmpty()) {
            return inventory.getLocation();
        }
        return locations.getFirst();
    }

    public void updatePickRecipes() {
        ConfigManager cm = plugin.getConfigManager();
        NamespacedKey rustyKey = new NamespacedKey(plugin, "rusty_lock_pick");
        NamespacedKey normalKey = new NamespacedKey(plugin, "normal_lock_pick");
        NamespacedKey silenceKey = new NamespacedKey(plugin, "silence_lock_pick");
        NamespacedKey lodestoneKey = new NamespacedKey(plugin, "lodestone_lock_pick");
        Bukkit.removeRecipe(rustyKey);
        Bukkit.removeRecipe(normalKey);
        Bukkit.removeRecipe(silenceKey);
        Bukkit.removeRecipe(lodestoneKey);
        if (!allowLockpicks) {
            return;
        }

        ItemStack rustyPick = ItemUtil.createPick(PickType.RUSTY);
        ItemStack normalPick = ItemUtil.createPick(PickType.NORMAL);
        ItemStack silencePick = ItemUtil.createPick(PickType.SILENCE);
        ItemStack lodestonePick = ItemUtil.createPick(PickType.LODESTONE);

        // Rusty Pick
        List<String> rustyIngreds = cm.getRustyRecipe();
        if (!rustyIngreds.isEmpty()) {
            ShapelessRecipe rustyRecipe = new ShapelessRecipe(rustyKey, rustyPick);
            boolean added = false;
            for (String s : rustyIngreds) {
                Material m = Material.getMaterial(s.toUpperCase());
                if (m != null) {
                    rustyRecipe.addIngredient(m);
                    added = true;
                } else {
                    plugin.getLogger().warning("Invalid material in rusty_pick recipe: " + s);
                }
            }
            if (added) Bukkit.addRecipe(rustyRecipe);
        }

        // Normal Pick
        List<String> normalIngreds = cm.getNormalRecipe();
        if (!normalIngreds.isEmpty()) {
            ShapelessRecipe normalRecipe = new ShapelessRecipe(normalKey, normalPick);
            boolean added = false;
            for (String s : normalIngreds) {
                Material m = Material.getMaterial(s.toUpperCase());
                if (m != null) {
                    normalRecipe.addIngredient(m);
                    added = true;
                } else {
                    plugin.getLogger().warning("Invalid material in normal_pick recipe: " + s);
                }
            }
            if (added) Bukkit.addRecipe(normalRecipe);
        }

        // Lodestone Pick
        List<String> lodestoneIngreds = cm.getLodestoneRecipe();
        if (!lodestoneIngreds.isEmpty()) {
            ShapelessRecipe lodestoneRecipe = new ShapelessRecipe(lodestoneKey, lodestonePick);
            boolean added = false;
            for (String s : lodestoneIngreds) {
                Material m = Material.getMaterial(s.toUpperCase());
                if (m != null) {
                    lodestoneRecipe.addIngredient(m);
                    added = true;
                } else {
                    plugin.getLogger().warning("Invalid material in lodestone_pick recipe: " + s);
                }
            }
            if (added) Bukkit.addRecipe(lodestoneRecipe);
        }

        // Silence Pick (Smithing)
        Material templateMat = Material.getMaterial(cm.getSilenceSmithingTemplate().toUpperCase());
        Material additionMat = Material.getMaterial(cm.getSilenceSmithingAddition().toUpperCase());

        RecipeChoice baseChoice;
        String baseId = cm.getSilenceSmithingBase().toLowerCase();
        if (baseId.endsWith("_pick")) {
            baseId = baseId.substring(0, baseId.length() - "_pick".length());
        }
        PickType baseType = switch (baseId) {
            case "rusty" -> PickType.RUSTY;
            case "normal" -> PickType.NORMAL;
            case "lodestone" -> PickType.LODESTONE;
            default -> null;
        };

        if (baseType != null) {
            baseChoice = new RecipeChoice.ExactChoice(ItemUtil.createPick(baseType));
        } else {
            Material baseMat = Material.getMaterial(cm.getSilenceSmithingBase().toUpperCase());
            if (baseMat != null) {
                baseChoice = new RecipeChoice.MaterialChoice(baseMat);
            } else {
                plugin.getLogger().warning("Invalid base for silence_pick smithing recipe: " + cm.getSilenceSmithingBase());
                return;
            }
        }

        if (templateMat != null && additionMat != null) {
            SmithingTransformRecipe silenceRecipe = new SmithingTransformRecipe(
                    silenceKey,
                    silencePick,
                    new RecipeChoice.MaterialChoice(templateMat),
                    baseChoice,
                    new RecipeChoice.MaterialChoice(additionMat)
            );
            Bukkit.addRecipe(silenceRecipe);
        } else {
            if (templateMat == null) plugin.getLogger().warning("Invalid template for silence_pick: " + cm.getSilenceSmithingTemplate());
            if (additionMat == null) plugin.getLogger().warning("Invalid addition for silence_pick: " + cm.getSilenceSmithingAddition());
        }
    }

    public boolean handleMinigameClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return false;
        }
        MinigameSession session = minigameSessions.get(player.getUniqueId());
        if (session == null || event.getView().getTopInventory() != session.inventory) {
            return false;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= session.inventory.getSize()) {
            return true;
        }
        int depths = Math.min(GRID_MAX_ROWS, session.minigameData.depths());
        int pins = Math.min(GRID_MAX_COLUMNS, session.minigameData.pins());
        if (slot == SLOT_TURN_LOCK) {
            turnLock(player, session);
            return true;
        }
        if (slot == SLOT_RESET_ALL) {
            for (int p = 0; p < session.eliminated.length; p++) {
                for (int d = 0; d < session.eliminated[p].length; d++) {
                    session.eliminated[p][d] = false;
                }
                session.selectedDepths[p] = -1;
            }
            session.feedbackColor = MinigameSession.FeedbackColor.OFF;
            session.feedbackProgress = 0.0;
            renderMinigame(session);
            return true;
        }
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return true;
        }
        int row = slot / 9;
        int col = slot % 9;
        int pin = col - GRID_FIRST_COLUMN;
        int depth = row;
        if (pin >= 0 && pin < pins && depth >= 0 && depth < depths) {
            if (event.getClick() == ClickType.RIGHT) {
                session.eliminated[pin][depth] = !session.eliminated[pin][depth];
                if (session.eliminated[pin][depth] && session.selectedDepths[pin] == depth) {
                    session.selectedDepths[pin] = -1;
                }
            } else {
                if (!session.eliminated[pin][depth]) {
                    session.selectedDepths[pin] = depth;
                }
            }
            renderMinigame(session);
        }
        return true;
    }

    public void handleMinigameClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        MinigameSession session = minigameSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (event.getInventory() == session.inventory) {
            closeMinigame(session.playerId);
        }
    }

    public void handlePlayerQuit(Player player) {
        closeMinigame(player.getUniqueId());
    }

    private void openMinigame(Player player, List<Location> locations, LockInfo lockInfo, PickType pickType) {
        String containerId = LocationUtil.locationKey(locations.getFirst());
        UUID owner = minigameOwners.get(containerId);
        if (owner != null && !owner.equals(player.getUniqueId())) {
            player.sendMessage(net.kyori.adventure.text.Component.text("Someone else is currently lockpicking this container."));
            return;
        }

        LockInfo updated = ensureMinigameData(locations, lockInfo);
        LockMinigameData data = updated.minigameData();
        if (data == null) {
            return;
        }
        Inventory inv = Bukkit.createInventory(player, MINIGAME_SIZE, "Locked Chest");
        MinigameSession session = new MinigameSession(player.getUniqueId(), containerId, locations, lockInfo.keyName(), pickType, inv, data);
        minigameSessions.put(player.getUniqueId(), session);
        minigameOwners.put(containerId, player.getUniqueId());
        renderMinigame(session);
        player.openInventory(inv);
    }

    private LockInfo ensureMinigameData(List<Location> locations, LockInfo lockInfo) {
        if (lockInfo.minigameData() != null) {
            return lockInfo;
        }
        Location anchor = locations.isEmpty() ? null : locations.getFirst();
        if (anchor == null || anchor.getWorld() == null) {
            return lockInfo;
        }
        boolean ominous = !lockInfo.normalKey() || anchor.getBlock().getType() == Material.LODESTONE;
        int pins = ominous ? minigameOminousPins : minigameTrialPins;
        int depths = ominous ? minigameOminousDepths : minigameTrialDepths;
        int[] secret = generateSeededSecret(anchor, lockInfo.keyName(), ominous ? "ominous" : "trial", pins, depths);
        LockMinigameData data = new LockMinigameData(ominous ? "ominous" : "trial", pins, depths, secret, System.currentTimeMillis(), 1);
        LockInfo updated = lockInfo.withMinigameData(data);
        updateLockInfo(locations, updated);
        return updated;
    }

    private int[] generateSeededSecret(Location location, String keyName, String type, int pins, int depths) {
        int safePins = Math.max(1, Math.min(GRID_MAX_COLUMNS, pins));
        int safeDepths = Math.max(1, Math.min(GRID_MAX_ROWS, depths));
        byte[] seed = createPinSeed(location, keyName, type);
        Random random = new Random(bytesToLong(seed));
        int[] secret = new int[safePins];
        for (int i = 0; i < safePins; i++) {
            secret[i] = random.nextInt(safeDepths);
        }
        return secret;
    }

    private byte[] createPinSeed(Location location, String keyName, String type) {
        String worldUid = location.getWorld() == null ? "unknown" : location.getWorld().getUID().toString();
        String payload = worldUid + "|" + location.getBlockX() + "|" + location.getBlockY() + "|" + location.getBlockZ()
                + "|" + keyName + "|" + type + "|" + "change-me";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ignored) {
            return payload.getBytes(StandardCharsets.UTF_8);
        }
    }

    private long bytesToLong(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return 0L;
        }
        long value = 0L;
        int limit = Math.min(8, bytes.length);
        for (int i = 0; i < limit; i++) {
            value = (value << 8) | (bytes[i] & 0xFFL);
        }
        return value;
    }

    private void renderMinigame(MinigameSession session) {
        Inventory inv = session.inventory;
        inv.clear();
        int depths = Math.min(GRID_MAX_ROWS, session.minigameData.depths());
        int pins = Math.min(GRID_MAX_COLUMNS, session.minigameData.pins());

        ItemStack filler = namedItem(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int i = 0; i < MINIGAME_SIZE; i++) {
            inv.setItem(i, filler);
        }

        for (int pin = 0; pin < pins; pin++) {
            for (int depth = 0; depth < depths; depth++) {
                boolean selected = session.selectedDepths[pin] == depth;
                boolean eliminated = session.eliminated[pin][depth];
                Material mat = selected ? minigamePinIcon : (eliminated ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
                NamedTextColor color = selected ? NamedTextColor.GREEN : (eliminated ? NamedTextColor.RED : NamedTextColor.GRAY);
                String state = selected ? "Selected" : (eliminated ? "Eliminated" : "Candidate");
                inv.setItem(depth * 9 + (GRID_FIRST_COLUMN + pin), namedItem(
                        mat, "Depth " + (depth + 1), color, List.of(
                                Component.text("Pin " + (pin + 1), NamedTextColor.WHITE),
                                Component.text(state, color),
                                Component.text("L-click: select", NamedTextColor.GRAY),
                                Component.text("R-click: eliminate", NamedTextColor.GRAY)
                        )));
            }
        }

        String lockTypeLabel = "trial".equalsIgnoreCase(session.minigameData.type()) ? "Trial Lock" : "Ominous Lock";
        inv.setItem(8, namedItem(Material.TRIAL_KEY, "Lock Type: " + lockTypeLabel, NamedTextColor.AQUA, List.of(
                Component.text("Pick: " + session.pickType.id(), NamedTextColor.GOLD),
                Component.text("Pins: " + session.minigameData.pins() + "  Depths: " + session.minigameData.depths(), NamedTextColor.GRAY)
        )));
        inv.setItem(SLOT_RESET_ALL, namedItem(Material.BARRIER, "Reset All", NamedTextColor.RED, List.of()));
        inv.setItem(SLOT_TURN_LOCK, namedItem(Material.LEVER, "TURN LOCK", NamedTextColor.GOLD, List.of(
                Component.text("Attempts are consumed here.", NamedTextColor.YELLOW)
        )));
        inv.setItem(SLOT_CLOSE, namedItem(Material.OAK_DOOR, "Close", NamedTextColor.GRAY, List.of()));
        renderBottomTurnMeter(inv, session);
    }

    private void renderBottomTurnMeter(Inventory inv, MinigameSession session) {
        Material meterMat = switch (session.feedbackColor) {
            case GREEN -> Material.LIME_STAINED_GLASS_PANE;
            case RED -> Material.RED_STAINED_GLASS_PANE;
            case YELLOW -> Material.YELLOW_STAINED_GLASS_PANE;
            default -> Material.GRAY_STAINED_GLASS_PANE;
        };
        int lit = (int) Math.round(Math.max(0.0, Math.min(1.0, session.feedbackProgress)) * 9.0);
        for (int col = 0; col < 9; col++) {
            if (col < lit) {
                NamedTextColor color = switch (session.feedbackColor) {
                    case GREEN -> NamedTextColor.GREEN;
                    case RED -> NamedTextColor.RED;
                    case YELLOW -> NamedTextColor.YELLOW;
                    default -> NamedTextColor.DARK_GRAY;
                };
                inv.setItem(45 + col, namedItem(meterMat, " ", color, List.of()));
            } else {
                inv.setItem(45 + col, namedItem(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of()));
            }
        }
    }

    private ItemStack namedItem(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void turnLock(Player player, MinigameSession session) {
        LockInfo lockInfo = getLockInfo(session.locations);
        if (lockInfo == null) {
            player.closeInventory();
            closeMinigame(player.getUniqueId());
            return;
        }
        int[] secret = session.minigameData.secret();
        int correctPins = countCorrectPins(session, secret, session.minigameData.depths());
        boolean allPinsCorrect = correctPins == session.minigameData.pins();
        PickOutcome outcome = attemptByPickType(player, session.locations.getFirst().getBlock(), lockInfo, session.pickType, allPinsCorrect);
        if (!outcome.success) {
            int distance = 0;
            for (int i = 0; i < secret.length; i++) {
                int selected = session.selectedDepths[i];
                int actual = normalizeSecretValue(secret[i], session.minigameData.depths());
                if (selected < 0) {
                    distance += session.minigameData.depths();
                } else {
                    distance += Math.abs(actual - selected);
                }
            }
            int maxDistance = session.minigameData.pins() * session.minigameData.depths();
            session.feedbackColor = outcome.lockoutNow ? MinigameSession.FeedbackColor.RED : MinigameSession.FeedbackColor.YELLOW;
            session.feedbackProgress = Math.max(0.0, Math.min(1.0, 1.0 - ((double) distance / (double) Math.max(1, maxDistance))));
            renderMinigame(session);
            String msg = outcome.lockoutNow ? "Locked Out" : "Chest Locked! (Turn Distance: " + distance + ")";
            player.sendMessage(net.kyori.adventure.text.Component.text(msg));
            return;
        }
        if (!allPinsCorrect) {
            player.damage(switch (session.pickType) {
                case RUSTY -> rustyDamage;
                case NORMAL -> normalDamage;
                case SILENCE -> silenceDamage;
                case LODESTONE -> lodestoneDamage;
            });
            playWorldSoundDelayed(session.locations.getFirst().getBlock(), Sound.BLOCK_CHEST_LOCKED);
            session.feedbackColor = MinigameSession.FeedbackColor.RED;
            session.feedbackProgress = 0.0;
            renderMinigame(session);
            player.sendMessage(net.kyori.adventure.text.Component.text("Wrong pin alignment."));
            return;
        }
        session.feedbackColor = MinigameSession.FeedbackColor.GREEN;
        session.feedbackProgress = 1.0;
        renderMinigame(session);
        Block block = session.locations.getFirst().getBlock();
        LockPickSuccessEvent successEvent = new LockPickSuccessEvent(player, block, lockInfo, session.pickType);
        Bukkit.getPluginManager().callEvent(successEvent);
        if (!successEvent.isCancelled()) {
            unlock(block, session.keyName);
        }
        closeMinigame(player.getUniqueId());
        player.closeInventory();
        if (!successEvent.isCancelled() && block.getState() instanceof InventoryHolder holder) {
            player.openInventory(holder.getInventory());
        }
    }

    private int countCorrectPins(MinigameSession session, int[] secret, int depths) {
        int correct = 0;
        for (int i = 0; i < session.minigameData.pins(); i++) {
            int actual = normalizeSecretValue(secret[i], depths);
            if (session.selectedDepths[i] == actual) {
                correct++;
            }
        }
        return correct;
    }

    private int normalizeSecretValue(int value, int depths) {
        if (value >= 0 && value < depths) {
            return value;
        }
        if (value >= 1 && value <= depths) {
            return value - 1;
        }
        return Math.max(0, Math.min(depths - 1, value));
    }

    private void closeMinigame(UUID playerId) {
        MinigameSession session = minigameSessions.remove(playerId);
        if (session == null) {
            return;
        }
        UUID owner = minigameOwners.get(session.containerId);
        if (owner != null && owner.equals(playerId)) {
            minigameOwners.remove(session.containerId);
        }
    }

    private PickOutcome attemptByPickType(Player player, Block block, LockInfo lockInfo, PickType pickType, boolean allPinsCorrect) {
        long now = System.currentTimeMillis();
        boolean success;
        boolean overLimit = false;
        boolean lockoutNow = false;
        LockInfo updated = lockInfo;
        PickState state = getPickState(lockInfo, player);

        switch (pickType) {
            case RUSTY -> {
                int limit = state.rustyLimit();
                if (limit < 0) {
                    limit = ThreadLocalRandom.current().nextInt(pickLimitMin, pickLimitMax + 1);
                    state = state.withRustyLimit(limit);
                }
                int attempts = state.rustyAttempts();
                boolean overLimitBefore = attempts >= limit;
                lockoutNow = !overLimitBefore && (attempts + 1) >= limit;
                overLimit = overLimitBefore || lockoutNow;
                state = state.withRustyAttempts(attempts + 1);
                success = !overLimitBefore && !lockoutNow && allPinsCorrect;
            }
            case NORMAL -> {
                int limit = state.normalLimit();
                if (limit < 0) {
                    limit = ThreadLocalRandom.current().nextInt(pickLimitMin, pickLimitMax + 1);
                    state = state.withNormalLimit(limit);
                }
                int attempts = state.normalAttempts();
                boolean overLimitBefore = attempts >= limit;
                lockoutNow = !overLimitBefore && (attempts + 1) >= limit;
                overLimit = overLimitBefore || lockoutNow;
                state = state.withNormalAttempts(attempts + 1);
                success = !overLimitBefore && !lockoutNow && allPinsCorrect;
            }
            case SILENCE -> {
                int limit = state.silenceLimit();
                if (limit < 0) {
                    limit = ThreadLocalRandom.current().nextInt(pickLimitMin, pickLimitMax + 1);
                    state = state.withSilenceLimit(limit);
                }
                int attempts = state.silenceAttempts();
                boolean overLimitBefore = attempts >= limit;
                lockoutNow = !overLimitBefore && (attempts + 1) >= limit;
                overLimit = overLimitBefore || lockoutNow;
                state = state.withSilenceAttempts(attempts + 1);
                success = !overLimitBefore && !lockoutNow && allPinsCorrect;
            }
            case LODESTONE -> {
                success = allPinsCorrect;
            }
            default -> success = false;
        }

        updated = updated.withLastPick(player, pickType, now);
        updated = updatePickState(updated, player, state);
        updateLockInfo(LocationUtil.resolveLockLocations(block), updated);
        return new PickOutcome(success, overLimit, lockoutNow);
    }

    private record PickOutcome(boolean success, boolean overLimit, boolean lockoutNow) {}

    // --- Logging methods ---
    public void logInventoryMove(org.bukkit.event.inventory.InventoryMoveItemEvent event, String detail) {
        InventoryHolder sourceHolder = event.getSource().getHolder();
        InventoryHolder destHolder = event.getDestination().getHolder();
        if (isLockedHolder(sourceHolder)) {
            Location loc = LocationUtil.resolveLockLocations(sourceHolder).stream().findFirst().orElse(event.getSource().getLocation());
            String actor = destHolder instanceof org.bukkit.block.Hopper ? inventoryActor(destHolder) : inventoryActor(sourceHolder);
            logLockEvent("INVENTORY_MOVE_DENY", actor, null, loc, getLockInfo(loc), detail);
        }
        if (isLockedHolder(destHolder)) {
            Location loc = LocationUtil.resolveLockLocations(destHolder).stream().findFirst().orElse(event.getDestination().getLocation());
            logLockEvent("INVENTORY_MOVE_DENY", inventoryActor(destHolder), null, loc, getLockInfo(loc), detail);
        }
    }

    public String inventoryActor(InventoryHolder holder) {
        if (holder instanceof org.bukkit.block.Hopper hopper) {
            HopperOwner owner = hopperOwners.get(LocationUtil.locationKey(hopper.getLocation()));
            if (owner != null) {
                return "HOPPER:" + owner.playerName();
            }
            return "HOPPER";
        }
        if (holder == null) {
            return "HOPPER";
        }
        return holder.getClass().getSimpleName();
    }

    public void logLockEvent(String action, String actor, String keyUsed, Location location, LockInfo info, String detail) {
        if (!shouldLog(action, location)) {
            return;
        }
        String used = keyUsed == null || keyUsed.isBlank() ? "none" : keyUsed;
        String keyName = info == null ? "unknown" : info.keyName();
        String creator = info == null || info.creatorName() == null ? "unknown" : info.creatorName();
        String lastUser = info == null || info.lastUserName() == null ? "unknown" : info.lastUserName();
        String loc = location == null ? "unknown" : LocationUtil.formatLocation(location);
        String extra = detail == null ? "" : " detail=" + detail;
        plugin.getLogger().info(action + " actor=" + actor + " usedKey=" + used + " lockKey=" + keyName
                + " creator=" + creator + " lastUser=" + lastUser + " location=" + loc + extra);
    }

    private boolean shouldLog(String action, Location location) {
        if (logLevel <= 0) {
            return false;
        }
        if (logLevel == 3) {
            return isDestructionAction(action);
        }
        if (logLevel == 2) {
            return isDestructionAction(action) || isFailedAction(action);
        }
        if (!"INVENTORY_MOVE_DENY".equals(action) && !"INVENTORY_PICKUP_DENY".equals(action)) {
            return true;
        }
        if (location == null || location.getWorld() == null) {
            return true;
        }
        String key = action + "|" + LocationUtil.locationKey(location);
        long now = System.currentTimeMillis();
        Long last = logCooldowns.get(key);
        if (last != null && now - last < 5000L) {
            return false;
        }
        logCooldowns.put(key, now);
        return true;
    }

    private boolean isDestructionAction(String action) {
        return "BREAK_DENY".equals(action)
                || "EXPLOSION_DENY".equals(action)
                || "BURN_DENY".equals(action)
                || "PISTON_EXTEND_DENY".equals(action)
                || "PISTON_RETRACT_DENY".equals(action)
                || "INVENTORY_MOVE_DENY".equals(action)
                || "INVENTORY_PICKUP_DENY".equals(action);
    }

    private boolean isFailedAction(String action) {
        return "OPEN_DENY".equals(action)
                || "INTERACT_DENY".equals(action)
                || "LOCK_DENY".equals(action)
                || "PICK_FAIL".equals(action)
                || "INVENTORY_CLICK_DENY".equals(action)
                || "INVENTORY_DRAG_DENY".equals(action)
                || "INVENTORY_MOVE_DENY".equals(action)
                || "INVENTORY_PICKUP_DENY".equals(action);
    }

    public String explosionActor(org.bukkit.entity.Entity entity) {
        if (entity instanceof TNTPrimed tnt) {
            org.bukkit.entity.Entity source = tnt.getSource();
            if (source instanceof Player player) {
                return "TNT:" + player.getName();
            }
            if (source != null) {
                return "TNT:" + source.getType().name();
            }
            String mapped = tntSources.get(tnt.getUniqueId());
            if (mapped != null) {
                return "TNT:" + mapped;
            }
            return "TNT";
        }
        if (entity instanceof EnderCrystal crystal) {
            PendingIgnite ignite = crystalSources.get(crystal.getUniqueId());
            if (ignite != null && System.currentTimeMillis() - ignite.timestamp() < 10000L) {
                return "END_CRYSTAL:" + ignite.playerName();
            }
            return "END_CRYSTAL";
        }
        if (entity != null) {
            return entity.getType().name();
        }
        return "ENTITY_EXPLODE";
    }

    public Map<String, PendingIgnite> getTntIgnites() {
        return tntIgnites;
    }

    public Map<UUID, String> getTntSources() {
        return tntSources;
    }

    public Map<UUID, PendingIgnite> getCrystalSources() {
        return crystalSources;
    }

    public Map<String, HopperOwner> getHopperOwners() {
        return hopperOwners;
    }

    public Map<String, LockInfo> getLockedChests() {
        return lockedChests;
    }

    public Map<String, String> getKeyToChest() {
        return keyToChest;
    }

    public NamespacedKey getPickTypeKey() {
        return pickTypeKey;
    }

    public int getLogLevel() {
        return logLevel;
    }
}
