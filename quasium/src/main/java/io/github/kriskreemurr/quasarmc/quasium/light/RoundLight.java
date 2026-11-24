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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

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
    private static final AtomicLong built = new AtomicLong(), lit = new AtomicLong(), nanos = new AtomicLong();
    private static volatile boolean warned;

    private RoundLight() {}

    private static final class Work {
        final short[] value = new short[N * N2];
        /** Cell index of the (possibly virtual) source this cell's light is measured from. */
        final int[] src = new int[N * N2];
        /** Light at that source, in sixteenths. */
        final short[] srcValue = new short[N * N2];
        final byte[] flag = new byte[N * N2];
        final byte[] damp = new byte[N * N2];
        final BlockState[] state = new BlockState[N * N2];
        final int[][] bucket = new int[MAX + 1][];
        final int[] bucketSize = new int[MAX + 1];
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        RenderSectionRegion region;  // non-null while a field is active for this thread
        int ox, oy, oz;              // block coords of the region's min corner

        Work() {
            for (int i = 0; i <= MAX; i++) bucket[i] = new int[64];
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
        Work w = WORK.get();
        w.region = null;
        if (!QuasiumConfig.roundLight) return;
        long start = System.nanoTime();
        built.incrementAndGet();
        try {
            if (compute(w, section, region)) {
                w.region = region;
                lit.incrementAndGet();
            }
        } catch (Throwable t) {
            w.region = null;
            if (!warned) {
                warned = true;
                Quasium.LOGGER.error("Quasium round light failed; this section uses vanilla light", t);
            }
        }
        nanos.addAndGet(System.nanoTime() - start);
    }

    /** Called when the section build ends. */
    public static void end() {
        WORK.get().region = null;
    }

    /** Light lookup hook: raise the block-light half of {@code packed} to the round value. */
    public static int adjust(BlockAndLightGetter level, BlockPos pos, int packed) {
        if (packed == 15728880) return packed; // emissive / full bright
        Work w = WORK.get();
        if (w.region == null || w.region != level) return packed;
        int x = pos.getX() - w.ox, y = pos.getY() - w.oy, z = pos.getZ() - w.oz;
        if (x < 0 || y < 0 || z < 0 || x >= N || y >= N || z >= N) return packed;
        int round = w.value[(y * N + z) * N + x];
        int vanilla = packed & 0xFF;
        return round > vanilla ? (packed & ~0xFF) | round : packed;
    }

    public static String f3Line() {
        long b = built.getAndSet(0), l = lit.getAndSet(0), n = nanos.getAndSet(0);
        if (!QuasiumConfig.roundLight) return "Round light: off";
        if (b == 0) return "Round light: idle";
        return String.format("Round light: %d builds (%d lit), %.2f ms avg, %.2f ms per lit", b, l,
                n / 1e6 / b, l == 0 ? 0.0 : n / 1e6 / l);
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
        boolean cleared = false;
        for (int sy = 0; sy < 3; sy++) {
            for (int sz = 0; sz < 3; sz++) {
                for (int sx = 0; sx < 3; sx++) {
                    SectionCopy copy = copies[sx + sy * 3 + sz * 9];
                    if (copy == null) continue;
                    PalettedContainer<BlockState> states = ((SectionCopyAccessor) copy).quasium$states();
                    if (states == null || !states.maybeHas(s -> s.getLightEmission() > 0)) continue;
                    for (int ly = 0; ly < 16; ly++) {
                        for (int lz = 0; lz < 16; lz++) {
                            for (int lx = 0; lx < 16; lx++) {
                                BlockState s = states.get(lx, ly, lz);
                                if (s.getLightEmission() <= 0) continue;
                                int x = (sx << 4) + lx, y = (sy << 4) + ly, z = (sz << 4) + lz;
                                w.pos.set(w.ox + x, w.oy + y, w.oz + z);
                                int emission = s.getLightEmission(region, w.pos);
                                if (emission <= 0) continue;
                                // can it reach the target box at all? (straight-line distance)
                                int ex = Math.max(0, Math.max(tMin - x, x - tMax));
                                int ey = Math.max(0, Math.max(tMin - y, y - tMax));
                                int ez = Math.max(0, Math.max(tMin - z, z - tMax));
                                if (ex * ex + ey * ey + ez * ez >= emission * emission) continue;
                                if (!cleared) {
                                    clear(w);
                                    cleared = true;
                                }
                                int idx = (y * N + z) * N + x;
                                int v = Math.min(MAX, emission * 16);
                                if (v > w.value[idx]) {
                                    w.value[idx] = (short) v;
                                    w.src[idx] = idx;
                                    w.srcValue[idx] = (short) v;
                                    w.push(v, idx);
                                    any = true;
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!any) return false;
        propagate(w, region);
        return true;
    }

    private static void clear(Work w) {
        Arrays.fill(w.value, (short) 0);
        Arrays.fill(w.src, -1);
        Arrays.fill(w.flag, UNKNOWN);
        Arrays.fill(w.state, null);
        Arrays.fill(w.bucketSize, 0);
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

    private static byte flag(Work w, RenderSectionRegion region, int idx) {
        byte f = w.flag[idx];
        if (f != UNKNOWN) return f;
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
