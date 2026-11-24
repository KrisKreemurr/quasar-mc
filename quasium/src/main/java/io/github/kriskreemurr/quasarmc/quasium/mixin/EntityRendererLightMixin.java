package io.github.kriskreemurr.quasarmc.quasium.mixin;

import io.github.kriskreemurr.quasarmc.quasium.QuasiumConfig;
import io.github.kriskreemurr.quasarmc.quasium.light.SmoothEntityLight;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Smooth entity light (see {@link SmoothEntityLight}). Samples through the renderer's own
 * {@code getBlockLightLevel}/{@code getSkyLightLevel}, so burning entities and mobs that glow
 * (blazes, magma cubes, glow squids, ...) keep their vanilla brightness.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererLightMixin<T extends Entity> {
    @Shadow protected abstract int getBlockLightLevel(T entity, BlockPos pos);
    @Shadow protected abstract int getSkyLightLevel(T entity, BlockPos pos);

    @Inject(method = "getPackedLightCoords", at = @At("HEAD"), cancellable = true)
    private void quasium$smoothLight(T entity, float partialTick, CallbackInfoReturnable<Integer> cir) {
        if (!QuasiumConfig.smoothEntityLight) return;
        Level level = entity.level();
        Vec3 p = entity.getLightProbePosition(partialTick);
        // Block centres sit at +0.5, so blend between the 8 centres around the probe.
        double gx = p.x - 0.5, gy = p.y - 0.5, gz = p.z - 0.5;
        int bx = Mth.floor(gx), by = Mth.floor(gy), bz = Mth.floor(gz);
        float fx = (float) (gx - bx), fy = (float) (gy - by), fz = (float) (gz - bz);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        float blockSum = 0, skySum = 0, weightSum = 0;
        for (int i = 0; i < 8; i++) {
            int dx = i & 1, dy = (i >> 1) & 1, dz = (i >> 2) & 1;
            float w = (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
            if (w < 1.0e-4f) continue;
            m.set(bx + dx, by + dy, bz + dz);
            // Solid blocks are dark inside; counting them would darken entities that stand next to walls.
            if (level.getBlockState(m).getLightDampening() >= 15) continue;
            blockSum += w * getBlockLightLevel(entity, m);
            skySum += w * getSkyLightLevel(entity, m);
            weightSum += w;
        }
        float block, sky;
        if (weightSum < 1.0e-3f) {
            BlockPos pos = BlockPos.containing(p);
            block = getBlockLightLevel(entity, pos);
            sky = getSkyLightLevel(entity, pos);
        } else {
            block = blockSum / weightSum;
            sky = skySum / weightSum;
        }
        cir.setReturnValue(SmoothEntityLight.ease(entity, block, sky));
    }
}
