package io.github.kriskreemurr.quasarmc.unbound.gold;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Golden tools and weapons are strong but disposable: they cannot be repaired and Mending does nothing on them.
 * Gold stays renewable (farms are fine); the balance is that every golden tool gets used up.
 *
 * Blocked paths (mixins): anvil repair with gold or by combining two golden tools ({@code AnvilNoGoldRepairMixin},
 * enchanted books still work, except Mending), crafting-grid repair ({@code RepairItemRecipeNoGoldMixin}),
 * grindstone combining ({@code GrindstoneNoGoldRepairMixin}), Mending's XP repair ({@code NoGoldMendingMixin}).
 * Golden armor is not affected.
 */
public final class GoldenGear {
    private GoldenGear() {
    }

    public static boolean is(ItemStack stack) {
        return stack.is(Items.GOLDEN_SWORD) || stack.is(Items.GOLDEN_AXE) || stack.is(Items.GOLDEN_PICKAXE)
                || stack.is(Items.GOLDEN_SHOVEL) || stack.is(Items.GOLDEN_HOE) || stack.is(Items.GOLDEN_SPEAR)
                || stack.is(FlintAndGold.ITEM.get());
    }
}
