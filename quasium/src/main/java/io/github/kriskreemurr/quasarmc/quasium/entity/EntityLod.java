package io.github.kriskreemurr.quasarmc.quasium.entity;

import io.github.kriskreemurr.quasarmc.quasium.QuasiumConfig;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/**
 * Quasium entity LOD: far entities rebuild their render state (pose, animation, position)
 * fewer times per second and reuse the last one in between.
 *
 * Players:  0-64 blocks every frame, 64-128 at 60/s, 128-256 at 30/s, beyond at 15/s.
 * Others use half those distances.
 */
public final class EntityLod {
    private static final long[] INTERVALS = {0L, 16_666_667L, 33_333_333L, 66_666_667L};

    // F3 stats (render thread only)
    private static int frameBuilt, frameReused;
    private static long frameNanos;
    private static double avgBuilt, avgReused, avgMs;
    private static final double SMOOTHING = 0.05;

    private EntityLod() {}

    /** Nanoseconds between render-state rebuilds for this entity, 0 = every frame. */
    public static long interval(Entity entity, double distanceSq) {
        if (!QuasiumConfig.entityLod) return 0;
        // riders and mounts must stay in step, so they always update
        if (entity.isPassenger() || entity.isVehicle()) return 0;
        double d = Math.sqrt(distanceSq);
        if (!(entity instanceof Player)) d *= 2; // mobs reach each tier at half the distance
        if (d <= 64) return INTERVALS[0];
        if (d <= 128) return INTERVALS[1];
        if (d <= 256) return INTERVALS[2];
        return INTERVALS[3];
    }

    /** Spreads entities loaded at the same moment over different frames. */
    public static long firstDelay(Entity entity, long interval) {
        return (entity.getId() & 7) * interval / 8;
    }

    public static void countReused() {
        frameReused++;
    }

    public static void countBuilt(long nanos) {
        frameBuilt++;
        frameNanos += nanos;
    }

    /** Called once per frame before entities are extracted. */
    public static void newFrame() {
        avgBuilt += (frameBuilt - avgBuilt) * SMOOTHING;
        avgReused += (frameReused - avgReused) * SMOOTHING;
        avgMs += (frameNanos / 1_000_000.0 - avgMs) * SMOOTHING;
        frameBuilt = 0;
        frameReused = 0;
        frameNanos = 0;
    }

    public static String f3Line() {
        double total = avgBuilt + avgReused;
        return String.format(Locale.ROOT, "Quasium entities: %.0f drawn, %.0f rebuilt, %.0f reused (%.0f%%), rebuild %.3f ms%s",
                total, avgBuilt, avgReused, total > 0 ? 100 * avgReused / total : 0, avgMs,
                QuasiumConfig.entityLod ? "" : " [entity LOD off]");
    }

    /** Per-entity cache, added to Entity by a mixin. */
    public interface Holder {
        EntityRenderState quasium$lodState();
        void quasium$setLodState(EntityRenderState state);
        long quasium$lodNext();
        void quasium$setLodNext(long nanos);
    }
}
