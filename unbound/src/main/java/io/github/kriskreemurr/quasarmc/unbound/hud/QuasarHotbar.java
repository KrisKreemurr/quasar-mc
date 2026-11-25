package io.github.kriskreemurr.quasarmc.unbound.hud;

import java.lang.reflect.Method;

import org.joml.Matrix3x2fStack;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Quasar's own hotbar: nine 24x24 tilted slots with 1 px gaps (224x24 strip),
 * a 26x26 selection frame, and the offhand drawn as one more slot.
 *
 * The hotbar is 2 px taller than vanilla's and sits 1 px off the bottom so the
 * selection frame fits, so everything vanilla stacks above it (XP bar, hearts,
 * hunger, armor, air) is moved up by {@link #LIFT} pixels.
 */
public final class QuasarHotbar {
    private static final Identifier HOTBAR = Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "hud/hotbar");
    private static final Identifier SELECTION = Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "hud/hotbar_selection");

    static final int SLOT = 24, PITCH = 25, WIDTH = 224, HEIGHT = 24;
    /** How far above vanilla's positions the XP bar and stat rows move. */
    static final int LIFT = 3;

    private QuasarHotbar() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.wrapLayer(VanillaGuiLayers.HOTBAR, vanilla -> (g, dt) -> draw(g, dt, vanilla));
        for (Identifier id : new Identifier[] { VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND,
                VanillaGuiLayers.CONTEXTUAL_INFO_BAR, VanillaGuiLayers.EXPERIENCE_LEVEL }) {
            event.wrapLayer(id, QuasarHotbar::lifted);
        }
    }

    /** Draws a vanilla layer {@link #LIFT} px higher. */
    private static GuiLayer lifted(GuiLayer vanilla) {
        return (g, dt) -> {
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            pose.translate(0, -LIFT);
            vanilla.render(g, dt);
            pose.popMatrix();
        };
    }

    private static void draw(GuiGraphicsExtractor g, DeltaTracker dt, GuiLayer vanilla) {
        Minecraft mc = Minecraft.getInstance();
        Hud hud = mc.gui.hud;
        // Rows stacked above the hotbar (hearts, armor, food, air) start LIFT px higher.
        hud.leftHeight += LIFT;
        hud.rightHeight += LIFT;

        if (!(mc.getCameraEntity() instanceof Player player) || mc.gameMode == null || mc.player == null
                || mc.player.isSpectator()) {
            vanilla.render(g, dt); // spectator menu keeps vanilla's look
            return;
        }

        int x = g.guiWidth() / 2 - WIDTH / 2;
        int y = g.guiHeight() - HEIGHT - 1;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, x, y, WIDTH, HEIGHT);
        int sel = player.getInventory().getSelectedSlot();
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION, x + sel * PITCH - 1, y - 1, SLOT + 2, SLOT + 2);

        ItemStack offhand = player.getOffhandItem();
        int offX = Integer.MIN_VALUE;
        if (!offhand.isEmpty()) {
            boolean leftSide = player.getMainArm() == HumanoidArm.RIGHT;
            offX = leftSide ? x - PITCH : x + WIDTH + 1;
            // One slot cut from the hotbar strip.
            g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, WIDTH, HEIGHT, 0, 0, offX, y, SLOT, SLOT);
        }

        int seed = 1;
        for (int i = 0; i < 9; i++) {
            slot(g, dt, player, player.getInventory().getItem(i), x + i * PITCH + 4, y + 4, seed++);
        }
        if (offX != Integer.MIN_VALUE) slot(g, dt, player, offhand, offX + 4, y + 4, seed);
    }

    private static Method vanillaSlot;
    private static boolean vanillaSlotFailed;

    /** Item plus count/durability, with vanilla's pick-up bounce when available. */
    private static void slot(GuiGraphicsExtractor g, DeltaTracker dt, Player p, ItemStack stack, int x, int y, int seed) {
        if (stack.isEmpty()) return;
        if (!vanillaSlotFailed) {
            try {
                if (vanillaSlot == null) {
                    vanillaSlot = Hud.class.getDeclaredMethod("extractSlot", GuiGraphicsExtractor.class, int.class,
                            int.class, DeltaTracker.class, Player.class, ItemStack.class, int.class);
                    vanillaSlot.setAccessible(true);
                }
                vanillaSlot.invoke(Minecraft.getInstance().gui.hud, g, x, y, dt, p, stack, seed);
                return;
            } catch (ReflectiveOperationException | RuntimeException e) {
                vanillaSlotFailed = true;
                QuasarUnbound.LOGGER.warn("Quasar hotbar: vanilla slot drawing unavailable, using plain items", e);
            }
        }
        g.item(p, stack, x, y, seed);
        g.itemDecorations(Minecraft.getInstance().font, stack, x, y);
    }
}
