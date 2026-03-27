
package com.ominouschestlock.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SignItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class LockService {
    private static final Logger LOGGER = LoggerFactory.getLogger("OminousChestLock");
    private static final String CONFIG_FILE_NAME = "config.yml";
    private static final String DATA_FILE_NAME = "data.yml";
    private static final String CONFIG_DIR_NAME = "ominouschestlock";
    private static final String PICK_NBT_KEY = "lock_pick_type";

    private static final long SILENCE_PENALTY_RESET_MS = 60L * 60L * 1000L;
    private static final int RUSTY_MODEL_DATA = 11001;
    private static final int NORMAL_MODEL_DATA = 11002;
    private static final int SILENCE_MODEL_DATA = 11003;

    private final Map<String, LockInfo> lockedChests = new HashMap<>();
    private final Map<String, String> keyToChest = new HashMap<>();
    private final Map<String, Long> logCooldowns = new HashMap<>();
    private final Map<String, Long> playerMessageCooldowns = new HashMap<>();
    private final Map<UUID, String> tntSources = new HashMap<>();
    private final Map<UUID, PendingIgnite> crystalSources = new HashMap<>();
    private final Map<String, HopperOwner> hopperOwners = new HashMap<>();
    private final Map<String, Set<UUID>> openViewers = new HashMap<>();
    private final Map<UUID, String> openLockByPlayer = new HashMap<>();

    private int logLevel = 1;
    private boolean allowNormalKeys = false;
    private boolean allowLockpicks = true;
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
    private boolean verboseMessages = false;

    private final Yaml yaml = new Yaml();
    private Path configDir;
    private Path configFile;
    private Path dataFile;
    private MinecraftServer server;

    public void onServerStarted(MinecraftServer server) {
        this.server = server;
        configDir = server.getServerDirectory().resolve("config").resolve(CONFIG_DIR_NAME);
        configFile = configDir.resolve(CONFIG_FILE_NAME);
        dataFile = configDir.resolve(DATA_FILE_NAME);
        loadConfigValues();
        loadData();
    }
    public InteractionResult onUseBlock(Player player, Level world, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (world.isClientSide()) {
            return InteractionResult.PASS;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (!isLockable(state)) {
            return InteractionResult.PASS;
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        List<BlockPos> locations = resolveLockLocations(world, pos, state);
        LockInfo existingLock = getLockInfo(world, locations);

        if (existingLock != null) {
            String heldKeyName = getHeldKeyName(serverPlayer);
            if (heldKeyName == null || !existingLock.keyName().equals(heldKeyName)) {
                PickMatch pickMatch = findHeldPick(serverPlayer);
                if (pickMatch != null) {
                    handlePickAttempt(serverPlayer, world, pos, existingLock, pickMatch);
                    return InteractionResult.FAIL;
                }
                ItemStack held = serverPlayer.getItemInHand(hand);
                if (isDecorationItem(held)) {
                    return InteractionResult.PASS;
                }
                playFail(serverPlayer, world, pos);
                if (heldKeyName == null) {
                    serverPlayer.sendSystemMessage(errorLine("This chest is locked. You need the correct key."), false);
                } else {
                    serverPlayer.sendSystemMessage(errorLine("Wrong key."), false);
                }
                logLockEvent("INTERACT_DENY", serverPlayer.getName().getString(), heldKeyName, world, pos, existingLock, null);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        }

        KeyMatch heldKey = findAnyHeldKey(serverPlayer);
        String heldKeyName = heldKey == null ? null : heldKey.name();
        if (heldKeyName == null) {
            return InteractionResult.PASS;
        }

        if (!tryLock(world, locations, heldKeyName, serverPlayer, heldKey.normal())) {
            playFail(serverPlayer, world, pos);
            logLockEvent("LOCK_DENY", serverPlayer.getName().getString(), heldKeyName, world, pos, null,
                    "container already locked with another key");
            return InteractionResult.FAIL;
        }

        playInsert(serverPlayer, world, pos);
        if (verboseMessages) {
            serverPlayer.sendSystemMessage(successLine("Chest locked to key: " + heldKeyName), false);
        }
        logLockEvent("LOCK_CREATED", serverPlayer.getName().getString(), heldKeyName, world, pos,
                getLockInfo(world, locations), null);
        return InteractionResult.PASS;
    }

    public boolean onBlockBreak(Level world, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (world.isClientSide()) {
            return true;
        }
        if (!isLockable(state)) {
            if (state.is(Blocks.HOPPER)) {
                hopperOwners.remove(locationKey(world, pos));
            }
            return true;
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        List<BlockPos> locations = resolveLockLocations(world, pos, state);
        LockInfo lockInfo = getLockInfo(world, locations);
        if (lockInfo == null) {
            return true;
        }

        KeyMatch keyMatch = findHeldKey(serverPlayer, lockInfo.keyName());
        String heldKeyName = keyMatch == null ? null : keyMatch.name();
        if (heldKeyName == null || !lockInfo.keyName().equals(heldKeyName)) {
            playFail(serverPlayer, (ServerLevel) world, pos);
            logLockEvent("BREAK_DENY", serverPlayer.getName().getString(), heldKeyName, world, pos, lockInfo, null);
            if (shouldSendPlayerMessage(serverPlayer.getUUID(), "break-deny:" + locationKey(world, pos), 1250L)) {
                serverPlayer.sendSystemMessage(errorLine("This container is locked and cannot be broken."), false);
            }
            return false;
        }

        unlock(world, locations, lockInfo.keyName());
        if (verboseMessages && shouldSendPlayerMessage(serverPlayer.getUUID(), "break-ok:" + locationKey(world, pos), 1250L)) {
            serverPlayer.sendSystemMessage(successLine("Chest unlocked with key: " + heldKeyName), false);
        }
        logLockEvent("BREAK_ALLOWED", serverPlayer.getName().getString(), heldKeyName, world, pos, lockInfo, null);
        return true;
    }

    public void onExplosion(Level world, net.minecraft.world.level.Explosion explosion, List<BlockPos> affectedBlocks) {
        if (!(world instanceof ServerLevel serverWorld)) {
            return;
        }
        affectedBlocks.removeIf(pos -> {
            BlockState state = serverWorld.getBlockState(pos);
            if (!isLockable(state)) {
                return false;
            }
            LockInfo info = getLockInfo(serverWorld, resolveLockLocations(serverWorld, pos, state));
            if (info == null) {
                return false;
            }
            logLockEvent("EXPLOSION_DENY", explosionActor(explosion.getDirectSourceEntity()), null, serverWorld, pos, info, null);
            return true;
        });
    }

    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection environment) {
        dispatcher.register(literal("chestlock")
                .requires(this::hasCommandPermission)
                .executes(ctx -> sendHelp(ctx.getSource()))
                .then(literal("info").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    BlockHitResult hit = raycastBlock(player, 5.0);
                    if (hit == null) {
                        ctx.getSource().sendSystemMessage(errorLine("Look at a chest, barrel, or shulker within 5 blocks."));
                        return 1;
                    }
                    BlockPos pos = hit.getBlockPos();
                    BlockState state = player.level().getBlockState(pos);
                    if (!isLockable(state)) {
                        ctx.getSource().sendSystemMessage(errorLine("Look at a chest, barrel, or shulker within 5 blocks."));
                        return 1;
                    }
                    LockInfo lockInfo = getLockInfo(player.level(), resolveLockLocations(player.level(), pos, state));
                    if (lockInfo == null) {
                        ctx.getSource().sendSystemMessage(errorLine("That container is not locked."));
                        return 1;
                    }
                    String creator = lockInfo.creatorName() == null ? "unknown" : lockInfo.creatorName();
                    String lastUser = lockInfo.lastUserName() == null ? "unknown" : lockInfo.lastUserName();
                    ctx.getSource().sendSystemMessage(statusLine("Lock details"));
                    ctx.getSource().sendSystemMessage(detailLine("Key", lockInfo.keyName(), ChatFormatting.AQUA));
                    ctx.getSource().sendSystemMessage(detailLine("Created by", creator, ChatFormatting.GREEN));
                    ctx.getSource().sendSystemMessage(detailLine("Last used by", lastUser, ChatFormatting.GREEN));
                    PickState stateInfo = getPickState(lockInfo, player);
                    ctx.getSource().sendSystemMessage(detailLine("Rusty pick", formatPickStatus(stateInfo.rustyAttempts(), stateInfo.rustyLimit()), ChatFormatting.GOLD));
                    ctx.getSource().sendSystemMessage(detailLine("Normal pick", formatPickStatus(stateInfo.normalAttempts(), stateInfo.normalLimit()), ChatFormatting.YELLOW));
                    ctx.getSource().sendSystemMessage(detailLine("Silence pick", formatSilenceStatus(stateInfo), ChatFormatting.LIGHT_PURPLE));
                    if (lockInfo.lastPickUserName() != null && lockInfo.lastPickType() != null) {
                        String when = lockInfo.lastPickTimestamp() > 0L
                                ? formatDuration(System.currentTimeMillis() - lockInfo.lastPickTimestamp()) + " ago"
                                : "unknown time";
                        ctx.getSource().sendSystemMessage(detailLine("Last pick attempt", lockInfo.lastPickUserName()
                                + " with " + lockInfo.lastPickType() + " (" + when + ")", ChatFormatting.LIGHT_PURPLE));
                    }
                    return 1;
                }))
                .then(literal("unlock").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    BlockHitResult hit = raycastBlock(player, 5.0);
                    if (hit == null) {
                        ctx.getSource().sendSystemMessage(errorLine("Look at a chest, barrel, or shulker within 5 blocks."));
                        return 1;
                    }
                    BlockPos pos = hit.getBlockPos();
                    BlockState state = player.level().getBlockState(pos);
                    if (!isLockable(state)) {
                        ctx.getSource().sendSystemMessage(errorLine("Look at a chest, barrel, or shulker within 5 blocks."));
                        return 1;
                    }
                    List<BlockPos> locations = resolveLockLocations(player.level(), pos, state);
                    LockInfo lockInfo = getLockInfo(player.level(), locations);
                    if (lockInfo == null) {
                        ctx.getSource().sendSystemMessage(errorLine("That container is not locked."));
                        return 1;
                    }
                    unlock(player.level(), locations, lockInfo.keyName());
                    ctx.getSource().sendSystemMessage(successLine("Unlocked container (key name was: " + lockInfo.keyName() + ")."));
                    return 1;
                }))
                .then(literal("keyinfo").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    String keyName = getHeldKeyName(player);
                    if (keyName == null) {
                        ctx.getSource().sendSystemMessage(errorLine("Hold a named ominous trial key in your main or off hand."));
                        return 1;
                    }
                    String locationKey = keyToChest.get(keyName);
                    if (locationKey == null) {
                        ctx.getSource().sendSystemMessage(errorLine("No locked container found for key name: " + keyName));
                        return 1;
                    }
                    LockInfo lockInfo = lockedChests.get(locationKey);
                    if (lockInfo == null) {
                        ctx.getSource().sendSystemMessage(errorLine("Lock data missing for key name: " + keyName));
                        return 1;
                    }
                    ctx.getSource().sendSystemMessage(statusLine("Key details"));
                    ctx.getSource().sendSystemMessage(detailLine("Key", lockInfo.keyName(), ChatFormatting.AQUA));
                    ctx.getSource().sendSystemMessage(detailLine("Locked container", locationKey, ChatFormatting.GREEN));
                    String creator = lockInfo.creatorName() == null ? "unknown" : lockInfo.creatorName();
                    String lastUser = lockInfo.lastUserName() == null ? "unknown" : lockInfo.lastUserName();
                    ctx.getSource().sendSystemMessage(detailLine("Created by", creator, ChatFormatting.GREEN));
                    ctx.getSource().sendSystemMessage(detailLine("Last used by", lastUser, ChatFormatting.GREEN));
                    return 1;
                }))
                .then(literal("reload").executes(ctx -> {
                    loadConfigValues();
                    loadData();
                    ctx.getSource().sendSystemMessage(successLine("ChestLock data reloaded."));
                    return 1;
                }))
                .then(literal("give")
                        .then(argument("player", EntityArgument.player())
                                .then(argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("rusty", "normal", "silence"), builder))
                                        .executes(ctx -> givePick(ctx, 1))
                                        .then(argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> givePick(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))))
                .then(literal("loglevel")
                        .then(argument("level", IntegerArgumentType.integer(0, 3))
                                .executes(ctx -> {
                                    int level = IntegerArgumentType.getInteger(ctx, "level");
                                    logLevel = level;
                                    saveConfigValue("logging.level", level);
                                    ctx.getSource().sendSystemMessage(successLine("Logging level set to " + level + "."));
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendSystemMessage(detailLine("Current log level", String.valueOf(logLevel), ChatFormatting.GOLD));
                            return 1;
                        }))
                .then(literal("normalkeys")
                        .then(argument("value", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("on", "off"), builder))
                                .executes(ctx -> {
                            String value = StringArgumentType.getString(ctx, "value").toLowerCase(Locale.ROOT);
                            if (!value.equals("on") && !value.equals("off")) {
                                ctx.getSource().sendSystemMessage(errorLine("Usage: /chestlock normalkeys <on|off>"));
                                return 1;
                            }
                            allowNormalKeys = value.equals("on");
                            saveConfigValue("keys.allow-normal", allowNormalKeys);
                            ctx.getSource().sendSystemMessage(successLine("Normal trial keys are now " + (allowNormalKeys ? "enabled." : "disabled.")));
                            return 1;
                        }))
                        .executes(ctx -> {
                            ctx.getSource().sendSystemMessage(detailLine("Normal trial keys", allowNormalKeys ? "enabled" : "disabled", allowNormalKeys ? ChatFormatting.GREEN : ChatFormatting.RED));
                            return 1;
                        }))
                .then(literal("lockpicks")
                        .then(argument("value", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("on", "off"), builder))
                                .executes(ctx -> {
                            String value = StringArgumentType.getString(ctx, "value").toLowerCase(Locale.ROOT);
                            if (!value.equals("on") && !value.equals("off")) {
                                ctx.getSource().sendSystemMessage(errorLine("Usage: /chestlock lockpicks <on|off>"));
                                return 1;
                            }
                            allowLockpicks = value.equals("on");
                            saveConfigValue("lockpicks.enabled", allowLockpicks);
                            ctx.getSource().sendSystemMessage(successLine("Lockpicking is now " + (allowLockpicks ? "enabled." : "disabled.")));
                            return 1;
                        }))
                        .executes(ctx -> {
                            ctx.getSource().sendSystemMessage(detailLine("Lockpicking", allowLockpicks ? "enabled" : "disabled", allowLockpicks ? ChatFormatting.GREEN : ChatFormatting.RED));
                            return 1;
                        }))
                .then(literal("verbosemessages")
                        .then(argument("value", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("on", "off"), builder))
                                .executes(ctx -> {
                            String value = StringArgumentType.getString(ctx, "value").toLowerCase(Locale.ROOT);
                            if (!value.equals("on") && !value.equals("off")) {
                                ctx.getSource().sendSystemMessage(errorLine("Usage: /chestlock verbosemessages <on|off>"));
                                return 1;
                            }
                            verboseMessages = value.equals("on");
                            saveConfigValue("messages.verbose-lock-actions", verboseMessages);
                            ctx.getSource().sendSystemMessage(successLine("Verbose lock messages are now " + (verboseMessages ? "enabled." : "disabled.")));
                            return 1;
                        }))
                        .executes(ctx -> {
                            ctx.getSource().sendSystemMessage(detailLine("Verbose lock messages", verboseMessages ? "enabled" : "disabled",
                                    verboseMessages ? ChatFormatting.GREEN : ChatFormatting.RED));
                            return 1;
                        }))
                .then(literal("lockoutscope")
                        .then(argument("value", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("chest", "player"), builder))
                                .executes(ctx -> {
                            String value = StringArgumentType.getString(ctx, "value");
                            if (!value.equalsIgnoreCase("chest") && !value.equalsIgnoreCase("player")) {
                                ctx.getSource().sendSystemMessage(errorLine("Usage: /chestlock lockoutscope <chest|player>"));
                                return 1;
                            }
                            lockoutScope = LockoutScope.fromConfig(value);
                            saveConfigValue("lockpicks.lockout-scope", lockoutScope.name().toLowerCase(Locale.ROOT));
                            ctx.getSource().sendSystemMessage(successLine("Lockout scope set to " + lockoutScope.name().toLowerCase(Locale.ROOT) + "."));
                            return 1;
                        }))
                        .executes(ctx -> {
                            ctx.getSource().sendSystemMessage(detailLine("Lockout scope", lockoutScope.name().toLowerCase(Locale.ROOT), ChatFormatting.AQUA));
                            return 1;
                        }))
                .then(literal("settings").executes(ctx -> {
                    ctx.getSource().sendSystemMessage(statusLine("ChestLock settings"));
                    ctx.getSource().sendSystemMessage(detailLine("Logging level", String.valueOf(logLevel), ChatFormatting.AQUA));
                    ctx.getSource().sendSystemMessage(detailLine("Normal trial keys", allowNormalKeys ? "enabled" : "disabled",
                            allowNormalKeys ? ChatFormatting.GREEN : ChatFormatting.RED));
                    ctx.getSource().sendSystemMessage(detailLine("Lockpicks", allowLockpicks ? "enabled" : "disabled",
                            allowLockpicks ? ChatFormatting.GREEN : ChatFormatting.RED));
                    ctx.getSource().sendSystemMessage(detailLine("Verbose lock messages", verboseMessages ? "enabled" : "disabled",
                            verboseMessages ? ChatFormatting.GREEN : ChatFormatting.RED));
                    ctx.getSource().sendSystemMessage(detailLine("Lockout scope", lockoutScope.name().toLowerCase(Locale.ROOT), ChatFormatting.GOLD));
                    ctx.getSource().sendSystemMessage(detailLine("Limit range", pickLimitMin + " - " + pickLimitMax, ChatFormatting.YELLOW));
                    return 1;
                }))
                .then(literal("help").executes(ctx -> sendHelp(ctx.getSource())))
        );
    }

    private boolean hasCommandPermission(CommandSourceStack source) {
        PermissionSet permissions = source.permissions();
        if (permissions instanceof LevelBasedPermissionSet levelBased) {
            return levelBased.level().id() >= 2;
        }
        return permissions == PermissionSet.ALL_PERMISSIONS || permissions == LevelBasedPermissionSet.ALL;
    }

    private int givePick(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int amount) {
        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(ctx, "player");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            ctx.getSource().sendSystemMessage(errorLine("Player not found."));
            return 1;
        }
        PickType pickType = parsePickType(StringArgumentType.getString(ctx, "type"));
        if (pickType == null) {
            ctx.getSource().sendSystemMessage(errorLine("Pick type must be rusty, normal, or silence."));
            return 1;
        }
        ItemStack stack = createPick(pickType);
        stack.setCount(amount);
        boolean added = target.getInventory().add(stack);
        if (!added) {
            target.drop(stack, false);
        }
        ctx.getSource().sendSystemMessage(successLine("Gave " + amount + " " + pickType.id + " pick(s) to " + target.getName().getString() + "."));
        return 1;
    }

    private int sendHelp(CommandSourceStack source) {
        source.sendSystemMessage(statusLine("ChestLock commands"));
        source.sendSystemMessage(helpLine("/chestlock info", "show lock key name for looked-at container"));
        source.sendSystemMessage(helpLine("/chestlock unlock", "force unlock looked-at container"));
        source.sendSystemMessage(helpLine("/chestlock keyinfo", "show lock info for key in hand"));
        source.sendSystemMessage(helpLine("/chestlock reload", "reload lock data from disk"));
        source.sendSystemMessage(helpLine("/chestlock loglevel <0-3>", "set log verbosity"));
        source.sendSystemMessage(helpLine("/chestlock normalkeys <on|off>", "allow normal trial keys"));
        source.sendSystemMessage(helpLine("/chestlock lockpicks <on|off>", "allow lock picking"));
        source.sendSystemMessage(helpLine("/chestlock verbosemessages <on|off>", "toggle verbose lock/unlock chat"));
        source.sendSystemMessage(helpLine("/chestlock lockoutscope <chest|player>", "set lockout scope"));
        source.sendSystemMessage(helpLine("/chestlock settings", "show current loaded settings"));
        source.sendSystemMessage(helpLine("/chestlock give <player> <rusty|normal|silence> [amount]", "give lock picks"));
        return 1;
    }

    private Component statusLine(String text) {
        return Component.literal(">> ").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.GREEN));
    }

    private Component successLine(String text) {
        return Component.literal(">> ").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.GREEN));
    }

    private Component errorLine(String text) {
        return Component.literal(">> ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.RED));
    }

    private Component detailLine(String label, String value, ChatFormatting valueColor) {
        return Component.literal(label + ": ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(value).withStyle(valueColor));
    }

    private Component helpLine(String command, String description) {
        return Component.literal("• ").withStyle(ChatFormatting.DARK_GREEN)
                .append(Component.literal(command).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(description).withStyle(ChatFormatting.GREEN));
    }
    public boolean onOpenLockedContainer(ServerPlayer player, BlockEntity blockEntity) {
        if (blockEntity == null) {
            return true;
        }
        ServerLevel world = player.level();
        List<BlockPos> locations = resolveLockLocations(world, blockEntity.getBlockPos(), world.getBlockState(blockEntity.getBlockPos()));
        if (locations.isEmpty()) {
            return true;
        }

        LockInfo lockInfo = getLockInfo(world, locations);
        if (lockInfo == null) {
            return true;
        }

        String lockKey = lockInfo.keyName();
        KeyMatch keyMatch = findHeldKey(player, lockKey);
        String heldKeyName = keyMatch == null ? null : keyMatch.name();
        if (heldKeyName == null || !lockKey.equals(heldKeyName)) {
            playFail(player, world, locations.getFirst());
            String anyHeldKey = getHeldKeyName(player);
            if (anyHeldKey == null) {
                player.sendSystemMessage(errorLine("This chest is locked. You need the correct key."), false);
            } else {
                player.sendSystemMessage(errorLine("Wrong key."), false);
            }
            logLockEvent("OPEN_DENY", player.getName().getString(), heldKeyName, world, locations.getFirst(),
                    lockInfo, "wrong or missing key");
            return false;
        }

        if (hasOtherViewers(world, locations, player.getUUID())) {
            playFail(player, world, locations.getFirst());
            logLockEvent("OPEN_DENY", player.getName().getString(), heldKeyName, world, locations.getFirst(),
                    lockInfo, "in use by another player");
            return false;
        }

        updateLastUser(world, locations, player);
        registerViewer(world, locations, player.getUUID());
        playSuccess(player, world, locations.getFirst());
        logLockEvent("OPEN_ALLOWED", player.getName().getString(), heldKeyName, world, locations.getFirst(), lockInfo, null);
        if (keyMatch != null && keyMatch.normal() && lockInfo.normalKey()) {
            if (lockInfo.normalArmed()) {
                unlock(world, locations, lockInfo.keyName());
                consumeOneKey(player, keyMatch);
                if (verboseMessages) {
                    player.sendSystemMessage(successLine("Chest unlocked with key: " + heldKeyName), false);
                }
                logLockEvent("NORMAL_KEY_CONSUMED", player.getName().getString(), heldKeyName, world, locations.getFirst(), lockInfo, null);
            } else {
                armNormalKeyLock(world, locations);
            }
        }
        return true;
    }

    public void onCloseLockedContainer(ServerPlayer player) {
        String locationKey = openLockByPlayer.remove(player.getUUID());
        if (locationKey == null) {
            return;
        }
        Set<UUID> viewers = openViewers.get(locationKey);
        if (viewers == null) {
            return;
        }
        viewers.remove(player.getUUID());
        if (viewers.isEmpty()) {
            openViewers.remove(locationKey);
        }
    }

    public boolean shouldBlockScreenInteraction(ServerPlayer player) {
        String locationKey = openLockByPlayer.get(player.getUUID());
        if (locationKey == null) {
            return false;
        }
        LockInfo info = lockedChests.get(locationKey);
        if (info == null) {
            return false;
        }
        String heldKeyName = getHeldKeyName(player);
        if (heldKeyName == null || !info.keyName().equals(heldKeyName)) {
            LocationData data = parseLocationKey(locationKey, player.level());
            BlockPos pos = data == null ? player.blockPosition() : data.pos();
            playFail(player, player.level(), pos);
            logLockEvent("INVENTORY_CLICK_DENY", player.getName().getString(), heldKeyName,
                    player.level(), pos, info, null);
            return true;
        }
        return false;
    }

    public boolean onPistonMove(ServerLevel world, List<BlockPos> blocks) {
        for (BlockPos pos : blocks) {
            BlockState state = world.getBlockState(pos);
            if (isLockable(state) && getLockInfo(world, resolveLockLocations(world, pos, state)) != null) {
                logLockEvent("PISTON_EXTEND_DENY", "PISTON", null, world, pos,
                        getLockInfo(world, resolveLockLocations(world, pos, state)), null);
                return false;
            }
        }
        return true;
    }

    public boolean onFireBurn(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!isLockable(state)) {
            return true;
        }
        LockInfo info = getLockInfo(world, resolveLockLocations(world, pos, state));
        if (info == null) {
            return true;
        }
        logLockEvent("BURN_DENY", "FIRE", null, world, pos, info, null);
        return false;
    }

    public boolean onHopperTransfer(ServerLevel world, Container source, Container destination) {
        boolean blocked = false;
        if (source != null && isLockedContainer(world, source)) {
            BlockPos pos = resolveContainerLocation(world, source);
            logLockEvent("INVENTORY_MOVE_DENY", inventoryActor(world, source), null, world, pos,
                    getLockInfo(world, resolveLockLocations(world, pos, world.getBlockState(pos))),
                    "source=" + inventoryName(source) + " dest=" + inventoryName(destination));
            blocked = true;
        }
        if (destination != null && isLockedContainer(world, destination)) {
            BlockPos pos = resolveContainerLocation(world, destination);
            logLockEvent("INVENTORY_MOVE_DENY", inventoryActor(world, destination), null, world, pos,
                    getLockInfo(world, resolveLockLocations(world, pos, world.getBlockState(pos))),
                    "source=" + inventoryName(source) + " dest=" + inventoryName(destination));
            blocked = true;
        }
        return !blocked;
    }

    public void onHopperPlaced(ServerPlayer player, BlockPos pos) {
        hopperOwners.put(locationKey(player.level(), pos), new HopperOwner(player.getName().getString(), System.currentTimeMillis()));
    }

    public void onTntPrimed(ServerPlayer player, Entity tntEntity) {
        if (player == null || tntEntity == null) {
            return;
        }
        tntSources.put(tntEntity.getUUID(), player.getName().getString());
    }

    public void onCrystalDamaged(ServerPlayer player, Entity crystal) {
        if (player == null || crystal == null) {
            return;
        }
        crystalSources.put(crystal.getUUID(), new PendingIgnite(player.getName().getString(), System.currentTimeMillis()));
    }

    private void registerViewer(ServerLevel world, List<BlockPos> locations, UUID playerId) {
        if (locations.isEmpty()) {
            return;
        }
        String key = locationKey(world, locations.getFirst());
        openLockByPlayer.put(playerId, key);
        openViewers.computeIfAbsent(key, ignored -> new HashSet<>()).add(playerId);
    }

    private boolean hasOtherViewers(ServerLevel world, List<BlockPos> locations, UUID viewerId) {
        if (locations.isEmpty()) {
            return false;
        }
        String key = locationKey(world, locations.getFirst());
        Set<UUID> viewers = openViewers.get(key);
        if (viewers == null || viewers.isEmpty()) {
            return false;
        }
        return viewers.size() > 1 || !viewers.contains(viewerId);
    }

    private boolean isLockedContainer(ServerLevel world, Container inventory) {
        BlockPos pos = resolveContainerLocation(world, inventory);
        if (pos == null) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        if (!isLockable(state)) {
            return false;
        }
        return getLockInfo(world, resolveLockLocations(world, pos, state)) != null;
    }

    private BlockPos resolveContainerLocation(ServerLevel world, Container inventory) {
        if (inventory instanceof BlockEntity blockEntity) {
            return blockEntity.getBlockPos();
        }
        return null;
    }

    private String inventoryActor(ServerLevel world, Container inventory) {
        if (inventory instanceof BlockEntity blockEntity && world.getBlockState(blockEntity.getBlockPos()).is(Blocks.HOPPER)) {
            HopperOwner owner = hopperOwners.get(locationKey(world, blockEntity.getBlockPos()));
            if (owner != null) {
                return "HOPPER:" + owner.playerName();
            }
            return "HOPPER";
        }
        return inventory == null ? "HOPPER" : inventory.getClass().getSimpleName();
    }

    private String inventoryName(Container inventory) {
        return inventory == null ? "unknown" : inventory.getClass().getSimpleName();
    }
    private void handlePickAttempt(ServerPlayer player, Level world, BlockPos pos, LockInfo lockInfo, PickMatch pickMatch) {
        if (!allowLockpicks) {
            player.sendSystemMessage(errorLine("Lockpicking is disabled."), false);
            return;
        }
        List<BlockPos> locations = resolveLockLocations(world, pos, world.getBlockState(pos));
        if (locations.isEmpty()) {
            return;
        }

        PickType pickType = pickMatch.type();
        boolean normalKeyLock = lockInfo.normalKey();
        boolean success;
        boolean overLimitBefore;
        boolean lockoutNow;
        boolean changed = false;
        PickState state = getPickState(lockInfo, player);
        long now = System.currentTimeMillis();

        switch (pickType) {
            case RUSTY -> {
                int limit = state.rustyLimit();
                if (limit < 0) {
                    limit = ThreadLocalRandom.current().nextInt(pickLimitMin, pickLimitMax + 1);
                    state = state.withRustyLimit(limit);
                    changed = true;
                }
                int attempts = state.rustyAttempts();
                overLimitBefore = attempts >= limit;
                lockoutNow = !overLimitBefore && (attempts + 1) >= limit;
                state = state.withRustyAttempts(attempts + 1);
                changed = true;
                double chance = normalKeyLock ? rustyNormalKeyChance : rustyOpenChance;
                success = !overLimitBefore && !lockoutNow && ThreadLocalRandom.current().nextDouble() < chance;
                if (!success) {
                    damagePlayer(player, rustyDamage);
                    if (lockoutNow) {
                        playWorldSoundDelayed(world, pos, SoundEvents.VAULT_DEACTIVATE);
                        playWorldSoundDelayed(world, pos, SoundEvents.VAULT_HIT);
                    } else if (overLimitBefore) {
                        playWorldSoundDelayed(world, pos, SoundEvents.VAULT_HIT);
                    } else {
                        playWorldSoundDelayed(world, pos, SoundEvents.CHEST_LOCKED);
                    }
                } else {
                    playWorldSoundDelayed(world, pos, SoundEvents.TRIPWIRE_CLICK_ON);
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
                overLimitBefore = attempts >= limit;
                lockoutNow = !overLimitBefore && (attempts + 1) >= limit;
                state = state.withNormalAttempts(attempts + 1);
                changed = true;
                double chance = normalKeyLock ? normalNormalKeyChance : normalOpenChance;
                success = !overLimitBefore && !lockoutNow && ThreadLocalRandom.current().nextDouble() < chance;
                if (!success) {
                    damagePlayer(player, normalDamage);
                    if (lockoutNow) {
                        playWorldSoundDelayed(world, pos, SoundEvents.VAULT_DEACTIVATE);
                        playWorldSoundDelayed(world, pos, SoundEvents.VAULT_HIT);
                    } else if (overLimitBefore) {
                        playWorldSoundDelayed(world, pos, SoundEvents.VAULT_HIT);
                    } else {
                        playWorldSoundDelayed(world, pos, SoundEvents.CHEST_LOCKED);
                    }
                } else {
                    playWorldSoundDelayed(world, pos, SoundEvents.TRIPWIRE_CLICK_ON);
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
                overLimitBefore = attempts >= limit;
                lockoutNow = !overLimitBefore && (attempts + 1) >= limit;
                state = state.withSilenceAttempts(attempts + 1);
                if (overLimitBefore || lockoutNow) {
                    int over = state.silenceOverLimitAttempts() + 1;
                    state = state.withSilenceOverLimitAttempts(over);
                    if (lockoutNow || state.silencePenaltyTimestamp() <= 0L) {
                        state = state.withSilencePenaltyTimestamp(System.currentTimeMillis());
                    }
                }
                changed = true;
                double chance = silenceOpenChance;
                long penaltyTimestamp = state.silencePenaltyTimestamp();
                if (penaltyTimestamp > 0L && System.currentTimeMillis() - penaltyTimestamp < silencePenaltyResetMs) {
                    int overLimitAttempts = Math.max(1, state.silenceOverLimitAttempts());
                    chance = chance / Math.pow(2.0, overLimitAttempts);
                }
                success = !overLimitBefore && !lockoutNow && ThreadLocalRandom.current().nextDouble() < chance;
                if (!success) {
                    damagePlayer(player, silenceDamage);
                    if (lockoutNow) {
                        playWorldSoundDelayed(world, pos, SoundEvents.VAULT_DEACTIVATE);
                    } else if (overLimitBefore) {
                        playWorldSoundDelayed(world, pos, SoundEvents.VAULT_HIT);
                    }
                } else {
                    playWorldSoundDelayed(world, pos, SoundEvents.TRIPWIRE_CLICK_ON);
                }
            }
            default -> {
                success = false;
            }
        }

        LockInfo updated = lockInfo.withLastPick(player, pickType, now);
        updated = updated.withPickState(updatePickState(updated, player, state));

        double breakChance = switch (pickType) {
            case RUSTY -> rustyBreakChance;
            case NORMAL -> normalBreakChance;
            case SILENCE -> silenceBreakChance;
        };
        if (ThreadLocalRandom.current().nextDouble() < breakChance) {
            consumeOnePick(player, pickMatch);
        }

        if (success) {
            unlock(world, locations, lockInfo.keyName());
            if (verboseMessages) {
                player.sendSystemMessage(successLine("Chest lock successfully picked."), false);
            }
            logLockEvent("PICK_SUCCESS", player.getName().getString(), null, world, pos, lockInfo, "pick=" + pickType.id);
            if (pickType == PickType.SILENCE) {
                openSilently(player, world, pos);
            } else if (world instanceof ServerLevel serverWorld) {
                BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity instanceof net.minecraft.world.MenuProvider factory) {
                    player.openMenu(factory);
                }
                playWorldSoundDelayed(serverWorld, pos, SoundEvents.VAULT_OPEN_SHUTTER);
            }
        } else {
            logLockEvent("PICK_FAIL", player.getName().getString(), null, world, pos, lockInfo, "pick=" + pickType.id);
            storeLockInfo(world, locations, updated);
            saveData();
        }
    }

    private void openSilently(ServerPlayer player, Level world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof net.minecraft.world.MenuProvider factory)) {
            return;
        }
        player.openMenu(factory);
        stopSound(player, SoundEvents.CHEST_OPEN);
        stopSound(player, SoundEvents.CHEST_CLOSE);
        stopSound(player, SoundEvents.BARREL_OPEN);
        stopSound(player, SoundEvents.BARREL_CLOSE);
        stopSound(player, SoundEvents.SHULKER_BOX_OPEN);
        stopSound(player, SoundEvents.SHULKER_BOX_CLOSE);
    }

    private void stopSound(ServerPlayer player, SoundEvent sound) {
        player.connection.send(new ClientboundStopSoundPacket(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(sound), SoundSource.BLOCKS));
    }

    private void damagePlayer(ServerPlayer player, double amount) {
        DamageSource source = player.damageSources().generic();
        player.hurt(source, (float) amount);
    }

    private void consumeOneKey(ServerPlayer player, KeyMatch match) {
        ItemStack stack = match.hand() == InteractionHand.MAIN_HAND ? player.getMainHandItem() : player.getOffhandItem();
        if (stack.isEmpty()) {
            return;
        }
        stack.shrink(1);
    }

    private void consumeOnePick(ServerPlayer player, PickMatch match) {
        ItemStack stack = match.hand() == InteractionHand.MAIN_HAND ? player.getMainHandItem() : player.getOffhandItem();
        if (stack.isEmpty()) {
            return;
        }
        stack.shrink(1);
    }

    private void playWorldSoundDelayed(Level world, BlockPos pos, SoundEvent sound) {
        if (!(world instanceof ServerLevel serverWorld)) {
            return;
        }
        MinecraftServer server = serverWorld.getServer();
        if (server == null) {
            return;
        }
        server.execute(() -> serverWorld.playSound(null, pos, sound, SoundSource.BLOCKS, 1.6f, 1.0f));
    }

    private void playSuccess(ServerPlayer player, Level world, BlockPos pos) {
        if (world instanceof ServerLevel serverWorld) {
            serverWorld.playSound(null, pos, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    private void playFail(ServerPlayer player, Level world, BlockPos pos) {
        if (world instanceof ServerLevel serverWorld) {
            serverWorld.playSound(null, pos, SoundEvents.VAULT_INSERT_ITEM_FAIL, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    private void playInsert(ServerPlayer player, Level world, BlockPos pos) {
        if (world instanceof ServerLevel serverWorld) {
            serverWorld.playSound(null, pos, SoundEvents.VAULT_INSERT_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    private boolean isLockable(BlockState state) {
        Block block = state.getBlock();
        return block instanceof ChestBlock || block instanceof ShulkerBoxBlock || state.getBlock() == Blocks.BARREL;
    }

    public boolean isLockedBlock(ServerLevel world, BlockPos pos, BlockState state) {
        if (!isLockable(state)) {
            return false;
        }
        return getLockInfo(world, resolveLockLocations(world, pos, state)) != null;
    }

    private List<BlockPos> resolveLockLocations(Level world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock) {
            return resolveChestLocations(world, pos, state);
        }
        if (state.getBlock() instanceof ShulkerBoxBlock || state.getBlock() == Blocks.BARREL) {
            return List.of(pos);
        }
        return List.of();
    }

    private List<BlockPos> resolveChestLocations(Level world, BlockPos pos, BlockState state) {
        List<BlockPos> locations = new ArrayList<>();
        locations.add(pos);
        if (!state.hasProperty(ChestBlock.TYPE)) {
            return locations;
        }
        net.minecraft.world.level.block.state.properties.ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            return locations;
        }
        Direction facing = state.getValue(ChestBlock.FACING);
        Direction offset = type == net.minecraft.world.level.block.state.properties.ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
        BlockPos other = pos.relative(offset);
        BlockState otherState = world.getBlockState(other);
        if (otherState.getBlock() instanceof ChestBlock) {
            locations.add(other);
        }
        return locations;
    }

    private void updateLastUser(Level world, List<BlockPos> locations, ServerPlayer player) {
        if (locations.isEmpty()) {
            return;
        }
        String key = locationKey(world, locations.getFirst());
        LockInfo info = lockedChests.get(key);
        if (info == null) {
            return;
        }
        LockInfo updated = info.withLastUser(player);
        for (BlockPos pos : locations) {
            lockedChests.put(locationKey(world, pos), updated);
        }
        saveData();
    }

    private void armNormalKeyLock(Level world, List<BlockPos> locations) {
        if (locations.isEmpty()) {
            return;
        }
        String key = locationKey(world, locations.getFirst());
        LockInfo info = lockedChests.get(key);
        if (info == null) {
            return;
        }
        LockInfo updated = info.withNormalArmed(true);
        for (BlockPos pos : locations) {
            lockedChests.put(locationKey(world, pos), updated);
        }
        saveData();
    }

    private boolean tryLock(Level world, List<BlockPos> locations, String keyName, ServerPlayer creator, boolean normalKey) {
        if (locations.isEmpty()) {
            return false;
        }
        Set<String> currentLocationKeys = new HashSet<>();
        for (BlockPos location : locations) {
            currentLocationKeys.add(locationKey(world, location));
        }

        for (BlockPos location : locations) {
            LockInfo existing = lockedChests.get(locationKey(world, location));
            if (existing != null && !existing.keyName().equals(keyName)) {
                return false;
            }
        }
        for (Map.Entry<String, LockInfo> entry : lockedChests.entrySet()) {
            LockInfo existing = entry.getValue();
            if (existing == null || !keyName.equals(existing.keyName())) {
                continue;
            }
            boolean sameOwner = creator.getUUID().equals(existing.creatorUuid())
                    || (existing.creatorUuid() == null && creator.getName().getString().equals(existing.creatorName()));
            if (sameOwner && !currentLocationKeys.contains(entry.getKey())) {
                return false;
            }
        }

        LockInfo info = new LockInfo(keyName, creator.getName().getString(), creator.getUUID(), null, null, normalKey, false,
                null, null, null, 0L,
                -1, 0, -1, 0, -1, 0, 0, 0L, new HashMap<>());
        for (BlockPos location : locations) {
            lockedChests.put(locationKey(world, location), info);
        }
        keyToChest.putIfAbsent(keyName, locationKey(world, locations.getFirst()));
        saveData();
        return true;
    }

    private void unlock(Level world, List<BlockPos> locations, String keyName) {
        for (BlockPos location : locations) {
            lockedChests.remove(locationKey(world, location));
        }
        refreshKeyMapping(keyName);
        saveData();
    }

    private void refreshKeyMapping(String keyName) {
        if (keyName == null || keyName.isBlank()) {
            return;
        }
        for (Map.Entry<String, LockInfo> entry : lockedChests.entrySet()) {
            LockInfo info = entry.getValue();
            if (info != null && keyName.equals(info.keyName())) {
                keyToChest.put(keyName, entry.getKey());
                return;
            }
        }
        keyToChest.remove(keyName);
    }

    private void storeLockInfo(Level world, List<BlockPos> locations, LockInfo info) {
        if (info == null) {
            return;
        }
        for (BlockPos pos : locations) {
            lockedChests.put(locationKey(world, pos), info);
        }
    }

    private PickState getPickState(LockInfo info, ServerPlayer player) {
        if (info == null) {
            return PickState.empty();
        }
        if (lockoutScope == LockoutScope.PLAYER) {
            if (player == null) {
                return PickState.empty();
            }
            PickState state = info.playerPickStates().get(player.getUUID());
            return state == null ? PickState.empty() : state;
        }
        return info.toPickState();
    }

    private PickState updatePickState(LockInfo info, ServerPlayer player, PickState state) {
        if (info == null || state == null) {
            return state;
        }
        if (lockoutScope == LockoutScope.PLAYER) {
            if (player == null) {
                return state;
            }
            return info.withPlayerPickState(player.getUUID(), state).toPickState();
        }
        return state;
    }

    private ItemStack createPick(PickType type) {
        ItemStack item = new ItemStack(Items.TRIPWIRE_HOOK);
        item.set(DataComponents.CUSTOM_NAME, Component.literal(type.displayName));
        item.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(type.modelData)));
        CompoundTag tag = new CompoundTag();
        tag.putString(PICK_NBT_KEY, type.id);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return item;
    }

    private PickType getPickType(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || itemStack.getItem() != Items.TRIPWIRE_HOOK) {
            return null;
        }
        CustomData data = itemStack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        if (tag == null) {
            return null;
        }
        String id = tag.getStringOr(PICK_NBT_KEY, "");
        if (id == null || id.isBlank()) {
            return null;
        }
        return PickType.fromId(id);
    }

    private PickMatch findHeldPick(ServerPlayer player) {
        PickType main = getPickType(player.getMainHandItem());
        if (main != null) {
            return new PickMatch(main, InteractionHand.MAIN_HAND);
        }
        PickType off = getPickType(player.getOffhandItem());
        if (off != null) {
            return new PickMatch(off, InteractionHand.OFF_HAND);
        }
        return null;
    }

    private String getHeldKeyName(ServerPlayer player) {
        String main = getKeyName(player.getMainHandItem());
        if (main != null) {
            return main;
        }
        return getKeyName(player.getOffhandItem());
    }

    private KeyMatch findHeldKey(ServerPlayer player, String requiredName) {
        KeyMatch main = getKeyMatch(player.getMainHandItem(), InteractionHand.MAIN_HAND, requiredName);
        if (main != null) {
            return main;
        }
        return getKeyMatch(player.getOffhandItem(), InteractionHand.OFF_HAND, requiredName);
    }

    private KeyMatch findAnyHeldKey(ServerPlayer player) {
        KeyMatch main = getAnyKeyMatch(player.getMainHandItem(), InteractionHand.MAIN_HAND);
        if (main != null) {
            return main;
        }
        return getAnyKeyMatch(player.getOffhandItem(), InteractionHand.OFF_HAND);
    }

    private KeyMatch getKeyMatch(ItemStack itemStack, InteractionHand hand, String requiredName) {
        String name = getKeyName(itemStack);
        if (name == null || !name.equals(requiredName)) {
            return null;
        }
        boolean normal = itemStack.getItem() == Items.TRIAL_KEY;
        if (normal && !allowNormalKeys) {
            return null;
        }
        return new KeyMatch(name, hand, normal);
    }

    private KeyMatch getAnyKeyMatch(ItemStack itemStack, InteractionHand hand) {
        String name = getKeyName(itemStack);
        if (name == null) {
            return null;
        }
        boolean normal = itemStack.getItem() == Items.TRIAL_KEY;
        if (normal && !allowNormalKeys) {
            return null;
        }
        return new KeyMatch(name, hand, normal);
    }

    private String getKeyName(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        Item type = itemStack.getItem();
        if (type != Items.OMINOUS_TRIAL_KEY && !(allowNormalKeys && type == Items.TRIAL_KEY)) {
            return null;
        }
        Component displayName = itemStack.get(DataComponents.CUSTOM_NAME);
        if (displayName == null) {
            return null;
        }
        String name = displayName.getString().trim();
        return name.isEmpty() ? null : name;
    }

    private String locationKey(Level world, BlockPos pos) {
        String worldName = world.dimension().identifier().toString();
        return worldName + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }


    private LocationData parseLocationKey(String locationKey, ServerLevel world) {
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
            ResourceKey<Level> key = world.dimension();
            String realm = mapRealm(key);
            return new LocationData(worldName, new BlockPos(x, y, z), realm, key.identifier().toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String mapRealm(ResourceKey<Level> key) {
        if (key == null) {
            return null;
        }
        Identifier id = key.identifier();
        if (Level.OVERWORLD.identifier().equals(id)) {
            return "OVERWORLD";
        }
        if (Level.NETHER.identifier().equals(id)) {
            return "NETHER";
        }
        if (Level.END.identifier().equals(id)) {
            return "END";
        }
        return id.toString();
    }

    private boolean isDecorationItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        Item item = itemStack.getItem();
        if (item == Items.ITEM_FRAME || item == Items.GLOW_ITEM_FRAME) {
            return true;
        }
        return item instanceof SignItem;
    }

    private LockInfo getLockInfo(Level world, List<BlockPos> locations) {
        if (locations.isEmpty()) {
            return null;
        }
        return lockedChests.get(locationKey(world, locations.getFirst()));
    }
    private void logLockEvent(String action, String actor, String keyUsed, Level world, BlockPos pos, LockInfo info, String detail) {
        if (!shouldLog(action, world, pos)) {
            return;
        }
        String used = keyUsed == null || keyUsed.isBlank() ? "none" : keyUsed;
        String keyName = info == null ? "unknown" : info.keyName();
        String creator = info == null || info.creatorName() == null ? "unknown" : info.creatorName();
        String lastUser = info == null || info.lastUserName() == null ? "unknown" : info.lastUserName();
        String loc = world == null ? "unknown" : formatLocation(world, pos);
        String extra = detail == null ? "" : " detail=" + detail;
        LOGGER.info(action + " actor=" + actor + " usedKey=" + used + " lockKey=" + keyName
                + " creator=" + creator + " lastUser=" + lastUser + " location=" + loc + extra);
    }

    private boolean shouldLog(String action, Level world, BlockPos pos) {
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
        if (world == null || pos == null) {
            return true;
        }
        String key = action + "|" + locationKey(world, pos);
        long now = System.currentTimeMillis();
        Long last = logCooldowns.get(key);
        if (last != null && now - last < 5000L) {
            return false;
        }
        logCooldowns.put(key, now);
        return true;
    }

    private boolean shouldSendPlayerMessage(UUID playerId, String actionKey, long cooldownMs) {
        if (playerId == null || actionKey == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        String key = playerId + "|" + actionKey;
        Long last = playerMessageCooldowns.get(key);
        if (last != null && now - last < cooldownMs) {
            return false;
        }
        playerMessageCooldowns.put(key, now);
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

    private String formatLocation(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return "unknown";
        }
        String worldName = world.dimension().identifier().toString();
        String realm = mapRealm(world.dimension());
        return worldName + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + " (" + realm + ")";
    }

    private String explosionActor(Entity entity) {
        if (entity == null) {
            return "ENTITY_EXPLODE";
        }
        if (entity.getType() == net.minecraft.world.entity.EntityType.TNT) {
            String mapped = tntSources.get(entity.getUUID());
            return mapped == null ? "TNT" : "TNT:" + mapped;
        }
        if (entity.getType() == net.minecraft.world.entity.EntityType.END_CRYSTAL) {
            PendingIgnite ignite = crystalSources.get(entity.getUUID());
            if (ignite != null && System.currentTimeMillis() - ignite.timestamp() < 10000L) {
                return "END_CRYSTAL:" + ignite.playerName();
            }
            return "END_CRYSTAL";
        }
        return entity.getType().toString();
    }

    private String formatPickStatus(int attempts, int limit) {
        if (limit < 0) {
            return "no attempts";
        }
        return attempts + "/" + limit;
    }

    private String formatSilenceStatus(PickState state) {
        String base = formatPickStatus(state.silenceAttempts(), state.silenceLimit());
        if (state.silenceLimit() < 0) {
            return base;
        }
        String penalty;
        int overLimit = state.silenceOverLimitAttempts();
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

    private PickType parsePickType(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.endsWith("s")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return PickType.fromId(normalized);
    }

    private BlockHitResult raycastBlock(ServerPlayer player, double distance) {
        if (player == null) {
            return null;
        }
        HitResult hit = player.pick(distance, 0.0f, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit;
        }
        return null;
    }
    private void loadConfigValues() {
        ensureConfigFile();
        Map<String, Object> config = loadYaml(configFile);
        logLevel = readInt(config, "logging.level", 1);
        allowNormalKeys = readBoolean(config, "keys.allow-normal", false);
        verboseMessages = readBoolean(config, "messages.verbose-lock-actions", false);
        allowLockpicks = readBoolean(config, "lockpicks.enabled", true);
        pickLimitMin = Math.max(1, readInt(config, "lockpicks.limit.min", 1));
        pickLimitMax = Math.max(pickLimitMin, readInt(config, "lockpicks.limit.max", 20));
        rustyOpenChance = clampChance(readDouble(config, "lockpicks.rusty.open-chance", 0.05));
        rustyNormalKeyChance = clampChance(readDouble(config, "lockpicks.rusty.normal-key-chance", 0.10));
        rustyBreakChance = clampChance(readDouble(config, "lockpicks.rusty.break-chance", 1.0));
        rustyDamage = readDouble(config, "lockpicks.rusty.damage", 1.0);
        normalOpenChance = clampChance(readDouble(config, "lockpicks.normal.open-chance", 0.10));
        normalNormalKeyChance = clampChance(readDouble(config, "lockpicks.normal.normal-key-chance", 0.20));
        normalBreakChance = clampChance(readDouble(config, "lockpicks.normal.break-chance", 0.50));
        normalDamage = readDouble(config, "lockpicks.normal.damage", 2.0);
        silenceOpenChance = clampChance(readDouble(config, "lockpicks.silence.open-chance", 0.50));
        silenceBreakChance = clampChance(readDouble(config, "lockpicks.silence.break-chance", 0.05));
        silenceDamage = readDouble(config, "lockpicks.silence.damage", 4.0);
        silencePenaltyResetMs = Math.max(0L, readLong(config, "lockpicks.silence.penalty-reset-minutes", 60) * 60L * 1000L);
        lockoutScope = LockoutScope.fromConfig(readString(config, "lockpicks.lockout-scope", "chest"));
    }

    private void ensureConfigFile() {
        try {
            Files.createDirectories(configDir);
            if (Files.exists(configFile)) {
                return;
            }
            try (InputStream input = LockService.class.getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
                if (input == null) {
                    return;
                }
                Files.copy(input, configFile);
            }
        } catch (IOException ex) {
            LOGGER.warn("Could not create config.yml: {}", ex.getMessage());
        }
    }

    private void saveConfigValue(String path, Object value) {
        Map<String, Object> config = loadYaml(configFile);
        writePath(config, path, value);
        try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(configFile), StandardCharsets.UTF_8)) {
            yaml.dump(config, writer);
        } catch (IOException ex) {
            LOGGER.warn("Could not save config.yml: {}", ex.getMessage());
        }
    }

    private void writePath(Map<String, Object> map, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = map;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                Map<String, Object> created = new HashMap<>();
                current.put(parts[i], created);
                current = created;
            } else {
                current = (Map<String, Object>) next;
            }
        }
        current.put(parts[parts.length - 1], value);
    }

    public void loadData() {
        lockedChests.clear();
        keyToChest.clear();
        if (!Files.exists(dataFile)) {
            return;
        }
        Map<String, Object> root = loadYaml(dataFile);
        Object sectionObj = root.get("locked-chests");
        if (!(sectionObj instanceof Map<?, ?> section)) {
            return;
        }
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            String locationKey = String.valueOf(entry.getKey());
            Object value = entry.getValue();
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

            if (value instanceof String str) {
                keyName = str;
            } else if (value instanceof Map<?, ?> lockSection) {
                keyName = readString(lockSection, "key", null);
                creatorName = readString(lockSection, "creator.name", null);
                creatorUuid = parseUuid(readString(lockSection, "creator.uuid", null));
                lastUserName = readString(lockSection, "last-user.name", null);
                lastUserUuid = parseUuid(readString(lockSection, "last-user.uuid", null));
                normalKey = readBoolean(lockSection, "normal.key", false);
                normalArmed = readBoolean(lockSection, "normal.armed", false);
                lastPickUserName = readString(lockSection, "pick.last.name", null);
                lastPickUserUuid = parseUuid(readString(lockSection, "pick.last.uuid", null));
                lastPickType = readString(lockSection, "pick.last.type", null);
                lastPickTimestamp = readLong(lockSection, "pick.last.timestamp", 0L);
                Object pickPlayers = readObject(lockSection, "pick.players");
                if (pickPlayers instanceof Map<?, ?> players) {
                    for (Map.Entry<?, ?> playerEntry : players.entrySet()) {
                        UUID playerUuid = parseUuid(String.valueOf(playerEntry.getKey()));
                        if (playerUuid == null) {
                            continue;
                        }
                        if (!(playerEntry.getValue() instanceof Map<?, ?> pickStateSection)) {
                            continue;
                        }
                        int rLimit = readInt(pickStateSection, "rusty.limit", -1);
                        int rAttempts = readInt(pickStateSection, "rusty.attempts", 0);
                        int nLimit = readInt(pickStateSection, "normal.limit", -1);
                        int nAttempts = readInt(pickStateSection, "normal.attempts", 0);
                        int sLimit = readInt(pickStateSection, "silence.limit", -1);
                        int sAttempts = readInt(pickStateSection, "silence.attempts", 0);
                        int sOver = readInt(pickStateSection, "silence.over-limit-attempts", 0);
                        long sPenalty = readLong(pickStateSection, "silence.penalty-timestamp", 0L);
                        playerPickStates.put(playerUuid, new PickState(rLimit, rAttempts, nLimit, nAttempts, sLimit, sAttempts, sOver, sPenalty));
                    }
                }
                rustyLimit = readInt(lockSection, "pick.rusty.limit", -1);
                rustyAttempts = readInt(lockSection, "pick.rusty.attempts", 0);
                normalLimit = readInt(lockSection, "pick.normal.limit", -1);
                normalAttempts = readInt(lockSection, "pick.normal.attempts", 0);
                silenceLimit = readInt(lockSection, "pick.silence.limit", -1);
                silenceAttempts = readInt(lockSection, "pick.silence.attempts", 0);
                silenceOverLimitAttempts = readInt(lockSection, "pick.silence.over-limit-attempts", 0);
                silencePenaltyTimestamp = readLong(lockSection, "pick.silence.penalty-timestamp", 0L);
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

    public void saveData() {
        Map<String, Object> root = new HashMap<>();
        Map<String, Object> locked = new HashMap<>();
        for (Map.Entry<String, LockInfo> entry : lockedChests.entrySet()) {
            String locationKey = entry.getKey();
            LockInfo info = entry.getValue();
            if (info == null) {
                continue;
            }
            Map<String, Object> lockSection = new HashMap<>();
            lockSection.put("key", info.keyName());
            LocationData locationData = parseLocationKey(locationKey, server.overworld());
            if (locationData != null) {
                lockSection.put("world.name", locationData.worldName());
                if (locationData.realm() != null) {
                    lockSection.put("world.realm", locationData.realm());
                }
                lockSection.put("world.uuid", locationData.worldKey());
            }
            if (info.creatorName() != null) {
                lockSection.put("creator.name", info.creatorName());
            }
            if (info.creatorUuid() != null) {
                lockSection.put("creator.uuid", info.creatorUuid().toString());
            }
            if (info.lastUserName() != null) {
                lockSection.put("last-user.name", info.lastUserName());
            }
            if (info.lastUserUuid() != null) {
                lockSection.put("last-user.uuid", info.lastUserUuid().toString());
            }
            if (info.lastPickUserName() != null) {
                lockSection.put("pick.last.name", info.lastPickUserName());
            }
            if (info.lastPickUserUuid() != null) {
                lockSection.put("pick.last.uuid", info.lastPickUserUuid().toString());
            }
            if (info.lastPickType() != null) {
                lockSection.put("pick.last.type", info.lastPickType());
            }
            if (info.lastPickTimestamp() > 0L) {
                lockSection.put("pick.last.timestamp", info.lastPickTimestamp());
            }
            if (!info.playerPickStates().isEmpty()) {
                Map<String, Object> pickPlayers = new HashMap<>();
                for (Map.Entry<UUID, PickState> stateEntry : info.playerPickStates().entrySet()) {
                    UUID playerId = stateEntry.getKey();
                    PickState state = stateEntry.getValue();
                    Map<String, Object> pickState = new HashMap<>();
                    pickState.put("rusty.limit", state.rustyLimit());
                    pickState.put("rusty.attempts", state.rustyAttempts());
                    pickState.put("normal.limit", state.normalLimit());
                    pickState.put("normal.attempts", state.normalAttempts());
                    pickState.put("silence.limit", state.silenceLimit());
                    pickState.put("silence.attempts", state.silenceAttempts());
                    pickState.put("silence.over-limit-attempts", state.silenceOverLimitAttempts());
                    pickState.put("silence.penalty-timestamp", state.silencePenaltyTimestamp());
                    pickPlayers.put(playerId.toString(), pickState);
                }
                lockSection.put("pick.players", pickPlayers);
            }
            if (info.normalKey()) {
                lockSection.put("normal.key", true);
                lockSection.put("normal.armed", info.normalArmed());
            }
            if (info.rustyLimit() >= 0 || info.rustyAttempts() > 0) {
                lockSection.put("pick.rusty.limit", info.rustyLimit());
                lockSection.put("pick.rusty.attempts", info.rustyAttempts());
            }
            if (info.normalLimit() >= 0 || info.normalAttempts() > 0) {
                lockSection.put("pick.normal.limit", info.normalLimit());
                lockSection.put("pick.normal.attempts", info.normalAttempts());
            }
            if (info.silenceLimit() >= 0 || info.silenceAttempts() > 0 || info.silenceOverLimitAttempts() > 0
                    || info.silencePenaltyTimestamp() > 0L) {
                lockSection.put("pick.silence.limit", info.silenceLimit());
                lockSection.put("pick.silence.attempts", info.silenceAttempts());
                lockSection.put("pick.silence.over-limit-attempts", info.silenceOverLimitAttempts());
                lockSection.put("pick.silence.penalty-timestamp", info.silencePenaltyTimestamp());
            }
            locked.put(locationKey, lockSection);
        }
        root.put("locked-chests", locked);
        try {
            Files.createDirectories(configDir);
            try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(dataFile), StandardCharsets.UTF_8)) {
                yaml.dump(root, writer);
            }
        } catch (IOException ex) {
            LOGGER.warn("Could not save data.yml: {}", ex.getMessage());
        }
    }

    private Map<String, Object> loadYaml(Path path) {
        try {
            if (!Files.exists(path)) {
                return new HashMap<>();
            }
            try (InputStream input = Files.newInputStream(path)) {
                Object data = yaml.load(input);
                if (data instanceof Map<?, ?> map) {
                    Map<String, Object> result = new HashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        result.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    return result;
                }
            }
        } catch (IOException ex) {
            LOGGER.warn("Could not read {}: {}", path.getFileName(), ex.getMessage());
        }
        return new HashMap<>();
    }

    private Object readObject(Map<?, ?> map, String path) {
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return null;
            }
            current = currentMap.get(part);
        }
        return current;
    }

    private String readString(Map<?, ?> map, String path, String def) {
        Object value = readObject(map, path);
        if (value == null) {
            return def;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? def : text;
    }

    private int readInt(Map<?, ?> map, String path, int def) {
        Object value = readObject(map, path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? def : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private long readLong(Map<?, ?> map, String path, long def) {
        Object value = readObject(map, path);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? def : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private double readDouble(Map<?, ?> map, String path, double def) {
        Object value = readObject(map, path);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? def : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private boolean readBoolean(Map<?, ?> map, String path, boolean def) {
        Object value = readObject(map, path);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return def;
        }
        return Boolean.parseBoolean(String.valueOf(value));
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
}

