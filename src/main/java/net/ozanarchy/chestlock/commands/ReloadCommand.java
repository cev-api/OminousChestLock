package net.ozanarchy.chestlock.commands;

import net.kyori.adventure.text.Component;
import net.ozanarchy.chestlock.ChestLockPlugin;
import net.ozanarchy.chestlock.config.ConfigManager;
import net.ozanarchy.chestlock.config.DataStore;
import net.ozanarchy.chestlock.lock.LockService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReloadCommand implements CommandExecutor {

    private final ChestLockPlugin plugin;
    private final ConfigManager configManager;
    private final DataStore dataStore;
    private final LockService lockService;

    public ReloadCommand(ChestLockPlugin plugin, ConfigManager configManager, DataStore dataStore, LockService lockService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dataStore = dataStore;
        this.lockService = lockService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chestlock.admin")) {
            sender.sendMessage(Component.text("You do not have permission."));
            return true;
        }

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
        sender.sendMessage(Component.text("ChestLock data reloaded."));
        return true;
    }
}
