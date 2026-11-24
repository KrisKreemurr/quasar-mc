package io.github.kriskreemurr.quasarmc.quasium.light;

import net.minecraft.world.entity.Entity;

/**
 * Smooth entity light: entities no longer snap between whole light levels.
 *
 * <p>Vanilla lights a whole entity with the light of the single block its light probe is in, so brightness
 * jumps when the probe crosses a block boundary. Quasium blends the 8 nearest block centres by distance
 * (trilinear) and then eases toward that value over ~0.1 s, which also softens torches being placed or broken.
 * The result is packed like vanilla's light coords, just with in-between values (vanilla's lightmap is sampled
 * with filtering, the same way smooth terrain lighting uses it).
 *
 * <p>Stopgap until the GPU light volume exists: then entities read light per pixel and this goes away.
 */
public final class SmoothEntityLight {
    /** Time constant of the easing, in seconds. */
    private static final double TAU = 0.1;
    /** After this long without an update (off screen, just spawned) snap straight to the new value. */
    private static final long SNAP_AFTER_NANOS = 500_000_000L;

    private SmoothEntityLight() {}

    /** Per-entity smoothing state, added to {@link Entity} by a mixin. */
    public interface Holder {
        float quasium$lightBlock();
        float quasium$lightSky();
        long quasium$lightTime();
        void quasium$setLight(float block, float sky, long time);
    }

    /**
     * Eases the entity's stored light toward the target (both in light levels 0..15) and returns packed
     * light coords.
     */
    public static int ease(Entity entity, float targetBlock, float targetSky) {
        Holder h = (Holder) entity;
        long now = System.nanoTime();
        long last = h.quasium$lightTime();
        float block = targetBlock;
        float sky = targetSky;
        if (last != 0 && now - last < SNAP_AFTER_NANOS) {
            double dt = (now - last) / 1.0e9;
            float a = (float) (1.0 - Math.exp(-dt / TAU));
            block = h.quasium$lightBlock() + (targetBlock - h.quasium$lightBlock()) * a;
            sky = h.quasium$lightSky() + (targetSky - h.quasium$lightSky()) * a;
        }
        h.quasium$setLight(block, sky, now);
        return pack(block, sky);
    }

    /** Same layout as {@code LightCoordsUtil.pack}: level * 16 in each 16-bit half, here with fractions kept. */
    public static int pack(float block, float sky) {
        int b = Math.max(0, Math.min(240, Math.round(block * 16f)));
        int s = Math.max(0, Math.min(240, Math.round(sky * 16f)));
        return b | (s << 16);
    }
}
