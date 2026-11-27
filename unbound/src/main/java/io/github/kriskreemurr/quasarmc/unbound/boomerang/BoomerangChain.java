package io.github.kriskreemurr.quasarmc.unbound.boomerang;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/**
 * Multi-Target I-III: after a hit the boomerang ricochets to the nearest valid target within {@link #RANGE} blocks
 * (in sight), up to 1/2/3 extra targets, each bounce doing {@link #FALLOFF} of the previous hit. It only chains within
 * the first target's group, so a hunting throw doesn't turn on your dog and a fight doesn't hit the villager behind.
 */
public final class BoomerangChain {
    public static final ResourceKey<Enchantment> MULTI_TARGET =
        ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "multi_target"));
    /** how far it looks for the next target, blocks */
    public static final double RANGE = 16;
    /** damage of each bounce relative to the hit before it */
    public static final float FALLOFF = 0.8f;

    /** who chains with whom */
    public enum Group { HOSTILE, PLAYERS, PASSIVE }

    private BoomerangChain() {}

    static int level(Level level, ItemStack stack) {
        Holder<Enchantment> e = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(MULTI_TARGET).orElse(null);
        return e == null ? 0 : Math.min(3, EnchantmentHelper.getItemEnchantmentLevel(e, stack));
    }

    /**
     * The chain group of {@code e} for a throw by {@code owner}, or null when it may never be chained to (it can still
     * be hit directly): villagers, wandering traders, iron golems, you, your teammates, and your own or your
     * teammates' pets.
     */
    static Group classify(Entity e, Player owner) {
        if (!(e instanceof LivingEntity) || !e.isAlive()) return null;
        if (e instanceof AbstractVillager || e instanceof IronGolem) return null;
        if (e instanceof Player p) return p == owner || owner.isAlliedTo(p) ? null : Group.PLAYERS;
        if (e instanceof OwnableEntity o && o.getOwner() != null) {
            LivingEntity petOwner = o.getOwner();
            if (petOwner == owner || owner.isAlliedTo(petOwner)) return null;
            return petOwner instanceof Player ? Group.PLAYERS : null;
        }
        if (e instanceof Enemy) return Group.HOSTILE;
        // neutral mobs that are after someone count as hostile
        if (e instanceof Mob m && m.getTarget() != null) return Group.HOSTILE;
        if (e instanceof Animal || e instanceof WaterAnimal || e instanceof AmbientCreature) return Group.PASSIVE;
        return null;
    }
}
