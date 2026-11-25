package io.github.kriskreemurr.quasarmc.unbound.mixin;

import io.github.kriskreemurr.quasarmc.unbound.gold.GoldenGear;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Golden tools can't be repaired in an anvil: no gold-ingot repair, no combining two golden tools. Renaming and
 * enchanted books still work, but never Mending.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilNoGoldRepairMixin {
    @Inject(method = "createResult", at = @At("TAIL"))
    private void quasarmc$noGoldRepair(CallbackInfo ci) {
        AnvilMenu self = (AnvilMenu) (Object) this;
        ItemStack left = self.getSlot(0).getItem();
        if (!GoldenGear.is(left)) return;
        ItemStack result = self.getSlot(2).getItem();
        if (result.isEmpty()) return;
        ItemStack right = self.getSlot(1).getItem();
        boolean repairOrCombine = !right.isEmpty() && !right.is(Items.ENCHANTED_BOOK);
        if (repairOrCombine || hasMending(result)) {
            self.getSlot(2).set(ItemStack.EMPTY);
        }
    }

    private static boolean hasMending(ItemStack stack) {
        for (Holder<Enchantment> h : stack.getEnchantments().keySet()) {
            if (h.is(Enchantments.MENDING)) return true;
        }
        return false;
    }
}
