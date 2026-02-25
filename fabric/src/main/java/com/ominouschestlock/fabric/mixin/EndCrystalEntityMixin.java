package com.ominouschestlock.fabric.mixin;

import com.ominouschestlock.fabric.ChestLockFabric;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndCrystal.class)
public abstract class EndCrystalEntityMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void chestlock$hurtServer(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player) {
            ChestLockFabric.getService().onCrystalDamaged(player, (Entity) (Object) this);
        }
    }
}


