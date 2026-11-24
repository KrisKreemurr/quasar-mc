package io.github.kriskreemurr.quasarmc.quasium.light;

import net.minecraft.core.SectionPos;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Hands section light colors to Quasar Meshelium (reflectively; does nothing without it). */
public final class ColorBridge {
    private static volatile boolean looked;
    private static MethodHandle submit;
    private static MethodHandle setMode;

    private ColorBridge() {}

    /**
     * CPU copy of the section colors for entity tint: SectionPos.asLong -> 4096 RGBA8 (index (y*16+z)*16+x).
     * Kept to render distance + 1 by {@link #prune}.
     */
    private static final java.util.concurrent.ConcurrentHashMap<Long, int[]> CELLS = new java.util.concurrent.ConcurrentHashMap<>();

    static void submit(SectionPos section, int[] cells) {
        if (cells == null) CELLS.remove(section.asLong());
        else CELLS.put(section.asLong(), cells);
        if (!looked) init();
        MethodHandle h = submit;
        if (h == null) return;
        try {
            h.invokeExact(section.x(), section.y(), section.z(), cells);
        } catch (Throwable t) {
            submit = null;
        }
    }

    /** RGBA8 of the block at x, y, z (rgb = light color, a = brightness in sixteenths), 0 if unknown. */
    static int cell(int x, int y, int z) {
        int[] c = CELLS.get(SectionPos.asLong(x >> 4, y >> 4, z >> 4));
        return c == null ? 0 : c[((y & 15) * 16 + (z & 15)) * 16 + (x & 15)];
    }

    /** Drop sections farther than {@code radius} sections (horizontally) from the camera section. */
    static void prune(int cx, int cz, int radius) {
        CELLS.keySet().removeIf(k -> Math.abs(SectionPos.x(k) - cx) > radius || Math.abs(SectionPos.z(k) - cz) > radius);
    }

    /** Colored Light setting changed: switch Meshelium's shader between color and plain. */
    public static void applySetting() {
        if (!looked) init();
        try {
            if (setMode != null) setMode.invoke(io.github.kriskreemurr.quasarmc.quasium.QuasiumConfig.coloredLight ? 2 : 0);
        } catch (Throwable ignored) {
        }
    }

    private static synchronized void init() {
        if (looked) return;
        try {
            Class<?> c = Class.forName("com.deds.meshelium.quasar.QuasarColorLight", true, ColorBridge.class.getClassLoader());
            submit = MethodHandles.publicLookup().findStatic(c, "submitSection",
                    MethodType.methodType(void.class, int.class, int.class, int.class, int[].class));
            setMode = MethodHandles.publicLookup().findStatic(c, "setMode", MethodType.methodType(void.class, int.class));
            setMode.invoke(io.github.kriskreemurr.quasarmc.quasium.QuasiumConfig.coloredLight ? 2 : 0);
        } catch (Throwable t) {
            submit = null;
        }
        looked = true;
    }
}
