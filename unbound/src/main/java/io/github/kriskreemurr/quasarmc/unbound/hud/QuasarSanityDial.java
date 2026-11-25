package io.github.kriskreemurr.quasarmc.unbound.hud;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import io.github.kriskreemurr.quasarmc.unbound.sanity.Sanity;
import io.github.kriskreemurr.quasarmc.unbound.sanity.SanityClientState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Sanity dial: a 26x26 orb on the side of the hotbar opposite the offhand (centred on the
 * 24x24 spot that mirrors it). Layers: container, purple fill drained from the top, brain shadow, brain.
 * Brain stage: full above 2/3, mid above 1/3, small below.
 *
 * The level comes from the server ({@link SanityClientState}) and slides to new values, so a
 * sudden drop visibly drains pixel by pixel. Admins can set it with {@code /quasarsanity <0-100>}.
 */
public final class QuasarSanityDial {
    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "hud/sanity/" + name);
    }
    private static final Identifier CONTAINER = sprite("sanity_container");
    private static final Identifier FILL = sprite("sanity_progress");
    private static final Identifier[] BRAIN = { sprite("brain_small"), sprite("brain_mid"), sprite("brain_full") };
    private static final Identifier[] SHADOW = { sprite("brain_small_shadow"), sprite("brain_mid_shadow"), sprite("brain_full_shadow") };

    static final int SIZE = 26;
    /** Rows of the fill sprite that hold liquid (top row inclusive, bottom row exclusive). */
    static final int FILL_TOP = 3, FILL_BOTTOM = 23;

    /** Sanity 0..1 currently drawn; slides toward the server's value. */
    private static float shown = 1f;
    private static long lastNanos = 0L;
    /** How quickly the drawn level catches up (fraction of the gap per second, roughly). */
    static final float SLIDE_PER_SEC = 6f;

    private QuasarSanityDial() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "sanity_dial"),
                QuasarSanityDial::draw);
    }

    private static void draw(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) return;

        // The side opposite the offhand, 1 px larger than a slot on every side.
        int hx = QuasarHotbar.left(g);
        int x = (QuasarHotbar.offhandOnLeft(mc.player)
                ? hx + QuasarHotbar.WIDTH + QuasarHotbar.OFFHAND_GAP
                : hx - QuasarHotbar.SLOT - QuasarHotbar.OFFHAND_GAP) - 1;
        int y = QuasarHotbar.top(g) - 1;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, CONTAINER, x, y, SIZE, SIZE);

        float target = Math.max(0f, Math.min(1f, SanityClientState.value / Sanity.MAX));
        long now = System.nanoTime();
        float secs = lastNanos == 0L ? 1f : Math.min(1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        shown += (target - shown) * Math.min(1f, secs * SLIDE_PER_SEC);
        if (Math.abs(target - shown) < 0.002f) shown = target;
        float f = shown;
        int h = Math.round(f * (FILL_BOTTOM - FILL_TOP));
        if (h > 0) {
            int top = FILL_BOTTOM - h;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, FILL, SIZE, SIZE, 0, top, x, y + top, SIZE, h);
        }

        int stage = f > 2f / 3f ? 2 : f > 1f / 3f ? 1 : 0;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SHADOW[stage], x, y, SIZE, SIZE);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, BRAIN[stage], x, y, SIZE, SIZE);
    }
}
