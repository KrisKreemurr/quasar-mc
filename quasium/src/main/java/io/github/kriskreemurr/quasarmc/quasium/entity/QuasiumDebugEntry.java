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
    }

    @Override
    public boolean isAllowed(boolean reducedDebugInfo) {
        return true;
    }
}
