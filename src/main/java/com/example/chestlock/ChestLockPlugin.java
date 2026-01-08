package com.example.chestlock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Tag;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ChestLockPlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final PlainTextComponentSerializer TEXT_SERIALIZER = PlainTextComponentSerializer.plainText();

    private final Map<String, LockInfo> lockedChests = new HashMap<>();
    private final Map<String, String> keyToChest = new HashMap<>();
    private final Map<String, Long> logCooldowns = new HashMap<>();
    private final Map<String, PendingIgnite> tntIgnites = new HashMap<>();
    private final Map<UUID, String> tntSources = new HashMap<>();
    private final Map<UUID, PendingIgnite> crystalSources = new HashMap<>();
    private final Map<String, HopperOwner> hopperOwners = new HashMap<>();
    private int logLevel = 1;
    private boolean allowNormalKeys = false;
    private boolean allowLockpicks = true;
    private NamespacedKey pickTypeKey;
    private int pickLimitMin = 1;
    private int pickLimitMax = 20;
    private double rustyOpenChance = 0.05;
    private double rustyNormalKeyChance = 0.10;
    private double rustyBreakChance = 1.0;
    private double rustyDamage = 1.0;
    private double normalOpenChance = 0.10;
    private double normalNormalKeyChance = 0.20;
    private double normalBreakChance = 0.50;
    private double normalDamage = 2.0;
    private double silenceOpenChance = 0.50;
    private double silenceBreakChance = 0.05;
    private double silenceDamage = 4.0;
    private long silencePenaltyResetMs = SILENCE_PENALTY_RESET_MS;
    private LockoutScope lockoutScope = LockoutScope.CHEST;

    private static final long SILENCE_PENALTY_RESET_MS = 60L * 60L * 1000L;
    private static final int RUSTY_MODEL_DATA = 11001;
    private static final int NORMAL_MODEL_DATA = 11002;
    private static final int SILENCE_MODEL_DATA = 11003;

    private File dataFile;

    @Override
    public void onEnable() {
        dataFile = new File(getDataFolder(), "data.yml");
        saveDefaultConfig();
        pickTypeKey = new NamespacedKey(this, "lock_pick_type");
        loadConfigValues();
        loadData();
        updatePickRecipes();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("chestlock") != null) {
            getCommand("chestlock").setExecutor(this);
            getCommand("chestlock").setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        saveData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("chestlock")) {
            return false;
        }

        if (!sender.hasPermission("chestlock.admin")) {
            sender.sendMessage(Component.text("You do not have permission."));
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                sendHelp(player);
            } else {
                sender.sendMessage(Component.text("Usage: /chestlock <info|unlock|keyinfo|reload|loglevel|normalkeys|lockpicks|give|help>"));
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "info" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("This command can only be used by players."));
                    return true;
                }
                Block target = player.getTargetBlockExact(5);
                if (target == null || !isLockable(target)) {
                    player.sendMessage(Component.text("Look at a chest, barrel, or shulker within 5 blocks."));
                    return true;
                }
                LockInfo lockInfo = getLockInfo(target);
                if (lockInfo == null) {
                    player.sendMessage(Component.text("That container is not locked."));
                    return true;
                }
                String creator = lockInfo.creatorName() == null ? "unknown" : lockInfo.creatorName();
                String lastUser = lockInfo.lastUserName() == null ? "unknown" : lockInfo.lastUserName();
                player.sendMessage(Component.text("Locked with key name: " + lockInfo.keyName()));
                player.sendMessage(Component.text("Created by: " + creator));
                player.sendMessage(Component.text("Last used by: " + lastUser));
                PickState state = getPickState(lockInfo, player);
                player.sendMessage(Component.text("Rusty pick: " + formatPickStatus(state.rustyAttempts(), state.rustyLimit())));
                player.sendMessage(Component.text("Normal pick: " + formatPickStatus(state.normalAttempts(), state.normalLimit())));
                player.sendMessage(Component.text("Silence pick: " + formatSilenceStatus(state)));
                if (lockInfo.lastPickUserName() != null && lockInfo.lastPickType() != null) {
                    String when = lockInfo.lastPickTimestamp() > 0L
                            ? formatDuration(System.currentTimeMillis() - lockInfo.lastPickTimestamp()) + " ago"
                            : "unknown time";
                    player.sendMessage(Component.text("Last pick attempt: " + lockInfo.lastPickUserName()
                            + " with " + lockInfo.lastPickType() + " (" + when + ")"));
                }
                return true;
            }
            case "unlock" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("This command can only be used by players."));
                    return true;
                }
                Block target = player.getTargetBlockExact(5);
                if (target == null || !isLockable(target)) {
                    player.sendMessage(Component.text("Look at a chest, barrel, or shulker within 5 blocks."));
                    return true;
                }
                LockInfo lockInfo = getLockInfo(target);
                if (lockInfo == null) {
                    player.sendMessage(Component.text("That container is not locked."));
                    return true;
                }
                unlock(target, lockInfo.keyName());
                player.sendMessage(Component.text("Unlocked container (key name was: " + lockInfo.keyName() + ")."));
                return true;
            }
            case "keyinfo" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("This command can only be used by players."));
                    return true;
                }
                String keyName = getHeldKeyName(player);
                if (keyName == null) {
                    player.sendMessage(Component.text("Hold a named ominous trial key in your main hand or off hand."));
                    return true;
                }
                String locationKey = keyToChest.get(keyName);
                if (locationKey == null) {
                    player.sendMessage(Component.text("No locked container found for key name: " + keyName));
                    return true;
                }
                LockInfo lockInfo = lockedChests.get(locationKey);
                if (lockInfo == null) {
                    player.sendMessage(Component.text("Lock data missing for key name: " + keyName));
                    return true;
                }
                LocationData locationData = parseLocationKey(locationKey);
                String creator = lockInfo.creatorName() == null ? "unknown" : lockInfo.creatorName();
                String lastUser = lockInfo.lastUserName() == null ? "unknown" : lockInfo.lastUserName();
                player.sendMessage(Component.text("Key name: " + lockInfo.keyName()));
                if (locationData != null && locationData.realm() != null) {
                    player.sendMessage(Component.text("Locked container: " + locationKey + " (" + locationData.realm() + ")"));
                } else {
                    player.sendMessage(Component.text("Locked container: " + locationKey));
                }
                player.sendMessage(Component.text("Created by: " + creator));
                player.sendMessage(Component.text("Last used by: " + lastUser));
                return true;
            }
            case "reload" -> {
                reloadConfig();
                loadConfigValues();
                loadData();
                updatePickRecipes();
                sender.sendMessage(Component.text("ChestLock data reloaded."));
                return true;
            }
            case "give" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /chestlock give <player> <rusty|normal|silence> [amount]"));
                    return true;
                }
                String playerName = args[1];
                Player target = Bukkit.getPlayerExact(playerName);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found: " + playerName));
                    return true;
                }
                PickType pickType = parsePickType(args[2]);
                if (pickType == null) {
                    sender.sendMessage(Component.text("Pick type must be rusty, normal, or silence."));
                    return true;
                }
                int amount = 1;
                if (args.length >= 4) {
                    try {
                        amount = Integer.parseInt(args[3]);
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(Component.text("Amount must be a number."));
                        return true;
                    }
                }
                if (amount <= 0) {
                    sender.sendMessage(Component.text("Amount must be at least 1."));
                    return true;
                }
                ItemStack stack = createPick(pickType);
                stack.setAmount(amount);
                Map<Integer, ItemStack> overflow = target.getInventory().addItem(stack);
                for (ItemStack extra : overflow.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), extra);
                }
                sender.sendMessage(Component.text("Gave " + amount + " " + pickType.id + " pick(s) to " + target.getName() + "."));
                return true;
            }
            case "loglevel" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Current log level: " + logLevel));
                    sender.sendMessage(Component.text("Usage: /chestlock loglevel <0-3>"));
                    return true;
                }
                try {
                    int level = Integer.parseInt(args[1]);
                    if (level < 0 || level > 3) {
                        sender.sendMessage(Component.text("Log level must be between 0 and 3."));
                        return true;
                    }
                    logLevel = level;
                    getConfig().set("logging.level", level);
                    saveConfig();
                    sender.sendMessage(Component.text("Logging level set to " + level + "."));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(Component.text("Log level must be a number between 0 and 3."));
                }
                return true;
            }
            case "normalkeys" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Normal trial keys are " + (allowNormalKeys ? "enabled." : "disabled.")));
                    sender.sendMessage(Component.text("Usage: /chestlock normalkeys <on|off>"));
                    return true;
                }
                String value = args[1].toLowerCase();
                if (!value.equals("on") && !value.equals("off")) {
                    sender.sendMessage(Component.text("Usage: /chestlock normalkeys <on|off>"));
                    return true;
                }
                allowNormalKeys = value.equals("on");
                getConfig().set("keys.allow-normal", allowNormalKeys);
                saveConfig();
                sender.sendMessage(Component.text("Normal trial keys are now " + (allowNormalKeys ? "enabled." : "disabled.")));
                return true;
            }
            case "lockpicks" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Lockpicking is " + (allowLockpicks ? "enabled." : "disabled.")));
                    sender.sendMessage(Component.text("Usage: /chestlock lockpicks <on|off>"));
                    return true;
                }
                String value = args[1].toLowerCase();
                if (!value.equals("on") && !value.equals("off")) {
                    sender.sendMessage(Component.text("Usage: /chestlock lockpicks <on|off>"));
                    return true;
                }
                allowLockpicks = value.equals("on");
                getConfig().set("lockpicks.enabled", allowLockpicks);
                saveConfig();
                updatePickRecipes();
                sender.sendMessage(Component.text("Lockpicking is now " + (allowLockpicks ? "enabled." : "disabled.")));
                return true;
            }
            case "lockoutscope" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Lockout scope is " + lockoutScope.name().toLowerCase() + "."));
                    sender.sendMessage(Component.text("Usage: /chestlock lockoutscope <chest|player>"));
                    return true;
                }
                String value = args[1].toLowerCase();
                LockoutScope scope = LockoutScope.fromConfig(value);
                if (!value.equals("chest") && !value.equals("player")) {
                    sender.sendMessage(Component.text("Usage: /chestlock lockoutscope <chest|player>"));
                    return true;
                }
                lockoutScope = scope;
                getConfig().set("lockpicks.lockout-scope", scope.name().toLowerCase());
                saveConfig();
                sender.sendMessage(Component.text("Lockout scope set to " + scope.name().toLowerCase() + "."));
                return true;
            }
            default -> {
                if (sender instanceof Player player) {
                    sendHelp(player);
                } else {
                    sender.sendMessage(Component.text("Usage: /chestlock <info|unlock|keyinfo|reload|loglevel|normalkeys|lockpicks|lockoutscope|give|help>"));
                }
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("chestlock")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("info", "unlock", "keyinfo", "reload", "loglevel", "normalkeys", "lockpicks", "lockoutscope", "give", "help");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("loglevel")) {
            return List.of("0", "1", "2", "3");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("normalkeys")) {
            return List.of("on", "off");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("lockpicks")) {
            return List.of("on", "off");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("lockoutscope")) {
            return List.of("chest", "player");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return List.of("rusty", "normal", "silence");
        }
        return List.of();
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("/chestlock info - show lock key name for looked-at container"));
        player.sendMessage(Component.text("/chestlock unlock - force unlock looked-at container"));
        player.sendMessage(Component.text("/chestlock keyinfo - show lock info for key in hand"));
        player.sendMessage(Component.text("/chestlock reload - reload lock data from disk"));
        player.sendMessage(Component.text("/chestlock loglevel <0-3> - set log verbosity"));
        player.sendMessage(Component.text("/chestlock normalkeys <on|off> - allow normal trial keys"));
        player.sendMessage(Component.text("/chestlock lockpicks <on|off> - allow lock picking and crafting"));
        player.sendMessage(Component.text("/chestlock lockoutscope <chest|player> - set lockout scope"));
        player.sendMessage(Component.text("/chestlock give <player> <rusty|normal|silence> [amount] - give lock picks"));
    }

    private PickType parsePickType(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase();
        if (normalized.endsWith("_pick")) {
            normalized = normalized.substring(0, normalized.length() - "_pick".length());
        }
        return switch (normalized) {
            case "rusty" -> PickType.RUSTY;
            case "normal" -> PickType.NORMAL;
            case "silence" -> PickType.SILENCE;
            default -> null;
        };
    }

    private String formatPickStatus(int attempts, int limit) {
        if (limit < 0) {
            return attempts + " attempts (limit pending)";
        }
        boolean lockedOut = attempts >= limit;
        return attempts + "/" + limit + (lockedOut ? " (locked out)" : "");
    }

    private String formatSilenceStatus(PickState state) {
        String base = formatPickStatus(state.silenceAttempts(), state.silenceLimit());
        int overLimit = state.silenceOverLimitAttempts();
        String penalty;
        if (state.silencePenaltyTimestamp() <= 0L) {
            penalty = "penalty ready";
        } else {
            long remaining = silencePenaltyResetMs - (System.currentTimeMillis() - state.silencePenaltyTimestamp());
            penalty = remaining > 0 ? "penalty resets in " + formatDuration(remaining) : "penalty ready";
        }
        return base + ", over-limit attempts " + overLimit + ", " + penalty;
    }

    private String formatDuration(long ms) {
        if (ms < 0) {
            return "unknown";
        }
        long seconds = ms / 1000L;
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        if (hours > 0) {
            long remMinutes = minutes % 60L;
            return hours + "h" + (remMinutes > 0 ? " " + remMinutes + "m" : "");
        }
        if (minutes > 0) {
            long remSeconds = seconds % 60L;
            return minutes + "m" + (remSeconds > 0 ? " " + remSeconds + "s" : "");
        }
        return seconds + "s";
    }

    private void updatePickRecipes() {
        NamespacedKey rustyKey = new NamespacedKey(this, "rusty_lock_pick");
        NamespacedKey normalKey = new NamespacedKey(this, "normal_lock_pick");
        NamespacedKey silenceKey = new NamespacedKey(this, "silence_lock_pick");
        Bukkit.removeRecipe(rustyKey);
        Bukkit.removeRecipe(normalKey);
        Bukkit.removeRecipe(silenceKey);
        if (!allowLockpicks) {
            return;
        }
        ItemStack rustyPick = createPick(PickType.RUSTY);
        ItemStack normalPick = createPick(PickType.NORMAL);
        ItemStack silencePick = createPick(PickType.SILENCE);

        ShapelessRecipe rustyRecipe = new ShapelessRecipe(rustyKey, rustyPick);
        rustyRecipe.addIngredient(Material.COPPER_INGOT);
        rustyRecipe.addIngredient(Material.TRIPWIRE_HOOK);
        rustyRecipe.addIngredient(Material.STICK);
        Bukkit.addRecipe(rustyRecipe);

        ShapelessRecipe normalRecipe = new ShapelessRecipe(normalKey, normalPick);
        normalRecipe.addIngredient(Material.IRON_INGOT);
        normalRecipe.addIngredient(Material.TRIPWIRE_HOOK);
        normalRecipe.addIngredient(Material.BREEZE_ROD);
        Bukkit.addRecipe(normalRecipe);

        SmithingTransformRecipe silenceRecipe = new SmithingTransformRecipe(
                silenceKey,
                silencePick,
                new RecipeChoice.MaterialChoice(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE),
                new RecipeChoice.ExactChoice(normalPick),
                new RecipeChoice.MaterialChoice(Material.ECHO_SHARD)
        );
        Bukkit.addRecipe(silenceRecipe);
    }

    private PickState getPickState(LockInfo info, Player player) {
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

    private LockInfo updatePickState(LockInfo info, Player player, PickState state) {
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

    private ItemStack createPick(PickType type) {
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(type.displayName));
            meta.setCustomModelData(type.modelData);
            meta.getPersistentDataContainer().set(pickTypeKey, PersistentDataType.STRING, type.id);
            item.setItemMeta(meta);
        }
        return item;
    }

    private PickType getPickType(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.TRIPWIRE_HOOK) {
            return null;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return null;
        }
        String id = meta.getPersistentDataContainer().get(pickTypeKey, PersistentDataType.STRING);
        if (id == null || id.isBlank()) {
            return null;
        }
        return PickType.fromId(id);
    }

    private PickMatch findHeldPick(Player player) {
        PickType main = getPickType(player.getInventory().getItemInMainHand());
        if (main != null) {
            return new PickMatch(main, EquipmentSlot.HAND);
        }
        PickType off = getPickType(player.getInventory().getItemInOffHand());
        if (off != null) {
            return new PickMatch(off, EquipmentSlot.OFF_HAND);
        }
        return null;
    }

    private void handlePickAttempt(PlayerInteractEvent event, Block block, LockInfo lockInfo, PickMatch pickMatch) {
        if (!allowLockpicks) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Lockpicking is disabled."));
            return;
        }
        List<Location> locations = resolveLockLocations(block);
        if (locations.isEmpty()) {
            return;
        }

        Player player = event.getPlayer();
        PickType pickType = pickMatch.type();
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
            default -> {
                return;
            }
        }

        updated = updated.withLastPick(player, pickType, now);
        updated = updatePickState(updated, player, state);
        changed = true;

        if (!success && changed) {
            updateLockInfo(locations, updated);
        }

        double breakChance = switch (pickType) {
            case RUSTY -> rustyBreakChance;
            case NORMAL -> normalBreakChance;
            case SILENCE -> silenceBreakChance;
        };
        if (ThreadLocalRandom.current().nextDouble() < breakChance) {
            consumeOnePick(player, pickMatch);
        }

        if (success) {
            unlock(block, lockInfo.keyName());
            logLockEvent("PICK_SUCCESS", player.getName(), null, block.getLocation(), lockInfo, "pick=" + pickType.id);
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
                    "pick=" + pickType.id + (overLimit ? " overLimit=true" : ""));
        }
    }

    private void updateLockInfo(List<Location> locations, LockInfo info) {
        for (Location location : locations) {
            lockedChests.put(locationKey(location), info);
        }
        saveData();
    }

    private void playWorldSoundDelayed(Block block, Sound sound) {
        if (block == null || sound == null) {
            return;
        }
        World world = block.getWorld();
        if (world == null) {
            return;
        }
        Bukkit.getScheduler().runTask(this,
                () -> world.playSound(block.getLocation(), sound, SoundCategory.MASTER, 1.6f, 1.0f));
    }

    private void consumeOnePick(Player player, PickMatch match) {
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

    private void openSilently(Player player, Block block) {
        if (!(block.getState() instanceof InventoryHolder holder)) {
            return;
        }
        player.openInventory(holder.getInventory());
        Bukkit.getScheduler().runTask(this, () -> {
            player.stopSound(Sound.BLOCK_CHEST_OPEN);
            player.stopSound(Sound.BLOCK_CHEST_CLOSE);
            player.stopSound(Sound.BLOCK_BARREL_OPEN);
            player.stopSound(Sound.BLOCK_BARREL_CLOSE);
            player.stopSound(Sound.BLOCK_SHULKER_BOX_OPEN);
            player.stopSound(Sound.BLOCK_SHULKER_BOX_CLOSE);
        });
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.hasBlock()) {
            return;
        }
        switch (event.getAction()) {
            case RIGHT_CLICK_BLOCK -> {
                Block block = event.getClickedBlock();
                if (block == null || !isLockable(block)) {
                    return;
                }

                KeyMatch heldKey = findAnyHeldKey(event.getPlayer());
                String heldKeyName = heldKey == null ? null : heldKey.name();
                LockInfo existingLock = getLockInfo(block);
                if (existingLock != null) {
                    if (heldKeyName == null || !existingLock.keyName().equals(heldKeyName)) {
                        PickMatch pickMatch = findHeldPick(event.getPlayer());
                        if (pickMatch != null) {
                            handlePickAttempt(event, block, existingLock, pickMatch);
                            return;
                        }
                        if (isDecorationItem(event.getItem())) {
                            event.setCancelled(false);
                            event.setUseInteractedBlock(Result.ALLOW);
                            event.setUseItemInHand(Result.ALLOW);
                        } else {
                            event.setCancelled(true);
                            playFail(event.getPlayer(), block.getLocation());
                            logLockEvent("INTERACT_DENY", event.getPlayer().getName(), heldKeyName, block.getLocation(), existingLock, null);
                        }
                    }
                    return;
                }

                if (heldKeyName == null) {
                    return;
                }

                if (!tryLock(block, heldKeyName, event.getPlayer(), heldKey != null && heldKey.normal())) {
                    playFail(event.getPlayer(), block.getLocation());
                    logLockEvent("LOCK_DENY", event.getPlayer().getName(), heldKeyName, block.getLocation(), null, "key already used or locked by another key");
                    return;
                }

                playInsert(event.getPlayer(), block.getLocation());
                logLockEvent("LOCK_CREATED", event.getPlayer().getName(), heldKeyName, block.getLocation(), getLockInfo(block), null);
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
        List<Location> locations = resolveLockLocations(holder);
        if (locations.isEmpty()) {
            return;
        }

        LockInfo lockInfo = getLockInfo(locations);
        if (lockInfo == null) {
            return;
        }

        KeyMatch keyMatch = findHeldKey(player, lockInfo.keyName());
        String heldKeyName = keyMatch == null ? null : keyMatch.name();
        if (heldKeyName == null || !lockInfo.keyName().equals(heldKeyName)) {
            event.setCancelled(true);
            playFail(player, locations.getFirst());
            logLockEvent("OPEN_DENY", player.getName(), heldKeyName, locations.getFirst(), lockInfo, "wrong or missing key");
            return;
        }

        if (hasOtherViewers(inventory, player)) {
            event.setCancelled(true);
            playFail(player, locations.getFirst());
            logLockEvent("OPEN_DENY", player.getName(), heldKeyName, locations.getFirst(), lockInfo, "in use by another player");
            return;
        }

        updateLastUser(locations, player);
        playSuccess(player, locations.getFirst());
        logLockEvent("OPEN_ALLOWED", player.getName(), heldKeyName, locations.getFirst(), lockInfo, null);
        if (keyMatch != null && keyMatch.normal() && lockInfo.normalKey()) {
            if (lockInfo.normalArmed()) {
                unlock(locations.getFirst().getBlock(), lockInfo.keyName());
                consumeOneKey(player, keyMatch);
                logLockEvent("NORMAL_KEY_CONSUMED", player.getName(), heldKeyName, locations.getFirst(), lockInfo, null);
            } else {
                armNormalKeyLock(locations);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top == null) {
            return;
        }
        String lockName = getLockKeyName(top);
        if (lockName == null) {
            return;
        }
        if (!lockName.equals(getHeldKeyName(player))) {
            event.setCancelled(true);
            playFail(player, lockLocation(top));
            logLockEvent("INVENTORY_CLICK_DENY", player.getName(), getHeldKeyName(player), lockLocation(top), getLockInfo(lockLocation(top)), null);
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
        String lockName = getLockKeyName(top);
        if (lockName == null) {
            return;
        }
        if (!lockName.equals(getHeldKeyName(player))) {
            event.setCancelled(true);
            playFail(player, lockLocation(top));
            logLockEvent("INVENTORY_DRAG_DENY", player.getName(), getHeldKeyName(player), lockLocation(top), getLockInfo(lockLocation(top)), null);
        }
    }

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent event) {
        InventoryHolder sourceHolder = event.getSource().getHolder();
        InventoryHolder destinationHolder = event.getDestination().getHolder();
        if (isLockedHolder(sourceHolder) || isLockedHolder(destinationHolder)) {
            event.setCancelled(true);
            String detail = "source=" + event.getSource().getType() + " dest=" + event.getDestination().getType();
            logInventoryMove(event, detail);
        }
    }

    @EventHandler
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (isLockedHolder(event.getInventory().getHolder())) {
            event.setCancelled(true);
            InventoryHolder holder = event.getInventory().getHolder();
            logLockEvent("INVENTORY_PICKUP_DENY", inventoryActor(holder),
                    null, lockLocation(event.getInventory()), getLockInfo(lockLocation(event.getInventory())), "inventory=" + event.getInventory().getType());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == Material.HOPPER) {
            hopperOwners.put(locationKey(event.getBlockPlaced().getLocation()),
                    new HopperOwner(event.getPlayer().getName(), System.currentTimeMillis()));
        }
        if (!isDecorationItem(event.getItemInHand())) {
            return;
        }
        Block against = event.getBlockAgainst();
        if (against == null || !isLockable(against)) {
            return;
        }
        if (getLockInfo(against) == null) {
            return;
        }
        event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getEntity() == null) {
            return;
        }
        switch (event.getEntity().getType()) {
            case ITEM_FRAME, GLOW_ITEM_FRAME -> {
                Block against = event.getBlock();
                if (against == null || !isLockable(against)) {
                    return;
                }
                if (getLockInfo(against) == null) {
                    return;
                }
                event.setCancelled(false);
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        Block ignited = event.getBlock();
        long now = System.currentTimeMillis();
        if (ignited.getType() == Material.TNT) {
            tntIgnites.put(locationKey(ignited.getLocation()), new PendingIgnite(player.getName(), now));
            return;
        }
        for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block adjacent = ignited.getRelative(face);
            if (adjacent.getType() == Material.TNT) {
                tntIgnites.put(locationKey(adjacent.getLocation()), new PendingIgnite(player.getName(), now));
            }
        }
    }

    @EventHandler
    public void onTntSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }
        String key = locationKey(tnt.getLocation());
        PendingIgnite ignite = tntIgnites.get(key);
        if (ignite != null && System.currentTimeMillis() - ignite.timestamp() < 10000L) {
            tntSources.put(tnt.getUniqueId(), ignite.playerName());
        }
    }

    @EventHandler
    public void onCrystalDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        String playerName = null;
        if (event.getDamager() instanceof Player player) {
            playerName = player.getName();
        } else if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                playerName = player.getName();
            }
        }
        if (playerName != null) {
            crystalSources.put(crystal.getUniqueId(), new PendingIgnite(playerName, System.currentTimeMillis()));
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.HOPPER) {
            hopperOwners.remove(locationKey(block.getLocation()));
        }
        if (!isLockable(block)) {
            return;
        }

        LockInfo lockInfo = getLockInfo(block);
        if (lockInfo == null) {
            return;
        }

        KeyMatch keyMatch = findHeldKey(event.getPlayer(), lockInfo.keyName());
        String heldKeyName = keyMatch == null ? null : keyMatch.name();
        if (heldKeyName == null || !lockInfo.keyName().equals(heldKeyName)) {
            event.setCancelled(true);
            playFail(event.getPlayer(), block.getLocation());
            logLockEvent("BREAK_DENY", event.getPlayer().getName(), heldKeyName, block.getLocation(), lockInfo, null);
            return;
        }

        unlock(block, lockInfo.keyName());
        logLockEvent("BREAK_ALLOWED", event.getPlayer().getName(), heldKeyName, block.getLocation(), lockInfo, null);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            if (isLockedBlock(block)) {
                logLockEvent("EXPLOSION_DENY", "BLOCK_EXPLODE:" + event.getBlock().getType(), null, block.getLocation(), getLockInfo(block), null);
                return true;
            }
            return false;
        });
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            if (isLockedBlock(block)) {
                String source = explosionActor(event.getEntity());
                logLockEvent("EXPLOSION_DENY", source, null, block.getLocation(), getLockInfo(block), null);
                return true;
            }
            return false;
        });
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        if (isLockedBlock(event.getBlock())) {
            event.setCancelled(true);
            logLockEvent("BURN_DENY", "FIRE", null, event.getBlock().getLocation(), getLockInfo(event.getBlock()), null);
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (isLockedBlock(block)) {
                event.setCancelled(true);
                logLockEvent("PISTON_EXTEND_DENY", "PISTON", null, block.getLocation(), getLockInfo(block), null);
                return;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (isLockedBlock(block)) {
                event.setCancelled(true);
                logLockEvent("PISTON_RETRACT_DENY", "PISTON", null, block.getLocation(), getLockInfo(block), null);
                return;
            }
        }
    }

    private boolean isLockedBlock(Block block) {
        if (!isLockable(block)) {
            return false;
        }
        return getLockInfo(block) != null;
    }

    private boolean tryLock(Block block, String keyName, Player creator, boolean normalKey) {
        List<Location> locations = resolveLockLocations(block);
        if (locations.isEmpty()) {
            return false;
        }

        Set<String> locationKeys = new HashSet<>();
        for (Location location : locations) {
            locationKeys.add(locationKey(location));
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
            LockInfo existing = lockedChests.get(locationKey(location));
            if (existing != null && !existing.keyName().equals(keyName)) {
                return false;
            }
        }

        LockInfo info = new LockInfo(keyName, creator.getName(), creator.getUniqueId(), null, null, normalKey, false,
                null, null, null, 0L,
                -1, 0, -1, 0, -1, 0, 0, 0L, new HashMap<>());
        for (Location location : locations) {
            lockedChests.put(locationKey(location), info);
        }
        keyToChest.putIfAbsent(keyName, locationKeys.iterator().next());
        saveData();
        return true;
    }

    private void unlock(Block block, String keyName) {
        List<Location> locations = resolveLockLocations(block);
        for (Location location : locations) {
            lockedChests.remove(locationKey(location));
        }
        keyToChest.remove(keyName);
        saveData();
    }

    private boolean isLockable(Block block) {
        return block.getState() instanceof Chest
                || block.getState() instanceof Barrel
                || block.getState() instanceof ShulkerBox;
    }

    private List<Location> resolveLockLocations(Block block) {
        if (block == null) {
            return List.of();
        }
        if (block.getState() instanceof InventoryHolder holder) {
            return resolveLockLocations(holder);
        }
        return List.of();
    }

    private List<Location> resolveLockLocations(InventoryHolder holder) {
        List<Location> locations = new ArrayList<>();
        if (holder instanceof org.bukkit.block.DoubleChest doubleChest) {
            if (doubleChest.getLeftSide() instanceof Chest left) {
                locations.add(left.getLocation());
            }
            if (doubleChest.getRightSide() instanceof Chest right) {
                locations.add(right.getLocation());
            }
        } else if (holder instanceof Chest chest) {
            Inventory chestInventory = chest.getInventory();
            InventoryHolder chestHolder = chestInventory.getHolder();
            if (chestHolder instanceof org.bukkit.block.DoubleChest doubleChest) {
                if (doubleChest.getLeftSide() instanceof Chest left) {
                    locations.add(left.getLocation());
                }
                if (doubleChest.getRightSide() instanceof Chest right) {
                    locations.add(right.getLocation());
                }
            } else {
                locations.add(chest.getLocation());
            }
        } else if (holder instanceof Barrel barrel) {
            locations.add(barrel.getLocation());
        } else if (holder instanceof ShulkerBox shulkerBox) {
            locations.add(shulkerBox.getLocation());
        }
        locations.removeIf(location -> location == null || location.getWorld() == null);
        return locations;
    }

    private boolean isLockedHolder(InventoryHolder holder) {
        if (holder == null) {
            return false;
        }
        List<Location> locations = resolveLockLocations(holder);
        if (locations.isEmpty()) {
            return false;
        }
        return getLockInfo(locations) != null;
    }

    private String getLockKeyName(Block block) {
        LockInfo info = getLockInfo(resolveLockLocations(block));
        return info == null ? null : info.keyName();
    }

    private String getLockKeyName(Inventory inventory) {
        LockInfo info = getLockInfo(resolveLockLocations(inventory.getHolder()));
        return info == null ? null : info.keyName();
    }

    private LockInfo getLockInfo(Block block) {
        return getLockInfo(resolveLockLocations(block));
    }

    private LockInfo getLockInfo(Location location) {
        if (location == null) {
            return null;
        }
        if (location.getWorld() == null) {
            return null;
        }
        return lockedChests.get(locationKey(location));
    }

    private LockInfo getLockInfo(List<Location> locations) {
        for (Location location : locations) {
            LockInfo info = lockedChests.get(locationKey(location));
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    private boolean hasOtherViewers(Inventory inventory, Player player) {
        for (org.bukkit.entity.HumanEntity viewer : inventory.getViewers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    private Location lockLocation(Inventory inventory) {
        List<Location> locations = resolveLockLocations(inventory.getHolder());
        if (locations.isEmpty()) {
            return inventory.getLocation();
        }
        return locations.getFirst();
    }

    private void updateLastUser(List<Location> locations, Player player) {
        boolean changed = false;
        for (Location location : locations) {
            String key = locationKey(location);
            LockInfo info = lockedChests.get(key);
            if (info == null) {
                continue;
            }
            if (!player.getUniqueId().equals(info.lastUserUuid()) || !player.getName().equals(info.lastUserName())) {
                lockedChests.put(key, info.withLastUser(player));
                changed = true;
            }
        }
        if (changed) {
            saveData();
        }
    }

    private void armNormalKeyLock(List<Location> locations) {
        boolean changed = false;
        for (Location location : locations) {
            String key = locationKey(location);
            LockInfo info = lockedChests.get(key);
            if (info == null || !info.normalKey() || info.normalArmed()) {
                continue;
            }
            lockedChests.put(key, info.withNormalArmed(true));
            changed = true;
        }
        if (changed) {
            saveData();
        }
    }

    private void consumeOneKey(Player player, KeyMatch match) {
        if (!allowNormalKeys || match == null || !match.normal()) {
            return;
        }
        ItemStack stack = match.slot() == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        if (stack == null || stack.getType() != Material.TRIAL_KEY) {
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

    private void logInventoryMove(InventoryMoveItemEvent event, String detail) {
        InventoryHolder sourceHolder = event.getSource().getHolder();
        InventoryHolder destHolder = event.getDestination().getHolder();
        if (isLockedHolder(sourceHolder)) {
            Location loc = resolveLockLocations(sourceHolder).stream().findFirst().orElse(event.getSource().getLocation());
            String actor = destHolder instanceof org.bukkit.block.Hopper ? inventoryActor(destHolder) : inventoryActor(sourceHolder);
            logLockEvent("INVENTORY_MOVE_DENY", actor, null, loc, getLockInfo(loc), detail);
        }
        if (isLockedHolder(destHolder)) {
            Location loc = resolveLockLocations(destHolder).stream().findFirst().orElse(event.getDestination().getLocation());
            logLockEvent("INVENTORY_MOVE_DENY", inventoryActor(destHolder), null, loc, getLockInfo(loc), detail);
        }
    }

    private String inventoryActor(InventoryHolder holder) {
        if (holder instanceof org.bukkit.block.Hopper hopper) {
            HopperOwner owner = hopperOwners.get(locationKey(hopper.getLocation()));
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

    private void logLockEvent(String action, String actor, String keyUsed, Location location, LockInfo info, String detail) {
        if (!shouldLog(action, location)) {
            return;
        }
        String used = keyUsed == null || keyUsed.isBlank() ? "none" : keyUsed;
        String keyName = info == null ? "unknown" : info.keyName();
        String creator = info == null || info.creatorName() == null ? "unknown" : info.creatorName();
        String lastUser = info == null || info.lastUserName() == null ? "unknown" : info.lastUserName();
        String loc = location == null ? "unknown" : formatLocation(location);
        String extra = detail == null ? "" : " detail=" + detail;
        getLogger().info(action + " actor=" + actor + " usedKey=" + used + " lockKey=" + keyName
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
        String key = action + "|" + locationKey(location);
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

    private void loadConfigValues() {
        logLevel = getConfig().getInt("logging.level", 1);
        allowNormalKeys = getConfig().getBoolean("keys.allow-normal", false);
        allowLockpicks = getConfig().getBoolean("lockpicks.enabled", true);
        pickLimitMin = Math.max(1, getConfig().getInt("lockpicks.limit.min", 1));
        pickLimitMax = Math.max(pickLimitMin, getConfig().getInt("lockpicks.limit.max", 20));
        rustyOpenChance = clampChance(getConfig().getDouble("lockpicks.rusty.open-chance", 0.05));
        rustyNormalKeyChance = clampChance(getConfig().getDouble("lockpicks.rusty.normal-key-chance", 0.10));
        rustyBreakChance = clampChance(getConfig().getDouble("lockpicks.rusty.break-chance", 1.0));
        rustyDamage = Math.max(0.0, getConfig().getDouble("lockpicks.rusty.damage", 1.0));
        normalOpenChance = clampChance(getConfig().getDouble("lockpicks.normal.open-chance", 0.10));
        normalNormalKeyChance = clampChance(getConfig().getDouble("lockpicks.normal.normal-key-chance", 0.20));
        normalBreakChance = clampChance(getConfig().getDouble("lockpicks.normal.break-chance", 0.50));
        normalDamage = Math.max(0.0, getConfig().getDouble("lockpicks.normal.damage", 2.0));
        silenceOpenChance = clampChance(getConfig().getDouble("lockpicks.silence.open-chance", 0.50));
        silenceBreakChance = clampChance(getConfig().getDouble("lockpicks.silence.break-chance", 0.05));
        silenceDamage = Math.max(0.0, getConfig().getDouble("lockpicks.silence.damage", 4.0));
        long resetMinutes = getConfig().getLong("lockpicks.silence.penalty-reset-minutes", 60L);
        silencePenaltyResetMs = Math.max(1L, resetMinutes) * 60L * 1000L;
        lockoutScope = LockoutScope.fromConfig(getConfig().getString("lockpicks.lockout-scope", "chest"));
    }

    private double clampChance(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private String formatLocation(Location location) {
        World world = location.getWorld();
        String worldName = world == null ? "unknown" : world.getName();
        String realm = world == null ? "unknown" : mapRealm(world.getEnvironment());
        return worldName + ":" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ()
                + " (" + realm + ")";
    }

    private boolean isDecorationItem(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        Material type = itemStack.getType();
        if (type == Material.ITEM_FRAME || type == Material.GLOW_ITEM_FRAME) {
            return true;
        }
        return Tag.SIGNS.isTagged(type) || type.name().endsWith("_HANGING_SIGN");
    }

    private String explosionActor(org.bukkit.entity.Entity entity) {
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

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String getHeldKeyName(Player player) {
        String main = getKeyName(player.getInventory().getItemInMainHand());
        if (main != null) {
            return main;
        }
        return getKeyName(player.getInventory().getItemInOffHand());
    }

    private KeyMatch findHeldKey(Player player, String requiredName) {
        KeyMatch main = getKeyMatch(player.getInventory().getItemInMainHand(), EquipmentSlot.HAND, requiredName);
        if (main != null) {
            return main;
        }
        return getKeyMatch(player.getInventory().getItemInOffHand(), EquipmentSlot.OFF_HAND, requiredName);
    }

    private KeyMatch findAnyHeldKey(Player player) {
        KeyMatch main = getAnyKeyMatch(player.getInventory().getItemInMainHand(), EquipmentSlot.HAND);
        if (main != null) {
            return main;
        }
        return getAnyKeyMatch(player.getInventory().getItemInOffHand(), EquipmentSlot.OFF_HAND);
    }

    private KeyMatch getKeyMatch(ItemStack itemStack, EquipmentSlot slot, String requiredName) {
        String name = getKeyName(itemStack);
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
        String name = getKeyName(itemStack);
        if (name == null) {
            return null;
        }
        boolean normal = itemStack.getType() == Material.TRIAL_KEY;
        if (normal && !allowNormalKeys) {
            return null;
        }
        return new KeyMatch(name, slot, normal);
    }

    private String getKeyName(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        Material type = itemStack.getType();
        if (type != Material.OMINOUS_TRIAL_KEY && !(allowNormalKeys && type == Material.TRIAL_KEY)) {
            return null;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }
        Component displayName = meta.displayName();
        if (displayName == null) {
            return null;
        }
        String name = TEXT_SERIALIZER.serialize(displayName).strip();
        return name.isEmpty() ? null : name;
    }

    private String locationKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private void playSuccess(Player player, Location location) {
        if (location.getWorld() == null) {
            return;
        }
        location.getWorld().playSound(location, Sound.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    private void playFail(Player player, Location location) {
        if (location.getWorld() == null) {
            return;
        }
        location.getWorld().playSound(location, Sound.BLOCK_VAULT_INSERT_ITEM_FAIL, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    private void playInsert(Player player, Location location) {
        if (location.getWorld() == null) {
            return;
        }
        location.getWorld().playSound(location, Sound.BLOCK_VAULT_INSERT_ITEM, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    private void loadData() {
        lockedChests.clear();
        keyToChest.clear();
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create data folder.");
        }

        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = config.getConfigurationSection("locked-chests");
        if (section == null) {
            return;
        }

        for (String locationKey : section.getKeys(false)) {
            String keyName = null;
            String creatorName = null;
            UUID creatorUuid = null;
            String lastUserName = null;
            UUID lastUserUuid = null;
            boolean normalKey = false;
            boolean normalArmed = false;
            String lastPickUserName = null;
            UUID lastPickUserUuid = null;
            String lastPickType = null;
            long lastPickTimestamp = 0L;
            Map<UUID, PickState> playerPickStates = new HashMap<>();
            int rustyLimit = -1;
            int rustyAttempts = 0;
            int normalLimit = -1;
            int normalAttempts = 0;
            int silenceLimit = -1;
            int silenceAttempts = 0;
            int silenceOverLimitAttempts = 0;
            long silencePenaltyTimestamp = 0L;

            if (section.isString(locationKey)) {
                keyName = section.getString(locationKey);
            } else {
                ConfigurationSection lockSection = section.getConfigurationSection(locationKey);
                if (lockSection != null) {
                    keyName = lockSection.getString("key");
                    creatorName = lockSection.getString("creator.name");
                    String creatorId = lockSection.getString("creator.uuid");
                    creatorUuid = parseUuid(creatorId);
                    lastUserName = lockSection.getString("last-user.name");
                    String lastUserId = lockSection.getString("last-user.uuid");
                    lastUserUuid = parseUuid(lastUserId);
                    normalKey = lockSection.getBoolean("normal.key", false);
                    normalArmed = lockSection.getBoolean("normal.armed", false);
                    lastPickUserName = lockSection.getString("pick.last.name");
                    lastPickUserUuid = parseUuid(lockSection.getString("pick.last.uuid"));
                    lastPickType = lockSection.getString("pick.last.type");
                    lastPickTimestamp = lockSection.getLong("pick.last.timestamp", 0L);
                    if (lastPickUserName != null && lastPickUserName.isBlank()) {
                        lastPickUserName = null;
                    }
                    if (lastPickType != null && lastPickType.isBlank()) {
                        lastPickType = null;
                    }
                    ConfigurationSection pickPlayers = lockSection.getConfigurationSection("pick.players");
                    if (pickPlayers != null) {
                        for (String playerId : pickPlayers.getKeys(false)) {
                            UUID playerUuid = parseUuid(playerId);
                            if (playerUuid == null) {
                                continue;
                            }
                            ConfigurationSection pickStateSection = pickPlayers.getConfigurationSection(playerId);
                            if (pickStateSection == null) {
                                continue;
                            }
                            int rLimit = pickStateSection.getInt("rusty.limit", -1);
                            int rAttempts = pickStateSection.getInt("rusty.attempts", 0);
                            int nLimit = pickStateSection.getInt("normal.limit", -1);
                            int nAttempts = pickStateSection.getInt("normal.attempts", 0);
                            int sLimit = pickStateSection.getInt("silence.limit", -1);
                            int sAttempts = pickStateSection.getInt("silence.attempts", 0);
                            int sOver = pickStateSection.getInt("silence.over-limit-attempts", 0);
                            long sPenalty = pickStateSection.getLong("silence.penalty-timestamp", 0L);
                            playerPickStates.put(playerUuid, new PickState(rLimit, rAttempts, nLimit, nAttempts, sLimit, sAttempts, sOver, sPenalty));
                        }
                    }
                    rustyLimit = lockSection.getInt("pick.rusty.limit", -1);
                    rustyAttempts = lockSection.getInt("pick.rusty.attempts", 0);
                    normalLimit = lockSection.getInt("pick.normal.limit", -1);
                    normalAttempts = lockSection.getInt("pick.normal.attempts", 0);
                    silenceLimit = lockSection.getInt("pick.silence.limit", -1);
                    silenceAttempts = lockSection.getInt("pick.silence.attempts", 0);
                    silenceOverLimitAttempts = lockSection.getInt("pick.silence.over-limit-attempts", 0);
                    silencePenaltyTimestamp = lockSection.getLong("pick.silence.penalty-timestamp", 0L);
                }
            }

            if (keyName == null || keyName.isBlank()) {
                continue;
            }
            LockInfo info = new LockInfo(keyName, creatorName, creatorUuid, lastUserName, lastUserUuid, normalKey, normalArmed,
                    lastPickUserName, lastPickUserUuid, lastPickType, lastPickTimestamp,
                    rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                    silenceOverLimitAttempts, silencePenaltyTimestamp, playerPickStates);
            lockedChests.put(locationKey, info);
            keyToChest.putIfAbsent(keyName, locationKey);
        }
    }

    private void saveData() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection section = config.createSection("locked-chests");
        for (Map.Entry<String, LockInfo> entry : lockedChests.entrySet()) {
            String locationKey = entry.getKey();
            LockInfo info = entry.getValue();
            if (info == null) {
                continue;
            }
            ConfigurationSection lockSection = section.createSection(locationKey);
            lockSection.set("key", info.keyName());
            LocationData locationData = parseLocationKey(locationKey);
            if (locationData != null) {
                lockSection.set("world.name", locationData.worldName());
                if (locationData.realm() != null) {
                    lockSection.set("world.realm", locationData.realm());
                }
                if (locationData.worldUuid() != null) {
                    lockSection.set("world.uuid", locationData.worldUuid().toString());
                }
            }
            if (info.creatorName() != null) {
                lockSection.set("creator.name", info.creatorName());
            }
            if (info.creatorUuid() != null) {
                lockSection.set("creator.uuid", info.creatorUuid().toString());
            }
            if (info.lastUserName() != null) {
                lockSection.set("last-user.name", info.lastUserName());
            }
            if (info.lastUserUuid() != null) {
                lockSection.set("last-user.uuid", info.lastUserUuid().toString());
            }
            if (info.lastPickUserName() != null) {
                lockSection.set("pick.last.name", info.lastPickUserName());
            }
            if (info.lastPickUserUuid() != null) {
                lockSection.set("pick.last.uuid", info.lastPickUserUuid().toString());
            }
            if (info.lastPickType() != null) {
                lockSection.set("pick.last.type", info.lastPickType());
            }
            if (info.lastPickTimestamp() > 0L) {
                lockSection.set("pick.last.timestamp", info.lastPickTimestamp());
            }
            if (!info.playerPickStates().isEmpty()) {
                ConfigurationSection pickPlayers = lockSection.createSection("pick.players");
                for (Map.Entry<UUID, PickState> stateEntry : info.playerPickStates().entrySet()) {
                    UUID playerId = stateEntry.getKey();
                    PickState state = stateEntry.getValue();
                    if (playerId == null || state == null) {
                        continue;
                    }
                    ConfigurationSection pickStateSection = pickPlayers.createSection(playerId.toString());
                    pickStateSection.set("rusty.limit", state.rustyLimit());
                    pickStateSection.set("rusty.attempts", state.rustyAttempts());
                    pickStateSection.set("normal.limit", state.normalLimit());
                    pickStateSection.set("normal.attempts", state.normalAttempts());
                    pickStateSection.set("silence.limit", state.silenceLimit());
                    pickStateSection.set("silence.attempts", state.silenceAttempts());
                    pickStateSection.set("silence.over-limit-attempts", state.silenceOverLimitAttempts());
                    pickStateSection.set("silence.penalty-timestamp", state.silencePenaltyTimestamp());
                }
            }
            if (info.normalKey()) {
                lockSection.set("normal.key", true);
                lockSection.set("normal.armed", info.normalArmed());
            }
            if (info.rustyLimit() >= 0 || info.rustyAttempts() > 0) {
                lockSection.set("pick.rusty.limit", info.rustyLimit());
                lockSection.set("pick.rusty.attempts", info.rustyAttempts());
            }
            if (info.normalLimit() >= 0 || info.normalAttempts() > 0) {
                lockSection.set("pick.normal.limit", info.normalLimit());
                lockSection.set("pick.normal.attempts", info.normalAttempts());
            }
            if (info.silenceLimit() >= 0 || info.silenceAttempts() > 0 || info.silenceOverLimitAttempts() > 0
                    || info.silencePenaltyTimestamp() > 0L) {
                lockSection.set("pick.silence.limit", info.silenceLimit());
                lockSection.set("pick.silence.attempts", info.silenceAttempts());
                lockSection.set("pick.silence.over-limit-attempts", info.silenceOverLimitAttempts());
                lockSection.set("pick.silence.penalty-timestamp", info.silencePenaltyTimestamp());
            }
        }
        try {
            config.save(dataFile);
        } catch (IOException exception) {
            getLogger().warning("Could not save data.yml: " + exception.getMessage());
        }
    }

    private static final class LockInfo {
        private final String keyName;
        private final String creatorName;
        private final UUID creatorUuid;
        private final String lastUserName;
        private final UUID lastUserUuid;
        private final boolean normalKey;
        private final boolean normalArmed;
        private final String lastPickUserName;
        private final UUID lastPickUserUuid;
        private final String lastPickType;
        private final long lastPickTimestamp;
        private final int rustyLimit;
        private final int rustyAttempts;
        private final int normalLimit;
        private final int normalAttempts;
        private final int silenceLimit;
        private final int silenceAttempts;
        private final int silenceOverLimitAttempts;
        private final long silencePenaltyTimestamp;
        private final Map<UUID, PickState> playerPickStates;

        private LockInfo(String keyName, String creatorName, UUID creatorUuid, String lastUserName, UUID lastUserUuid,
                         boolean normalKey, boolean normalArmed,
                         String lastPickUserName, UUID lastPickUserUuid, String lastPickType, long lastPickTimestamp,
                         int rustyLimit, int rustyAttempts,
                         int normalLimit, int normalAttempts,
                         int silenceLimit, int silenceAttempts,
                         int silenceOverLimitAttempts, long silencePenaltyTimestamp,
                         Map<UUID, PickState> playerPickStates) {
            this.keyName = keyName;
            this.creatorName = creatorName;
            this.creatorUuid = creatorUuid;
            this.lastUserName = lastUserName;
            this.lastUserUuid = lastUserUuid;
            this.normalKey = normalKey;
            this.normalArmed = normalArmed;
            this.lastPickUserName = lastPickUserName;
            this.lastPickUserUuid = lastPickUserUuid;
            this.lastPickType = lastPickType;
            this.lastPickTimestamp = lastPickTimestamp;
            this.rustyLimit = rustyLimit;
            this.rustyAttempts = rustyAttempts;
            this.normalLimit = normalLimit;
            this.normalAttempts = normalAttempts;
            this.silenceLimit = silenceLimit;
            this.silenceAttempts = silenceAttempts;
            this.silenceOverLimitAttempts = silenceOverLimitAttempts;
            this.silencePenaltyTimestamp = silencePenaltyTimestamp;
            this.playerPickStates = playerPickStates == null ? new HashMap<>() : new HashMap<>(playerPickStates);
        }

        private String keyName() {
            return keyName;
        }

        private String creatorName() {
            return creatorName;
        }

        private UUID creatorUuid() {
            return creatorUuid;
        }

        private String lastUserName() {
            return lastUserName;
        }

        private UUID lastUserUuid() {
            return lastUserUuid;
        }

        private boolean normalKey() {
            return normalKey;
        }

        private boolean normalArmed() {
            return normalArmed;
        }

        private String lastPickUserName() {
            return lastPickUserName;
        }

        private UUID lastPickUserUuid() {
            return lastPickUserUuid;
        }

        private String lastPickType() {
            return lastPickType;
        }

        private long lastPickTimestamp() {
            return lastPickTimestamp;
        }

        private int rustyLimit() {
            return rustyLimit;
        }

        private int rustyAttempts() {
            return rustyAttempts;
        }

        private int normalLimit() {
            return normalLimit;
        }

        private int normalAttempts() {
            return normalAttempts;
        }

        private int silenceLimit() {
            return silenceLimit;
        }

        private int silenceAttempts() {
            return silenceAttempts;
        }

        private int silenceOverLimitAttempts() {
            return silenceOverLimitAttempts;
        }

        private long silencePenaltyTimestamp() {
            return silencePenaltyTimestamp;
        }

        private Map<UUID, PickState> playerPickStates() {
            return playerPickStates;
        }

        private PickState toPickState() {
            return new PickState(rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                    silenceOverLimitAttempts, silencePenaltyTimestamp);
        }

        private LockInfo withLastUser(Player player) {
            return new LockInfo(keyName, creatorName, creatorUuid, player.getName(), player.getUniqueId(), normalKey, normalArmed,
                    lastPickUserName, lastPickUserUuid, lastPickType, lastPickTimestamp,
                    rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                    silenceOverLimitAttempts, silencePenaltyTimestamp, playerPickStates);
        }

        private LockInfo withNormalArmed(boolean armed) {
            return new LockInfo(keyName, creatorName, creatorUuid, lastUserName, lastUserUuid, normalKey, armed,
                    lastPickUserName, lastPickUserUuid, lastPickType, lastPickTimestamp,
                    rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                    silenceOverLimitAttempts, silencePenaltyTimestamp, playerPickStates);
        }

        private LockInfo withLastPick(Player player, PickType pickType, long timestamp) {
            return new LockInfo(keyName, creatorName, creatorUuid, lastUserName, lastUserUuid, normalKey, normalArmed,
                    player.getName(), player.getUniqueId(), pickType.id, timestamp,
                    rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                    silenceOverLimitAttempts, silencePenaltyTimestamp, playerPickStates);
        }

        private LockInfo withPickState(PickState state) {
            if (state == null) {
                return this;
            }
            return new LockInfo(keyName, creatorName, creatorUuid, lastUserName, lastUserUuid, normalKey, normalArmed,
                    lastPickUserName, lastPickUserUuid, lastPickType, lastPickTimestamp,
                    state.rustyLimit(), state.rustyAttempts(), state.normalLimit(), state.normalAttempts(),
                    state.silenceLimit(), state.silenceAttempts(), state.silenceOverLimitAttempts(),
                    state.silencePenaltyTimestamp(), playerPickStates);
        }

        private LockInfo withPlayerPickState(UUID playerId, PickState state) {
            if (playerId == null || state == null) {
                return this;
            }
            Map<UUID, PickState> updatedStates = new HashMap<>(playerPickStates);
            updatedStates.put(playerId, state);
            return new LockInfo(keyName, creatorName, creatorUuid, lastUserName, lastUserUuid, normalKey, normalArmed,
                    lastPickUserName, lastPickUserUuid, lastPickType, lastPickTimestamp,
                    rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                    silenceOverLimitAttempts, silencePenaltyTimestamp, updatedStates);
        }
    }

    private LocationData parseLocationKey(String locationKey) {
        if (locationKey == null) {
            return null;
        }
        int idx = locationKey.indexOf(':');
        if (idx <= 0 || idx >= locationKey.length() - 1) {
            return null;
        }
        String worldName = locationKey.substring(0, idx);
        String coords = locationKey.substring(idx + 1);
        String[] parts = coords.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            World world = Bukkit.getWorld(worldName);
            String realm = world != null ? mapRealm(world.getEnvironment()) : null;
            UUID worldUuid = world != null ? world.getUID() : null;
            return new LocationData(worldName, x, y, z, realm, worldUuid);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String mapRealm(World.Environment environment) {
        if (environment == null) {
            return null;
        }
        return switch (environment) {
            case NORMAL -> "OVERWORLD";
            case NETHER -> "NETHER";
            case THE_END -> "END";
            default -> environment.name();
        };
    }

    private record LocationData(String worldName, int x, int y, int z, String realm, UUID worldUuid) {
    }

    private record PendingIgnite(String playerName, long timestamp) {
    }

    private record HopperOwner(String playerName, long timestamp) {
    }

    private record KeyMatch(String name, EquipmentSlot slot, boolean normal) {
    }

    private record PickMatch(PickType type, EquipmentSlot slot) {
    }

    private record PickState(int rustyLimit, int rustyAttempts,
                             int normalLimit, int normalAttempts,
                             int silenceLimit, int silenceAttempts,
                             int silenceOverLimitAttempts, long silencePenaltyTimestamp) {
        private static PickState empty() {
            return new PickState(-1, 0, -1, 0, -1, 0, 0, 0L);
        }

        private PickState withRustyLimit(int limit) {
            return new PickState(limit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                    silenceOverLimitAttempts, silencePenaltyTimestamp);
        }

        private PickState withRustyAttempts(int attempts) {
            return new PickState(rustyLimit, attempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                    silenceOverLimitAttempts, silencePenaltyTimestamp);
        }

        private PickState withNormalLimit(int limit) {
            return new PickState(rustyLimit, rustyAttempts, limit, normalAttempts, silenceLimit, silenceAttempts,
                    silenceOverLimitAttempts, silencePenaltyTimestamp);
        }

        private PickState withNormalAttempts(int attempts) {
            return new PickState(rustyLimit, rustyAttempts, normalLimit, attempts, silenceLimit, silenceAttempts,
                    silenceOverLimitAttempts, silencePenaltyTimestamp);
        }

        private PickState withSilenceLimit(int limit) {
            return new PickState(rustyLimit, rustyAttempts, normalLimit, normalAttempts, limit, silenceAttempts,
                    silenceOverLimitAttempts, silencePenaltyTimestamp);
        }

        private PickState withSilenceAttempts(int attempts) {
            return new PickState(rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, attempts,
                    silenceOverLimitAttempts, silencePenaltyTimestamp);
        }

        private PickState withSilenceOverLimitAttempts(int attempts) {
            return new PickState(rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                    attempts, silencePenaltyTimestamp);
        }

        private PickState withSilencePenaltyTimestamp(long timestamp) {
            return new PickState(rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                    silenceOverLimitAttempts, timestamp);
        }
    }

    private enum LockoutScope {
        CHEST,
        PLAYER;

        private static LockoutScope fromConfig(String value) {
            if (value == null) {
                return CHEST;
            }
            return switch (value.toLowerCase()) {
                case "player" -> PLAYER;
                default -> CHEST;
            };
        }
    }

    private enum PickType {
        RUSTY("rusty", "Rusty Lock Pick", RUSTY_MODEL_DATA),
        NORMAL("normal", "Lock Pick", NORMAL_MODEL_DATA),
        SILENCE("silence", "Silence Lock Pick", SILENCE_MODEL_DATA);

        private final String id;
        private final String displayName;
        private final int modelData;

        PickType(String id, String displayName, int modelData) {
            this.id = id;
            this.displayName = displayName;
            this.modelData = modelData;
        }

        private static PickType fromId(String id) {
            for (PickType type : values()) {
                if (type.id.equalsIgnoreCase(id)) {
                    return type;
                }
            }
            return null;
        }
    }
}
