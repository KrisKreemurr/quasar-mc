package io.github.kriskreemurr.quasarmc.unbound.mixin;

import io.github.kriskreemurr.quasarmc.unbound.gold.GoldenGear;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mending repairs nothing on golden tools (covers Mending from loot or older items). The XP is not lost: an orb that
 * repairs nothing goes to the player as normal experience.
 */
@Mixin(EnchantmentHelper.class)
public abstract class NoGoldMendingMixin {
    @Inject(method = "modifyDurabilityToRepairFromXp", at = @At("HEAD"), cancellable = true)
    private static void quasarmc$noGoldMending(ServerLevel level, ItemStack stack, int amount,
            CallbackInfoReturnable<Integer> cir) {
        if (GoldenGear.is(stack)) {
            cir.setReturnValue(0);
        }
    }
}
