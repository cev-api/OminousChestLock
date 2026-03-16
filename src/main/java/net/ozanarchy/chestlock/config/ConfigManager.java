package net.ozanarchy.chestlock.config;

import net.ozanarchy.chestlock.ChestLockPlugin;
import net.ozanarchy.chestlock.lock.LockoutScope;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private final ChestLockPlugin plugin;
    private FileConfiguration config;

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
    private int logLevel;
    private boolean minigameEnabled;
    private int minigameTrialPins;
    private int minigameTrialDepths;
    private int minigameOminousPins;
    private int minigameOminousDepths;
    private String minigamePinIcon;

    private java.util.List<String> rustyRecipe;
    private java.util.List<String> normalRecipe;
    private java.util.List<String> lodestoneRecipe;
    private String silenceSmithingTemplate;
    private String silenceSmithingBase;
    private String silenceSmithingAddition;

    public ConfigManager(ChestLockPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        pickLimitMin = config.getInt("lockpicks.pick-limit-min", 2);
        pickLimitMax = config.getInt("lockpicks.pick-limit-max", 5);
        rustyNormalKeyChance = config.getDouble("lockpicks.rusty.normal-key-chance", 0.05);
        rustyOpenChance = config.getDouble("lockpicks.rusty.open-chance", 0.1);
        normalNormalKeyChance = config.getDouble("lockpicks.normal.normal-key-chance", 0.15);
        normalOpenChance = config.getDouble("lockpicks.normal.open-chance", 0.25);
        silenceOpenChance = config.getDouble("lockpicks.silence.open-chance", 0.35);
        rustyDamage = config.getDouble("lockpicks.rusty.damage", 1.0);
        normalDamage = config.getDouble("lockpicks.normal.damage", 2.0);
        silenceDamage = config.getDouble("lockpicks.silence.damage", 3.0);
        rustyBreakChance = config.getDouble("lockpicks.rusty.break-chance", 0.5);
        normalBreakChance = config.getDouble("lockpicks.normal.break-chance", 0.3);
        silenceBreakChance = config.getDouble("lockpicks.silence.break-chance", 0.1);
        lodestoneOpenChance = config.getDouble("lockpicks.lodestone.open-chance", 0.2);
        lodestoneDamage = config.getDouble("lockpicks.lodestone.damage", 1.5);
        lodestoneBreakChance = config.getDouble("lockpicks.lodestone.break-chance", 0.2);
        silencePenaltyResetMs = config.getLong("lockpicks.silence.penalty-reset-ms", 15 * 60 * 1000L);
        lockoutScope = LockoutScope.fromConfig(config.getString("lockpicks.lockout-scope", "chest"));
        allowLockpicks = config.getBoolean("lockpicks.enabled", true);
        allowNormalKeys = config.getBoolean("keys.allow-normal", true);
        logLevel = config.getInt("logging.level", 1);
        minigameEnabled = config.getBoolean("lockpicks.minigame.enabled", true);
        minigameTrialPins = Math.max(1, config.getInt("lockpicks.minigame.trial.pins", 4));
        minigameTrialDepths = Math.min(5, Math.max(1, config.getInt("lockpicks.minigame.trial.depths", 4)));
        minigameOminousPins = Math.max(1, config.getInt("lockpicks.minigame.ominous.pins", 6));
        minigameOminousDepths = Math.min(5, Math.max(1, config.getInt("lockpicks.minigame.ominous.depths", 5)));
        minigamePinIcon = config.getString("lockpicks.minigame.ui.pin-icon", "END_ROD");

        rustyRecipe = config.getStringList("lockpicks.rusty.recipe");
        if (rustyRecipe.isEmpty()) {
            rustyRecipe = java.util.List.of("COPPER_INGOT", "TRIPWIRE_HOOK", "STICK");
        }
        normalRecipe = config.getStringList("lockpicks.normal.recipe");
        if (normalRecipe.isEmpty()) {
            normalRecipe = java.util.List.of("IRON_INGOT", "TRIPWIRE_HOOK", "BREEZE_ROD");
        }
        lodestoneRecipe = config.getStringList("lockpicks.lodestone.recipe");
        if (lodestoneRecipe.isEmpty()) {
            lodestoneRecipe = java.util.List.of("IRON_INGOT", "TRIPWIRE_HOOK", "LODESTONE");
        }
        silenceSmithingTemplate = config.getString("lockpicks.silence.smithing.template", "SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE");
        silenceSmithingBase = config.getString("lockpicks.silence.smithing.base", "normal_pick");
        silenceSmithingAddition = config.getString("lockpicks.silence.smithing.addition", "ECHO_SHARD");
    }

    public void setAllowNormalKeys(boolean allowNormalKeys) {
        this.allowNormalKeys = allowNormalKeys;
        config.set("keys.allow-normal", allowNormalKeys);
        plugin.saveConfig();
    }

    public void setAllowLockpicks(boolean allowLockpicks) {
        this.allowLockpicks = allowLockpicks;
        config.set("lockpicks.enabled", allowLockpicks);
        plugin.saveConfig();
    }

    public void setLockoutScope(LockoutScope lockoutScope) {
        this.lockoutScope = lockoutScope;
        config.set("lockpicks.lockout-scope", lockoutScope.name().toLowerCase());
        plugin.saveConfig();
    }

    public void setLogLevel(int logLevel) {
        this.logLevel = logLevel;
        config.set("logging.level", logLevel);
        plugin.saveConfig();
    }

    public void setMinigameEnabled(boolean enabled) {
        this.minigameEnabled = enabled;
        config.set("lockpicks.minigame.enabled", enabled);
        plugin.saveConfig();
    }


    // Getters for all config values
    public int getPickLimitMin() { return pickLimitMin; }
    public int getPickLimitMax() { return pickLimitMax; }
    public double getRustyNormalKeyChance() { return rustyNormalKeyChance; }
    public double getRustyOpenChance() { return rustyOpenChance; }
    public double getNormalNormalKeyChance() { return normalNormalKeyChance; }
    public double getNormalOpenChance() { return normalOpenChance; }
    public double getSilenceOpenChance() { return silenceOpenChance; }
    public double getRustyDamage() { return rustyDamage; }
    public double getNormalDamage() { return normalDamage; }
    public double getSilenceDamage() { return silenceDamage; }
    public double getRustyBreakChance() { return rustyBreakChance; }
    public double getNormalBreakChance() { return normalBreakChance; }
    public double getSilenceBreakChance() { return silenceBreakChance; }
    public double getLodestoneOpenChance() { return lodestoneOpenChance; }
    public double getLodestoneDamage() { return lodestoneDamage; }
    public double getLodestoneBreakChance() { return lodestoneBreakChance; }
    public long getSilencePenaltyResetMs() { return silencePenaltyResetMs; }
    public LockoutScope getLockoutScope() { return lockoutScope; }
    public boolean getAllowLockpicks() { return allowLockpicks; }
    public boolean getAllowNormalKeys() { return allowNormalKeys; }
    public int getLogLevel() { return logLevel; }
    public boolean getMinigameEnabled() { return minigameEnabled; }
    public int getMinigameTrialPins() { return minigameTrialPins; }
    public int getMinigameTrialDepths() { return minigameTrialDepths; }
    public int getMinigameOminousPins() { return minigameOminousPins; }
    public int getMinigameOminousDepths() { return minigameOminousDepths; }
    public String getMinigamePinIcon() { return minigamePinIcon; }

    public java.util.List<String> getRustyRecipe() { return rustyRecipe; }
    public java.util.List<String> getNormalRecipe() { return normalRecipe; }
    public java.util.List<String> getLodestoneRecipe() { return lodestoneRecipe; }
    public String getSilenceSmithingTemplate() { return silenceSmithingTemplate; }
    public String getSilenceSmithingBase() { return silenceSmithingBase; }
    public String getSilenceSmithingAddition() { return silenceSmithingAddition; }
}
