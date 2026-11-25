package io.github.kriskreemurr.quasarmc.unbound.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Knockback belongs to hoes now. Swords that already have Knockback (old items,
 * loot) keep the enchantment on the item, but it does nothing.
 */
@Mixin(EnchantmentHelper.class)
public abstract class NoSwordKnockbackMixin {
    @Inject(method = "modifyKnockback", at = @At("HEAD"), cancellable = true)
    private static void quasarmc$noSwordKnockback(ServerLevel level, ItemStack weapon, Entity target,
            DamageSource source, float knockback, CallbackInfoReturnable<Float> cir) {
        if (weapon.is(ItemTags.SWORDS)) {
            cir.setReturnValue(knockback);
        }
    }
}
