package io.github.kriskreemurr.quasarmc.quasium;

import net.minecraft.world.phys.Vec3;

/** Per-frame render state shared between Quasium's hooks (render thread only). */
public final class QuasiumState {
    public static Vec3 cameraPos;

    /** Last frame's face-culling numbers (quads drawn / skipped), for a future stats display. */
    public static long quadsDrawn, quadsSkipped;
    static long pendingDrawn, pendingSkipped;

    public static void beginFrame(Vec3 cam) {
        FrameStats.frame();
        cameraPos = cam;
        quadsDrawn = pendingDrawn;
        quadsSkipped = pendingSkipped;
        pendingDrawn = 0;
        pendingSkipped = 0;
    }

    public static void count(long drawn, long skipped) {
        pendingDrawn += drawn;
        pendingSkipped += skipped;
    }

    private QuasiumState() {}
}
