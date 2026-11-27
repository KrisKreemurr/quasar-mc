package io.github.kriskreemurr.quasarmc.unbound.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A lantern is a flame in a box: waterlogged, it goes out (vanilla keeps it glowing). Torches already can't be placed
 * in water, and candles and campfires already go out; held flames are handled by DynamicLightSources (core).
 * Only checked when the state gives off light, so it costs nothing for every other block.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class WaterloggedLanternMixin {
    @ModifyReturnValue(method = "getLightEmission()I", at = @At("RETURN"))
    private int quasarmc$lanternOutInWater(int light) {
        if (light <= 0) return light;
        BlockBehaviour.BlockStateBase self = (BlockBehaviour.BlockStateBase) (Object) this;
        if (self.getBlock() instanceof LanternBlock && self.hasProperty(BlockStateProperties.WATERLOGGED)
                && self.getValue(BlockStateProperties.WATERLOGGED)) return 0;
        return light;
    }
}
