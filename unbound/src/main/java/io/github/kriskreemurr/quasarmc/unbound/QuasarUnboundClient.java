package io.github.kriskreemurr.quasarmc.unbound;

import io.github.kriskreemurr.quasarmc.unbound.hud.QuasarHotbar;
import io.github.kriskreemurr.quasarmc.unbound.hud.QuasarSanityDial;
import io.github.kriskreemurr.quasarmc.unbound.hud.QuasarXpBar;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Client-only side of Quasar Unbound (HUD). Never loads on a dedicated server. */
@Mod(value = QuasarUnbound.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = QuasarUnbound.MODID, value = Dist.CLIENT)
public class QuasarUnboundClient {

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        QuasarHotbar.register(event);
        QuasarXpBar.register(event);
        QuasarSanityDial.register(event);
        io.github.kriskreemurr.quasarmc.unbound.boomerang.client.BoomerangClient.registerHud(event);
    }

    @SubscribeEvent
    static void onRegisterRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        io.github.kriskreemurr.quasarmc.unbound.boomerang.client.BoomerangClient.registerRenderers(event);
    }

    @SubscribeEvent
    static void onUseKey(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        io.github.kriskreemurr.quasarmc.unbound.boomerang.client.BoomerangClient.onUse(event);
    }

}
