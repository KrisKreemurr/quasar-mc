package io.github.kriskreemurr.quasarmc.quasium;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

/** Added to SectionCompiler.Results and CompiledSectionMesh by mixins: face buckets per layer. */
public interface FaceBucketHolder {
    FaceBuckets[] quasium$buckets();

    void quasium$setBuckets(FaceBuckets[] buckets);

    default FaceBuckets quasium$bucketsFor(ChunkSectionLayer layer) {
        FaceBuckets[] b = quasium$buckets();
        return b == null ? null : b[layer.ordinal()];
    }
}
