package io.github.kriskreemurr.quasarmc.unbound.music;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/**
 * Vanilla in-game music is off (user + brother, 2026-11-27): Quasar's music will be fully dynamic - one song with
 * stems that fade in and out with what you're doing (day/night, caves, combat, sanity, bosses) - and vanilla's
 * "a random track, then 10-20 minutes of silence" doesn't fit that. The title screen keeps its music. The dynamic
 * system takes over here once tracks exist.
 */
public final class QuasarMusic {
    private QuasarMusic() {}

    public static void select(SelectMusicEvent event) {
        if (Minecraft.getInstance().level == null) return; // menus: leave vanilla alone
        event.setMusic(null);
    }
}
