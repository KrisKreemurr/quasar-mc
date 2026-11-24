package io.github.kriskreemurr.quasarmc.quasium.light;

import io.github.kriskreemurr.quasarmc.quasium.QuasiumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Colored light on entities. When an entity's render state is built, the light colors of the 8 blocks around
 * its light probe are blended (weighted by how much block light each holds, like the terrain shader does) and
 * turned into a model tint: full light color where block light dominates, none where daylight does. The tint is
 * applied to the model's tint color when it is submitted ({@link #current}).
 *
 * <p>Covers models (mobs, players, armor). Not yet: held items, Figura avatars (their own geometry path).</p>
 */
public final class EntityTint {
    /** ARGB multiplier for the entity being submitted right now (render thread), -1 = none. */
    public static int current = -1;

    public interface Holder {
        int quasium$tint();
        void quasium$setTint(int tint);
    }

    private static long lastPrune;

    private EntityTint() {}

    /** Tint for this entity's state (light already in {@code state.lightCoords}). */
    public static int compute(Entity entity, EntityRenderState state, float partialTick) {
        if (!QuasiumConfig.coloredLight) return -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return -1;
        long now = System.nanoTime();
        if (now - lastPrune > 1_000_000_000L) {
            lastPrune = now;
            Vec3 cam = mc.gameRenderer.mainCamera().position();
            ColorBridge.prune(Mth.floor(cam.x) >> 4, Mth.floor(cam.z) >> 4, mc.options.getEffectiveRenderDistance() + 1);
        }
        int lc = state.lightCoords;
        float block = (lc & 0xFFFF) / 16f, sky = (lc >>> 16) / 16f;
        if (block < 0.5f) return -1;

        Vec3 p = entity.getLightProbePosition(partialTick);
        double gx = p.x - 0.5, gy = p.y - 0.5, gz = p.z - 0.5;
        int bx = Mth.floor(gx), by = Mth.floor(gy), bz = Mth.floor(gz);
        float fx = (float) (gx - bx), fy = (float) (gy - by), fz = (float) (gz - bz);
        float r = 0, g = 0, b = 0, wsum = 0;
        for (int i = 0; i < 8; i++) {
            int dx = i & 1, dy = (i >> 1) & 1, dz = (i >> 2) & 1;
            float w = (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
            int c = ColorBridge.cell(bx + dx, by + dy, bz + dz);
            float a = (c >>> 24) * w;
            if (a <= 0) continue;
            r += a * (c & 0xFF);
            g += a * ((c >> 8) & 0xFF);
            b += a * ((c >> 16) & 0xFF);
            wsum += a;
        }
        if (wsum <= 0) return -1;
        r /= wsum * 255f; g /= wsum * 255f; b /= wsum * 255f;
        float m = Math.max(r, Math.max(g, b));
        if (m <= 0) return -1;
        r /= m; g /= m; b /= m;

        // How much of the entity's light is block light: daylight (sky light scaled by time of day) washes it out.
        float day = 1f - mc.level.getSkyDarken() / 15f;
        float skyEff = sky * day;
        float strength = block / Math.max(block + skyEff, 1e-3f);
        // Vanilla already tints block light warm; replacing it fully would overshoot, so stop at 85 %.
        strength *= 0.85f;
        int ri = (int) (255 * (1 + (r - 1) * strength));
        int gi = (int) (255 * (1 + (g - 1) * strength));
        int bi = (int) (255 * (1 + (b - 1) * strength));
        return 0xFF000000 | ri << 16 | gi << 8 | bi;
    }

    /** Multiply two ARGB colors. */
    public static int multiply(int a, int b) {
        int aa = ((a >>> 24) * (b >>> 24)) / 255;
        int ar = (((a >> 16) & 0xFF) * ((b >> 16) & 0xFF)) / 255;
        int ag = (((a >> 8) & 0xFF) * ((b >> 8) & 0xFF)) / 255;
        int ab = ((a & 0xFF) * (b & 0xFF)) / 255;
        return aa << 24 | ar << 16 | ag << 8 | ab;
    }
}
