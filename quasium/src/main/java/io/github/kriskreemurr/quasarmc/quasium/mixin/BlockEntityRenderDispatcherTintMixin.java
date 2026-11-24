package io.github.kriskreemurr.quasarmc.quasium.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.kriskreemurr.quasarmc.quasium.QuasiumConfig;
import io.github.kriskreemurr.quasarmc.quasium.light.EntityTint;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chests, signs, beds, banners, shulker boxes, skulls...: round light (brighter of vanilla and Quasium's baked
 * round value) and the light-color tint, the same way entities get it.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherTintMixin {
    @Inject(method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;ZLnet/minecraft/client/renderer/culling/Frustum;)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
            at = @At("RETURN"))
    private <E extends BlockEntity, S extends BlockEntityRenderState> void quasium$light(E be, float partialTick,
            ModelFeatureRenderer.CrumblingOverlay crumbling, boolean outline, Frustum frustum,
            CallbackInfoReturnable<S> cir) {
        S state = cir.getReturnValue();
        if (state == null) return;
        if (QuasiumConfig.roundLight && state.lightCoords != 15728880) {
            int round = EntityTint.roundBrightness(state.blockPos.getX(), state.blockPos.getY(), state.blockPos.getZ());
            if (round > (state.lightCoords & 0xFF)) state.lightCoords = (state.lightCoords & ~0xFF) | round;
        }
        if (state instanceof EntityTint.Holder h) h.quasium$setTint(EntityTint.computeBlock(state.blockPos, state.lightCoords));
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private <S extends BlockEntityRenderState> void quasium$tintBegin(S state, PoseStack pose, SubmitNodeCollector collector,
            CameraRenderState camera, CallbackInfo ci) {
        EntityTint.current = state instanceof EntityTint.Holder h ? h.quasium$tint() : -1;
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private <S extends BlockEntityRenderState> void quasium$tintEnd(S state, PoseStack pose, SubmitNodeCollector collector,
            CameraRenderState camera, CallbackInfo ci) {
        EntityTint.current = -1;
    }
}
