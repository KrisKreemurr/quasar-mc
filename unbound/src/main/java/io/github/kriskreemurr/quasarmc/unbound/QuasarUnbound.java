package io.github.kriskreemurr.quasarmc.unbound;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.kriskreemurr.quasarmc.unbound.gold.GoldenWeapons;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Quasar Unbound: everything that adds to or changes the game itself
 * (gear balance, and later gems, survival systems, new blocks...).
 *
 * Each feature lives in its own package and registers itself from here,
 * so a feature can be split into its own mod later without untangling it.
 */
@Mod(QuasarUnbound.MODID)
public class QuasarUnbound {
    public static final String MODID = "quasarmc_unbound";
    public static final Logger LOGGER = LogUtils.getLogger();

    public QuasarUnbound(IEventBus modEventBus, ModContainer modContainer) {
        GoldenWeapons.register(modEventBus);
        LOGGER.info("Quasar Unbound loaded");
    }
}
