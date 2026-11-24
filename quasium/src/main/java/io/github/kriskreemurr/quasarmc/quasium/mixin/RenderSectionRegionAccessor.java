package io.github.kriskreemurr.quasarmc.quasium.mixin;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCopy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSectionRegion.class)
public interface RenderSectionRegionAccessor {
    @Accessor("sections") SectionCopy[] quasium$sections();
    @Accessor("minSectionX") int quasium$minSectionX();
    @Accessor("minSectionY") int quasium$minSectionY();
    @Accessor("minSectionZ") int quasium$minSectionZ();
}
