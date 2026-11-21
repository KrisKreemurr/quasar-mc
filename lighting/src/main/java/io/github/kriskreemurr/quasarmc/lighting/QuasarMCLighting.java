package io.github.kriskreemurr.quasarmc.lighting;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.kriskreemurr.quasarmc.core.QuasarMCCore;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Quasar MC Lighting: colored block light and time-of-day sky tint (Phase 1).
 * For now it only proves that it loads and can see Core.
 */
@Mod(QuasarMCLighting.MODID)
public class QuasarMCLighting {
    public static final String MODID = "quasarmc_lighting";
    public static final Logger LOGGER = LogUtils.getLogger();

    public QuasarMCLighting(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Quasar MC Lighting loaded (using {})", QuasarMCCore.MODID);
    }
}
