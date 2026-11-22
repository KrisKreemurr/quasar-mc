package io.github.kriskreemurr.quasarmc.quasium.mixin;

import io.github.kriskreemurr.quasarmc.quasium.entity.EntityLod;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void quasium$frame(CallbackInfo ci) {
        EntityLod.newFrame();
    }
}
