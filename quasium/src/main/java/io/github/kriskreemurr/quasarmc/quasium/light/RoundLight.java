package io.github.kriskreemurr.quasarmc.quasium.light;

import io.github.kriskreemurr.quasarmc.quasium.Quasium;
import io.github.kriskreemurr.quasarmc.quasium.QuasiumConfig;
import io.github.kriskreemurr.quasarmc.quasium.mixin.RenderSectionRegionAccessor;
import io.github.kriskreemurr.quasarmc.quasium.mixin.SectionCopyAccessor;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.shapes.Shapes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Round (circular) block light for terrain — visual only, client side.
 *
 * <p>Vanilla block light spreads to the 6 face neighbours and loses one level per step, so a torch lights a
 * diamond (taxicab distance) in whole-level rings. While a chunk section is being meshed (on a worker thread)
 * this recomputes block light for the section's neighbourhood with 26-neighbour steps that cost their real
 * length (1, √2, √3), in sixteenths of a level, so light falls off by straight-line distance and without rings.
 *
 * <ul>
 *   <li>No leaks: light never enters fully opaque blocks, partial blocks (slabs, stairs...) are checked face by
 *       face like vanilla, and a diagonal step is only allowed if a path of straight steps around it is open.</li>
 *   <li>Never darker than vanilla: the vertex light is max(vanilla, round).</li>
 *   <li>Sections with no light source within 15 blocks are skipped almost for free (palette check).</li>
 *   <li>Light level 1 sources (brown mushrooms, sculk...) are skipped: their light never leaves their own block,
 *       so round and vanilla light are identical there.</li>
 *   <li>Work is limited to the box the found lights can reach (light can't travel further than its level), so a
 *       weak light costs a few hundred cells instead of the whole 48³ region.</li>
 *   <li>Nether lava is not spread: a lava sea is hundreds of sources that light evenly anyway, so its roundness
 *       is invisible. It keeps vanilla's light level and only adds its color (at export). Torches, glowstone etc.
 *       next to it stay round. Overworld lava is still round.</li>
 * </ul>
 *
 * <p>The result only changes the block-light half of terrain vertex light, so it works with Meshelium, which
 * draws vanilla's vertex data. Gameplay light (mob spawning, crops) is untouched.
 */
public final class RoundLight {
    private static final int N = 48;                 // RenderSectionRegion: 3x3x3 sections
    private static final int N2 = N * N;
    private static final int MAX = 15 * 16;          // light in sixteenths of a level
    private static final int COST1 = 16, COST2 = 23, COST3 = 28; // 16·1, 16·√2, 16·√3

    /** The cells we read back: the section plus one block around it (smooth lighting samples neighbours). */
    private static final int T_MIN = 15, T_MAX = 32;

    // cell flags
    private static final byte UNKNOWN = 0, OPEN = 1, OPAQUE = 2, SHAPED = 3;

    private static final int[][] OFFSETS = new int[26][];
    static {
        int i = 0;
        for (int dy = -1; dy <= 1; dy++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dx = -1; dx <= 1; dx++)
                    if (dx != 0 || dy != 0 || dz != 0) OFFSETS[i++] = new int[]{dx, dy, dz};
    }

    private static final ThreadLocal<Work> WORK = ThreadLocal.withInitial(Work::new);

    // F3 stats
    private static final AtomicLong built = new AtomicLong(), lit = new AtomicLong(), nanos = new AtomicLong(),
            maxNanos = new AtomicLong();
    // F3 split: where the time goes (scan = every build, spread/colors = lit builds only)
    private static final AtomicLong scanNanos = new AtomicLong(), spreadNanos = new AtomicLong(),
            exportNanos = new AtomicLong(), seedsTotal = new AtomicLong(), boxCells = new AtomicLong(),
            threeChannel = new AtomicLong(), onRenderThread = new AtomicLong();
    private static final Map<Object, LongAdder> seedsBySource = new ConcurrentHashMap<>();
    private static final Object MOVING = new Object();
    private static final AtomicLong flatLavaBuilds = new AtomicLong();
    /** RenderSectionRegion's Level field, found by type (null: none, fall back to the client's level). */
    private static volatile java.lang.reflect.Field regionLevelField;
    private static volatile boolean regionLevelLooked;
    private static volatile Thread renderThread;
    private static long windowStart = System.nanoTime();
    private static String shown = "Round light: idle";
    private static String shownSplit = null;
    private static volatile boolean warned;

    private RoundLight() {}

    private static final class Work {
        /** Per color channel (R, G, B): light in sixteenths, and the (possibly virtual) source it's measured from. */
        final short[][] valueC = new short[3][N * N2];
        final int[][] srcC = new int[3][N * N2];
        final short[][] srcValueC = new short[3][N * N2];
        /** The channel being propagated right now (points into the arrays above). */
        short[] value;
        int[] src;
        short[] srcValue;
        /** Brightness = brightest channel; what the terrain light lookup uses. */
        final short[] bright = new short[N * N2];
        /** Light sources found for this build: cell index, light (sixteenths), color. */
        int seedCount;
        /** Nether lava near the section: not spread, colored at export from vanilla's light level. */
        boolean flatLava;
        float[] flatLavaColor;
        int[] seedIdx = new int[64];
        int[] seedV = new int[64];
        float[][] seedColor = new float[64][];
        final byte[] flag = new byte[N * N2];
        /** flag/damp/state of a cell are valid only if flagGen[cell] == gen (saves clearing 48³ every build). */
        final int[] flagGen = new int[N * N2];
        int gen;
        final byte[] damp = new byte[N * N2];
        final BlockState[] state = new BlockState[N * N2];
        /** Box the current light field lives in, and the box the previous lit build wrote (still to clear). */
        int bx0, by0, bz0, bx1, by1, bz1;
        int px0 = 0, py0 = 0, pz0 = 0, px1 = -1, py1 = -1, pz1 = -1;
        final int[][] bucket = new int[MAX + 1][];
        final int[] bucketSize = new int[MAX + 1];
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        RenderSectionRegion region;  // non-null while a field is active for this thread
        int ox, oy, oz;              // block coords of the region's min corner

        Work() {
            for (int i = 0; i <= MAX; i++) bucket[i] = new int[64];
        }

        void addSeed(int idx, int v, float[] color) {
            if (seedCount == seedIdx.length) {
                seedIdx = Arrays.copyOf(seedIdx, seedCount * 2);
                seedV = Arrays.copyOf(seedV, seedCount * 2);
                seedColor = Arrays.copyOf(seedColor, seedCount * 2);
            }
            seedIdx[seedCount] = idx;
            seedV[seedCount] = v;
            seedColor[seedCount] = color;
            seedCount++;
        }

        void push(int v, int idx) {
            int s = bucketSize[v];
            int[] b = bucket[v];
            if (s == b.length) bucket[v] = b = Arrays.copyOf(b, s * 2);
            b[s] = idx;
            bucketSize[v] = s + 1;
        }
    }

    /** Called at the start of a section build. */
    public static void begin(SectionPos section, RenderSectionRegion region) {
        ColorBridge.beginSection(region); // another world's section (portal view) must not publish colors here
        Work w = WORK.get();
        w.region = null;
        long seq = region instanceof ColorBridge.Snapshot snap ? snap.quasium$seq() : ColorBridge.nextSeq();
        if (!QuasiumConfig.roundLight && DynamicLights.sources().length == 0) {
            ColorBridge.submit(section, null, seq);
            return;
        }
        long start = System.nanoTime();
        built.incrementAndGet();
        if (Thread.currentThread() == renderThread) onRenderThread.incrementAndGet();
        try {
            boolean round = compute(w, section, region);
            if (round || w.flatLava) {
                if (round) w.region = region;
                lit.incrementAndGet();
                if (w.flatLava) flatLavaBuilds.incrementAndGet();
                long e0 = System.nanoTime();
                int[] colors = exportColors(w, region, round);
                exportNanos.addAndGet(System.nanoTime() - e0);
                ColorBridge.submit(section, colors, seq);
            } else {
                ColorBridge.submit(section, null, seq);
            }
        } catch (Throwable t) {
            w.region = null;
            if (!warned) {
                warned = true;
                Quasium.LOGGER.error("Quasium round light failed; this section uses vanilla light", t);
            }
        }
        long took = System.nanoTime() - start;
        nanos.addAndGet(took);
        maxNanos.accumulateAndGet(took, Math::max);
    }

    /** Called when the section build ends. */
    public static void end() {
        WORK.get().region = null;
    }

    /** Light lookup hook: raise the block-light half of {@code packed} to the round value. */
    public static int adjust(BlockAndLightGetter level, BlockPos pos, int packed) {
        if (packed == 15728880) return packed; // emissive / full bright
        if (level instanceof net.minecraft.client.renderer.block.MovingBlockRenderState) {
            // Falling sand, piston-moved blocks: use the round light baked for the block they're in.
            if (!QuasiumConfig.roundLight) return packed;
            int round = EntityTint.roundBrightness(pos.getX(), pos.getY(), pos.getZ());
            return round > (packed & 0xFF) ? (packed & ~0xFF) | round : packed;
        }
        Work w = WORK.get();
        if (w.region == null || w.region != level) return packed;
        int x = pos.getX() - w.ox, y = pos.getY() - w.oy, z = pos.getZ() - w.oz;
        if (x < 0 || y < 0 || z < 0 || x >= N || y >= N || z >= N) return packed;
        int round = w.bright[(y * N + z) * N + x];
        int vanilla = packed & 0xFF;
        return round > vanilla ? (packed & ~0xFF) | round : packed;
    }

    /** F3 line: stats over the last full second (counted on worker threads, shown on the render thread). */
    public static String f3Line() {
        renderThread = Thread.currentThread();
        if (!QuasiumConfig.roundLight && DynamicLights.sources().length == 0) return "Round light: off";
        long now = System.nanoTime();
        if (now - windowStart >= 1_000_000_000L) {
            // per second over the real window: F3 may have been closed for a while (counts kept adding up)
            double secs = (now - windowStart) / 1e9;
            windowStart = now;
            long b = built.getAndSet(0), l = lit.getAndSet(0), n = nanos.getAndSet(0), mx = maxNanos.getAndSet(0);
            long sc = scanNanos.getAndSet(0), sp = spreadNanos.getAndSet(0), ex = exportNanos.getAndSet(0);
            long seeds = seedsTotal.getAndSet(0), cells = boxCells.getAndSet(0), tri = threeChannel.getAndSet(0);
            long rt = onRenderThread.getAndSet(0);
            long flat = flatLavaBuilds.getAndSet(0);
            Object top = null;
            long topCount = 0, allCount = 0;
            for (Map.Entry<Object, LongAdder> e : seedsBySource.entrySet()) {
                long c = e.getValue().sum();
                allCount += c;
                if (c > topCount) {
                    topCount = c;
                    top = e.getKey();
                }
            }
            seedsBySource.clear();
            shown = b == 0 ? "Round light: idle"
                    : String.format("Round light: %d builds/s (%d lit), %.2f ms avg, %.2f ms per lit, max %.2f ms",
                    Math.round(b / secs), Math.round(l / secs), n / 1e6 / b, l == 0 ? 0.0 : n / 1e6 / l, mx / 1e6);
            if (b == 0) {
                shownSplit = null;
            } else {
                String topName = top == null ? "-" : top == MOVING ? "moving light"
                        : BuiltInRegistries.BLOCK.getKey((Block) top).getPath();
                shownSplit = String.format(
                        "Round light split: scan %.3f ms/build; per lit: spread %.2f, colors %.3f ms, %.1f lights, box %dk cells, 3-color %d%%, flat Nether lava %d%%; render thread %d/s; top light: %s (%d%%)",
                        sc / 1e6 / b,
                        l == 0 ? 0.0 : sp / 1e6 / l, l == 0 ? 0.0 : ex / 1e6 / l,
                        l == 0 ? 0.0 : (double) seeds / l, l == 0 ? 0 : Math.round(cells / 1000.0 / l),
                        l == 0 ? 0 : Math.round(100.0 * tri / l), l == 0 ? 0 : Math.round(100.0 * flat / l),
                        Math.round(rt / secs),
                        topName, allCount == 0 ? 0 : Math.round(100.0 * topCount / allCount));
            }
        }
        return shown;
    }

    /** Second F3 line (null = nothing to show): the split of {@link #f3Line()}'s window. Call after f3Line. */
    public static String f3Split() {
        if (!QuasiumConfig.roundLight && DynamicLights.sources().length == 0) return null;
        return shownSplit;
    }

    private static boolean compute(Work w, SectionPos section, RenderSectionRegion region) {
        RenderSectionRegionAccessor acc = (RenderSectionRegionAccessor) region;
        SectionCopy[] copies = acc.quasium$sections();
        int msx = acc.quasium$minSectionX(), msy = acc.quasium$minSectionY(), msz = acc.quasium$minSectionZ();
        w.ox = msx << 4;
        w.oy = msy << 4;
        w.oz = msz << 4;
        // cells we read: the section plus one block around it (smooth lighting samples neighbours)
        int tMin = T_MIN, tMax = T_MAX;
        // 1) find light sources that can reach the target box
        boolean any = false;
        w.seedCount = 0;
        w.flatLava = false;
        boolean netherLava = QuasiumConfig.roundLight && isNether(region);
        // block info cache, shared by the seed scan (buried check) and the three channels: a new generation
        // invalidates every cell without clearing the arrays
        if (++w.gen == Integer.MAX_VALUE) {
            Arrays.fill(w.flagGen, 0);
            w.gen = 1;
        }
        long scan0 = System.nanoTime();
        if (QuasiumConfig.roundLight) for (int sy = 0; sy < 3; sy++) {
            for (int sz = 0; sz < 3; sz++) {
                for (int sx = 0; sx < 3; sx++) {
                    SectionCopy copy = copies[sx + sy * 3 + sz * 9];
                    if (copy == null) continue;
                    PalettedContainer<BlockState> states = ((SectionCopyAccessor) copy).quasium$states();
                    // level 1 lights never leave their own block: identical to vanilla, skip them
                    if (states == null || !states.maybeHas(s -> s.getLightEmission() > 1)) continue;
                    for (int ly = 0; ly < 16; ly++) {
                        for (int lz = 0; lz < 16; lz++) {
                            for (int lx = 0; lx < 16; lx++) {
                                BlockState s = states.get(lx, ly, lz);
                                if (s.getLightEmission() <= 1) continue;
                                int x = (sx << 4) + lx, y = (sy << 4) + ly, z = (sz << 4) + lz;
                                w.pos.set(w.ox + x, w.oy + y, w.oz + z);
                                int emission = s.getLightEmission(region, w.pos);
                                if (emission <= 1) continue;
                                // can it reach the target box at all? (straight-line distance)
                                int ex = Math.max(0, Math.max(tMin - x, x - tMax));
                                int ey = Math.max(0, Math.max(tMin - y, y - tMax));
                                int ez = Math.max(0, Math.max(tMin - z, z - tMax));
                                if (ex * ex + ey * ey + ez * ez >= emission * emission) continue;
                                // lava seas / glowstone blobs: a source walled in by rock or by the same light
                                // adds nothing visible; only its surface seeds (volume -> surface)
                                if (buried(w, region, x, y, z, s)) continue;
                                if (netherLava && s.getBlock() == Blocks.LAVA) {
                                    if (!w.flatLava) {
                                        w.flatLava = true;
                                        w.flatLavaColor = LightColors.of(s.getBlock());
                                    }
                                    continue;
                                }
                                w.addSeed((y * N + z) * N + x, Math.min(MAX, emission * 16), LightColors.of(s.getBlock()));
                                seedsBySource.computeIfAbsent(s.getBlock(), k -> new LongAdder()).increment();
                                any = true;
                            }
                        }
                    }
                }
            }
        }
        // moving lights (held torches, dropped items, burning mobs)
        for (DynamicLights.Source d : DynamicLights.sources()) {
            int x = d.x() - w.ox, y = d.y() - w.oy, z = d.z() - w.oz;
            if (x < 0 || y < 0 || z < 0 || x >= N || y >= N || z >= N) continue;
            if (d.lum() <= 1) continue;
            int ex = Math.max(0, Math.max(tMin - x, x - tMax));
            int ey = Math.max(0, Math.max(tMin - y, y - tMax));
            int ez = Math.max(0, Math.max(tMin - z, z - tMax));
            if (ex * ex + ey * ey + ez * ez >= d.lum() * d.lum()) continue;
            w.addSeed((y * N + z) * N + x, Math.min(MAX, d.lum() * 16), d.color());
            seedsBySource.computeIfAbsent(MOVING, k -> new LongAdder()).increment();
            any = true;
        }
        scanNanos.addAndGet(System.nanoTime() - scan0);
        if (!any) return false;
        long spread0 = System.nanoTime();
        try {
            return spreadAll(w, region);
        } finally {
            spreadNanos.addAndGet(System.nanoTime() - spread0);
        }
    }

    /**
     * Spreads the seeds' light. Light loses at least a full level per block (straight-line distance is never
     * shorter than the largest axis step), so a seed of level L can only light cells within L blocks on every
     * axis: everything happens inside that box. Cells outside it are kept at zero by clearing only the box the
     * previous lit build wrote.
     */
    private static boolean spreadAll(Work w, RenderSectionRegion region) {
        // clear what the previous lit build left behind (value channels + brightness)
        clearBox(w, w.px0, w.py0, w.pz0, w.px1, w.py1, w.pz1);
        int x0 = N, y0 = N, z0 = N, x1 = -1, y1 = -1, z1 = -1;
        for (int i = 0; i < w.seedCount; i++) {
            int idx = w.seedIdx[i];
            int sx = idx % N, sz = (idx / N) % N, sy = idx / N2;
            int r = (w.seedV[i] + 15) / 16;
            x0 = Math.min(x0, sx - r); x1 = Math.max(x1, sx + r);
            y0 = Math.min(y0, sy - r); y1 = Math.max(y1, sy + r);
            z0 = Math.min(z0, sz - r); z1 = Math.max(z1, sz + r);
        }
        w.bx0 = Math.max(0, x0); w.by0 = Math.max(0, y0); w.bz0 = Math.max(0, z0);
        w.bx1 = Math.min(N - 1, x1); w.by1 = Math.min(N - 1, y1); w.bz1 = Math.min(N - 1, z1);
        w.px0 = w.bx0; w.py0 = w.by0; w.pz0 = w.bz0; w.px1 = w.bx1; w.py1 = w.by1; w.pz1 = w.bz1;
        seedsTotal.addAndGet(w.seedCount);
        boxCells.addAndGet((long) (w.bx1 - w.bx0 + 1) * (w.by1 - w.by0 + 1) * (w.bz1 - w.bz0 + 1));
        int bx0 = w.bx0, bx1 = w.bx1;
        // One light color for every seed (a lava sea, a glowstone blob, one torch type): spread only the strongest
        // channel and scale the other two from it, a third of the work. Light then keeps its hue with distance
        // instead of the weaker channels dying out first (a slightly truer color, not a visible change).
        float[] mono = w.seedColor[0];
        for (int i = 1; i < w.seedCount && mono != null; i++) if (w.seedColor[i] != mono) mono = null;
        if (mono != null) {
            int dom = mono[0] >= mono[1] ? (mono[0] >= mono[2] ? 0 : 2) : (mono[1] >= mono[2] ? 1 : 2);
            if (mono[dom] > 0f) {
                spreadChannel(w, region, dom);
                short[] dv = w.valueC[dom], br = w.bright;
                for (int c = 0; c < 3; c++) {
                    if (c == dom) continue;
                    short[] cv = w.valueC[c];
                    float k = mono[c] / mono[dom];
                    for (int y = w.by0; y <= w.by1; y++)
                        for (int z = w.bz0; z <= w.bz1; z++) {
                            int base = (y * N + z) * N;
                            for (int i = base + bx0; i <= base + bx1; i++) cv[i] = (short) Math.round(dv[i] * k);
                        }
                }
                for (int y = w.by0; y <= w.by1; y++)
                    for (int z = w.bz0; z <= w.bz1; z++) {
                        int base = (y * N + z) * N + bx0;
                        System.arraycopy(dv, base, br, base, bx1 - bx0 + 1);
                    }
                return true;
            }
        }
        threeChannel.incrementAndGet();
        for (int c = 0; c < 3; c++) {
            spreadChannel(w, region, c);
            short[] val = w.value, br = w.bright;
            for (int y = w.by0; y <= w.by1; y++)
                for (int z = w.bz0; z <= w.bz1; z++) {
                    int base = (y * N + z) * N;
                    for (int i = base + bx0; i <= base + bx1; i++) if (val[i] > br[i]) br[i] = val[i];
                }
        }
        return true;
    }

    /** Zeroes the three value channels and the brightness inside a box (empty box: x1 < x0). */
    private static void clearBox(Work w, int x0, int y0, int z0, int x1, int y1, int z1) {
        if (x1 < x0 || y1 < y0 || z1 < z0) return;
        for (int y = y0; y <= y1; y++)
            for (int z = z0; z <= z1; z++) {
                int from = (y * N + z) * N + x0, to = (y * N + z) * N + x1 + 1;
                Arrays.fill(w.bright, from, to, (short) 0);
                for (int c = 0; c < 3; c++) Arrays.fill(w.valueC[c], from, to, (short) 0);
            }
    }

    /**
     * The section's block-light colors for Quasar Meshelium: one RGBA8 per block, rgb = color (brightest channel
     * 255), a = brightness in sixteenths. Null if the section itself has no block light.
     */
    private static int[] exportColors(Work w, RenderSectionRegion region, boolean round) {
        int[] out = null;
        float[] lava = w.flatLava ? w.flatLavaColor : null;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int idx = ((y + 16) * N + (z + 16)) * N + (x + 16);
                    int m = round ? w.bright[idx] : 0;
                    int vr = round ? w.valueC[0][idx] : 0, vg = round ? w.valueC[1][idx] : 0,
                            vb = round ? w.valueC[2][idx] : 0;
                    if (lava != null) {
                        // flat Nether lava: vanilla's own light level, in lava's color; the brighter light wins
                        w.pos.set(w.ox + x + 16, w.oy + y + 16, w.oz + z + 16);
                        int v = region.getBrightness(LightLayer.BLOCK, w.pos) * 16;
                        if (v > 0) {
                            vr = Math.max(vr, Math.round(v * lava[0]));
                            vg = Math.max(vg, Math.round(v * lava[1]));
                            vb = Math.max(vb, Math.round(v * lava[2]));
                            m = Math.max(m, v);
                        }
                    }
                    if (m <= 0) continue;
                    int r = vr * 255 / m, g = vg * 255 / m, b = vb * 255 / m;
                    if (out == null) out = new int[4096];
                    out[(y * 16 + z) * 16 + x] = r | (g << 8) | (b << 16) | (Math.min(255, m) << 24);
                }
            }
        }
        return out;
    }

    private static void propagate(Work w, RenderSectionRegion region) {
        for (int v = MAX; v > 0; v--) {
            // bucketSize is re-read each round: a few cells can be (re)queued into the bucket being walked
            for (int i = 0; i < w.bucketSize[v]; i++) {
                int idx = w.bucket[v][i];
                int cv = w.value[idx];
                if (cv < v) continue; // superseded (stale entry)
                int x = idx % N, z = (idx / N) % N, y = idx / N2;
                int s = w.src[idx];
                int sv = w.srcValue[idx];
                int sx = s % N, sz = (s / N) % N, sy = s / N2;
                for (int[] o : OFFSETS) {
                    int nx = x + o[0], ny = y + o[1], nz = z + o[2];
                    if (nx < 0 || ny < 0 || nz < 0 || nx >= N || ny >= N || nz >= N) continue;
                    int nIdx = (ny * N + nz) * N + nx;
                    if (flag(w, region, nIdx) == OPAQUE) continue;
                    int d = w.damp[nIdx];
                    int nv, ns, nsv;
                    if (d <= 1 && visible(w, region, nx, ny, nz, s, sx, sy, sz)) {
                        // straight line back to the source is open: true distance -> round falloff
                        double ddx = nx - sx, ddy = ny - sy, ddz = nz - sz;
                        nv = sv - (int) Math.round(16.0 * Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz));
                        ns = s;
                        nsv = sv;
                    } else {
                        // around a corner or through a dimming block: continue from this cell
                        int axes = (o[0] != 0 ? 1 : 0) + (o[1] != 0 ? 1 : 0) + (o[2] != 0 ? 1 : 0);
                        int cost = axes == 1 ? COST1 : axes == 2 ? COST2 : COST3;
                        if (d > 1) cost = Math.max(cost, d * 16);
                        nv = cv - cost;
                        ns = idx;
                        nsv = cv;
                    }
                    if (nv <= w.value[nIdx]) continue;
                    // prune: from here the light can't get back into the box we actually read
                    int ex = Math.max(0, Math.max(T_MIN - nx, nx - T_MAX));
                    int ey = Math.max(0, Math.max(T_MIN - ny, ny - T_MAX));
                    int ez = Math.max(0, Math.max(T_MIN - nz, nz - T_MAX));
                    if (nv * nv <= 256 * (ex * ex + ey * ey + ez * ez)) continue;
                    if (!reachable(w, region, x, y, z, o[0], o[1], o[2])) continue;
                    w.value[nIdx] = (short) nv;
                    w.src[nIdx] = ns;
                    w.srcValue[nIdx] = (short) nsv;
                    w.push(Math.min(nv, v), nIdx);
                }
            }
            w.bucketSize[v] = 0;
        }
    }

    /** -1, 0 or 1: the step along one axis that best follows the line toward the source (symmetric rounding). */
    private static int towards(int d, int m) {
        return 2 * Math.abs(d) >= m ? Integer.signum(d) : 0;
    }

    /**
     * Can cell n see source s in a straight line? Checked by induction: the neighbour of n one step toward s
     * must be s itself, or be lit by s directly (same source), with an open step between them.
     */
    private static boolean visible(Work w, RenderSectionRegion region, int nx, int ny, int nz, int s, int sx, int sy, int sz) {
        int dx = sx - nx, dy = sy - ny, dz = sz - nz;
        int m = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        if (m == 0) return true;
        int px = nx + towards(dx, m), py = ny + towards(dy, m), pz = nz + towards(dz, m);
        int p = (py * N + pz) * N + px;
        if (p != s && (w.src[p] != s || w.value[p] <= 0)) return false;
        return reachable(w, region, px, py, pz, nx - px, ny - py, nz - pz);
    }

    /** Is there a path of straight steps from (x,y,z) to (x+dx, y+dy, z+dz) through non-blocking cells? */
    private static boolean reachable(Work w, RenderSectionRegion region, int x, int y, int z, int dx, int dy, int dz) {
        int axes = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
        if (axes == 1) return step(w, region, x, y, z, dx, dy, dz);
        // try each axis as the first step, then recurse on the rest
        if (dx != 0 && step(w, region, x, y, z, dx, 0, 0) && reachable(w, region, x + dx, y, z, 0, dy, dz)) return true;
        if (dy != 0 && step(w, region, x, y, z, 0, dy, 0) && reachable(w, region, x, y + dy, z, dx, 0, dz)) return true;
        return dz != 0 && step(w, region, x, y, z, 0, 0, dz) && reachable(w, region, x, y, z + dz, dx, dy, 0);
    }

    /** One straight step: target not opaque, and partial-block faces between the two cells don't close it. */
    private static boolean step(Work w, RenderSectionRegion region, int x, int y, int z, int dx, int dy, int dz) {
        int nx = x + dx, ny = y + dy, nz = z + dz;
        if (nx < 0 || ny < 0 || nz < 0 || nx >= N || ny >= N || nz >= N) return false;
        int a = (y * N + z) * N + x, b = (ny * N + nz) * N + nx;
        byte fb = flag(w, region, b);
        if (fb == OPAQUE) return false;
        byte fa = flag(w, region, a);
        if (fa != SHAPED && fb != SHAPED) return true;
        Direction dir = dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST
                : dy > 0 ? Direction.UP : dy < 0 ? Direction.DOWN
                : dz > 0 ? Direction.SOUTH : Direction.NORTH;
        return !Shapes.faceShapeOccludes(LightEngine.getOcclusionShape(state(w, region, a), dir),
                LightEngine.getOcclusionShape(state(w, region, b), dir.getOpposite()));
    }

    private static BlockState state(Work w, RenderSectionRegion region, int idx) {
        flag(w, region, idx);
        return w.state[idx];
    }

    /**
     * True if every neighbour of this light source is opaque or the same block glowing at least as bright, so no
     * light from it reaches an open cell its neighbours don't already light. Sources on the region's edge count as
     * open. Buried cells then read one step less than full (only faces hidden under the same block sample them).
     */
    /** Spreads one color channel from the seeds into {@code w.valueC[c]} and raises {@code w.bright}. */
    private static void spreadChannel(Work w, RenderSectionRegion region, int c) {
        w.value = w.valueC[c];
        w.src = w.srcC[c];
        w.srcValue = w.srcValueC[c];
        // value is already zero outside the lights' box and was cleared inside it (spreadAll); src/srcValue are
        // only read where value > 0, so they need no clearing
        Arrays.fill(w.bucketSize, 0);
        boolean seeded = false;
        for (int i = 0; i < w.seedCount; i++) {
            int idx = w.seedIdx[i];
            int v = Math.round(w.seedV[i] * w.seedColor[i][c]);
            if (v <= 0 || v <= w.value[idx]) continue;
            w.value[idx] = (short) v;
            w.src[idx] = idx;
            w.srcValue[idx] = (short) v;
            w.push(v, idx);
            seeded = true;
        }
        if (seeded) propagate(w, region);
    }

    private static boolean buried(Work w, RenderSectionRegion region, int x, int y, int z, BlockState self) {
        if (x <= 0 || y <= 0 || z <= 0 || x >= N - 1 || y >= N - 1 || z >= N - 1) return false;
        int emission = self.getLightEmission();
        for (int[] o : FACES) {
            int nIdx = ((y + o[1]) * N + (z + o[2])) * N + (x + o[0]);
            if (flag(w, region, nIdx) == OPAQUE) continue;
            BlockState n = w.state[nIdx];
            if (n.getBlock() != self.getBlock() || n.getLightEmission() < emission) return false;
        }
        return true;
    }

    /** Is this build for a Nether section? Uses the region's own world (portal views build the other world). */
    private static boolean isNether(RenderSectionRegion region) {
        Level level = regionLevel(region);
        if (level == null) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            level = mc.level;
        }
        return level != null && level.dimension() == Level.NETHER;
    }

    private static Level regionLevel(RenderSectionRegion region) {
        if (!regionLevelLooked) {
            synchronized (RoundLight.class) {
                if (!regionLevelLooked) {
                    try {
                        for (java.lang.reflect.Field f : RenderSectionRegion.class.getDeclaredFields()) {
                            if (Level.class.isAssignableFrom(f.getType())
                                    && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                                f.setAccessible(true);
                                regionLevelField = f;
                                break;
                            }
                        }
                    } catch (Throwable t) {
                        regionLevelField = null;
                    }
                    if (regionLevelField == null)
                        Quasium.LOGGER.warn("Quasium: RenderSectionRegion has no Level field; Nether lava check uses the client's world");
                    regionLevelLooked = true;
                }
            }
        }
        java.lang.reflect.Field f = regionLevelField;
        if (f == null) return null;
        try {
            return (Level) f.get(region);
        } catch (Throwable t) {
            return null;
        }
    }

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private static byte flag(Work w, RenderSectionRegion region, int idx) {
        if (w.flagGen[idx] == w.gen) return w.flag[idx];
        w.flagGen[idx] = w.gen;
        byte f;
        int x = idx % N, z = (idx / N) % N, y = idx / N2;
        w.pos.set(w.ox + x, w.oy + y, w.oz + z);
        BlockState s = region.getBlockState(w.pos);
        w.state[idx] = s;
        int d = s.getLightDampening();
        w.damp[idx] = (byte) Math.min(15, d);
        if (d >= 15 && s.getLightEmission() <= 0) f = OPAQUE;
        else if (s.useShapeForLightOcclusion()) f = SHAPED;
        else f = OPEN;
        w.flag[idx] = f;
        return f;
    }
}
