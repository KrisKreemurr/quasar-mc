package io.github.kriskreemurr.quasarmc.unbound.gold;

import java.util.ArrayList;
import java.util.List;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.KineticWeapon;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * Golden weapons: "nasty but short-lived".
 * They hit harder than netherite, but keep gold's vanilla durability (32),
 * so they are burst weapons you burn through, not a main weapon.
 *
 *   Golden sword: 4 -> 10 damage   (netherite 8)
 *   Golden axe:   7 -> 12 damage   (netherite 10)
 *   Golden spear: jab 1 -> 6       (netherite 5), charge multiplier 0.7x -> 1.4x (netherite 1.2x)
 *
 * Attack speed, durability and enchantability stay vanilla; golden gear can't be repaired or mended (GoldenGear).
 * This changes the item's default components, so it applies to every golden
 * weapon (also ones already in a world, and ones mobs carry) and the tooltip
 * shows the new numbers.
 */
public final class GoldenWeapons {
    // Total damage shown in the tooltip. A player's own base damage is 1,
    // and vanilla stores the rest as the item's modifier, so modifier = total - 1.
    public static final double SWORD_DAMAGE = 10.0;
    public static final double AXE_DAMAGE = 12.0;
    public static final double SPEAR_JAB_DAMAGE = 6.0;
    public static final float SPEAR_CHARGE_MULTIPLIER = 1.4F;

    private static final double PLAYER_BASE_DAMAGE = 1.0;

    private GoldenWeapons() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(GoldenWeapons::modifyComponents);
    }

    private static void modifyComponents(ModifyDefaultComponentsEvent event) {
        event.modify(Items.GOLDEN_SWORD, (builder, registries, item) -> setDamage(builder, item, SWORD_DAMAGE));
        event.modify(Items.GOLDEN_AXE, (builder, registries, item) -> setDamage(builder, item, AXE_DAMAGE));
        event.modify(Items.GOLDEN_SPEAR, (builder, registries, item) -> {
            setDamage(builder, item, SPEAR_JAB_DAMAGE);
            setChargeMultiplier(builder, item, SPEAR_CHARGE_MULTIPLIER);
        });
    }

    /** Replaces the item's base attack damage modifier; every other modifier (attack speed...) is kept. */
    private static void setDamage(DataComponentMap.Builder builder, Item item, double totalDamage) {
        ItemAttributeModifiers old = builder.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (old == null) {
            QuasarUnbound.LOGGER.warn("[Unbound] {} has no attribute modifiers, damage not changed", item);
            return;
        }
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>();
        boolean replaced = false;
        for (ItemAttributeModifiers.Entry entry : old.modifiers()) {
            if (entry.matches(Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID)) {
                AttributeModifier modifier = new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                        totalDamage - PLAYER_BASE_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
                entries.add(new ItemAttributeModifiers.Entry(entry.attribute(), modifier, entry.slot(), entry.display()));
                replaced = true;
            } else {
                entries.add(entry);
            }
        }
        if (!replaced) {
            QuasarUnbound.LOGGER.warn("[Unbound] {} has no base attack damage modifier, damage not changed", item);
            return;
        }
        builder.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(List.copyOf(entries)));
    }

    /** Changes only the charge attack's damage multiplier; timings, conditions and sounds stay vanilla. */
    private static void setChargeMultiplier(DataComponentMap.Builder builder, Item item, float multiplier) {
        KineticWeapon old = builder.get(DataComponents.KINETIC_WEAPON);
        if (old == null) {
            QuasarUnbound.LOGGER.warn("[Unbound] {} has no kinetic weapon component, charge not changed", item);
            return;
        }
        builder.set(DataComponents.KINETIC_WEAPON, new KineticWeapon(
                old.contactCooldownTicks(),
                old.delayTicks(),
                old.dismountConditions(),
                old.knockbackConditions(),
                old.damageConditions(),
                old.forwardMovement(),
                multiplier,
                old.sound(),
                old.hitSound()));
    }
}
