package io.github.kriskreemurr.quasarmc.unbound;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.kriskreemurr.quasarmc.unbound.gold.GoldenHoe;
import io.github.kriskreemurr.quasarmc.unbound.gold.GoldenTools;
import io.github.kriskreemurr.quasarmc.unbound.gold.GoldenWeapons;
import io.github.kriskreemurr.quasarmc.unbound.hoe.HoeCombat;
import io.github.kriskreemurr.quasarmc.unbound.sanity.Sanity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

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
        GoldenTools.register(modEventBus);
        io.github.kriskreemurr.quasarmc.unbound.gold.FlintAndGold.register(modEventBus);
        GoldenHoe.register(NeoForge.EVENT_BUS);
        HoeCombat.register(NeoForge.EVENT_BUS);
        Sanity.register(modEventBus);
        io.github.kriskreemurr.quasarmc.unbound.boomerang.Boomerangs.register(modEventBus);
        LOGGER.info("Quasar Unbound loaded");
    }
}
