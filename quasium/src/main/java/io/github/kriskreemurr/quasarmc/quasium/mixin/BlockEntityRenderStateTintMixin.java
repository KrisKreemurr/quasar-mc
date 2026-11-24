package io.github.kriskreemurr.quasarmc.quasium.mixin;

import io.github.kriskreemurr.quasarmc.quasium.light.EntityTint;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockEntityRenderState.class)
public abstract class BlockEntityRenderStateTintMixin implements EntityTint.Holder {
    @Unique private int quasium$tint = -1;

    @Override public int quasium$tint() { return quasium$tint; }
    @Override public void quasium$setTint(int tint) { quasium$tint = tint; }
}
