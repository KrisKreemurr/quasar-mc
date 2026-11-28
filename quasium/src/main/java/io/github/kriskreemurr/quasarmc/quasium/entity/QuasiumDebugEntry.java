package io.github.kriskreemurr.quasarmc.quasium.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/** F3 lines for Quasium. */
public class QuasiumDebugEntry implements DebugScreenEntry {
    @Override
    public void display(DebugScreenDisplayer displayer, @Nullable Level level, @Nullable LevelChunk a, @Nullable LevelChunk b) {
        displayer.addLine(ChatFormatting.LIGHT_PURPLE + "[Quasium]" + ChatFormatting.RESET);
        displayer.addLine(io.github.kriskreemurr.quasarmc.quasium.FrameStats.line1());
        String lows = io.github.kriskreemurr.quasarmc.quasium.FrameStats.line2();
        if (!lows.isEmpty()) displayer.addLine(lows);
        displayer.addLine(EntityLod.f3Line());
        displayer.addLine(io.github.kriskreemurr.quasarmc.quasium.light.RoundLight.f3Line());
        String roundSplit = io.github.kriskreemurr.quasarmc.quasium.light.RoundLight.f3Split();
        if (roundSplit != null) displayer.addLine(roundSplit);
        displayer.addLine(io.github.kriskreemurr.quasarmc.quasium.light.DynamicLights.f3Line());
        String portals = portalLine();
        if (portals != null) displayer.addLine(portals);
    }

    // Quasar Immersive Portals' timing line, looked up by name so Quasium does not depend on the portal mod
    private static java.lang.reflect.Method portalLineMethod;
    private static boolean portalLookupDone;

    private static String portalLine() {
        if (!portalLookupDone) {
            portalLookupDone = true;
            try {
                Class<?> c = Class.forName("qouteall.imm_ptl.core.render.quasar.PortalViewRenderer", false,
                        QuasiumDebugEntry.class.getClassLoader());
                portalLineMethod = c.getMethod("f3Line");
            } catch (Throwable ignored) {
                portalLineMethod = null;
            }
        }
        if (portalLineMethod == null) return null;
        try {
            return (String) portalLineMethod.invoke(null);
        } catch (Throwable t) {
            portalLineMethod = null;
            return null;
        }
    }

    @Override
    public boolean isAllowed(boolean reducedDebugInfo) {
        return true;
    }
}
