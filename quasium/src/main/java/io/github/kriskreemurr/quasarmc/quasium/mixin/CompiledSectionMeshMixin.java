package io.github.kriskreemurr.quasarmc.quasium.mixin;

import io.github.kriskreemurr.quasarmc.quasium.FaceBucketHolder;
import io.github.kriskreemurr.quasarmc.quasium.FaceBuckets;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carry the face buckets from the build results onto the section mesh that gets drawn. */
@Mixin(CompiledSectionMesh.class)
public class CompiledSectionMeshMixin implements FaceBucketHolder {
    @Unique private FaceBuckets[] quasium$buckets;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void quasium$copyBuckets(TranslucencyPointOfView pov, SectionCompiler.Results results, long startTime, CallbackInfo ci) {
        quasium$buckets = ((FaceBucketHolder) (Object) results).quasium$buckets();
    }

    @Override
    public FaceBuckets[] quasium$buckets() {
        return quasium$buckets;
    }

    @Override
    public void quasium$setBuckets(FaceBuckets[] buckets) {
        quasium$buckets = buckets;
    }
}
