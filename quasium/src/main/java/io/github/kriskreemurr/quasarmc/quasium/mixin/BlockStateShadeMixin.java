package io.github.kriskreemurr.quasarmc.quasium.mixin;

import io.github.kriskreemurr.quasarmc.quasium.QuasiumConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Light sources don't cast ambient-occlusion shadow: vanilla smooth lighting darkens corners next to any full
 * block, so glowstone, froglights, sea lanterns etc. sat in a dark ring of their own making. Light-emitting
 * blocks now count as fully open for AO (with Round Light on; applies on the next section build).
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateShadeMixin {
    @Inject(method = "getShadeBrightness(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F",
            at = @At("HEAD"), cancellable = true)
    private void quasium$lightsCastNoAo(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (QuasiumConfig.roundLight && ((BlockBehaviour.BlockStateBase) (Object) this).getLightEmission() > 0) {
            cir.setReturnValue(1.0f);
        }
    }
}
