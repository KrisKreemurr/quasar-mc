package io.github.kriskreemurr.quasarmc.quasium.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import io.github.kriskreemurr.quasarmc.quasium.light.EntityTint;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.MovingBlockFeatureRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Falling sand, piston-moved blocks: tint each face with the light color where the block is. */
@Mixin(MovingBlockFeatureRenderer.class)
public abstract class MovingBlockTintMixin {
    @Inject(method = "putBakedQuad", at = @At("HEAD"))
    private void quasium$tint(PoseStack pose, float x, float y, float z, BakedQuad quad, QuadInstance instance,
            ChunkSectionLayer layer, int outlineColor, CallbackInfo ci) {
        net.minecraft.core.BlockPos pos = EntityTint.movingPos;
        if (pos == null) return;
        int tint = EntityTint.computeBlock(pos, instance.getLightCoords(0));
        if (tint != -1) instance.multiplyColor(tint);
    }
}
