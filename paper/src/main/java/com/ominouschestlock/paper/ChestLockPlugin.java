package com.ominouschestlock.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
    private final Map<String, MinigameSession> minigameSessionsByContainer = new HashMap<>();
    private final Map<UUID, MinigameSession> minigameSessionsByPlayer = new HashMap<>();
    private final Map<Inventory, MinigameSession> minigameSessionsByInventory = new HashMap<>();
    private int logLevel = 1;
    private boolean allowNormalKeys = false;
    private boolean allowLockpicks = true;
    private NamespacedKey pickTypeKey;
    private int pickLimitMin = 1;
    private int pickLimitMax = 20;
    private double rustyOpenChance = 0.05;
    private double rustyNormalKeyChance = 0.10;
    private double rustyBreakChance = 0.88;
    private double rustyDamage = 1.0;
    private double normalOpenChance = 0.10;
    private double normalNormalKeyChance = 0.20;
    private double normalBreakChance = 0.33;
    private double normalDamage = 2.0;
    private double silenceOpenChance = 0.50;
    private double silenceBreakChance = 0.05;
    private double silenceDamage = 4.0;
    private long silencePenaltyResetMs = SILENCE_PENALTY_RESET_MS;
    private LockoutScope lockoutScope = LockoutScope.CHEST;
    private boolean minigameEnabled = true;
    private int trialPins = 4;
    private int trialDepths = 4;
    private int ominousPins = 6;
    private int ominousDepths = 5;
    private int minigameSessionTimeoutSeconds = 90;
    private int minigameBossbarAnimateTicks = 12;
    private int minigameBossbarSnapbackDelayTicks = 20;
    private int minigameBossbarPeakHoldTicks = 20;
    private boolean minigameBossbarEnabled = true;
    private boolean minigameVisualFeedbackEnabled = true;
    private boolean minigameVisualFeedbackRenameTitle = true;
    private boolean minigameClickPerCorrectPin = true;
    private boolean minigameRequireHoldingPick = true;
    private Material minigamePinIcon = Material.END_ROD;
    private String minigameSalt = "change-me";
    private int minigameSaltVersion = 1;
    private boolean trialAssistEliminateOne = true;
    private boolean trialRegenerateOnAttempt = false;
    private boolean ominousRegenerateOnAttempt = true;

    private static final long SILENCE_PENALTY_RESET_MS = 60L * 60L * 1000L;
    private static final int RUSTY_MODEL_DATA = 11001;
    private static final int NORMAL_MODEL_DATA = 11002;
    private static final int SILENCE_MODEL_DATA = 11003;
    private static final int MINIGAME_SIZE = 54;
    private static final int GRID_FIRST_COLUMN = 1;
    private static final int GRID_MAX_COLUMNS = 6;
    private static final int GRID_MAX_ROWS = 5;
    private static final int SLOT_RESET_ALL = 17;
    private static final int SLOT_TURN_LOCK = 26;
    private static final int SLOT_CLOSE = 35;

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
        for (MinigameSession session : new ArrayList<>(minigameSessionsByPlayer.values())) {
            endMinigameSession(session, false, null);
        }
        saveData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("chestlock")) {
            return false;
        }

        if (!sender.hasPermission("chestlock.admin")) {
            sender.sendMessage(errorLine("You do not have permission."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "info" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(errorLine("This command can only be used by players."));
                    return true;
                }
                Block target = player.getTargetBlockExact(5);
                if (target == null || !isLockable(target)) {
                    player.sendMessage(errorLine("Look at a chest, barrel, or shulker within 5 blocks."));
                    return true;
                }
                LockInfo lockInfo = getLockInfo(target);
                if (lockInfo == null) {
                    player.sendMessage(errorLine("That container is not locked."));
                    return true;
                }
                List<Location> locations = resolveLockLocations(target);
                if (!locations.isEmpty()) {
                    lockInfo = ensureMinigameData(locations, lockInfo);
                }
                String creator = lockInfo.creatorName() == null ? "unknown" : lockInfo.creatorName();
                String lastUser = lockInfo.lastUserName() == null ? "unknown" : lockInfo.lastUserName();
                Component info = statusLine("Lock details")
                        .append(Component.newline())
                        .append(detailLine("Key", lockInfo.keyName(), NamedTextColor.AQUA))
                        .append(Component.newline())
                        .append(detailLine("Created by", creator, NamedTextColor.GREEN))
                        .append(Component.newline())
                        .append(detailLine("Last used by", lastUser, NamedTextColor.GREEN));
                LockMinigameData minigameData = lockInfo.minigameData();
                if (minigameData != null) {
                    int[] secret = minigameData.secret();
                    StringBuilder combo = new StringBuilder();
                    for (int i = 0; i < secret.length; i++) {
                        if (i > 0) {
                            combo.append("-");
                        }
                        combo.append(secret[i] + 1);
                    }
                    info = info.append(Component.newline())
                            .append(detailLine("Minigame type", minigameData.type(), NamedTextColor.GOLD))
                            .append(Component.newline())
                            .append(detailLine("Pin combo", combo.toString(), NamedTextColor.YELLOW));
                }
                PickState state = getPickState(lockInfo, player);
                info = info.append(Component.newline())
                        .append(detailLine("Rusty pick", formatPickStatus(state.rustyAttempts(), state.rustyLimit()), NamedTextColor.GOLD))
                        .append(Component.newline())
                        .append(detailLine("Normal pick", formatPickStatus(state.normalAttempts(), state.normalLimit()), NamedTextColor.YELLOW))
                        .append(Component.newline())
                        .append(detailLine("Silence pick", formatSilenceStatus(state), NamedTextColor.LIGHT_PURPLE));
                if (lockInfo.lastPickUserName() != null && lockInfo.lastPickType() != null) {
                    String when = lockInfo.lastPickTimestamp() > 0L
                            ? formatDuration(System.currentTimeMillis() - lockInfo.lastPickTimestamp()) + " ago"
                            : "unknown time";
                    info = info.append(Component.newline())
                            .append(detailLine("Last pick attempt", lockInfo.lastPickUserName()
                                    + " with " + lockInfo.lastPickType() + " (" + when + ")", NamedTextColor.LIGHT_PURPLE));
                }
                player.sendMessage(info);
                return true;
            }
            case "unlock" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(errorLine("This command can only be used by players."));
                    return true;
                }
                Block target = player.getTargetBlockExact(5);
                if (target == null || !isLockable(target)) {
                    player.sendMessage(errorLine("Look at a chest, barrel, or shulker within 5 blocks."));
                    return true;
                }
                LockInfo lockInfo = getLockInfo(target);
                if (lockInfo == null) {
                    player.sendMessage(errorLine("That container is not locked."));
                    return true;
                }
                unlock(target, lockInfo.keyName());
                player.sendMessage(successLine("Unlocked container (key name was: " + lockInfo.keyName() + ")."));
                return true;
            }
            case "keyinfo" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(errorLine("This command can only be used by players."));
                    return true;
                }
                String keyName = getHeldKeyName(player);
                if (keyName == null) {
                    player.sendMessage(errorLine("Hold a named ominous trial key in your main hand or off hand."));
                    return true;
                }
                String locationKey = keyToChest.get(keyName);
                if (locationKey == null) {
                    player.sendMessage(errorLine("No locked container found for key name: " + keyName));
                    return true;
                }
                LockInfo lockInfo = lockedChests.get(locationKey);
                if (lockInfo == null) {
                    player.sendMessage(errorLine("Lock data missing for key name: " + keyName));
                    return true;
                }
                LocationData locationData = parseLocationKey(locationKey);
                String creator = lockInfo.creatorName() == null ? "unknown" : lockInfo.creatorName();
                String lastUser = lockInfo.lastUserName() == null ? "unknown" : lockInfo.lastUserName();
                player.sendMessage(statusLine("Key details"));
                player.sendMessage(detailLine("Key", lockInfo.keyName(), NamedTextColor.AQUA));
                if (locationData != null && locationData.realm() != null) {
                    player.sendMessage(detailLine("Locked container", locationKey + " (" + locationData.realm() + ")", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(detailLine("Locked container", locationKey, NamedTextColor.GREEN));
                }
                player.sendMessage(detailLine("Created by", creator, NamedTextColor.GREEN));
                player.sendMessage(detailLine("Last used by", lastUser, NamedTextColor.GREEN));
                return true;
            }
            case "reload" -> {
                reloadConfig();
                loadConfigValues();
                loadData();
                updatePickRecipes();
                sender.sendMessage(successLine("ChestLock data reloaded."));
                return true;
            }
            case "give" -> {
                if (args.length < 3) {
                    sender.sendMessage(errorLine("Usage: /chestlock give <player> <rusty|normal|silence> [amount]"));
                    return true;
                }
                String playerName = args[1];
                Player target = Bukkit.getPlayerExact(playerName);
                if (target == null) {
                    sender.sendMessage(errorLine("Player not found: " + playerName));
                    return true;
                }
                PickType pickType = parsePickType(args[2]);
                if (pickType == null) {
                    sender.sendMessage(errorLine("Pick type must be rusty, normal, or silence."));
                    return true;
                }
                int amount = 1;
                if (args.length >= 4) {
                    try {
                        amount = Integer.parseInt(args[3]);
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(errorLine("Amount must be a number."));
                        return true;
                    }
                }
                if (amount <= 0) {
                    sender.sendMessage(errorLine("Amount must be at least 1."));
                    return true;
                }
                ItemStack stack = createPick(pickType);
                stack.setAmount(amount);
                Map<Integer, ItemStack> overflow = target.getInventory().addItem(stack);
                for (ItemStack extra : overflow.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), extra);
                }
                sender.sendMessage(successLine("Gave " + amount + " " + pickType.id + " pick(s) to " + target.getName() + "."));
                return true;
            }
            case "loglevel" -> {
                if (args.length < 2) {
                    sender.sendMessage(detailLine("Current log level", String.valueOf(logLevel), NamedTextColor.GOLD));
                    sender.sendMessage(errorLine("Usage: /chestlock loglevel <0-3>"));
                    return true;
                }
                try {
                    int level = Integer.parseInt(args[1]);
                    if (level < 0 || level > 3) {
                        sender.sendMessage(errorLine("Log level must be between 0 and 3."));
                        return true;
                    }
                    logLevel = level;
                    getConfig().set("logging.level", level);
                    saveConfig();
                    sender.sendMessage(successLine("Logging level set to " + level + "."));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(errorLine("Log level must be a number between 0 and 3."));
                }
                return true;
            }
            case "normalkeys" -> {
                if (args.length < 2) {
                    sender.sendMessage(detailLine("Normal trial keys", allowNormalKeys ? "enabled" : "disabled", allowNormalKeys ? NamedTextColor.GREEN : NamedTextColor.RED));
                    sender.sendMessage(errorLine("Usage: /chestlock normalkeys <on|off>"));
                    return true;
                }
                String value = args[1].toLowerCase();
                if (!value.equals("on") && !value.equals("off")) {
                    sender.sendMessage(errorLine("Usage: /chestlock normalkeys <on|off>"));
                    return true;
                }
                allowNormalKeys = value.equals("on");
                getConfig().set("keys.allow-normal", allowNormalKeys);
                saveConfig();
                sender.sendMessage(successLine("Normal trial keys are now " + (allowNormalKeys ? "enabled." : "disabled.")));
                return true;
            }
            case "lockpicks" -> {
                if (args.length < 2) {
                    sender.sendMessage(detailLine("Lockpicking", allowLockpicks ? "enabled" : "disabled", allowLockpicks ? NamedTextColor.GREEN : NamedTextColor.RED));
                    sender.sendMessage(errorLine("Usage: /chestlock lockpicks <on|off>"));
                    return true;
                }
                String value = args[1].toLowerCase();
                if (!value.equals("on") && !value.equals("off")) {
                    sender.sendMessage(errorLine("Usage: /chestlock lockpicks <on|off>"));
                    return true;
                }
                allowLockpicks = value.equals("on");
                getConfig().set("lockpicks.enabled", allowLockpicks);
                saveConfig();
                updatePickRecipes();
                sender.sendMessage(successLine("Lockpicking is now " + (allowLockpicks ? "enabled." : "disabled.")));
                return true;
            }
            case "lockoutscope" -> {
                if (args.length < 2) {
                    sender.sendMessage(detailLine("Lockout scope", lockoutScope.name().toLowerCase(), NamedTextColor.AQUA));
                    sender.sendMessage(errorLine("Usage: /chestlock lockoutscope <chest|player>"));
                    return true;
                }
                String value = args[1].toLowerCase();
                LockoutScope scope = LockoutScope.fromConfig(value);
                if (!value.equals("chest") && !value.equals("player")) {
                    sender.sendMessage(errorLine("Usage: /chestlock lockoutscope <chest|player>"));
                    return true;
                }
                lockoutScope = scope;
                getConfig().set("lockpicks.lockout-scope", scope.name().toLowerCase());
                saveConfig();
                sender.sendMessage(successLine("Lockout scope set to " + scope.name().toLowerCase() + "."));
                return true;
            }
            case "minigame" -> {
                if (args.length < 2) {
                    sender.sendMessage(detailLine("Lockpick minigame", minigameEnabled ? "enabled" : "disabled",
                            minigameEnabled ? NamedTextColor.GREEN : NamedTextColor.RED));
                    sender.sendMessage(errorLine("Usage: /chestlock minigame <on|off>"));
                    return true;
                }
                String value = args[1].toLowerCase();
                if (!value.equals("on") && !value.equals("off")) {
                    sender.sendMessage(errorLine("Usage: /chestlock minigame <on|off>"));
                    return true;
                }
                minigameEnabled = value.equals("on");
                getConfig().set("lockpicks.minigame.enabled", minigameEnabled);
                saveConfig();
                if (!minigameEnabled) {
                    for (MinigameSession session : new ArrayList<>(minigameSessionsByPlayer.values())) {
                        endMinigameSession(session, true, errorLine("Lockpick minigame was disabled by an operator."));
                    }
                }
                sender.sendMessage(successLine("Lockpick minigame is now " + (minigameEnabled ? "enabled." : "disabled.")));
                return true;
            }
            case "minigamebossbar" -> {
                if (args.length < 2) {
                    sender.sendMessage(detailLine("Minigame bossbar", minigameBossbarEnabled ? "enabled" : "disabled",
                            minigameBossbarEnabled ? NamedTextColor.GREEN : NamedTextColor.RED));
                    sender.sendMessage(errorLine("Usage: /chestlock minigamebossbar <on|off>"));
                    return true;
                }
                String value = args[1].toLowerCase();
                if (!value.equals("on") && !value.equals("off")) {
                    sender.sendMessage(errorLine("Usage: /chestlock minigamebossbar <on|off>"));
                    return true;
                }
                minigameBossbarEnabled = value.equals("on");
                getConfig().set("lockpicks.minigame.bossbar.enabled", minigameBossbarEnabled);
                saveConfig();
                if (!minigameBossbarEnabled) {
                    for (MinigameSession session : new ArrayList<>(minigameSessionsByPlayer.values())) {
                        if (session.bossBar != null) {
                            session.bossBar.removeAll();
                        }
                    }
                }
                sender.sendMessage(successLine("Minigame bossbar is now " + (minigameBossbarEnabled ? "enabled." : "disabled.")));
                return true;
            }
            case "minigamevisual" -> {
                if (args.length < 2) {
                    sender.sendMessage(detailLine("Minigame visual bar", minigameVisualFeedbackEnabled ? "enabled" : "disabled",
                            minigameVisualFeedbackEnabled ? NamedTextColor.GREEN : NamedTextColor.RED));
                    sender.sendMessage(errorLine("Usage: /chestlock minigamevisual <on|off>"));
                    return true;
                }
                String value = args[1].toLowerCase();
                if (!value.equals("on") && !value.equals("off")) {
                    sender.sendMessage(errorLine("Usage: /chestlock minigamevisual <on|off>"));
                    return true;
                }
                minigameVisualFeedbackEnabled = value.equals("on");
                getConfig().set("lockpicks.minigame.visual-feedback.enabled", minigameVisualFeedbackEnabled);
                saveConfig();
                for (MinigameSession session : new ArrayList<>(minigameSessionsByPlayer.values())) {
                    if (!minigameVisualFeedbackEnabled) {
                        session.feedbackColor = MinigameSession.FeedbackColor.OFF;
                        session.feedbackProgress = 0.0;
                        session.feedbackTitle = "";
                    }
                    renderMinigame(session);
                }
                sender.sendMessage(successLine("Minigame visual bar is now " + (minigameVisualFeedbackEnabled ? "enabled." : "disabled.")));
                return true;
            }
            case "settings" -> {
                sender.sendMessage(statusLine("ChestLock settings"));
                sender.sendMessage(detailLine("Logging level", String.valueOf(logLevel), NamedTextColor.AQUA));
                sender.sendMessage(detailLine("Normal trial keys", allowNormalKeys ? "enabled" : "disabled",
                        allowNormalKeys ? NamedTextColor.GREEN : NamedTextColor.RED));
                sender.sendMessage(detailLine("Lockpicks", allowLockpicks ? "enabled" : "disabled",
                        allowLockpicks ? NamedTextColor.GREEN : NamedTextColor.RED));
                sender.sendMessage(detailLine("Lockout scope", lockoutScope.name().toLowerCase(), NamedTextColor.GOLD));
                sender.sendMessage(detailLine("Limit range", pickLimitMin + " - " + pickLimitMax, NamedTextColor.YELLOW));

                sender.sendMessage(statusLine("Pick values"));
                sender.sendMessage(detailLine("Rusty chance/break/dmg",
                        formatPercent(rustyOpenChance) + " / " + formatPercent(rustyBreakChance) + " / " + rustyDamage,
                        NamedTextColor.GOLD));
                sender.sendMessage(detailLine("Normal chance/break/dmg",
                        formatPercent(normalOpenChance) + " / " + formatPercent(normalBreakChance) + " / " + normalDamage,
                        NamedTextColor.YELLOW));
                sender.sendMessage(detailLine("Silence chance/break/dmg",
                        formatPercent(silenceOpenChance) + " / " + formatPercent(silenceBreakChance) + " / " + silenceDamage,
                        NamedTextColor.LIGHT_PURPLE));
                sender.sendMessage(detailLine("Silence penalty reset", formatDuration(silencePenaltyResetMs), NamedTextColor.LIGHT_PURPLE));

                sender.sendMessage(statusLine("Minigame"));
                sender.sendMessage(detailLine("Enabled", minigameEnabled ? "yes" : "no",
                        minigameEnabled ? NamedTextColor.GREEN : NamedTextColor.RED));
                sender.sendMessage(detailLine("Session timeout", minigameSessionTimeoutSeconds + "s", NamedTextColor.AQUA));
                sender.sendMessage(detailLine("Require holding pick", minigameRequireHoldingPick ? "yes" : "no",
                        minigameRequireHoldingPick ? NamedTextColor.GREEN : NamedTextColor.RED));
                sender.sendMessage(detailLine("Click per correct pin", minigameClickPerCorrectPin ? "yes" : "no",
                        minigameClickPerCorrectPin ? NamedTextColor.GREEN : NamedTextColor.RED));
                sender.sendMessage(detailLine("Trial pins/depths", trialPins + " / " + trialDepths, NamedTextColor.GOLD));
                sender.sendMessage(detailLine("Trial assist", trialAssistEliminateOne ? "enabled" : "disabled",
                        trialAssistEliminateOne ? NamedTextColor.GREEN : NamedTextColor.RED));
                sender.sendMessage(detailLine("Trial regen on attempt", trialRegenerateOnAttempt ? "enabled" : "disabled",
                        trialRegenerateOnAttempt ? NamedTextColor.GREEN : NamedTextColor.RED));
                sender.sendMessage(detailLine("Ominous pins/depths", ominousPins + " / " + ominousDepths, NamedTextColor.DARK_AQUA));
                sender.sendMessage(detailLine("Ominous regen on attempt", ominousRegenerateOnAttempt ? "enabled" : "disabled",
                        ominousRegenerateOnAttempt ? NamedTextColor.GREEN : NamedTextColor.RED));
                sender.sendMessage(detailLine("Bossbar", minigameBossbarEnabled ? "enabled" : "disabled",
                        minigameBossbarEnabled ? NamedTextColor.GREEN : NamedTextColor.RED));
                sender.sendMessage(detailLine("Bossbar timing",
                        minigameBossbarAnimateTicks + " / " + minigameBossbarPeakHoldTicks + " / " + minigameBossbarSnapbackDelayTicks + " ticks",
                        NamedTextColor.YELLOW));
                sender.sendMessage(detailLine("Visual feedback", minigameVisualFeedbackEnabled ? "enabled" : "disabled",
                        minigameVisualFeedbackEnabled ? NamedTextColor.GREEN : NamedTextColor.RED));
                sender.sendMessage(detailLine("Title status", minigameVisualFeedbackRenameTitle ? "enabled" : "disabled",
                        minigameVisualFeedbackRenameTitle ? NamedTextColor.GREEN : NamedTextColor.RED));
                sender.sendMessage(detailLine("Pin icon", minigamePinIcon.name().toLowerCase(), NamedTextColor.AQUA));
                sender.sendMessage(detailLine("Salt version", String.valueOf(minigameSaltVersion), NamedTextColor.GRAY));
                return true;
            }
            case "pinicon" -> {
                if (args.length < 2) {
                    sender.sendMessage(detailLine("Minigame pin icon", minigamePinIcon.name().toLowerCase(), NamedTextColor.AQUA));
                    sender.sendMessage(errorLine("Usage: /chestlock pinicon <material>"));
                    return true;
                }
                Material material = Material.matchMaterial(args[1]);
                if (material == null || material.isAir()) {
                    sender.sendMessage(errorLine("Unknown or invalid material. Example: end_rod"));
                    return true;
                }
                minigamePinIcon = material;
                getConfig().set("lockpicks.minigame.ui.pin-icon", material.name().toLowerCase());
                saveConfig();
                for (MinigameSession session : new ArrayList<>(minigameSessionsByPlayer.values())) {
                    renderMinigame(session);
                }
                sender.sendMessage(successLine("Minigame pin icon set to " + material.name().toLowerCase() + "."));
                return true;
            }
            default -> {
                sendHelp(sender);
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
            return List.of("info", "unlock", "keyinfo", "reload", "loglevel", "normalkeys", "lockpicks", "minigame",
                    "minigamebossbar", "minigamevisual", "pinicon", "lockoutscope", "settings", "give", "help");
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
        if (args.length == 2 && args[0].equalsIgnoreCase("minigame")) {
            return List.of("on", "off");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("minigamebossbar")) {
            return List.of("on", "off");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("minigamevisual")) {
            return List.of("on", "off");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("lockoutscope")) {
            return List.of("chest", "player");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pinicon")) {
            String q = args[1].toLowerCase();
            List<String> out = new ArrayList<>();
            for (Material material : Material.values()) {
                if (material.isAir()) {
                    continue;
                }
                String id = material.name().toLowerCase();
                if (id.startsWith(q)) {
                    out.add(id);
                    if (out.size() >= 40) {
                        break;
                    }
                }
            }
            return out;
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

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(statusLine("ChestLock commands"));
        sender.sendMessage(helpLine("/chestlock info", "show lock key name for looked-at container"));
        sender.sendMessage(helpLine("/chestlock unlock", "force unlock looked-at container"));
        sender.sendMessage(helpLine("/chestlock keyinfo", "show lock info for key in hand"));
        sender.sendMessage(helpLine("/chestlock reload", "reload lock data from disk"));
        sender.sendMessage(helpLine("/chestlock loglevel <0-3>", "set log verbosity"));
        sender.sendMessage(helpLine("/chestlock normalkeys <on|off>", "allow normal trial keys"));
        sender.sendMessage(helpLine("/chestlock lockpicks <on|off>", "allow lock picking and crafting"));
        sender.sendMessage(helpLine("/chestlock minigame <on|off>", "toggle lockpick minigame mode"));
        sender.sendMessage(helpLine("/chestlock minigamebossbar <on|off>", "toggle minigame bossbar feedback"));
        sender.sendMessage(helpLine("/chestlock minigamevisual <on|off>", "toggle minigame inventory meter feedback"));
        sender.sendMessage(helpLine("/chestlock pinicon <material>", "set selected pin icon item"));
        sender.sendMessage(helpLine("/chestlock lockoutscope <chest|player>", "set lockout scope"));
        sender.sendMessage(helpLine("/chestlock settings", "show current loaded settings"));
        sender.sendMessage(helpLine("/chestlock give <player> <rusty|normal|silence> [amount]", "give lock picks"));
    }

    private String formatPercent(double value) {
        return String.format("%.0f%%", Math.max(0.0, value) * 100.0);
    }

    private Component statusLine(String text) {
        return Component.text(">> ", NamedTextColor.DARK_GREEN, TextDecoration.BOLD)
                .append(Component.text(text, NamedTextColor.GREEN));
    }

    private Component successLine(String text) {
        return Component.text(">> ", NamedTextColor.DARK_GREEN, TextDecoration.BOLD)
                .append(Component.text(text, NamedTextColor.GREEN));
    }

    private Component errorLine(String text) {
        return Component.text(">> ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                .append(Component.text(text, NamedTextColor.RED));
    }

    private Component detailLine(String label, String value, NamedTextColor valueColor) {
        return Component.text(label + ": ", NamedTextColor.YELLOW)
                .append(Component.text(value, valueColor));
    }

    private Component helpLine(String command, String description) {
        return Component.text("• ", NamedTextColor.DARK_GREEN)
                .append(Component.text(command, NamedTextColor.AQUA))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(description, NamedTextColor.GREEN));
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

    private PickMatch findHeldPick(Player player, PickType expectedType) {
        if (player == null || expectedType == null) {
            return null;
        }
        PickType main = getPickType(player.getInventory().getItemInMainHand());
        if (main == expectedType) {
            return new PickMatch(main, EquipmentSlot.HAND);
        }
        PickType off = getPickType(player.getInventory().getItemInOffHand());
        if (off == expectedType) {
            return new PickMatch(off, EquipmentSlot.OFF_HAND);
        }
        return null;
    }

    private void handleMinigameOpen(PlayerInteractEvent event, Block block, LockInfo lockInfo, PickMatch pickMatch) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        List<Location> locations = resolveLockLocations(block);
        if (locations.isEmpty()) {
            return;
        }
        String containerId = containerSessionKey(locations);
        if (containerId == null) {
            return;
        }

        MinigameSession ownedByOther = minigameSessionsByContainer.get(containerId);
        if (ownedByOther != null && !ownedByOther.playerId.equals(player.getUniqueId())) {
            player.sendMessage(errorLine("Someone else is already lockpicking this container."));
            playFail(player, locations.getFirst());
            return;
        }

        MinigameSession current = minigameSessionsByPlayer.get(player.getUniqueId());
        if (current != null && !current.containerId.equals(containerId)) {
            endMinigameSession(current, false, null);
        }
        if (ownedByOther != null) {
            renderMinigame(ownedByOther);
            if (!player.getOpenInventory().getTopInventory().equals(ownedByOther.inventory)) {
                player.openInventory(ownedByOther.inventory);
            }
            scheduleSessionTimeout(ownedByOther);
            return;
        }

        LockInfo refreshed = getLockInfo(locations);
        if (refreshed == null) {
            return;
        }
        LockInfo withMinigame = ensureMinigameData(locations, refreshed);
        LockMinigameData minigameData = withMinigame.minigameData();
        if (minigameData == null) {
            return;
        }

        String label = "Locked Chest";
        Inventory inventory = Bukkit.createInventory(null, MINIGAME_SIZE, Component.text(label, NamedTextColor.GOLD));
        MinigameSession session = new MinigameSession(
                player.getUniqueId(),
                player.getName(),
                containerId,
                List.copyOf(locations),
                withMinigame.keyName(),
                pickMatch.type(),
                inventory,
                minigameData
        );
        session.feedbackTitle = "";
        session.feedbackColor = MinigameSession.FeedbackColor.OFF;
        session.feedbackProgress = 0.0;
        applyTrialAssist(session);
        minigameSessionsByContainer.put(containerId, session);
        minigameSessionsByPlayer.put(player.getUniqueId(), session);
        minigameSessionsByInventory.put(inventory, session);
        renderMinigame(session);
        player.openInventory(inventory);
        playWorldSoundDelayed(block, Sound.BLOCK_VAULT_INSERT_ITEM);
        scheduleSessionTimeout(session);
    }

    private void handleMinigameClick(Player player, MinigameSession session, int rawSlot, boolean leftClick, boolean rightClick) {
        if (rawSlot < 0 || rawSlot >= session.inventory.getSize()) {
            return;
        }
        if (rawSlot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (rawSlot == SLOT_RESET_ALL) {
            for (boolean[] row : session.eliminated) {
                Arrays.fill(row, false);
            }
            Arrays.fill(session.selectedDepths, -1);
            touchSession(session);
            renderMinigame(session);
            return;
        }
        if (rawSlot == SLOT_TURN_LOCK) {
            attemptMinigameTurn(player, session);
            return;
        }

        int row = rawSlot / 9;
        int col = rawSlot % 9;
        int pinIndex = col - GRID_FIRST_COLUMN;
        int depthIndex = row;
        if (pinIndex < 0 || pinIndex >= session.minigameData.pins()) {
            return;
        }
        if (depthIndex < 0 || depthIndex >= session.minigameData.depths()) {
            return;
        }

        if (rightClick) {
            session.eliminated[pinIndex][depthIndex] = !session.eliminated[pinIndex][depthIndex];
            if (session.eliminated[pinIndex][depthIndex] && session.selectedDepths[pinIndex] == depthIndex) {
                session.selectedDepths[pinIndex] = -1;
            }
        } else if (leftClick) {
            session.selectedDepths[pinIndex] = depthIndex;
            session.eliminated[pinIndex][depthIndex] = false;
        } else {
            return;
        }
        touchSession(session);
        renderMinigame(session);
    }

    private void attemptMinigameTurn(Player player, MinigameSession session) {
        LockInfo lockInfo = getLockInfo(session.locations);
        if (lockInfo == null || !session.keyName.equals(lockInfo.keyName())) {
            endMinigameSession(session, true, errorLine("Lock no longer exists."));
            return;
        }
        if (minigameRequireHoldingPick) {
            PickMatch heldPick = findHeldPick(player, session.pickType);
            if (heldPick == null) {
                endMinigameSession(session, true, errorLine("Hold the same lock pick to continue."));
                return;
            }
        }

        int correctPins = countCorrectPins(session);
        boolean allPinsCorrect = correctPins == session.minigameData.pins();
        TurnAttemptResult result = evaluateTurnAttempt(player, session, lockInfo, allPinsCorrect);
        double shownProgress = (double) result.shownCorrectPins / (double) session.minigameData.pins();
        String attemptDetail = buildMinigameAttemptDetail(session, result);
        boolean pickBroken = false;
        if (ThreadLocalRandom.current().nextDouble() < result.breakChance) {
            PickMatch heldPick = findHeldPick(player, session.pickType);
            if (heldPick != null) {
                consumeOnePick(player, heldPick);
                pickBroken = true;
            }
        }
        if (pickBroken) {
            playWorldSoundDelayed(session.locations.getFirst().getBlock(), Sound.ENTITY_ITEM_BREAK);
        }
        boolean noSameTypeInHand = !hasHeldPickType(player, session.pickType);

        animateTurnBossbar(player, session, shownProgress, result, pickBroken, () -> {
            unlock(session.locations.getFirst().getBlock(), lockInfo.keyName());
            playSuccess(player, session.locations.getFirst());
            logLockEvent("PICK_SUCCESS", player.getName(), null, session.locations.getFirst(), lockInfo, attemptDetail);
            openUnlockedContainer(player, session.locations.getFirst().getBlock());
            endMinigameSession(session, false, null);
        });

        if (result.playPinClicks) {
            playCorrectPinClicks(player, session.locations.getFirst(), result.shownCorrectPins);
        }

        if (result.success) {
            return;
        }

        if (pickBroken && noSameTypeInHand) {
            endMinigameSession(session, true, errorLine("Your " + session.pickType.displayName + " broke."));
            return;
        }

        player.damage(result.damageOnFail);
        if (result.lockoutHard) {
            playWorldSoundDelayed(session.locations.getFirst().getBlock(), Sound.BLOCK_VAULT_DEACTIVATE);
            playWorldSoundDelayed(session.locations.getFirst().getBlock(), Sound.BLOCK_VAULT_HIT);
        } else if (result.overLimit) {
            playWorldSoundDelayed(session.locations.getFirst().getBlock(), Sound.BLOCK_VAULT_HIT);
        } else {
            playWorldSoundDelayed(session.locations.getFirst().getBlock(), Sound.BLOCK_CHEST_LOCKED);
        }
        logLockEvent("PICK_FAIL", player.getName(), null, session.locations.getFirst(), lockInfo,
                attemptDetail);

        if (shouldRegenerateOnAttempt(session, result)) {
            rerollMinigameSecret(session);
        }
        touchSession(session);
        renderMinigame(session);
    }

    private TurnAttemptResult evaluateTurnAttempt(Player player, MinigameSession session, LockInfo lockInfo, boolean allPinsCorrect) {
        long now = System.currentTimeMillis();
        PickState state = getPickState(lockInfo, player);
        boolean overLimit = false;
        boolean lockoutHard = false;
        boolean lockoutDisplay = false;
        boolean success = false;
        int shownCorrectPins = countCorrectPins(session);
        int silencePenaltyStage = 0;
        boolean feedbackObfuscated = false;
        long lockoutEndsAtMs = 0L;
        int attemptsAfter = 0;
        int limitValue = 0;
        double damageOnFail;
        double breakChance;

        switch (session.pickType) {
            case RUSTY -> {
                int limit = state.rustyLimit();
                if (limit < 0) {
                    limit = ThreadLocalRandom.current().nextInt(pickLimitMin, pickLimitMax + 1);
                    state = state.withRustyLimit(limit);
                }
                int attempts = state.rustyAttempts();
                boolean overLimitBefore = attempts >= limit;
                boolean lockoutNow = !overLimitBefore && (attempts + 1) >= limit;
                overLimit = overLimitBefore || lockoutNow;
                lockoutHard = overLimit;
                lockoutDisplay = overLimit;
                attemptsAfter = attempts + 1;
                limitValue = limit;
                state = state.withRustyAttempts(attemptsAfter);
                success = allPinsCorrect && !overLimitBefore && !lockoutNow;
                damageOnFail = rustyDamage;
                breakChance = rustyBreakChance;
            }
            case NORMAL -> {
                int limit = state.normalLimit();
                if (limit < 0) {
                    limit = ThreadLocalRandom.current().nextInt(pickLimitMin, pickLimitMax + 1);
                    state = state.withNormalLimit(limit);
                }
                int attempts = state.normalAttempts();
                boolean overLimitBefore = attempts >= limit;
                boolean lockoutNow = !overLimitBefore && (attempts + 1) >= limit;
                overLimit = overLimitBefore || lockoutNow;
                lockoutHard = overLimit;
                lockoutDisplay = overLimit;
                attemptsAfter = attempts + 1;
                limitValue = limit;
                state = state.withNormalAttempts(attemptsAfter);
                success = allPinsCorrect && !overLimitBefore && !lockoutNow;
                damageOnFail = normalDamage;
                breakChance = normalBreakChance;
            }
            case SILENCE -> {
                int limit = state.silenceLimit();
                if (limit < 0) {
                    limit = ThreadLocalRandom.current().nextInt(pickLimitMin, pickLimitMax + 1);
                    state = state.withSilenceLimit(limit);
                }
                int attempts = state.silenceAttempts();
                boolean overLimitBefore = attempts >= limit;
                boolean lockoutNow = !overLimitBefore && (attempts + 1) >= limit;
                overLimit = overLimitBefore || lockoutNow;

                int overLimitAttempts = state.silenceOverLimitAttempts();
                long penaltyTimestamp = state.silencePenaltyTimestamp();
                boolean penaltyExpired = penaltyTimestamp > 0L && now - penaltyTimestamp >= silencePenaltyResetMs;
                if (penaltyExpired) {
                    overLimitAttempts = 0;
                    penaltyTimestamp = 0L;
                    state = state.withSilenceOverLimitAttempts(0).withSilencePenaltyTimestamp(0L);
                }
                if (overLimit) {
                    silencePenaltyStage = Math.max(1, overLimitAttempts + 1);
                    if (penaltyTimestamp <= 0L) {
                        penaltyTimestamp = now;
                    }
                    state = state.withSilenceOverLimitAttempts(silencePenaltyStage).withSilencePenaltyTimestamp(penaltyTimestamp);
                    feedbackObfuscated = true;
                    lockoutDisplay = true;
                    lockoutEndsAtMs = penaltyTimestamp + silencePenaltyResetMs;
                }
                attemptsAfter = attempts + 1;
                limitValue = limit;
                state = state.withSilenceAttempts(attemptsAfter);
                success = allPinsCorrect;
                damageOnFail = silenceDamage;
                breakChance = silenceBreakChance;
            }
            default -> throw new IllegalStateException("Unexpected pick type: " + session.pickType);
        }

        LockInfo updated = updatePickState(lockInfo.withLastPick(player, session.pickType, now), player, state);
        updateLockInfo(session.locations, updated);

        if (session.pickType == PickType.SILENCE && silencePenaltyStage > 0) {
            double falseFeedbackChance = Math.min(0.75, silencePenaltyStage * 0.20);
            if (shownCorrectPins > 0 && ThreadLocalRandom.current().nextDouble() < falseFeedbackChance) {
                shownCorrectPins -= 1;
            }
        }
        boolean playPinClicks = minigameClickPerCorrectPin
                && (!(session.pickType == PickType.SILENCE) || silencePenaltyStage < 2);
        return new TurnAttemptResult(success, overLimit, lockoutHard, lockoutDisplay, lockoutEndsAtMs,
                shownCorrectPins, playPinClicks, feedbackObfuscated, attemptsAfter, limitValue, damageOnFail, breakChance);
    }

    private int countCorrectPins(MinigameSession session) {
        int[] secret = session.minigameData.secret();
        int correct = 0;
        for (int pin = 0; pin < session.minigameData.pins(); pin++) {
            if (session.selectedDepths[pin] == secret[pin]) {
                correct += 1;
            }
        }
        return correct;
    }

    private void renderMinigame(MinigameSession session) {
        Inventory inv = session.inventory;
        inv.clear();
        ItemStack filler = namedItem(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 0; slot < inv.getSize(); slot++) {
            inv.setItem(slot, filler);
        }

        renderBottomTurnMeter(inv, session);

        for (int pin = 0; pin < session.minigameData.pins() && pin < GRID_MAX_COLUMNS; pin++) {
            for (int depth = 0; depth < session.minigameData.depths() && depth < GRID_MAX_ROWS; depth++) {
                int slot = depth * 9 + pin + GRID_FIRST_COLUMN;
                boolean eliminated = session.eliminated[pin][depth];
                boolean selected = session.selectedDepths[pin] == depth;
                Material material = selected ? minigamePinIcon : (eliminated ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
                NamedTextColor color = selected ? NamedTextColor.GREEN : (eliminated ? NamedTextColor.RED : NamedTextColor.GRAY);
                String state = selected ? "Selected" : (eliminated ? "Eliminated" : "Candidate");
                inv.setItem(slot, namedItem(material, "Depth " + (depth + 1), color,
                        List.of(
                                Component.text("Pin " + (pin + 1), NamedTextColor.WHITE),
                                Component.text(state, color),
                                Component.text("L-click: select", NamedTextColor.GRAY),
                                Component.text("R-click: eliminate", NamedTextColor.GRAY)
                        )));
            }
        }

        String lockTypeLabel = "trial".equalsIgnoreCase(session.minigameData.type()) ? "Trial Lock" : "Ominous Lock";
        String statusText = activeFeedbackStatus(session);
        List<Component> lockTypeLore = new ArrayList<>();
        lockTypeLore.add(Component.text("Pick: " + session.pickType.id, NamedTextColor.GOLD));
        lockTypeLore.add(Component.text("Pins: " + session.minigameData.pins() + "  Depths: " + session.minigameData.depths(), NamedTextColor.GRAY));
        if (statusText != null) {
            lockTypeLore.add(Component.text("Status: " + statusText, NamedTextColor.YELLOW));
        }
        inv.setItem(8, namedItem(Material.TRIAL_KEY, "Lock Type: " + lockTypeLabel, NamedTextColor.AQUA, lockTypeLore));
        inv.setItem(SLOT_RESET_ALL, namedItem(Material.BARRIER, "Reset All", NamedTextColor.RED, List.of()));
        List<Component> turnLore = new ArrayList<>();
        turnLore.add(Component.text("Attempts are consumed here.", NamedTextColor.YELLOW));
        if (statusText != null) {
            turnLore.add(Component.text("Status: " + statusText, NamedTextColor.GRAY));
        }
        inv.setItem(SLOT_TURN_LOCK, namedItem(Material.LEVER, "TURN LOCK", NamedTextColor.GOLD, turnLore));
        inv.setItem(SLOT_CLOSE, namedItem(Material.OAK_DOOR, "Close", NamedTextColor.GRAY, List.of()));
        updateMinigameTitle(session);
    }

    private void renderBottomTurnMeter(Inventory inv, MinigameSession session) {
        double progress = Math.max(0.0, Math.min(1.0, session.feedbackProgress));
        int meterStart = 45;
        int meterSlots = 9;
        int filled = (int) Math.ceil(progress * meterSlots);
        if (progress <= 0.0) {
            filled = 0;
        }
        Material litMaterial;
        NamedTextColor litColor;
        switch (session.feedbackColor) {
            case RED -> {
                litMaterial = Material.RED_STAINED_GLASS_PANE;
                litColor = NamedTextColor.RED;
            }
            case GREEN -> {
                litMaterial = Material.LIME_STAINED_GLASS_PANE;
                litColor = NamedTextColor.GREEN;
            }
            case YELLOW -> {
                litMaterial = Material.YELLOW_STAINED_GLASS_PANE;
                litColor = NamedTextColor.YELLOW;
            }
            default -> {
                litMaterial = Material.BLACK_STAINED_GLASS_PANE;
                litColor = NamedTextColor.DARK_GRAY;
            }
        }
        for (int i = 0; i < meterSlots; i++) {
            int slot = meterStart + i;
            boolean lit = i < filled;
            if (lit) {
                inv.setItem(slot, namedItem(litMaterial, " ", litColor, List.of()));
            } else {
                inv.setItem(slot, namedItem(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of()));
            }
        }
    }

    private String activeFeedbackStatus(MinigameSession session) {
        if (session == null || session.feedbackColor == MinigameSession.FeedbackColor.OFF) {
            return null;
        }
        if (session.feedbackTitle == null || session.feedbackTitle.isBlank()) {
            return null;
        }
        return session.feedbackTitle;
    }

    private void updateMinigameTitle(MinigameSession session) {
        Player owner = Bukkit.getPlayer(session.playerId);
        if (owner == null || !owner.isOnline()) {
            return;
        }
        if (!minigameVisualFeedbackRenameTitle) {
            return;
        }
        if (!owner.getOpenInventory().getTopInventory().equals(session.inventory)) {
            return;
        }
        String status = activeFeedbackStatus(session);
        String title;
        if (status == null) {
            title = ChatColor.GOLD + session.baseTitle;
        } else {
            title = switch (session.feedbackColor) {
                case RED -> ChatColor.RED + status;
                case GREEN -> ChatColor.GREEN + status;
                case YELLOW -> ChatColor.YELLOW + status;
                default -> ChatColor.WHITE + status;
            };
        }
        try {
            owner.getOpenInventory().setTitle(title);
        } catch (Throwable ignored) {
            // Inventory title updates can fail on unsupported implementations.
        }
    }

    private ItemStack namedItem(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (lore != null && !lore.isEmpty()) {
            List<Component> sanitized = new ArrayList<>(lore.size());
            for (Component line : lore) {
                sanitized.add(line.decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(sanitized);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void animateTurnBossbar(Player player, MinigameSession session, double progress, TurnAttemptResult result,
                                    boolean pickBroken, Runnable onSuccessDone) {
        BossBar bossBar = null;
        if (minigameBossbarEnabled) {
            bossBar = session.bossBar;
            if (bossBar == null) {
                bossBar = Bukkit.createBossBar("Turn Distance", BarColor.YELLOW, BarStyle.SOLID);
                session.bossBar = bossBar;
            }
            bossBar.setTitle("Turn Distance");
            bossBar.setProgress(0.0);
            bossBar.setColor(BarColor.YELLOW);
            bossBar.removeAll();
            bossBar.addPlayer(player);
        } else if (session.bossBar != null) {
            session.bossBar.removeAll();
        }
        session.feedbackTitle = "Turn Distance";
        session.feedbackColor = minigameVisualFeedbackEnabled ? MinigameSession.FeedbackColor.YELLOW : MinigameSession.FeedbackColor.OFF;
        session.feedbackProgress = 0.0;
        renderMinigame(session);
        if (session.bossbarTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(session.bossbarTaskId);
            session.bossbarTaskId = -1;
        }
        if (result.lockoutDisplay && !result.success) {
            showLockedOutBossbar(player, session, result, pickBroken);
            return;
        }
        int ticks = Math.max(1, minigameBossbarAnimateTicks);
        final double finalProgress = Math.max(0.0, Math.min(1.0, progress));
        final BossBar finalBossBar = bossBar;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            int step = 0;

            @Override
            public void run() {
                if (!player.isOnline() || minigameSessionsByPlayer.get(player.getUniqueId()) != session) {
                    if (finalBossBar != null) {
                        finalBossBar.removeAll();
                    }
                    if (session.bossbarTaskId >= 0) {
                        Bukkit.getScheduler().cancelTask(session.bossbarTaskId);
                        session.bossbarTaskId = -1;
                    }
                    return;
                }
                step += 1;
                double value = finalProgress * (step / (double) ticks);
                double clamped = Math.max(0.0, Math.min(1.0, value));
                if (finalBossBar != null) {
                    finalBossBar.setProgress(clamped);
                }
                if (minigameVisualFeedbackEnabled) {
                    session.feedbackTitle = "Turn Distance";
                    session.feedbackColor = MinigameSession.FeedbackColor.YELLOW;
                    session.feedbackProgress = clamped;
                    renderMinigame(session);
                }
                if (step >= ticks) {
                    if (session.bossbarTaskId >= 0) {
                        Bukkit.getScheduler().cancelTask(session.bossbarTaskId);
                        session.bossbarTaskId = -1;
                    }
                    if (result.success) {
                        String unlockedTitle = pickBroken ? "Chest Unlocked! Pick Broke!" : "Chest Unlocked!";
                        flashBossBar(session, player, unlockedTitle, BarColor.GREEN, BarColor.YELLOW, 4, () -> {
                            if (onSuccessDone != null) {
                                onSuccessDone.run();
                            }
                        });
                    } else {
                        String lockedTitle = pickBroken ? "Chest Locked! Pick Broke!" : "Chest Locked!";
                        Bukkit.getScheduler().runTaskLater(ChestLockPlugin.this, () -> {
                            if (minigameSessionsByPlayer.get(player.getUniqueId()) != session) {
                                return;
                            }
                            flashBossBar(session, player, lockedTitle, BarColor.RED, BarColor.YELLOW, 3, () ->
                                    Bukkit.getScheduler().runTaskLater(ChestLockPlugin.this, () -> {
                                        if (minigameSessionsByPlayer.get(player.getUniqueId()) == session && session.bossBar != null) {
                                            session.bossBar.setTitle("Turn Distance");
                                            session.bossBar.setColor(BarColor.YELLOW);
                                            session.bossBar.setProgress(0.0);
                                        }
                                        session.feedbackTitle = "";
                                        session.feedbackColor = MinigameSession.FeedbackColor.OFF;
                                        session.feedbackProgress = 0.0;
                                        renderMinigame(session);
                                    }, Math.max(0, minigameBossbarSnapbackDelayTicks)));
                        }, Math.max(0, minigameBossbarPeakHoldTicks));
                    }
                }
            }
        }, 1L, 1L);
        session.bossbarTaskId = task.getTaskId();
    }


    private void showLockedOutBossbar(Player player, MinigameSession session, TurnAttemptResult result, boolean pickBroken) {
        BossBar bossBar = session.bossBar;
        if (minigameBossbarEnabled && bossBar == null) {
            bossBar = Bukkit.createBossBar("Locked Out", BarColor.RED, BarStyle.SOLID);
            session.bossBar = bossBar;
            bossBar.addPlayer(player);
        }
        if (bossBar != null) {
            bossBar.setColor(BarColor.RED);
            bossBar.setProgress(1.0);
        }
        if (result.lockoutEndsAtMs <= 0L) {
            String title = pickBroken ? "Locked Out - Pick Broke!" : "Locked Out";
            if (bossBar != null) {
                bossBar.setTitle(title);
            }
            session.feedbackTitle = title;
            session.feedbackColor = minigameVisualFeedbackEnabled ? MinigameSession.FeedbackColor.RED : MinigameSession.FeedbackColor.OFF;
            session.feedbackProgress = 1.0;
            renderMinigame(session);
            return;
        }
        if (session.bossbarTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(session.bossbarTaskId);
            session.bossbarTaskId = -1;
        }
        BukkitTask lockoutTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline() || minigameSessionsByPlayer.get(player.getUniqueId()) != session) {
                if (session.bossbarTaskId >= 0) {
                    Bukkit.getScheduler().cancelTask(session.bossbarTaskId);
                    session.bossbarTaskId = -1;
                }
                return;
            }
            long remaining = Math.max(0L, result.lockoutEndsAtMs - System.currentTimeMillis());
            String timer = formatClock(remaining);
            String base = pickBroken ? "Locked Out - Pick Broke!" : "Locked Out";
            if (session.bossBar != null) {
                session.bossBar.setTitle(base + " (" + timer + ")");
            }
            double ratio = silencePenaltyResetMs <= 0 ? 0.0 : (remaining / (double) silencePenaltyResetMs);
            if (session.bossBar != null) {
                session.bossBar.setProgress(Math.max(0.0, Math.min(1.0, ratio)));
            }
            session.feedbackTitle = base + " (" + timer + ")";
            session.feedbackColor = minigameVisualFeedbackEnabled ? MinigameSession.FeedbackColor.RED : MinigameSession.FeedbackColor.OFF;
            session.feedbackProgress = 1.0;
            renderMinigame(session);
            if (remaining <= 0L) {
                if (session.bossbarTaskId >= 0) {
                    Bukkit.getScheduler().cancelTask(session.bossbarTaskId);
                    session.bossbarTaskId = -1;
                }
                if (session.bossBar != null) {
                    session.bossBar.setTitle("Turn Distance");
                    session.bossBar.setColor(BarColor.YELLOW);
                    session.bossBar.setProgress(0.0);
                }
                session.feedbackTitle = "";
                session.feedbackColor = MinigameSession.FeedbackColor.OFF;
                session.feedbackProgress = 0.0;
                renderMinigame(session);
            }
        }, 0L, 20L);
        session.bossbarTaskId = lockoutTask.getTaskId();
    }

    private String formatClock(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void flashBossBar(MinigameSession session, Player player, String title, BarColor firstColor, BarColor secondColor,
                              int flashes, Runnable onDone) {
        BossBar bar = session.bossBar;
        if (minigameBossbarEnabled && bar == null && !minigameVisualFeedbackEnabled) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        if (session.bossbarTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(session.bossbarTaskId);
            session.bossbarTaskId = -1;
        }
        if (bar != null) {
            bar.setTitle(title);
            bar.setProgress(1.0);
        }
        MinigameSession.FeedbackColor firstVisual = barColorToFeedback(firstColor);
        MinigameSession.FeedbackColor secondVisual = barColorToFeedback(secondColor);
        BukkitTask flashTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            int step = 0;

            @Override
            public void run() {
                if (!player.isOnline() || minigameSessionsByPlayer.get(player.getUniqueId()) != session) {
                    if (session.bossbarTaskId >= 0) {
                        Bukkit.getScheduler().cancelTask(session.bossbarTaskId);
                        session.bossbarTaskId = -1;
                    }
                    return;
                }
                BarColor nextBarColor = (step % 2 == 0) ? firstColor : secondColor;
                MinigameSession.FeedbackColor nextVisualColor = (step % 2 == 0) ? firstVisual : secondVisual;
                if (session.bossBar != null) {
                    session.bossBar.setColor(nextBarColor);
                }
                session.feedbackTitle = title;
                session.feedbackColor = minigameVisualFeedbackEnabled ? nextVisualColor : MinigameSession.FeedbackColor.OFF;
                session.feedbackProgress = 1.0;
                renderMinigame(session);
                step += 1;
                if (step >= Math.max(1, flashes * 2)) {
                    if (session.bossbarTaskId >= 0) {
                        Bukkit.getScheduler().cancelTask(session.bossbarTaskId);
                        session.bossbarTaskId = -1;
                    }
                    if (session.bossBar != null) {
                        session.bossBar.setColor(firstColor);
                    }
                    session.feedbackTitle = title;
                    session.feedbackColor = minigameVisualFeedbackEnabled ? firstVisual : MinigameSession.FeedbackColor.OFF;
                    session.feedbackProgress = 1.0;
                    renderMinigame(session);
                    if (onDone != null) {
                        onDone.run();
                    }
                }
            }
        }, 0L, 3L);
        session.bossbarTaskId = flashTask.getTaskId();
    }

    private MinigameSession.FeedbackColor barColorToFeedback(BarColor color) {
        if (color == null) {
            return MinigameSession.FeedbackColor.OFF;
        }
        return switch (color) {
            case RED -> MinigameSession.FeedbackColor.RED;
            case GREEN -> MinigameSession.FeedbackColor.GREEN;
            case YELLOW -> MinigameSession.FeedbackColor.YELLOW;
            default -> MinigameSession.FeedbackColor.OFF;
        };
    }

    private void playCorrectPinClicks(Player player, Location location, int correctPins) {
        if (location == null || location.getWorld() == null || correctPins <= 0) {
            return;
        }
        for (int i = 0; i < correctPins; i++) {
            long delay = i * 2L;
            float pitch = 0.85f + (Math.min(6, i) * 0.06f);
            Bukkit.getScheduler().runTaskLater(this, () ->
                            location.getWorld().playSound(location, Sound.BLOCK_TRIPWIRE_CLICK_ON, SoundCategory.MASTER, 1.0f, pitch),
                    delay);
        }
    }

    private void endMinigameSession(MinigameSession session, boolean closeInventory, Component reason) {
        if (session == null) {
            return;
        }
        minigameSessionsByContainer.remove(session.containerId, session);
        minigameSessionsByPlayer.remove(session.playerId, session);
        minigameSessionsByInventory.remove(session.inventory, session);
        if (session.timeoutTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(session.timeoutTaskId);
            session.timeoutTaskId = -1;
        }
        if (session.bossbarTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(session.bossbarTaskId);
            session.bossbarTaskId = -1;
        }
        if (session.bossBar != null) {
            session.bossBar.removeAll();
        }
        Player owner = Bukkit.getPlayer(session.playerId);
        if (owner != null && owner.isOnline()) {
            if (reason != null) {
                owner.sendMessage(reason);
            }
            if (closeInventory && owner.getOpenInventory().getTopInventory().equals(session.inventory)) {
                owner.closeInventory();
            }
        }
    }

    private void scheduleSessionTimeout(MinigameSession session) {
        if (session.timeoutTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(session.timeoutTaskId);
        }
        long timeoutTicks = Math.max(20L, minigameSessionTimeoutSeconds * 20L);
        session.timeoutTaskId = Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!minigameSessionsByPlayer.containsKey(session.playerId)) {
                return;
            }
            Player owner = Bukkit.getPlayer(session.playerId);
            Component timeoutMessage = errorLine("Lockpicking session timed out.");
            endMinigameSession(session, owner != null, timeoutMessage);
        }, timeoutTicks).getTaskId();
    }

    private void touchSession(MinigameSession session) {
        session.lastActionMs = System.currentTimeMillis();
        scheduleSessionTimeout(session);
    }

    private void applyTrialAssist(MinigameSession session) {
        if (!trialAssistEliminateOne || !"trial".equalsIgnoreCase(session.minigameData.type())) {
            return;
        }
        int pin = ThreadLocalRandom.current().nextInt(session.minigameData.pins());
        int depth = ThreadLocalRandom.current().nextInt(session.minigameData.depths());
        if (session.minigameData.secret()[pin] == depth && session.minigameData.depths() > 1) {
            depth = (depth + 1) % session.minigameData.depths();
        }
        session.eliminated[pin][depth] = true;
    }

    private boolean shouldRegenerateOnAttempt(MinigameSession session, TurnAttemptResult result) {
        if (session == null || session.minigameData == null) {
            return false;
        }
        if (result == null || (!result.lockoutHard && !result.overLimit)) {
            return false;
        }
        return switch (session.minigameData.type().toLowerCase()) {
            case "trial" -> trialRegenerateOnAttempt;
            case "ominous" -> ominousRegenerateOnAttempt;
            default -> false;
        };
    }

    private void rerollMinigameSecret(MinigameSession session) {
        LockInfo latest = getLockInfo(session.locations);
        if (latest == null || latest.minigameData() == null) {
            return;
        }
        LockMinigameData current = latest.minigameData();
        int[] secret = rollRandomSecret(current.pins(), current.depths());
        LockMinigameData rerolled = new LockMinigameData(
                current.type(),
                current.pins(),
                current.depths(),
                secret,
                System.currentTimeMillis(),
                current.saltVersion()
        );
        LockInfo updated = latest.withMinigameData(rerolled);
        updateLockInfo(session.locations, updated);
        session.minigameData = rerolled;
        for (boolean[] col : session.eliminated) {
            Arrays.fill(col, false);
        }
        Arrays.fill(session.selectedDepths, -1);
        applyTrialAssist(session);
    }

    private LockInfo ensureMinigameData(List<Location> locations, LockInfo info) {
        if (info == null) {
            return null;
        }
        if (info.minigameData() != null) {
            return info;
        }
        Location anchor = locations.isEmpty() ? null : locations.getFirst();
        if (anchor == null || anchor.getWorld() == null) {
            return info;
        }
        String type = info.normalKey() ? "trial" : "ominous";
        int pins = "trial".equals(type) ? trialPins : ominousPins;
        int depths = "trial".equals(type) ? trialDepths : ominousDepths;
        LockMinigameData generated = generateMinigameData(anchor, info.keyName(), type, pins, depths);
        LockInfo updated = info.withMinigameData(generated);
        updateLockInfo(locations, updated);
        return updated;
    }

    private LockMinigameData generateMinigameData(Location location, String keyName, String type, int pins, int depths) {
        int safePins = Math.max(1, Math.min(GRID_MAX_COLUMNS, pins));
        int safeDepths = Math.max(1, Math.min(GRID_MAX_ROWS, depths));
        byte[] seed = createPinSeed(location, keyName, type);
        Random random = new Random(bytesToLong(seed));
        int[] secret = new int[safePins];
        for (int i = 0; i < safePins; i++) {
            secret[i] = random.nextInt(safeDepths);
        }
        return new LockMinigameData(type, safePins, safeDepths, secret, System.currentTimeMillis(), minigameSaltVersion);
    }

    private int[] rollRandomSecret(int pins, int depths) {
        int safePins = Math.max(1, Math.min(GRID_MAX_COLUMNS, pins));
        int safeDepths = Math.max(1, Math.min(GRID_MAX_ROWS, depths));
        int[] secret = new int[safePins];
        for (int i = 0; i < safePins; i++) {
            secret[i] = ThreadLocalRandom.current().nextInt(safeDepths);
        }
        return secret;
    }

    private byte[] createPinSeed(Location location, String keyName, String type) {
        String worldUid = location.getWorld() == null ? "unknown" : location.getWorld().getUID().toString();
        String payload = worldUid + "|" + location.getBlockX() + "|" + location.getBlockY() + "|" + location.getBlockZ()
                + "|" + keyName + "|" + type + "|" + minigameSalt;
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
        int max = Math.min(8, bytes.length);
        for (int i = 0; i < max; i++) {
            value = (value << 8) | (bytes[i] & 0xFFL);
        }
        return value;
    }

    private String containerSessionKey(List<Location> locations) {
        if (locations == null || locations.isEmpty()) {
            return null;
        }
        List<String> keys = new ArrayList<>();
        for (Location location : locations) {
            if (location == null || location.getWorld() == null) {
                continue;
            }
            keys.add(locationKey(location));
        }
        if (keys.isEmpty()) {
            return null;
        }
        keys.sort(String::compareTo);
        return String.join("|", keys);
    }

    private boolean hasHeldPickType(Player player, PickType type) {
        if (player == null || type == null) {
            return false;
        }
        PickType main = getPickType(player.getInventory().getItemInMainHand());
        if (main == type) {
            return true;
        }
        PickType off = getPickType(player.getInventory().getItemInOffHand());
        return off == type;
    }

    private String buildMinigameAttemptDetail(MinigameSession session, TurnAttemptResult result) {
        StringBuilder detail = new StringBuilder("pick=")
                .append(session.pickType.id)
                .append(" mode=minigame")
                .append(" combo=").append(formatAttemptedCombo(session))
                .append(" actual=").append(formatActualCombo(session))
                .append(" limit=").append(result.attemptsAfter).append("/").append(result.limitValue);
        if (result.attemptsAfter < result.limitValue) {
            detail.append(" untilLimit=").append(result.limitValue - result.attemptsAfter);
        } else if (result.attemptsAfter == result.limitValue) {
            detail.append(" atLimit=true");
        } else {
            detail.append(" overBy=").append(result.attemptsAfter - result.limitValue);
        }
        if (result.overLimit) {
            detail.append(" overLimit=true");
        }
        if (result.lockoutEndsAtMs > 0L) {
            long remaining = Math.max(0L, result.lockoutEndsAtMs - System.currentTimeMillis());
            detail.append(" penaltyResetIn=").append(formatDuration(remaining));
        }
        return detail.toString();
    }

    private String formatAttemptedCombo(MinigameSession session) {
        if (session == null || session.selectedDepths == null) {
            return "-";
        }
        StringBuilder combo = new StringBuilder();
        for (int i = 0; i < session.selectedDepths.length; i++) {
            if (i > 0) {
                combo.append("-");
            }
            int depth = session.selectedDepths[i];
            combo.append(depth >= 0 ? (depth + 1) : "?");
        }
        return combo.toString();
    }

    private String formatActualCombo(MinigameSession session) {
        if (session == null || session.minigameData == null) {
            return "-";
        }
        int[] secret = session.minigameData.secret();
        StringBuilder combo = new StringBuilder();
        for (int i = 0; i < secret.length; i++) {
            if (i > 0) {
                combo.append("-");
            }
            combo.append(secret[i] + 1);
        }
        return combo.toString();
    }

    private void handlePickAttempt(PlayerInteractEvent event, Block block, LockInfo lockInfo, PickMatch pickMatch) {
        if (!allowLockpicks) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(errorLine("Lockpicking is disabled."));
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
                long penaltyTimestamp = state.silencePenaltyTimestamp();
                boolean penaltyExpired = penaltyTimestamp > 0
                        && now - penaltyTimestamp >= silencePenaltyResetMs;
                if (penaltyExpired) {
                    overLimitAttempts = 0;
                    penaltyTimestamp = 0L;
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
                    if (penaltyTimestamp <= 0L) {
                        penaltyTimestamp = now;
                    }
                    state = state.withSilenceOverLimitAttempts(overLimitAttempts).withSilencePenaltyTimestamp(penaltyTimestamp);
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

    private void openUnlockedContainer(Player player, Block block) {
        if (player == null || block == null) {
            return;
        }
        if (!(block.getState() instanceof InventoryHolder holder)) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.openInventory(holder.getInventory());
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
                            if (minigameEnabled) {
                                handleMinigameOpen(event, block, existingLock, pickMatch);
                            } else {
                                handlePickAttempt(event, block, existingLock, pickMatch);
                            }
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
        MinigameSession minigameSession = minigameSessionsByInventory.get(event.getView().getTopInventory());
        if (minigameSession != null) {
            event.setCancelled(true);
            if (!player.getUniqueId().equals(minigameSession.playerId)) {
                return;
            }
            handleMinigameClick(player, minigameSession, event.getRawSlot(), event.isLeftClick(), event.isRightClick());
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
        MinigameSession session = minigameSessionsByInventory.get(event.getView().getTopInventory());
        if (session != null) {
            event.setCancelled(true);
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
                -1, 0, -1, 0, -1, 0, 0, 0L, new HashMap<>(), null);
        for (Location location : locations) {
            lockedChests.put(locationKey(location), info);
        }
        keyToChest.putIfAbsent(keyName, locationKeys.iterator().next());
        saveData();
        return true;
    }

    private void unlock(Block block, String keyName) {
        List<Location> locations = resolveLockLocations(block);
        String containerId = containerSessionKey(locations);
        if (containerId != null) {
            MinigameSession session = minigameSessionsByContainer.get(containerId);
            if (session != null) {
                endMinigameSession(session, true, null);
            }
        }
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
        rustyBreakChance = clampChance(getConfig().getDouble("lockpicks.rusty.break-chance", 0.88));
        rustyDamage = Math.max(0.0, getConfig().getDouble("lockpicks.rusty.damage", 1.0));
        normalOpenChance = clampChance(getConfig().getDouble("lockpicks.normal.open-chance", 0.10));
        normalNormalKeyChance = clampChance(getConfig().getDouble("lockpicks.normal.normal-key-chance", 0.20));
        normalBreakChance = clampChance(getConfig().getDouble("lockpicks.normal.break-chance", 0.33));
        normalDamage = Math.max(0.0, getConfig().getDouble("lockpicks.normal.damage", 2.0));
        silenceOpenChance = clampChance(getConfig().getDouble("lockpicks.silence.open-chance", 0.50));
        silenceBreakChance = clampChance(getConfig().getDouble("lockpicks.silence.break-chance", 0.05));
        silenceDamage = Math.max(0.0, getConfig().getDouble("lockpicks.silence.damage", 4.0));
        long resetMinutes = getConfig().getLong("lockpicks.silence.penalty-reset-minutes", 60L);
        silencePenaltyResetMs = Math.max(1L, resetMinutes) * 60L * 1000L;
        lockoutScope = LockoutScope.fromConfig(getConfig().getString("lockpicks.lockout-scope", "chest"));
        minigameEnabled = getConfig().getBoolean("lockpicks.minigame.enabled", true);
        trialPins = Math.max(1, Math.min(GRID_MAX_COLUMNS, getConfig().getInt("lockpicks.minigame.trial.pins", 4)));
        trialDepths = Math.max(1, Math.min(GRID_MAX_ROWS, getConfig().getInt("lockpicks.minigame.trial.depths", 4)));
        ominousPins = Math.max(1, Math.min(GRID_MAX_COLUMNS, getConfig().getInt("lockpicks.minigame.ominous.pins", 6)));
        ominousDepths = Math.max(1, Math.min(GRID_MAX_ROWS, getConfig().getInt("lockpicks.minigame.ominous.depths", 5)));
        minigameSessionTimeoutSeconds = Math.max(30, getConfig().getInt("lockpicks.minigame.session-timeout-seconds", 90));
        minigameBossbarEnabled = getConfig().getBoolean("lockpicks.minigame.bossbar.enabled", true);
        minigameBossbarAnimateTicks = Math.max(1, getConfig().getInt("lockpicks.minigame.bossbar.animate-ticks", 12));
        minigameBossbarSnapbackDelayTicks = Math.max(0, getConfig().getInt("lockpicks.minigame.bossbar.snapback-delay-ticks", 20));
        minigameBossbarPeakHoldTicks = Math.max(0, getConfig().getInt("lockpicks.minigame.bossbar.peak-hold-ticks", 20));
        minigameVisualFeedbackEnabled = getConfig().getBoolean("lockpicks.minigame.visual-feedback.enabled", true);
        minigameVisualFeedbackRenameTitle = getConfig().getBoolean("lockpicks.minigame.visual-feedback.rename-inventory-title", true);
        minigamePinIcon = parseItemMaterial(getConfig().getString("lockpicks.minigame.ui.pin-icon"), Material.END_ROD);
        minigameClickPerCorrectPin = getConfig().getBoolean("lockpicks.minigame.sounds.click-per-correct-pin", true);
        minigameRequireHoldingPick = getConfig().getBoolean("lockpicks.minigame.security.require-holding-pick", true);
        minigameSalt = getConfig().getString("lockpicks.minigame.salt", "change-me");
        if (minigameSalt == null || minigameSalt.isBlank()) {
            minigameSalt = "change-me";
        }
        minigameSaltVersion = Math.max(1, getConfig().getInt("lockpicks.minigame.salt-version", 1));
        trialAssistEliminateOne = getConfig().getBoolean("lockpicks.minigame.trial.assist-eliminate-one", true);
        trialRegenerateOnAttempt = getConfig().getBoolean("lockpicks.minigame.trial.regenerate-on-attempt", false);
        ominousRegenerateOnAttempt = getConfig().getBoolean("lockpicks.minigame.ominous.regenerate-on-attempt", true);
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

    private Material parseItemMaterial(String value, Material fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(value);
        if (material == null || material.isAir()) {
            return fallback;
        }
        return material;
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
        for (MinigameSession session : new ArrayList<>(minigameSessionsByPlayer.values())) {
            endMinigameSession(session, true, errorLine("Lock data reloaded."));
        }
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
            LockMinigameData minigameData = null;

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
                    ConfigurationSection minigameSection = lockSection.getConfigurationSection("minigame");
                    if (minigameSection != null) {
                        String type = minigameSection.getString("type");
                        int pins = minigameSection.getInt("pins", 0);
                        int depths = minigameSection.getInt("depths", 0);
                        List<Integer> secretList = minigameSection.getIntegerList("secret");
                        int safePins = Math.max(1, Math.min(GRID_MAX_COLUMNS, pins));
                        int safeDepths = Math.max(1, Math.min(GRID_MAX_ROWS, depths));
                        int[] secret = new int[Math.min(secretList.size(), safePins)];
                        boolean oneBased = true;
                        for (Integer value : secretList) {
                            if (value == null || value <= 0) {
                                oneBased = false;
                                break;
                            }
                        }
                        for (int i = 0; i < secret.length; i++) {
                            int raw = secretList.get(i);
                            int normalized = oneBased ? (raw - 1) : raw;
                            secret[i] = Math.max(0, Math.min(safeDepths - 1, normalized));
                        }
                        long created = minigameSection.getLong("created", 0L);
                        int saltVersion = minigameSection.getInt("salt-version", 1);
                        if (type != null && !type.isBlank() && secret.length == safePins) {
                            minigameData = new LockMinigameData(type, safePins, safeDepths, secret, created, saltVersion);
                        }
                    }
                }
            }

            if (keyName == null || keyName.isBlank()) {
                continue;
            }
            LockInfo info = new LockInfo(keyName, creatorName, creatorUuid, lastUserName, lastUserUuid, normalKey, normalArmed,
                    lastPickUserName, lastPickUserUuid, lastPickType, lastPickTimestamp,
                    rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                    silenceOverLimitAttempts, silencePenaltyTimestamp, playerPickStates, minigameData);
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
            if (info.minigameData() != null) {
                LockMinigameData mg = info.minigameData();
                lockSection.set("minigame.type", mg.type());
                lockSection.set("minigame.pins", mg.pins());
                lockSection.set("minigame.depths", mg.depths());
                List<Integer> secretList = new ArrayList<>(mg.pins());
                for (int value : mg.secret()) {
                    secretList.add(Math.max(1, value + 1));
                }
                lockSection.set("minigame.secret", secretList);
                lockSection.set("minigame.created", mg.createdTimestamp());
                lockSection.set("minigame.salt-version", mg.saltVersion());
            }
        }
        try {
            config.save(dataFile);
        } catch (IOException exception) {
            getLogger().warning("Could not save data.yml: " + exception.getMessage());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        MinigameSession session = minigameSessionsByInventory.get(event.getInventory());
        if (session == null) {
            return;
        }
        endMinigameSession(session, false, null);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        MinigameSession session = minigameSessionsByPlayer.get(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        endMinigameSession(session, false, null);
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

    private record TurnAttemptResult(boolean success,
                                     boolean overLimit,
                                     boolean lockoutHard,
                                     boolean lockoutDisplay,
                                     long lockoutEndsAtMs,
                                     int shownCorrectPins,
                                     boolean playPinClicks,
                                     boolean feedbackObfuscated,
                                     int attemptsAfter,
                                     int limitValue,
                                     double damageOnFail,
                                     double breakChance) {
    }

}

