package io.github.kriskreemurr.quasarmc.quasium;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Face culling data for one layer of one chunk section.
 *
 * When a section is built, its quads are regrouped by the direction they face:
 * bucket 0 = not axis-aligned (always drawn), then -X, +X, -Y, +Y, -Z, +Z.
 * At draw time, buckets that face away from the camera are skipped. A face can only be
 * seen from its front side (terrain uses back-face culling), so this never hides anything
 * that would have been visible.
 */
public final class FaceBuckets {
    public static final int UNASSIGNED = 0, NEG_X = 1, POS_X = 2, NEG_Y = 3, POS_Y = 4, NEG_Z = 5, POS_Z = 6, COUNT = 7;
    private static final double EPS = 0.01;

    /** First quad of each bucket, and quads in each bucket. */
    public final int[] start = new int[COUNT];
    public final int[] count = new int[COUNT];
    /** For +dir buckets the lowest face plane, for -dir buckets the highest (section-relative). */
    public final float[] plane = new float[COUNT];
    public final int totalQuads;

    private FaceBuckets(int totalQuads) {
        this.totalQuads = totalQuads;
    }

    /** Which buckets can have front-facing quads for a camera at (cx, cy, cz), section-relative. Bit i = bucket i. */
    public int visibleMask(double cx, double cy, double cz) {
        int mask = 0;
        if (count[UNASSIGNED] > 0) mask |= 1 << UNASSIGNED;
        if (count[NEG_X] > 0 && cx < plane[NEG_X] + EPS) mask |= 1 << NEG_X;
        if (count[POS_X] > 0 && cx > plane[POS_X] - EPS) mask |= 1 << POS_X;
        if (count[NEG_Y] > 0 && cy < plane[NEG_Y] + EPS) mask |= 1 << NEG_Y;
        if (count[POS_Y] > 0 && cy > plane[POS_Y] - EPS) mask |= 1 << POS_Y;
        if (count[NEG_Z] > 0 && cz < plane[NEG_Z] + EPS) mask |= 1 << NEG_Z;
        if (count[POS_Z] > 0 && cz > plane[POS_Z] - EPS) mask |= 1 << POS_Z;
        return mask;
    }

    public int nonEmptyMask() {
        int mask = 0;
        for (int b = 0; b < COUNT; b++) if (count[b] > 0) mask |= 1 << b;
        return mask;
    }

    /**
     * Regroups the quads of a freshly built mesh by facing, in place.
     * Returns null (and leaves the mesh untouched) if the mesh isn't plain float-position quads.
     */
    public static FaceBuckets sortInPlace(MeshData mesh) {
        MeshData.DrawState state = mesh.drawState();
        if (state.primitiveTopology() != PrimitiveTopology.QUADS) return null;
        VertexFormat format = state.format();
        VertexFormatElement pos = format.getElement("Position");
        if (pos == null || pos.format() != GpuFormat.RGB32_FLOAT) return null;
        int vertexCount = state.vertexCount();
        if (vertexCount <= 0 || vertexCount % 4 != 0) return null;
        int quads = vertexCount / 4;
        if (state.indexCount() != quads * 6) return null;

        int stride = format.getVertexSize();
        int quadBytes = stride * 4;
        int posOff = pos.offset();
        ByteBuffer vb = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
        int base = vb.position();
        if (vb.remaining() < quads * quadBytes) return null;

        FaceBuckets out = new FaceBuckets(quads);
        for (int b = 0; b < COUNT; b++) out.plane[b] = (b % 2 == 0) ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        // even buckets (POS_*) track the minimum plane, odd buckets (NEG_*) the maximum
        byte[] bucketOf = new byte[quads];
        float[] p = new float[12];

        for (int q = 0; q < quads; q++) {
            int qb = base + q * quadBytes;
            for (int v = 0; v < 4; v++) {
                int o = qb + v * stride + posOff;
                p[v * 3] = vb.getFloat(o);
                p[v * 3 + 1] = vb.getFloat(o + 4);
                p[v * 3 + 2] = vb.getFloat(o + 8);
            }
            int b = classify(p);
            bucketOf[q] = (byte) b;
            out.count[b]++;
            if (b != UNASSIGNED) {
                int axis = (b - 1) / 2; // 0 = x, 1 = y, 2 = z
                float plane = p[axis];
                if (b % 2 == 0) out.plane[b] = Math.min(out.plane[b], plane);
                else out.plane[b] = Math.max(out.plane[b], plane);
            }
        }

        int run = 0;
        for (int b = 0; b < COUNT; b++) {
            out.start[b] = run;
            run += out.count[b];
        }

        // Already grouped? Then nothing to move.
        boolean sorted = true;
        for (int q = 1; q < quads && sorted; q++) if (bucketOf[q] < bucketOf[q - 1]) sorted = false;
        if (!sorted) {
            byte[] src = new byte[quads * quadBytes];
            vb.get(base, src);
            byte[] dst = new byte[src.length];
            int[] cursor = out.start.clone();
            for (int q = 0; q < quads; q++) {
                int to = cursor[bucketOf[q]]++;
                System.arraycopy(src, q * quadBytes, dst, to * quadBytes, quadBytes);
            }
            vb.put(base, dst);
        }
        return out;
    }

    /** Axis-aligned flat quads get a direction bucket; everything else is UNASSIGNED. */
    private static int classify(float[] p) {
        // Normal from the diagonals: (v2 - v0) x (v3 - v1). Counter-clockwise front faces point it outward.
        float ax = p[6] - p[0], ay = p[7] - p[1], az = p[8] - p[2];
        float bx = p[9] - p[3], by = p[10] - p[4], bz = p[11] - p[5];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-8f) return UNASSIGNED;
        nx /= len; ny /= len; nz /= len;
        final float T = 0.9999f;
        int axis;
        if (Math.abs(nx) > T) axis = 0;
        else if (Math.abs(ny) > T) axis = 1;
        else if (Math.abs(nz) > T) axis = 2;
        else return UNASSIGNED;
        // all four corners must lie on the same plane along that axis
        float c = p[axis];
        for (int v = 1; v < 4; v++) if (Math.abs(p[v * 3 + axis] - c) > 1e-4f) return UNASSIGNED;
        float n = axis == 0 ? nx : axis == 1 ? ny : nz;
        return 1 + axis * 2 + (n > 0 ? 1 : 0);
    }
}
