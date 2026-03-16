package net.ozanarchy.chestlock.commands;

import net.kyori.adventure.text.Component;
import net.ozanarchy.chestlock.ChestLockPlugin;
import net.ozanarchy.chestlock.config.ConfigManager;
import net.ozanarchy.chestlock.lock.LockService;
import net.ozanarchy.chestlock.lock.LockoutScope;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ConfigCommand implements CommandExecutor, TabCompleter {

    private final ChestLockPlugin plugin;
    private final ConfigManager configManager;
    private final LockService lockService;

    public ConfigCommand(ChestLockPlugin plugin, ConfigManager configManager, LockService lockService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.lockService = lockService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chestlock.admin")) {
            sender.sendMessage(Component.text("You do not have permission."));
            return true;
        }

        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "loglevel" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Current log level: " + configManager.getLogLevel()));
                    sender.sendMessage(Component.text("Usage: /chestlock loglevel <0-3>"));
                    return true;
                }
                try {
                    int level = Integer.parseInt(args[1]);
                    if (level < 0 || level > 3) {
                        sender.sendMessage(Component.text("Log level must be between 0 and 3."));
                        return true;
                    }
                    configManager.setLogLevel(level);
                    lockService.setConfigValues( // Update LockService with new log level
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
                    sender.sendMessage(Component.text("Logging level set to " + level + "."));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(Component.text("Log level must be a number between 0 and 3."));
                }
                return true;
            }
            case "normalkeys" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Normal trial keys are " + (configManager.getAllowNormalKeys() ? "enabled." : "disabled.")));
                    sender.sendMessage(Component.text("Usage: /chestlock normalkeys <on|off>"));
                    return true;
                }
                String value = args[1].toLowerCase();
                if (!value.equals("on") && !value.equals("off")) {
                    sender.sendMessage(Component.text("Usage: /chestlock normalkeys <on|off>"));
                    return true;
                }
                configManager.setAllowNormalKeys(value.equals("on"));
                lockService.setConfigValues( // Update LockService
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
                sender.sendMessage(Component.text("Normal trial keys are now " + (configManager.getAllowNormalKeys() ? "enabled." : "disabled.")));
                return true;
            }
            case "lockpicks" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Lockpicking is " + (configManager.getAllowLockpicks() ? "enabled." : "disabled.")));
                    sender.sendMessage(Component.text("Usage: /chestlock lockpicks <on|off>"));
                    return true;
                }
                String value = args[1].toLowerCase();
                if (!value.equals("on") && !value.equals("off")) {
                    sender.sendMessage(Component.text("Usage: /chestlock lockpicks <on|off>"));
                    return true;
                }
                configManager.setAllowLockpicks(value.equals("on"));
                lockService.setConfigValues( // Update LockService
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
                lockService.updatePickRecipes(); // Recipes depend on allowLockpicks
                sender.sendMessage(Component.text("Lockpicking is now " + (configManager.getAllowLockpicks() ? "enabled." : "disabled.")));
                return true;
            }
            case "lockoutscope" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Lockout scope is " + configManager.getLockoutScope().name().toLowerCase() + "."));
                    sender.sendMessage(Component.text("Usage: /chestlock lockoutscope <chest|player>"));
                    return true;
                }
                String value = args[1].toLowerCase();
                LockoutScope scope = LockoutScope.fromConfig(value);
                if (scope == null) { // fromConfig returns null for invalid values
                    sender.sendMessage(Component.text("Usage: /chestlock lockoutscope <chest|player>"));
                    return true;
                }
                configManager.setLockoutScope(scope);
                lockService.setConfigValues( // Update LockService
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
                sender.sendMessage(Component.text("Lockout scope set to " + scope.name().toLowerCase() + "."));
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /chestlock loglevel <0-3> - set log verbosity"));
        sender.sendMessage(Component.text("Usage: /chestlock normalkeys <on|off> - allow normal trial keys"));
        sender.sendMessage(Component.text("Usage: /chestlock lockpicks <on|off> - allow lock picking and crafting"));
        sender.sendMessage(Component.text("Usage: /chestlock lockoutscope <chest|player> - set lockout scope"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("chestlock.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return List.of("loglevel", "normalkeys", "lockpicks", "lockoutscope");
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "loglevel" -> List.of("0", "1", "2", "3");
                case "normalkeys", "lockpicks" -> List.of("on", "off");
                case "lockoutscope" -> List.of("chest", "player");
                default -> List.of();
            };
        }
        return List.of();
    }
}
