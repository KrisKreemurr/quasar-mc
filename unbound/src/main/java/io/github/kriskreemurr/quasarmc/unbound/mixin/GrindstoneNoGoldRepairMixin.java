package io.github.kriskreemurr.quasarmc.unbound.mixin;

import io.github.kriskreemurr.quasarmc.unbound.gold.GoldenGear;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Two golden tools can't be merged in a grindstone (that repairs them). Disenchanting one still works. */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneNoGoldRepairMixin {
    @Inject(method = "mergeItems", at = @At("HEAD"), cancellable = true)
    private void quasarmc$noGoldMerge(ItemStack a, ItemStack b, CallbackInfoReturnable<ItemStack> cir) {
        if (GoldenGear.is(a) || GoldenGear.is(b)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
