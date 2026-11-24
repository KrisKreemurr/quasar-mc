package io.github.kriskreemurr.quasarmc.quasium;

import java.util.Arrays;

/**
 * Frame-time stats for the [Quasium] F3 lines: average FPS, 1% / 0.1% lows and the worst frame over the last
 * 5 seconds. Every frame's time is recorded (render thread), so unlike vanilla's once-a-second FPS counter
 * this also catches single-frame hitches.
 */
public final class FrameStats {
    private static final long WINDOW_NANOS = 5_000_000_000L;
    private static final int CAP = 1 << 15; // 32768 frames ≈ 5 s at 6500 FPS

    private static final long[] times = new long[CAP];     // frame durations, nanos
    private static final long[] stamps = new long[CAP];    // when each frame ended
    private static int head, count;
    private static long last;
    private static long lastReport;
    private static String line1 = "Frames: measuring...", line2 = "";

    private FrameStats() {}

    /** Called once per rendered frame. */
    public static void frame() {
        long now = System.nanoTime();
        if (last != 0) {
            long dt = now - last;
            if (dt < 2_000_000_000L) { // ignore pauses (menus, loading)
                times[head] = dt;
                stamps[head] = now;
                head = (head + 1) & (CAP - 1);
                if (count < CAP) count++;
            }
        }
        last = now;
    }

    public static String line1() {
        update();
        return line1;
    }

    public static String line2() {
        return line2;
    }

    private static void update() {
        long now = System.nanoTime();
        if (now - lastReport < 1_000_000_000L) return;
        lastReport = now;
        int n = 0;
        long total = 0;
        long[] window = new long[count];
        for (int i = 0; i < count; i++) {
            int idx = (head - 1 - i) & (CAP - 1);
            if (now - stamps[idx] > WINDOW_NANOS) break;
            window[n++] = times[idx];
            total += times[idx];
        }
        if (n < 10) {
            line1 = "Frames: measuring...";
            line2 = "";
            return;
        }
        window = Arrays.copyOf(window, n);
        Arrays.sort(window); // ascending: slowest frames at the end
        double avgMs = total / 1e6 / n;
        line1 = String.format("Frames (last 5 s): %.0f FPS avg (%.3f ms), %d frames", 1000.0 / avgMs, avgMs, n);
        line2 = String.format("Lows: 1%% %.0f FPS, 0.1%% %.0f FPS, worst frame %.2f ms",
                1000.0 / worstAvgMs(window, 0.01), 1000.0 / worstAvgMs(window, 0.001), window[n - 1] / 1e6);
    }

    /** Average time (ms) of the slowest {@code fraction} of frames (at least one frame). */
    private static double worstAvgMs(long[] sorted, double fraction) {
        int k = Math.max(1, (int) Math.round(sorted.length * fraction));
        long sum = 0;
        for (int i = sorted.length - k; i < sorted.length; i++) sum += sorted[i];
        return sum / 1e6 / k;
    }
}
