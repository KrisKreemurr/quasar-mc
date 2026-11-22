package io.github.kriskreemurr.quasarmc.quasium.mixin;

import io.github.kriskreemurr.quasarmc.quasium.entity.EntityLod;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityLodHolderMixin implements EntityLod.Holder {
    @Unique private EntityRenderState quasium$lodState;
    @Unique private long quasium$lodNext;

    @Override public EntityRenderState quasium$lodState() { return quasium$lodState; }
    @Override public void quasium$setLodState(EntityRenderState state) { quasium$lodState = state; }
    @Override public long quasium$lodNext() { return quasium$lodNext; }
    @Override public void quasium$setLodNext(long nanos) { quasium$lodNext = nanos; }
}
