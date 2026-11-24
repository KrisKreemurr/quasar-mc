package io.github.kriskreemurr.quasarmc.quasium.mixin;

import io.github.kriskreemurr.quasarmc.quasium.light.SmoothEntityLight;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityLightHolderMixin implements SmoothEntityLight.Holder {
    @Unique private float quasium$lightBlock;
    @Unique private float quasium$lightSky;
    @Unique private long quasium$lightTime;

    @Override public float quasium$lightBlock() { return quasium$lightBlock; }
    @Override public float quasium$lightSky() { return quasium$lightSky; }
    @Override public long quasium$lightTime() { return quasium$lightTime; }

    @Override
    public void quasium$setLight(float block, float sky, long time) {
        quasium$lightBlock = block;
        quasium$lightSky = sky;
        quasium$lightTime = time;
    }
}
