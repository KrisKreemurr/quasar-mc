package io.github.kriskreemurr.quasarmc.quasium.mixin;

import io.github.kriskreemurr.quasarmc.quasium.light.EntityTint;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Applies the current entity's light-color tint to every model it submits (body, layers, armor). */
@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionTintMixin {
    @ModifyVariable(method = "submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int quasium$tint(int tint) {
        int t = EntityTint.current;
        return t == -1 ? tint : EntityTint.multiply(tint, t);
    }
}
