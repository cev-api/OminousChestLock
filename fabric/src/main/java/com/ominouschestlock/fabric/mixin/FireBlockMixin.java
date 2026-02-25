package com.ominouschestlock.fabric.mixin;

import com.ominouschestlock.fabric.ChestLockFabric;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void chestlock$checkBurnOut(Level world, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
        if (world instanceof ServerLevel serverWorld) {
            boolean allowed = ChestLockFabric.getService().onFireBurn(serverWorld, pos);
            if (!allowed) {
                ci.cancel();
            }
        }
    }
}


