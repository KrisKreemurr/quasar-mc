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
 * The hotbar is 2 px taller than vanilla's and sits BOTTOM_GAP px off the bottom,
 * and the XP bar above it ({@link QuasarXpBar}) is 1 px taller, so everything vanilla
 * stacks above them (XP level, hearts, hunger, armor, air) moves up by {@link #LIFT} px.
 */
public final class QuasarHotbar {
    private static final Identifier HOTBAR = Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "hud/hotbar");
    private static final Identifier SELECTION = Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "hud/hotbar_selection");

    static final int SLOT = 24, PITCH = 25, WIDTH = 224, HEIGHT = 24;
    /** Space between the hotbar and the bottom of the screen. */
    static final int BOTTOM_GAP = 3;
    /** How far above vanilla's positions the XP level number and stat rows move. */
    static final int LIFT = 6;
    /** Gap between the hotbar and the offhand slot (half a slot plus 1). */
    static final int OFFHAND_GAP = 13;

    private QuasarHotbar() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.wrapLayer(VanillaGuiLayers.HOTBAR, vanilla -> (g, dt) -> draw(g, dt, vanilla));
    }

    /** True when the offhand slot goes left of the hotbar (main hand set to Right). */
    static boolean offhandOnLeft(Player player) {
        return player.getMainArm() == HumanoidArm.RIGHT;
    }

    /** Top edge of the hotbar strip on screen. */
    static int top(GuiGraphicsExtractor g) {
        return g.guiHeight() - HEIGHT - BOTTOM_GAP;
    }

    /** Left edge of the hotbar strip on screen. */
    static int left(GuiGraphicsExtractor g) {
        return g.guiWidth() / 2 - WIDTH / 2;
    }

    /** Draws a vanilla layer {@link #LIFT} px higher. */
    static GuiLayer lifted(GuiLayer vanilla) {
        return lifted(vanilla, LIFT);
    }

    /** Draws a vanilla layer {@code lift} px higher. */
    static GuiLayer lifted(GuiLayer vanilla, int lift) {
        return (g, dt) -> {
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            pose.translate(0, -lift);
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

        int x = left(g);
        int y = top(g);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, x, y, WIDTH, HEIGHT);
        int sel = player.getInventory().getSelectedSlot();
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION, x + sel * PITCH - 1, y - 1, SLOT + 2, SLOT + 2);

        // Offhand slot is always shown (empty frame when nothing is held), one slot cut from the strip.
        ItemStack offhand = player.getOffhandItem();
        // Vanilla rule: offhand on the left for right-handed players, right for left-handed.
        // The sanity dial takes the other side.
        int offX = offhandOnLeft(player) ? x - SLOT - OFFHAND_GAP : x + WIDTH + OFFHAND_GAP;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, WIDTH, HEIGHT, 0, 0, offX, y, SLOT, SLOT);

        int seed = 1;
        for (int i = 0; i < 9; i++) {
            slot(g, dt, player, player.getInventory().getItem(i), x + i * PITCH + 4, y + 4, seed++);
        }
        slot(g, dt, player, offhand, offX + 4, y + 4, seed);
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
