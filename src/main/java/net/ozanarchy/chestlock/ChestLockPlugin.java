package net.ozanarchy.chestlock;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
// import org.bukkit.command.TabCompleter; // Removed as it's not needed directly here
import java.util.List;
// import java.util.Arrays; // Not used
import java.lang.reflect.Field;
import org.bukkit.plugin.java.JavaPlugin;
import net.ozanarchy.chestlock.config.ConfigManager;
import net.ozanarchy.chestlock.config.DataStore;
import net.ozanarchy.chestlock.lock.LockService;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ChestLockPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private DataStore dataStore;
    private LockService lockService;

    // Temporary maps to pass to LockService and DataStore during initialization
    // These will eventually be managed fully by DataStore/LockService
    private final Map<String, net.ozanarchy.chestlock.lock.LockInfo> lockedChests = new HashMap<>();
    private final Map<String, String> keyToChest = new HashMap<>();
    private final Map<String, Long> logCooldowns = new HashMap<>();
    private final Map<String, net.ozanarchy.chestlock.model.PendingIgnite> tntIgnites = new HashMap<>();
    private final Map<UUID, String> tntSources = new HashMap<>();
    private final Map<UUID, net.ozanarchy.chestlock.model.PendingIgnite> crystalSources = new HashMap<>();
    private final Map<String, net.ozanarchy.chestlock.model.HopperOwner> hopperOwners = new HashMap<>();

    @Override
    public void onEnable() {
        // Initialize config and data files
        File dataFile = new File(getDataFolder(), "data.yml");
        saveDefaultConfig(); // Save default config.yml if not present

        // Initialize managers and services
        this.configManager = new ConfigManager(this); // Pass plugin instance
        this.dataStore = new DataStore(this, dataFile, lockedChests, keyToChest, hopperOwners); // Pass plugin, file, and maps
        this.lockService = new LockService(this, lockedChests, keyToChest, hopperOwners, logCooldowns, tntIgnites, tntSources, crystalSources);

        // Load config and data
        configManager.loadConfig();
        dataStore.loadData();
        lockService.setConfigValues(
                configManager.getPickLimitMin(),
                configManager.getPickLimitMax(),
                configManager.getRustyNormalKeyChance(),
                configManager.getRustyOpenChance(),
                configManager.getNormalNormalKeyChance(),
                configManager.getNormalOpenChance(),
                configManager.getSilenceOpenChance(),
                configManager.getRustyDamage(),
                configManager.getNormalDamage(),
                configManager.getSilenceDamage(),
                configManager.getRustyBreakChance(),
                configManager.getNormalBreakChance(),
                configManager.getSilenceBreakChance(),
                configManager.getLodestoneOpenChance(),
                configManager.getLodestoneDamage(),
                configManager.getLodestoneBreakChance(),
                configManager.getSilencePenaltyResetMs(),
                configManager.getLockoutScope(),
                configManager.getAllowLockpicks(),
                configManager.getAllowNormalKeys(),
                configManager.getLogLevel(),
                configManager.getMinigameEnabled(),
                configManager.getMinigameTrialPins(),
                configManager.getMinigameTrialDepths(),
                configManager.getMinigameOminousPins(),
                configManager.getMinigameOminousDepths(),
                configManager.getMinigamePinIcon()
        );

        lockService.updatePickRecipes();

        // Register commands
        net.ozanarchy.chestlock.commands.CommandManager commandManager = new net.ozanarchy.chestlock.commands.CommandManager(this);
        commandManager.registerCommand("info", new net.ozanarchy.chestlock.commands.LockCommand(this, lockService), null); // LockCommand handles info
        commandManager.registerCommand("lock", new net.ozanarchy.chestlock.commands.AdminLockCommand(this, lockService), null);
        commandManager.registerCommand("unlock", new net.ozanarchy.chestlock.commands.UnlockCommand(this, lockService), null);
        commandManager.registerCommand("keyinfo", new net.ozanarchy.chestlock.commands.KeyCommand(this, lockService), null);
        commandManager.registerCommand("reload", new net.ozanarchy.chestlock.commands.ReloadCommand(this, configManager, dataStore, lockService), null);
        commandManager.registerCommand("loglevel", new net.ozanarchy.chestlock.commands.ConfigCommand(this, configManager, lockService), new net.ozanarchy.chestlock.commands.ConfigCommand(this, configManager, lockService));
        commandManager.registerCommand("normalkeys", new net.ozanarchy.chestlock.commands.ConfigCommand(this, configManager, lockService), new net.ozanarchy.chestlock.commands.ConfigCommand(this, configManager, lockService));
        commandManager.registerCommand("lockpicks", new net.ozanarchy.chestlock.commands.ConfigCommand(this, configManager, lockService), new net.ozanarchy.chestlock.commands.ConfigCommand(this, configManager, lockService));
        commandManager.registerCommand("lockoutscope", new net.ozanarchy.chestlock.commands.ConfigCommand(this, configManager, lockService), new net.ozanarchy.chestlock.commands.ConfigCommand(this, configManager, lockService));
        commandManager.registerCommand("give", new net.ozanarchy.chestlock.commands.GiveCommand(this), new net.ozanarchy.chestlock.commands.GiveCommand(this));
        commandManager.registerCommand("help", new net.ozanarchy.chestlock.commands.HelpCommand(this), null);

        // Programmatically register the main "chestlock" command
        try {
            final Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());

            // Create a custom Command that wraps our CommandManager
            Command chestLockCommand = new Command("chestlock") {
                @Override
                public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                    return commandManager.onCommand(sender, this, commandLabel, args);
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    // The 'command' parameter in onTabComplete for org.bukkit.command.Command is 'this'.
                    // The TabCompleter.onTabComplete signature expects an org.bukkit.command.Command as the second parameter.
                    // We must pass the current Command instance ('this') to commandManager.onTabComplete.
                    // The 'command' parameter of this method is the 'alias' in the CommandManager's onTabComplete.
                    return commandManager.onTabComplete(sender, this, alias, args);
                }
            };
            chestLockCommand.setDescription("Main command for OminousChestLock plugin.");
            chestLockCommand.setUsage("/<command> [subcommand]");
            chestLockCommand.setPermission("ominouschestlock.command");
            chestLockCommand.setPermissionMessage("You do not have permission to use this command.");

            commandMap.register(this.getName(), chestLockCommand);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            getLogger().severe("Failed to programmatically register command 'chestlock': " + e.getMessage());
            // It's crucial to handle this error appropriately, perhaps disable the plugin if commands are essential
        }

        // Register event listeners
        getServer().getPluginManager().registerEvents(new net.ozanarchy.chestlock.events.ChestInteractListener(this, lockService), this);
        getServer().getPluginManager().registerEvents(new net.ozanarchy.chestlock.events.BlockBreakListener(this, lockService), this);
        getServer().getPluginManager().registerEvents(new net.ozanarchy.chestlock.events.HopperListener(this, lockService), this);
        getServer().getPluginManager().registerEvents(new net.ozanarchy.chestlock.events.BlockProtectionListener(this, lockService), this);
        getServer().getPluginManager().registerEvents(new net.ozanarchy.chestlock.events.PlayerQuitListener(this, lockService), this);
    }

    @Override
    public void onDisable() {
        dataStore.saveData();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LockService getLockService() {
        return lockService;
    }
}
