package io.github.kriskreemurr.quasarmc.unbound.sanity;

/**
 * The local player's sanity as last sent by the server (0..100). Plain field in a common
 * class so the packet handler never touches client-only classes on a dedicated server.
 */
public final class SanityClientState {
    public static volatile float value = Sanity.MAX;
    /** Current change per minute (negative = draining), for the dial's arrows. */
    public static volatile float ratePerMinute = 0f;

    private SanityClientState() {}
}
