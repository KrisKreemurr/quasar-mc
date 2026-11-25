package io.github.kriskreemurr.quasarmc.unbound.hud;

import java.lang.reflect.Field;

import com.mojang.datafixers.util.Pair;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import net.minecraft.client.gui.contextualbar.JumpableVehicleBar;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Quasar's wavy XP bar: 302x8 sprites (user art, 10 px boxes) (wave on rows 1-6, clear rows above and below),
 * centred on screen: spans offhand (24) + gap (13) + hotbar (224) + gap (13) + brain dial (24) = 298, plus 2 px each end.
 * Only replaces the bar while vanilla is showing XP; the locator and horse-jump bars
 * stay vanilla, lifted to sit above the taller hotbar.
 */
public final class QuasarXpBar {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "hud/experience_bar_background");
    private static final Identifier PROGRESS = Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "hud/experience_bar_progress");

    private static final Identifier JUMP_BACKGROUND = Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "hud/jump_bar_background");
    private static final Identifier JUMP_PROGRESS = Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "hud/jump_bar_progress");
    private static final Identifier JUMP_COOLDOWN = Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "hud/jump_bar_cooldown");

    static final int WIDTH = 302, HEIGHT = 8;
    /** Jump bar sits this far above the XP bar: same 6-px wave, 1 px gap between them. */
    static final int JUMP_ABOVE = 7;
    /** How far above vanilla's position the green level number moves. */
    static final int LEVEL_LIFT = 10;
    /** Sprite top relative to the hotbar top: wave ends 3 px above it, clear of the selection frame. */
    static final int OFFSET = -9;

    private QuasarXpBar() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.wrapLayer(VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND, v -> bar(v, true));
        event.wrapLayer(VanillaGuiLayers.CONTEXTUAL_INFO_BAR, v -> bar(v, false));
        event.wrapLayer(VanillaGuiLayers.EXPERIENCE_LEVEL, v -> QuasarHotbar.lifted(v, LEVEL_LIFT));
    }

    private static GuiLayer bar(GuiLayer vanilla, boolean background) {
        GuiLayer lifted = QuasarHotbar.lifted(vanilla);
        return (g, dt) -> {
            Object active = activeBar();
            if (active instanceof ExperienceBar) {
                drawXp(g, background);
            } else if (active instanceof JumpableVehicleBar) {
                // Riding: XP stays, the jump bar sits on top of it (or in its place without XP).
                boolean xp = hasXp();
                if (xp) drawXp(g, background);
                drawJump(g, background, xp ? QuasarHotbar.top(g) + OFFSET - JUMP_ABOVE : QuasarHotbar.top(g) + OFFSET);
            } else {
                lifted.render(g, dt); // locator bar (and anything else) stays vanilla
            }
        };
    }

    /** True when the XP bar is shown: every mode but spectator (survival HUD stays on in creative). */
    static boolean hasXp() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && !mc.player.isSpectator();
    }

    /** True while the jump bar is drawn above the XP bar, so the stat rows above must make room. */
    static boolean jumpBarStacked() {
        return activeBar() instanceof JumpableVehicleBar && hasXp();
    }

    private static void drawXp(GuiGraphicsExtractor g, boolean background) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.getXpNeededForNextLevel() <= 0) return;
        int x = g.guiWidth() / 2 - WIDTH / 2;
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

    private static void drawJump(GuiGraphicsExtractor g, boolean background, int y) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        PlayerRideableJumping mount = p.jumpableVehicle();
        if (mount == null) return;
        int x = g.guiWidth() / 2 - WIDTH / 2;
        if (background) {
            // Camels: while the dash recharges the whole bar shows the cooldown art.
            g.blitSprite(RenderPipelines.GUI_TEXTURED, mount.getJumpCooldown() > 0 ? JUMP_COOLDOWN : JUMP_BACKGROUND,
                    x, y, WIDTH, HEIGHT);
        } else if (mount.getJumpCooldown() <= 0) {
            int filled = Math.min(WIDTH, (int) (p.getJumpRidingScale() * (WIDTH + 1)));
            if (filled > 0) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, JUMP_PROGRESS, WIDTH, HEIGHT, 0, 0, x, y, filled, HEIGHT);
            }
        }
    }

    private static Field active;
    private static boolean activeFailed;

    /** Vanilla's active bottom bar (ExperienceBar, JumpableVehicleBar, LocatorBar...), or null. */
    private static Object activeBar() {
        if (activeFailed) return null;
        try {
            if (active == null) {
                active = Hud.class.getDeclaredField("contextualInfoBar");
                active.setAccessible(true);
            }
            Object pair = active.get(Minecraft.getInstance().gui.hud);
            return pair instanceof Pair<?, ?> pr ? pr.getSecond() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            activeFailed = true;
            QuasarUnbound.LOGGER.warn("Quasar XP bar: can't tell which bar is showing, keeping vanilla's", e);
            return null;
        }
    }
}
