package com.ominouschestlock.fabric.mixin;

import com.ominouschestlock.fabric.ChestLockFabric;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerExplosion.class)
public abstract class ExplosionMixin {
    @Shadow public abstract ServerLevel level();

    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void chestlock$interactWithBlocks(List<BlockPos> positions, CallbackInfo ci) {
        ServerLevel serverWorld = level();
        if (serverWorld != null) {
            ChestLockFabric.getService().onExplosion(serverWorld, (Explosion) (Object) this, positions);
        }
    }
}


