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
        LOGGER.info("Quasium loaded (face culling: {})", QuasiumConfig.faceCulling ? "on" : "off");
    }
}
