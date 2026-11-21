package io.github.kriskreemurr.quasarmc.lighting;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only side of Quasar MC Lighting. All rendering code will start here.
 * This class never loads on a dedicated server.
 */
@Mod(value = QuasarMCLighting.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = QuasarMCLighting.MODID, value = Dist.CLIENT)
public class QuasarMCLightingClient {

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        QuasarMCLighting.LOGGER.info("Quasar MC Lighting client setup done");
    }
}
