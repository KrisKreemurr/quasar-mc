package io.github.kriskreemurr.quasarmc.quasium.mixin;

import io.github.kriskreemurr.quasarmc.quasium.light.RoundLight;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Terrain light lookups during a section build read the round light field (see {@link RoundLight}). */
@Mixin(LightCoordsUtil.class)
public abstract class LightCoordsUtilMixin {
    @Inject(
            method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void quasium$roundLight(LightCoordsUtil.BrightnessGetter getter, BlockAndLightGetter level, BlockState state,
                                           BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int packed = cir.getReturnValueI();
        int adjusted = RoundLight.adjust(level, pos, packed);
        adjusted = io.github.kriskreemurr.quasarmc.quasium.light.SunShadow.adjust(level, pos, adjusted);
        if (adjusted != packed) cir.setReturnValue(adjusted);
    }
}
