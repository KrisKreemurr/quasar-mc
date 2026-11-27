package io.github.kriskreemurr.quasarmc.unbound.boomerang.client;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import io.github.kriskreemurr.quasarmc.unbound.boomerang.BoomerangCatchPayload;
import io.github.kriskreemurr.quasarmc.unbound.boomerang.BoomerangEntity;
import io.github.kriskreemurr.quasarmc.unbound.boomerang.Boomerangs;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client side: the spinning renderer, catching with an empty hand, and the "Catch!" prompt. */
public final class BoomerangClient {
    private BoomerangClient() {}

    /** the tilted-arm hold (not while winding up: then vanilla's trident pose) */
    public static void registerItemExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent e) {
        var ext = new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(net.minecraft.world.entity.LivingEntity entity,
                    InteractionHand hand, net.minecraft.world.item.ItemStack stack) {
                if (entity.isUsingItem() && entity.getUsedItemHand() == hand) return null;
                // gliding: both boomerangs up overhead (you: by your jump key; others: airborne with two Glide boomerangs)
                if (entity instanceof net.minecraft.world.entity.player.Player p
                        && io.github.kriskreemurr.quasarmc.unbound.boomerang.BoomerangGlide.gliding(p,
                            p.isLocalPlayer() ? jumpHeld() : true)) return BoomerangArmPose.GLIDE.getValue();
                return BoomerangArmPose.HOLD.getValue();
            }
        };
        for (var tier : io.github.kriskreemurr.quasarmc.unbound.boomerang.BoomerangTier.values()) {
            e.registerItem(ext, Boomerangs.ITEM.get(tier).get());
        }
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(Boomerangs.ENTITY.get(), BoomerangRenderer::new);
    }

    public static void registerHud(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.CROSSHAIR, Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "boomerang_catch"),
            BoomerangClient::drawPrompt);
    }

    /** jump held by the local player (Glide) */
    public static boolean jumpHeld() {
        return Minecraft.getInstance().options.keyJump.isDown();
    }

    /** the flying loop: follows the boomerang (mono, so it fades with distance) until its first hit */
    public static void startFlyingSound(BoomerangEntity b) {
        Minecraft.getInstance().getSoundManager().play(new BoomerangFlyingSound(b));
    }

    /** your boomerang with its catch window open (the nearest, if both are), or null */
    static BoomerangEntity catchable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        BoomerangEntity best = null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof BoomerangEntity b && b.catchWindow() && b.getOwner() == mc.player
                && (best == null || b.distanceToSqr(mc.player) < best.distanceToSqr(mc.player))) best = b;
        }
        return best;
    }

    /**
     * Right-click while your boomerang can be caught: catch it with an empty hand (main first, then offhand) instead
     * of using whatever else you hold. No empty hand: nothing special (it will hit you).
     */
    public static void onUse(InputEvent.InteractionKeyMappingTriggered e) {
        BoomerangEntity b;
        if (!e.isUseItem() || (b = catchable()) == null) return;
        Minecraft mc = Minecraft.getInstance();
        // back into the hand that threw it if that hand is free, else the other free hand
        InteractionHand own = b.thrownFrom();
        InteractionHand other = own == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        InteractionHand hand = mc.player.getItemInHand(own).isEmpty() ? own
            : mc.player.getItemInHand(other).isEmpty() ? other : null;
        if (hand == null) return;
        ClientPacketDistributor.sendToServer(new BoomerangCatchPayload(hand == InteractionHand.OFF_HAND));
        e.setSwingHand(true);
        e.setCanceled(true);
    }

    private static void drawPrompt(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (catchable() == null) return;
        Minecraft mc = Minecraft.getInstance();
        boolean hasEmptyHand = mc.player.getMainHandItem().isEmpty() || mc.player.getOffhandItem().isEmpty();
        String text = hasEmptyHand ? "Catch!" : "Catch! (empty a hand)";
        int x = g.guiWidth() / 2, y = g.guiHeight() / 2 + 12;
        g.centeredText(mc.font, text, x, y, hasEmptyHand ? 0xFFFFE070 : 0xFFFF7060);
    }
}
