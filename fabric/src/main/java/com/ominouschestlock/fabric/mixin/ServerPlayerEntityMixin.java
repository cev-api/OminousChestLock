package com.ominouschestlock.fabric.mixin;

import com.ominouschestlock.fabric.ChestLockFabric;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.MenuProvider;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntityMixin {
    @Inject(method = "openMenu", at = @At("HEAD"), cancellable = true)
    private void chestlock$openMenu(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (provider instanceof BlockEntity blockEntity) {
            ServerPlayer player = (ServerPlayer) (Object) this;
            boolean allowed = ChestLockFabric.getService().onOpenLockedContainer(player, blockEntity);
            if (!allowed) {
                cir.setReturnValue(OptionalInt.empty());
            }
        }
    }

    @Inject(method = "closeContainer", at = @At("HEAD"))
    private void chestlock$closeContainer(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        ChestLockFabric.getService().onCloseLockedContainer(player);
    }
}


