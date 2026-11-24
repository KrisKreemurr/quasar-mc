package io.github.kriskreemurr.quasarmc.lighting;

import io.github.kriskreemurr.quasarmc.core.light.DynamicLightSources;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Gameplay side of dynamic lights: a held torch, a dropped glowstone or a burning mob stops hostile mobs
 * spawning where its light reaches, exactly like a placed torch would.
 *
 * <p>Nothing is added to the server's light data (that would mean relighting every time someone moves).
 * Instead the monster spawn check asks: is a light carrier close enough, with a clear line, that its
 * light at this spot is at least 1? Same round reach as the client draws. Only runs on real spawn
 * attempts, against the few entities near that spot.
 */
public final class HeldLightSpawning {
    private HeldLightSpawning() {}

    public static boolean litByCarrier(ServerLevelAccessor level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(15);
        Vec3 target = Vec3.atCenterOf(pos);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box, x -> DynamicLightSources.luminance(x) > 0)) {
            int lum = DynamicLightSources.luminance(e);
            BlockPos lp = DynamicLightSources.lightPos(e);
            Vec3 from = Vec3.atCenterOf(lp);
            double dist = from.distanceTo(target);
            if (lum - dist < 1) continue;
            if (lp.equals(pos) || clearLine(level, from, target, pos)) return true;
        }
        return false;
    }

    /** No full block between the light and the spawn spot (glass counts as blocking; conservative). */
    private static boolean clearLine(ServerLevelAccessor level, Vec3 from, Vec3 to, BlockPos target) {
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }
}
