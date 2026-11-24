package io.github.kriskreemurr.quasarmc.quasium.mixin;

import io.github.kriskreemurr.quasarmc.quasium.light.ColorBridge;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Numbers every world snapshot a section build works from, in the order the snapshots are taken. Builds of the
 * same section can finish out of order on the worker threads; the number lets {@link ColorBridge} drop colors
 * from an older snapshot that finishes after a newer one.
 */
@Mixin(RenderSectionRegion.class)
public abstract class RenderSectionRegionSeqMixin implements ColorBridge.Snapshot {
    @Unique private final long quasium$seq = ColorBridge.nextSeq();

    @Override public long quasium$seq() { return quasium$seq; }
}
