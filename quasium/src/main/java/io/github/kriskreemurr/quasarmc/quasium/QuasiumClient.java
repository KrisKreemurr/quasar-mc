package io.github.kriskreemurr.quasarmc.quasium;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Quasium's client-only setup (key bindings). */
@Mod(value = Quasium.MODID, dist = Dist.CLIENT)
public class QuasiumClient {
    public QuasiumClient(IEventBus modEventBus) {
        io.github.kriskreemurr.quasarmc.quasium.client.F3Snapshot.register(modEventBus);
    }
}
