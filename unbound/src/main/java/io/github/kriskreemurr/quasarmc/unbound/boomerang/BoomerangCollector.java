package io.github.kriskreemurr.quasarmc.unbound.boomerang;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/**
 * Treasure Collector I-III (user's enchantment): the boomerang pulls in every item and XP orb lying near its path, out
 * and back, and brings them home. They land in your inventory when you catch it; a missed catch spills them at your
 * feet with the boomerang. With Multi-Target, bounces left over after the last mob go to loot within the chain range.
 */
public final class BoomerangCollector {
    public static final ResourceKey<Enchantment> COLLECTOR =
        ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "treasure_collector"));
    /** pickup radius around the boomerang by level, blocks */
    private static final double[] RADIUS = {0, 1.5, 3, 5};

    private BoomerangCollector() {}

    static int level(Level level, ItemStack stack) {
        Holder<Enchantment> e = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(COLLECTOR).orElse(null);
        return e == null ? 0 : Math.min(3, EnchantmentHelper.getItemEnchantmentLevel(e, stack));
    }

    static double radius(int level) {
        return RADIUS[Math.max(0, Math.min(3, level))];
    }

    /** loot it may take for {@code owner}: items without a pickup delay and not meant for someone else, XP orbs */
    static boolean collectable(Entity e, Player owner) {
        if (e.isRemoved()) return false;
        if (e instanceof ExperienceOrb) return true;
        if (e instanceof ItemEntity item) {
            if (item.hasPickUpDelay() || item.getItem().isEmpty()) return false;
            return item.getTarget() == null || item.getTarget().equals(owner.getUUID());
        }
        return false;
    }
}
