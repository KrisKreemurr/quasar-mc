package io.github.kriskreemurr.quasarmc.quasium.mixin;

import io.github.kriskreemurr.quasarmc.quasium.light.EntityTint;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Colored light on entities: work out the tint when the state is built, apply it while the entity submits. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherTintMixin {
    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void quasium$tint(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        EntityRenderState state = cir.getReturnValue();
        if (state instanceof EntityTint.Holder h) h.quasium$setTint(EntityTint.compute(entity, state, partialTick));
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private <S extends EntityRenderState> void quasium$tintBegin(S state, CameraRenderState camera, double x, double y,
            double z, PoseStack pose, SubmitNodeCollector collector, CallbackInfo ci) {
        EntityTint.current = state instanceof EntityTint.Holder h ? h.quasium$tint() : -1;
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private <S extends EntityRenderState> void quasium$tintEnd(S state, CameraRenderState camera, double x, double y,
            double z, PoseStack pose, SubmitNodeCollector collector, CallbackInfo ci) {
        EntityTint.current = -1;
    }
}
