package io.github.kriskreemurr.quasarmc.quasium;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Quasium: Quasar MC's renderer.
 * First feature: face culling (skip chunk faces that point away from the camera).
 */
@Mod(Quasium.MODID)
public class Quasium {
    public static final String MODID = "quasarmc_quasium";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Quasium(IEventBus modEventBus, ModContainer modContainer) {
        QuasiumConfig.load();
        modEventBus.addListener(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            registerDebugEntry();
            io.github.kriskreemurr.quasarmc.quasium.client.ColorGrade.init();
        }));
        LOGGER.info("Quasium loaded (face culling: {}, entity LOD: {})", QuasiumConfig.faceCulling ? "on" : "off", QuasiumConfig.entityLod ? "on" : "off");
    }

    private static void registerDebugEntry() {
        try {
            io.github.kriskreemurr.quasarmc.quasium.mixin.DebugScreenEntriesAccessor.quasium$register(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(MODID, "quasium"),
                    new io.github.kriskreemurr.quasarmc.quasium.entity.QuasiumDebugEntry());
        } catch (Throwable t) {
            LOGGER.warn("Could not add the Quasium F3 lines", t);
        }
    }

}
