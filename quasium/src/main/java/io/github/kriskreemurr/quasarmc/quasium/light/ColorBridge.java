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

    static void submit(SectionPos section, int[] cells) {
        if (!looked) init();
        MethodHandle h = submit;
        if (h == null) return;
        try {
            h.invokeExact(section.x(), section.y(), section.z(), cells);
        } catch (Throwable t) {
            submit = null;
        }
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
