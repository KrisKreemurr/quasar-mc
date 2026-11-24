package io.github.kriskreemurr.quasarmc.quasium.light;

import io.github.kriskreemurr.quasarmc.quasium.Quasium;
import io.github.kriskreemurr.quasarmc.quasium.QuasiumConfig;
import io.github.kriskreemurr.quasarmc.quasium.mixin.RenderSectionRegionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

/**
 * Baked sun shadows (first version, visual only).
 *
 * <p>While a chunk section is meshed, every sky-lit cell the terrain samples is tested against the sun: a
 * voxel walk toward the sun through the section's 3x3x3 neighbourhood. Shaded cells get their sky light
 * lowered, so the shadow is drawn by the normal lighting (vanilla and Meshelium alike) and smooth lighting
 * softens the edge over one block. The sun moves, so sections near the camera are re-meshed a few at a time
 * whenever it has moved a few degrees.</p>
 */
public final class SunShadow {
    private static final int N = 48, N2 = N * N;
    private static final byte UNKNOWN = 0, LIT = 1, SHADE = 2;
    /** Re-bake when the sun has moved this far (radians, ~6 degrees ≈ 20 s of real time). */
    private static final double REBAKE_STEP = Math.toRadians(6);
    /** Sections around the camera that follow the sun (horizontal radius in sections, vertical half-height). */
    private static final int REBAKE_RADIUS = 8, REBAKE_HEIGHT = 4;
    private static final int REBAKE_PER_TICK = 160;

    /** Sun direction (unit vector toward the sun) and strength 0..1, set on the client tick, read by workers. */
    private static volatile float[] sun = {0, 1, 0, 0};

    private static final class Work {
        final byte[] state = new byte[N * N2];
        RenderSectionRegion region;
        int ox, oy, oz;
        float dx, dy, dz, strength;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    }

    private static final ThreadLocal<Work> WORK = ThreadLocal.withInitial(Work::new);

    private SunShadow() {}

    // ---- client tick: sun position + gradual re-bake ---------------------------------------------

    private static double bakedAngle = Double.NaN;
    private static int rebakeCursor = -1, rebakeCx, rebakeCy, rebakeCz;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !QuasiumConfig.sunShadows || !level.dimensionType().hasSkyLight()) {
            sun = new float[]{0, 1, 0, 0};
            bakedAngle = Double.NaN;
            return;
        }
        // Minecraft's sun: rises in the east (+X) at day time 0, overhead at 6000, sets west at 12000.
        double t = Math.floorMod(level.getDefaultClockTime(), 24000L) / 24000.0;
        double a = t * Math.PI * 2;
        float sx = (float) Math.cos(a), sy = (float) Math.sin(a);
        // full shadows in daylight, fading out as the sun gets low (and none at night)
        float strength = (float) Math.max(0, Math.min(1, (sy - 0.05) * 4));
        sun = new float[]{sx, sy, 0, strength};

        if (Double.isNaN(bakedAngle) || Math.abs(a - bakedAngle) >= REBAKE_STEP) {
            bakedAngle = a;
            BlockPos cam = mc.gameRenderer.mainCamera().blockPosition();
            rebakeCx = SectionPos.blockToSectionCoord(cam.getX());
            rebakeCy = SectionPos.blockToSectionCoord(cam.getY());
            rebakeCz = SectionPos.blockToSectionCoord(cam.getZ());
            rebakeCursor = 0;
        }
        if (rebakeCursor >= 0) {
            int side = REBAKE_RADIUS * 2 + 1, height = REBAKE_HEIGHT * 2 + 1;
            int total = side * side * height;
            var extractor = mc.levelExtractor;
            for (int k = 0; k < REBAKE_PER_TICK && rebakeCursor < total; k++, rebakeCursor++) {
                int i = rebakeCursor;
                int x = i % side, z = (i / side) % side, y = i / (side * side);
                if (extractor != null) extractor.setSectionDirty(rebakeCx + x - REBAKE_RADIUS, rebakeCy + y - REBAKE_HEIGHT,
                        rebakeCz + z - REBAKE_RADIUS);
            }
            if (rebakeCursor >= total) rebakeCursor = -1;
        }
    }

    // ---- section build ---------------------------------------------------------------------------

    public static void begin(RenderSectionRegion region) {
        Work w = WORK.get();
        w.region = null;
        float[] s = sun;
        if (!QuasiumConfig.sunShadows || s[3] <= 0) return;
        RenderSectionRegionAccessor acc = (RenderSectionRegionAccessor) region;
        w.ox = acc.quasium$minSectionX() << 4;
        w.oy = acc.quasium$minSectionY() << 4;
        w.oz = acc.quasium$minSectionZ() << 4;
        w.dx = s[0];
        w.dy = s[1];
        w.dz = s[2];
        w.strength = s[3] * QuasiumConfig.sunShadowStrength / 100f;
        Arrays.fill(w.state, UNKNOWN);
        w.region = region;
    }

    public static void end() {
        WORK.get().region = null;
    }

    /** Light lookup hook: lower the sky half of {@code packed} where the sun is blocked. */
    public static int adjust(BlockAndLightGetter level, BlockPos pos, int packed) {
        Work w = WORK.get();
        if (w.region == null || w.region != level || packed == 15728880) return packed;
        int sky = (packed >>> 16) & 0xFF;
        if (sky == 0) return packed;
        int x = pos.getX() - w.ox, y = pos.getY() - w.oy, z = pos.getZ() - w.oz;
        if (x < 0 || y < 0 || z < 0 || x >= N || y >= N || z >= N) return packed;
        try {
            if (!shaded(w, x, y, z)) return packed;
        } catch (Throwable t) {
            w.region = null;
            Quasium.LOGGER.error("Quasium sun shadows failed; this section draws without them", t);
            return packed;
        }
        int newSky = Math.round(sky * (1f - w.strength));
        return (packed & 0xFF00FFFF) | (newSky << 16);
    }

    private static boolean shaded(Work w, int x, int y, int z) {
        int idx = (y * N + z) * N + x;
        byte s = w.state[idx];
        if (s != UNKNOWN) return s == SHADE;
        boolean hit = march(w, x, y, z);
        w.state[idx] = hit ? SHADE : LIT;
        return hit;
    }

    /** Voxel walk (Amanatides-Woo) from the cell centre toward the sun; true if a sun-blocking block is hit. */
    private static boolean march(Work w, int cx, int cy, int cz) {
        double px = cx + 0.5, py = cy + 0.5, pz = cz + 0.5;
        double dx = w.dx, dy = w.dy, dz = w.dz;
        int x = cx, y = cy, z = cz;
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0, stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0, stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tDeltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
        double tDeltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
        double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
        double tMaxX = stepX == 0 ? Double.MAX_VALUE : (stepX > 0 ? (x + 1 - px) : (px - x)) * tDeltaX;
        double tMaxY = stepY == 0 ? Double.MAX_VALUE : (stepY > 0 ? (y + 1 - py) : (py - y)) * tDeltaY;
        double tMaxZ = stepZ == 0 ? Double.MAX_VALUE : (stepZ > 0 ? (z + 1 - pz) : (pz - z)) * tDeltaZ;
        for (int i = 0; i < 64; i++) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ) { x += stepX; tMaxX += tDeltaX; }
            else if (tMaxY < tMaxZ) { y += stepY; tMaxY += tDeltaY; }
            else { z += stepZ; tMaxZ += tDeltaZ; }
            if (x < 0 || y < 0 || z < 0 || x >= N || y >= N || z >= N) return false; // left the region: assume sunlit
            w.pos.set(w.ox + x, w.oy + y, w.oz + z);
            BlockState st = w.region.getBlockState(w.pos);
            if (st.isAir()) continue;
            if (st.isSolidRender() || st.is(BlockTags.LEAVES)) return true;
        }
        return false;
    }
}
