package io.github.kriskreemurr.quasarmc.unbound.mixin;

import io.github.kriskreemurr.quasarmc.unbound.gold.GoldenGear;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Two golden tools in the crafting grid don't combine into a repaired one. */
@Mixin(RepairItemRecipe.class)
public abstract class RepairItemRecipeNoGoldMixin {
    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",
            at = @At("HEAD"), cancellable = true)
    private void quasarmc$noGoldRepair(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
        for (int i = 0; i < input.size(); i++) {
            if (GoldenGear.is(input.getItem(i))) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
