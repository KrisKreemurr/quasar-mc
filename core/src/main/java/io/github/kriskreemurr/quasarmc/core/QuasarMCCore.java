package io.github.kriskreemurr.quasarmc.core;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Quasar MC Core: shared code used by the other Quasar MC mods.
 * For now it only proves that it loads. Shared render/config helpers will live here.
 */
@Mod(QuasarMCCore.MODID)
public class QuasarMCCore {
    public static final String MODID = "quasarmc_core";
    public static final Logger LOGGER = LogUtils.getLogger();

    public QuasarMCCore(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Quasar MC Core loaded");
    }
}
