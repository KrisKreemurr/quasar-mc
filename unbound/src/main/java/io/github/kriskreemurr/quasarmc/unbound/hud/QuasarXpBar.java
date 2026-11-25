package io.github.kriskreemurr.quasarmc.unbound.hud;

import java.lang.reflect.Field;

import com.mojang.datafixers.util.Pair;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Quasar's wavy XP bar: 262x8 sprites (wave on rows 1-6, clear rows above and below),
 * starting at the hotbar's left edge and running past it to where the brain dial goes.
 * Only replaces the bar while vanilla is showing XP; the locator and horse-jump bars
 * stay vanilla, lifted to sit above the taller hotbar.
 */
public final class QuasarXpBar {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "hud/experience_bar_background");
    private static final Identifier PROGRESS = Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "hud/experience_bar_progress");

    static final int WIDTH = 262, HEIGHT = 8;
    /** Sprite top relative to the hotbar top: wave ends 3 px above it, clear of the selection frame. */
    static final int OFFSET = -9;

    private QuasarXpBar() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.wrapLayer(VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND, v -> bar(v, true));
        event.wrapLayer(VanillaGuiLayers.CONTEXTUAL_INFO_BAR, v -> bar(v, false));
        event.wrapLayer(VanillaGuiLayers.EXPERIENCE_LEVEL, QuasarHotbar::lifted);
    }

    private static GuiLayer bar(GuiLayer vanilla, boolean background) {
        GuiLayer lifted = QuasarHotbar.lifted(vanilla);
        return (g, dt) -> {
            if (showingXp()) draw(g, background);
            else lifted.render(g, dt);
        };
    }

    private static void draw(GuiGraphicsExtractor g, boolean background) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.getXpNeededForNextLevel() <= 0) return;
        int x = QuasarHotbar.left(g);
        int y = QuasarHotbar.top(g) + OFFSET;
        if (background) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, WIDTH, HEIGHT);
        } else {
            int filled = Math.min(WIDTH, (int) (p.experienceProgress * (WIDTH + 1)));
            if (filled > 0) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS, WIDTH, HEIGHT, 0, 0, x, y, filled, HEIGHT);
            }
        }
    }

    private static Field active;
    private static boolean activeFailed;

    /** True while vanilla's bottom bar is the XP bar (not the locator or horse jump bar). */
    private static boolean showingXp() {
        if (activeFailed) return false;
        try {
            if (active == null) {
                active = Hud.class.getDeclaredField("contextualInfoBar");
                active.setAccessible(true);
            }
            Object pair = active.get(Minecraft.getInstance().gui.hud);
            return pair instanceof Pair<?, ?> pr && pr.getSecond() instanceof ExperienceBar;
        } catch (ReflectiveOperationException | RuntimeException e) {
            activeFailed = true;
            QuasarUnbound.LOGGER.warn("Quasar XP bar: can't tell which bar is showing, keeping vanilla's", e);
            return false;
        }
    }
}
