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
    public record Source(int x, int y, int z, int lum, float[] color) {}

    private static final class Tracked {
        Entity entity;
        long pos;
        int lum;
        float[] color = LightColors.WARM_WHITE;
        long nextAllowed;
        boolean seen;
    }

    private static final Source[] NONE = new Source[0];
    private static volatile Source[] published = NONE;
    private static final Int2ObjectOpenHashMap<Tracked> tracked = new Int2ObjectOpenHashMap<>();
    private static ClientLevel trackedLevel;
    private static int updatesThisSecond, shownUpdates, sectionsThisSecond, shownSections;
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
            exact = new double[0];
            exactCount = 0;
            trackedLevel = level;
        }
        if (level == null) return;
        if (QuasiumConfig.dynamicLights == 0) {
            if (!tracked.isEmpty()) {
                for (Tracked t : tracked.values()) dirty(BlockPos.of(t.pos), t.lum);
                tracked.clear();
                published = NONE;
            }
            exact = new double[0];
            exactCount = 0;
            pushToMeshelium();
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
                t.entity = e;
                t.pos = pos;
                t.lum = lum;
                t.color = LightColors.of(e);
                tracked.put(e.getId(), t);
                dirty(BlockPos.of(pos), lum);
                changed = true;
                updatesThisSecond++;
            } else if ((t.pos != pos || t.lum != lum) && now >= t.nextAllowed) {
                dirty(BlockPos.of(t.pos), t.lum);
                dirty(BlockPos.of(pos), lum);
                t.pos = pos;
                t.lum = lum;
                t.color = LightColors.of(e);
                changed = true;
                updatesThisSecond++;
                double d2 = e.distanceToSqr(cam);
                long interval = base == 0 ? 0 : d2 > 64 * 64 ? base * 4 : d2 > 32 * 32 ? base * 2 : base;
                if (base == 0 && d2 > 64 * 64) interval = 200_000_000L;      // far lights: max ~5/s even on Smooth
                else if (base == 0 && d2 > 32 * 32) interval = 100_000_000L; // mid: max ~10/s
                t.nextAllowed = now + interval;
            }
            t.entity = e;
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
            shownSections = sectionsThisSecond;
            sectionsThisSecond = 0;
        }
    }

    private static void publish() {
        Source[] out = new Source[tracked.size()];
        int i = 0;
        for (Tracked t : tracked.values()) {
            out[i++] = new Source(BlockPos.getX(t.pos), BlockPos.getY(t.pos), BlockPos.getZ(t.pos), t.lum, t.color);
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
        // only sections the light's sphere actually reaches (the cube's corner sections usually aren't)
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        double r2 = (double) r * r;
        for (int sx = x0; sx <= x1; sx++)
            for (int sy = y0; sy <= y1; sy++)
                for (int sz = z0; sz <= z1; sz++) {
                    double dx = Math.max(0, Math.max((sx << 4) - cx, cx - ((sx << 4) + 16)));
                    double dy = Math.max(0, Math.max((sy << 4) - cy, cy - ((sy << 4) + 16)));
                    double dz = Math.max(0, Math.max((sz << 4) - cz, cz - ((sz << 4) + 16)));
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    extractor.setSectionDirty(sx, sy, sz);
                    sectionsThisSecond++;
                }
    }

    // ---- per frame: exact (interpolated) light positions ------------------------------------------

    /** Doubles per light, same layout as Meshelium's QuasarLights: exact xyz, baked-centre xyz, level. */
    private static final int STRIDE = 7;
    private static final int MAX_GPU = 16;
    private static double[] exact = new double[0];
    private static int exactCount;

    /** Render thread, once per frame: exact light positions for entities and for Meshelium's smoothing. */
    public static void frame(float partialTick, Vec3 cam) {
        if (QuasiumConfig.dynamicLights == 0 || tracked.isEmpty()) {
            if (exactCount != 0) {
                exact = new double[0];
                exactCount = 0;
                pushToMeshelium();
            }
            return;
        }
        int n = tracked.size();
        double[] out = new double[n * STRIDE];
        double[] dist = new double[n];
        int i = 0;
        for (Tracked t : tracked.values()) {
            Entity e = t.entity;
            if (e == null || e.isRemoved()) continue;
            Vec3 p = e instanceof net.minecraft.world.entity.item.ItemEntity
                    ? e.getPosition(partialTick).add(0, 0.25, 0)
                    : e.getEyePosition(partialTick);
            int b = i * STRIDE;
            out[b] = p.x;
            out[b + 1] = p.y;
            out[b + 2] = p.z;
            out[b + 3] = BlockPos.getX(t.pos) + 0.5;
            out[b + 4] = BlockPos.getY(t.pos) + 0.5;
            out[b + 5] = BlockPos.getZ(t.pos) + 0.5;
            out[b + 6] = t.lum;
            dist[i] = p.distanceToSqr(cam);
            i++;
        }
        // the GPU takes the 16 nearest; sort by distance (n is small)
        for (int a = 1; a < i; a++) {
            for (int c = a; c > 0 && dist[c] < dist[c - 1]; c--) {
                double td = dist[c]; dist[c] = dist[c - 1]; dist[c - 1] = td;
                for (int k = 0; k < STRIDE; k++) {
                    double tv = out[c * STRIDE + k];
                    out[c * STRIDE + k] = out[(c - 1) * STRIDE + k];
                    out[(c - 1) * STRIDE + k] = tv;
                }
            }
        }
        exact = out;
        exactCount = i;
        pushToMeshelium();
    }

    private static java.lang.invoke.MethodHandle meshelium;
    private static boolean mesheliumLooked;

    /** Hand the list to Quasar Meshelium if it is installed (looked up once, reflectively). */
    private static void pushToMeshelium() {
        if (!mesheliumLooked) {
            mesheliumLooked = true;
            try {
                Class<?> c = Class.forName("com.deds.meshelium.quasar.QuasarLights", true, DynamicLights.class.getClassLoader());
                meshelium = java.lang.invoke.MethodHandles.publicLookup().findStatic(c, "set",
                        java.lang.invoke.MethodType.methodType(void.class, double[].class, int.class));
            } catch (Throwable ignored) {
                meshelium = null; // no Quasar Meshelium: lights stay baked (step per block)
            }
        }
        if (meshelium == null) return;
        try {
            meshelium.invokeExact(exact, Math.min(exactCount, MAX_GPU));
        } catch (Throwable t) {
            meshelium = null;
        }
    }

    public static boolean smoothOnGpu() {
        if (!mesheliumLooked) pushToMeshelium();
        return meshelium != null;
    }

    /**
     * Extra block light (0..15, fractional) that dynamic lights give a point, for lighting entities.
     * Uses the exact (per-frame) light positions; straight-line falloff, walls aren't checked (cheap).
     */
    public static float entityLight(Vec3 p) {
        double[] d = exact;
        int n = exactCount;
        float best = 0;
        for (int i = 0; i < n; i++) {
            int b = i * STRIDE;
            double dx = p.x - d[b], dy = p.y - d[b + 1], dz = p.z - d[b + 2];
            float v = (float) d[b + 6] - (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (v > best) best = v;
        }
        return best;
    }

    public static String f3Line() {
        if (QuasiumConfig.dynamicLights == 0) return "Dynamic lights: off";
        return "Dynamic lights: " + published.length + " active, " + shownUpdates + " moves/s, "
                + shownSections + " section rebuilds/s" + (smoothOnGpu() ? ", smooth (Meshelium)" : ", stepped");
    }
}
