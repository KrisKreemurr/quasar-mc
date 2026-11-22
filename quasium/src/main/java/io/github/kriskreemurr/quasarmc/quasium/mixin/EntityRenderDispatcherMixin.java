package io.github.kriskreemurr.quasarmc.quasium.mixin;

import io.github.kriskreemurr.quasarmc.quasium.entity.EntityLod;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity LOD: reuse a far entity's last render state until its next update is due. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Shadow public Camera camera;
    @Shadow public abstract double distanceToSqr(Entity entity);

    @Unique private long quasium$start;
    @Unique private long quasium$interval;

    @Inject(method = "extractEntity", at = @At("HEAD"), cancellable = true)
    private void quasium$reuse(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        quasium$start = System.nanoTime();
        quasium$interval = 0;
        if (camera == null || camera.entity() == entity) return;
        long interval = EntityLod.interval(entity, distanceToSqr(entity));
        quasium$interval = interval;
        if (interval == 0) return;
        EntityLod.Holder holder = (EntityLod.Holder) entity;
        EntityRenderState cached = holder.quasium$lodState();
        if (cached != null && quasium$start < holder.quasium$lodNext()) {
            EntityLod.countReused();
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void quasium$store(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        long now = System.nanoTime();
        EntityLod.Holder holder = (EntityLod.Holder) entity;
        if (holder.quasium$lodState() == cir.getReturnValue()) return; // reused, handled at HEAD
        long interval = quasium$interval;
        if (interval > 0) {
            boolean first = holder.quasium$lodState() == null;
            holder.quasium$setLodState(cir.getReturnValue());
            holder.quasium$setLodNext(now + (first ? EntityLod.firstDelay(entity, interval) : interval));
        } else {
            holder.quasium$setLodState(null);
        }
        EntityLod.countBuilt(now - quasium$start);
    }
}
