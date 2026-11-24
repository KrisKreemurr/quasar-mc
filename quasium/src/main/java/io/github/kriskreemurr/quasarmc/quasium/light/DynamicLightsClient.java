package io.github.kriskreemurr.quasarmc.quasium.light;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only registration, kept apart so a dedicated server never loads client event classes. */
public final class DynamicLightsClient {
    private DynamicLightsClient() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> DynamicLights.tick());
    }
}
