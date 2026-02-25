package com.ominouschestlock.fabric.mixin;

import com.ominouschestlock.fabric.ChestLockFabric;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
public abstract class PistonHandlerMixin {
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private static void chestlock$isPushable(BlockState state, Level world, BlockPos pos, Direction direction, boolean canBreak, Direction pistonDirection, CallbackInfoReturnable<Boolean> cir) {
        if (world instanceof ServerLevel serverWorld) {
            if (ChestLockFabric.getService().isLockedBlock(serverWorld, pos, state)) {
                cir.setReturnValue(false);
            }
        }
    }
}


