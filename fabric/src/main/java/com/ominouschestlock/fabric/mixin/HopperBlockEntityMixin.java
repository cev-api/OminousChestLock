package com.ominouschestlock.fabric.mixin;

import com.ominouschestlock.fabric.ChestLockFabric;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Inject(method = "tryMoveItems", at = @At("HEAD"), cancellable = true)
    private static void chestlock$tryMoveItems(Level world, BlockPos pos, BlockState state, HopperBlockEntity hopper, java.util.function.BooleanSupplier supplier, CallbackInfoReturnable<Boolean> cir) {
        if (!(world instanceof ServerLevel serverWorld)) {
            return;
        }
        Container source = HopperBlockEntity.getContainerAt(world, pos.above());
        Direction facing = state.getValue(HopperBlock.FACING);
        Container destination = HopperBlockEntity.getContainerAt(world, pos.relative(facing));
        boolean allowed = ChestLockFabric.getService().onHopperTransfer(serverWorld, source, destination);
        if (!allowed) {
            cir.setReturnValue(false);
        }
    }
}


