package io.github.kriskreemurr.quasarmc.lighting.mixin;

import io.github.kriskreemurr.quasarmc.lighting.HeldLightSpawning;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Held / dropped light items and burning mobs count as light for hostile mob spawning. */
@Mixin(Monster.class)
public abstract class MonsterSpawnLightMixin {
    @Inject(method = "isDarkEnoughToSpawn", at = @At("HEAD"), cancellable = true)
    private static void quasarmc$heldLight(ServerLevelAccessor level, BlockPos pos, RandomSource random,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (HeldLightSpawning.litByCarrier(level, pos)) cir.setReturnValue(false);
    }
}
