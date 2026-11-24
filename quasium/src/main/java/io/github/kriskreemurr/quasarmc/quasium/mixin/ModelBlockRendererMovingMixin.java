package io.github.kriskreemurr.quasarmc.quasium.mixin;

import io.github.kriskreemurr.quasarmc.quasium.light.EntityTint;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers which moving block is being built (render thread), for {@link MovingBlockTintMixin}. */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMovingMixin {
    @Inject(method = "tesselateBlock", at = @At("HEAD"))
    private void quasium$movingBegin(BlockQuadOutput out, float x, float y, float z, BlockAndTintGetter level, BlockPos pos,
            BlockState state, BlockStateModel model, long seed, CallbackInfo ci) {
        if (level instanceof MovingBlockRenderState) EntityTint.movingPos = pos.immutable();
    }

    @Inject(method = "tesselateBlock", at = @At("RETURN"))
    private void quasium$movingEnd(BlockQuadOutput out, float x, float y, float z, BlockAndTintGetter level, BlockPos pos,
            BlockState state, BlockStateModel model, long seed, CallbackInfo ci) {
        if (level instanceof MovingBlockRenderState) EntityTint.movingPos = null;
    }
}
