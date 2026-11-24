package io.github.kriskreemurr.quasarmc.quasium.light;

import net.minecraft.core.SectionPos;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Hands section light colors to Quasar Meshelium (reflectively; does nothing without it). */
final class ColorBridge {
    private static volatile boolean looked;
    private static MethodHandle submit;

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

    private static synchronized void init() {
        if (looked) return;
        try {
            Class<?> c = Class.forName("com.deds.meshelium.quasar.QuasarColorLight", true, ColorBridge.class.getClassLoader());
            submit = MethodHandles.publicLookup().findStatic(c, "submitSection",
                    MethodType.methodType(void.class, int.class, int.class, int.class, int[].class));
            MethodHandles.publicLookup().findStatic(c, "setMode", MethodType.methodType(void.class, int.class)).invoke(2);
        } catch (Throwable t) {
            submit = null;
        }
        looked = true;
    }
}
