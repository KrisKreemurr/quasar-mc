package io.github.kriskreemurr.quasarmc.quasium.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.kriskreemurr.quasarmc.quasium.FaceBucketHolder;
import io.github.kriskreemurr.quasarmc.quasium.FaceBuckets;
import io.github.kriskreemurr.quasarmc.quasium.QuasiumConfig;
import io.github.kriskreemurr.quasarmc.quasium.QuasiumState;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Face culling at draw time: when vanilla queues one draw for a section's layer, queue only
 * the face groups that can face the camera instead (merged into as few draws as possible).
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void quasium$captureCamera(CallbackInfo ci, @Local(argsOnly = true) CameraRenderState camera) {
        QuasiumState.beginFrame(camera.pos);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @WrapOperation(
            method = "extractSectionDrawGroups",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 2))
    private boolean quasium$faceCull(List list, Object element, Operation<Boolean> original,
                                     @Local SectionRenderDispatcher.RenderSection section,
                                     @Local SectionMesh mesh,
                                     @Local ChunkSectionLayer layer) {
        if (!QuasiumConfig.faceCulling || !(element instanceof DynamicGpuData.IndexedDraw draw)
                || !(mesh instanceof FaceBucketHolder holder)) {
            return original.call(list, element);
        }
        FaceBuckets b = holder.quasium$bucketsFor(layer);
        Vec3 cam = QuasiumState.cameraPos;
        if (b == null || cam == null || draw.indexCount() != b.totalQuads * 6) {
            return original.call(list, element);
        }

        BlockPos origin = section.getRenderOrigin();
        int visible = b.visibleMask(cam.x - origin.getX(), cam.y - origin.getY(), cam.z - origin.getZ());
        if (visible == b.nonEmptyMask()) {
            QuasiumState.count(b.totalQuads, 0);
            return original.call(list, element);
        }

        int drawn = 0;
        int runStart = -1, runEnd = -1; // quad range [runStart, runEnd)
        for (int i = 0; i < FaceBuckets.COUNT; i++) {
            if (b.count[i] == 0) continue;
            if ((visible & (1 << i)) != 0) {
                if (runStart < 0) runStart = b.start[i];
                runEnd = b.start[i] + b.count[i];
            } else if (runStart >= 0) {
                drawn += quasium$emit(list, original, draw, runStart, runEnd);
                runStart = -1;
            }
        }
        if (runStart >= 0) drawn += quasium$emit(list, original, draw, runStart, runEnd);
        QuasiumState.count(drawn, b.totalQuads - drawn);
        return true;
    }

    @SuppressWarnings("rawtypes")
    private static int quasium$emit(List list, Operation<Boolean> original, DynamicGpuData.IndexedDraw draw, int firstQuad, int endQuad) {
        int quads = endQuad - firstQuad;
        if (quads <= 0) return 0;
        original.call(list, new DynamicGpuData.IndexedDraw(
                quads * 6, draw.instanceCount(), draw.firstIndex() + firstQuad * 6, draw.baseVertex(), draw.baseInstance()));
        return quads;
    }
}
