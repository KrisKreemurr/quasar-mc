package io.github.kriskreemurr.quasarmc.core.light;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Which entities give off light while they move (held torches, dropped glowstone, burning mobs...).
 * Shared by the client (Quasium draws the light) and the server (Quasar MC Lighting stops mob spawns),
 * so both always agree on what glows and how brightly.
 */
public final class DynamicLightSources {
    private DynamicLightSources() {}

    /**
     * Lights that are a flame (torches, lanterns, candles, campfires, jack o'lanterns): they go out underwater.
     * Everything else (glowstone, sea lanterns, froglights...) keeps glowing.
     */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> FIRE_LIGHT = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            net.minecraft.resources.Identifier.fromNamespaceAndPath("quasarmc_core", "fire_light"));

    /** Light level 0..15 an entity gives off right now. */
    public static int luminance(Entity entity) {
        if (entity.isRemoved() || entity.isSpectator()) return 0;
        int lum = entity.isOnFire() ? 15 : 0;
        if (lum >= 15) return 15;
        if (entity instanceof LivingEntity living) {
            boolean wet = submerged(entity);
            lum = Math.max(lum, luminance(living.getMainHandItem(), wet));
            lum = Math.max(lum, luminance(living.getOffhandItem(), wet));
        } else if (entity instanceof ItemEntity item) {
            lum = Math.max(lum, luminance(item.getItem(), submerged(entity)));
        }
        return lum;
    }

    /** the light's spot is underwater (a held torch at your eyes, a dropped one where it lies) */
    private static boolean submerged(Entity entity) {
        return entity.level().getFluidState(lightPos(entity)).is(net.minecraft.tags.FluidTags.WATER);
    }

    /** {@link #luminance(ItemStack)}, but a flame gives nothing underwater */
    public static int luminance(ItemStack stack, boolean underwater) {
        if (underwater && stack.is(FIRE_LIGHT)) return 0;
        return luminance(stack);
    }

    /** Light level of an item: the light of the block it places (torch 14, lantern 15, ...), lava bucket 15. */
    public static int luminance(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.is(Items.LAVA_BUCKET)) return 15;
        if (stack.getItem() instanceof BlockItem blockItem) {
            return Math.min(15, Math.max(0, blockItem.getBlock().defaultBlockState().getLightEmission()));
        }
        return 0;
    }

    /** Where the light sits: the block at the entity's eyes (items: where they lie). */
    public static BlockPos lightPos(Entity entity) {
        return BlockPos.containing(entity instanceof ItemEntity ? entity.position().add(0, 0.25, 0) : entity.getEyePosition());
    }
}
