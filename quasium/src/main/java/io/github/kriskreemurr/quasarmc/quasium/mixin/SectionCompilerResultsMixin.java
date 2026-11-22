package io.github.kriskreemurr.quasarmc.quasium.mixin;

import io.github.kriskreemurr.quasarmc.quasium.FaceBucketHolder;
import io.github.kriskreemurr.quasarmc.quasium.FaceBuckets;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SectionCompiler.Results.class)
public class SectionCompilerResultsMixin implements FaceBucketHolder {
    @Unique private FaceBuckets[] quasium$buckets;

    @Override
    public FaceBuckets[] quasium$buckets() {
        return quasium$buckets;
    }

    @Override
    public void quasium$setBuckets(FaceBuckets[] buckets) {
        quasium$buckets = buckets;
    }
}
