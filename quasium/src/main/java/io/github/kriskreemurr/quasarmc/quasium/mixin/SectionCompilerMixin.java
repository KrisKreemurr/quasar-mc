package io.github.kriskreemurr.quasarmc.quasium.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import io.github.kriskreemurr.quasarmc.quasium.FaceBucketHolder;
import io.github.kriskreemurr.quasarmc.quasium.FaceBuckets;
import io.github.kriskreemurr.quasarmc.quasium.Quasium;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** After a chunk section is built (on a worker thread, before upload), group its quads by facing. */
@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {
    @Unique private static boolean quasium$warned;

    @Inject(
            method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
            at = @At("RETURN"))
    private void quasium$bucketFaces(CallbackInfoReturnable<SectionCompiler.Results> cir) {
        SectionCompiler.Results results = cir.getReturnValue();
        if (results == null) return;
        FaceBuckets[] buckets = null;
        try {
            for (Map.Entry<ChunkSectionLayer, MeshData> e : results.renderedLayers.entrySet()) {
                ChunkSectionLayer layer = e.getKey();
                if (layer.translucent() || e.getValue() == null) continue;
                if (!layer.pipeline(false).isCull()) continue; // two-sided geometry: never skip faces
                FaceBuckets b = FaceBuckets.sortInPlace(e.getValue());
                if (b == null) continue;
                if (buckets == null) buckets = new FaceBuckets[ChunkSectionLayer.values().length];
                buckets[layer.ordinal()] = b;
            }
        } catch (Throwable t) {
            buckets = null; // fall back to normal drawing for this section
            if (!quasium$warned) {
                quasium$warned = true;
                Quasium.LOGGER.error("Quasium face bucketing failed; this section draws normally", t);
            }
        }
        ((FaceBucketHolder) (Object) results).quasium$setBuckets(buckets);
    }
}
