package com.ominouschestlock.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

public final class ChestLockFabric implements ModInitializer {
    private static final LockService SERVICE = new LockService();

    public static LockService getService() {
        return SERVICE;
    }

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(SERVICE::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> SERVICE.saveData());
        UseBlockCallback.EVENT.register(SERVICE::onUseBlock);
        PlayerBlockBreakEvents.BEFORE.register(SERVICE::onBlockBreak);
        CommandRegistrationCallback.EVENT.register(SERVICE::registerCommands);
    }
}


