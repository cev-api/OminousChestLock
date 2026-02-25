package com.ominouschestlock.fabric.mixin;

import com.ominouschestlock.fabric.ChestLockFabric;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void chestlock$place(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue() != InteractionResult.SUCCESS) {
            return;
        }
        Level world = context.getLevel();
        if (world.isClientSide()) {
            return;
        }
        BlockPos pos = context.getClickedPos();
        if (world.getBlockState(pos).is(Blocks.HOPPER) && context.getPlayer() instanceof ServerPlayer player) {
            ChestLockFabric.getService().onHopperPlaced(player, pos);
        }
    }
}


