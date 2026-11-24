package io.github.kriskreemurr.quasarmc.quasium.light;

import io.github.kriskreemurr.quasarmc.core.light.DynamicLightSources;
import io.github.kriskreemurr.quasarmc.quasium.QuasiumConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;

/**
 * Dynamic lights (client, visual): held torches, dropped light items and burning entities light up their
 * surroundings. Each light is fed into {@link RoundLight} as an extra emitter, so it is round like placed
 * lights. Terrain only re-meshes when a light moves into another block, throttled by the
 * "Dynamic Lights" setting and by distance; entities near a light are lit every frame (see
 * {@link #entityLight}).
 */
public final class DynamicLights {
    /** A light as the chunk builder sees it (published as an immutable array for the worker threads). */
    public record Source(int x, int y, int z, int lum) {}

    private static final class Tracked {
        long pos;
        int lum;
        long nextAllowed;
        boolean seen;
    }

    private static final Source[] NONE = new Source[0];
    private static volatile Source[] published = NONE;
    private static final Int2ObjectOpenHashMap<Tracked> tracked = new Int2ObjectOpenHashMap<>();
    private static ClientLevel trackedLevel;
    private static int updatesThisSecond, shownUpdates;
    private static long secondStart;

    private DynamicLights() {}

    /** Lights the chunk builder should add (safe to read from any thread). */
    public static Source[] sources() {
        return published;
    }

    /** Minimum time between moves of one light, by setting, before distance scaling (nanos). */
    private static long baseInterval() {
        return switch (QuasiumConfig.dynamicLights) {
            case 1 -> 500_000_000L;  // Eco: ~2 per second
            case 2 -> 200_000_000L;  // Balanced: ~5 per second
            default -> 0L;           // Smooth: every block crossed (checked every tick)
        };
    }

    /** Client tick: find moving lights and re-mesh the chunk sections their light touches. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != trackedLevel) {
            tracked.clear();
            published = NONE;
            trackedLevel = level;
        }
        if (level == null) return;
        if (QuasiumConfig.dynamicLights == 0) {
            if (!tracked.isEmpty()) {
                for (Tracked t : tracked.values()) dirty(BlockPos.of(t.pos), t.lum);
                tracked.clear();
                published = NONE;
            }
            return;
        }
        long now = System.nanoTime();
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        long base = baseInterval();
        boolean changed = false;
        for (Tracked t : tracked.values()) t.seen = false;
        for (Entity e : level.entitiesForRendering()) {
            int lum = DynamicLightSources.luminance(e);
            if (lum <= 0) continue;
            long pos = DynamicLightSources.lightPos(e).asLong();
            Tracked t = tracked.get(e.getId());
            if (t == null) {
                t = new Tracked();
                t.pos = pos;
                t.lum = lum;
                tracked.put(e.getId(), t);
                dirty(BlockPos.of(pos), lum);
                changed = true;
                updatesThisSecond++;
            } else if ((t.pos != pos || t.lum != lum) && now >= t.nextAllowed) {
                dirty(BlockPos.of(t.pos), t.lum);
                dirty(BlockPos.of(pos), lum);
                t.pos = pos;
                t.lum = lum;
                changed = true;
                updatesThisSecond++;
                double d2 = e.distanceToSqr(cam);
                long interval = base == 0 ? 0 : d2 > 64 * 64 ? base * 4 : d2 > 32 * 32 ? base * 2 : base;
                if (base == 0 && d2 > 64 * 64) interval = 200_000_000L;      // far lights: max ~5/s even on Smooth
                else if (base == 0 && d2 > 32 * 32) interval = 100_000_000L; // mid: max ~10/s
                t.nextAllowed = now + interval;
            }
            t.seen = true;
        }
        for (Iterator<Tracked> it = tracked.values().iterator(); it.hasNext(); ) {
            Tracked t = it.next();
            if (!t.seen) {
                dirty(BlockPos.of(t.pos), t.lum);
                it.remove();
                changed = true;
            }
        }
        if (changed) publish();
        if (now - secondStart >= 1_000_000_000L) {
            secondStart = now;
            shownUpdates = updatesThisSecond;
            updatesThisSecond = 0;
        }
    }

    private static void publish() {
        Source[] out = new Source[tracked.size()];
        int i = 0;
        for (Tracked t : tracked.values()) {
            out[i++] = new Source(BlockPos.getX(t.pos), BlockPos.getY(t.pos), BlockPos.getZ(t.pos), t.lum);
        }
        published = out;
    }

    /** Re-mesh every section the light of a source at {@code pos} can reach. */
    private static void dirty(BlockPos pos, int lum) {
        var extractor = Minecraft.getInstance().levelExtractor;
        if (extractor == null) return;
        int r = lum + 1;
        int x0 = SectionPos.blockToSectionCoord(pos.getX() - r), x1 = SectionPos.blockToSectionCoord(pos.getX() + r);
        int y0 = SectionPos.blockToSectionCoord(pos.getY() - r), y1 = SectionPos.blockToSectionCoord(pos.getY() + r);
        int z0 = SectionPos.blockToSectionCoord(pos.getZ() - r), z1 = SectionPos.blockToSectionCoord(pos.getZ() + r);
        for (int sx = x0; sx <= x1; sx++)
            for (int sy = y0; sy <= y1; sy++)
                for (int sz = z0; sz <= z1; sz++)
                    extractor.setSectionDirty(sx, sy, sz);
    }

    /**
     * Extra block light (0..15, fractional) that dynamic lights give a point, for lighting entities.
     * Straight-line falloff like the terrain; walls aren't checked here (cheap, and entities are small).
     */
    public static float entityLight(Vec3 p) {
        Source[] src = published;
        float best = 0;
        for (Source s : src) {
            double dx = p.x - (s.x() + 0.5), dy = p.y - (s.y() + 0.5), dz = p.z - (s.z() + 0.5);
            float v = s.lum() - (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (v > best) best = v;
        }
        return best;
    }

    public static String f3Line() {
        if (QuasiumConfig.dynamicLights == 0) return "Dynamic lights: off";
        return "Dynamic lights: " + published.length + " active, " + shownUpdates + " moves/s";
    }
}
