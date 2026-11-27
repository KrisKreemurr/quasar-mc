package io.github.kriskreemurr.quasarmc.unbound.gold;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Golden armour (user, 2026-11-27), the gold rule for armour: absurd protection that melts. Each piece blocks a share
 * of every hit armour would normally stop (helmet 20%, chestplate 35%, leggings 25%, boots 15%: a full set blocks 95%,
 * and nothing - Protection included - takes a player past 95%). The price: every hit wears each golden piece 3x the
 * vanilla amount for the hit's full (unreduced) damage, at least 6 a hit, so small mobs can't be ignored forever and
 * big hits shred it. No repair, no Mending ({@link GoldenGear}). Players only: piglins in gold stay vanilla.
 */
public final class GoldenArmor {
    private GoldenArmor() {}

    /** the share of damage one golden piece blocks; 0 for anything else */
    static float share(ItemStack s) {
        if (s.is(Items.GOLDEN_HELMET)) return 0.20f;
        if (s.is(Items.GOLDEN_CHESTPLATE)) return 0.35f;
        if (s.is(Items.GOLDEN_LEGGINGS)) return 0.25f;
        if (s.is(Items.GOLDEN_BOOTS)) return 0.15f;
        return 0f;
    }

    public static boolean is(ItemStack s) {
        return share(s) > 0;
    }

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** a player never takes less than this much of a hit while wearing gold */
    private static final float MAX_BLOCK = 0.95f;
    private static final int WEAR_MULTIPLIER = 3, MIN_WEAR = 6;

    public static void register(IEventBus gameBus) {
        // before armour: the golden share comes off, and the gold pays for the whole hit in durability
        gameBus.addListener(LivingIncomingDamageEvent.class, e -> {
            if (!(e.getEntity() instanceof Player p) || p.level().isClientSide()) return;
            if (e.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) return;
            float blocked = 0;
            float amount = e.getAmount();
            int wear = Math.max(MIN_WEAR, WEAR_MULTIPLIER * Math.max(1, (int) (amount / 4f)));
            for (EquipmentSlot slot : ARMOR) {
                ItemStack s = p.getItemBySlot(slot);
                float sh = share(s);
                if (sh <= 0) continue;
                blocked += sh;
                // vanilla still takes its own (now tiny) wear after this; ours is the real cost
                if (!p.getAbilities().instabuild) s.hurtAndBreak(wear, p, slot);
            }
            if (blocked > 0) e.setAmount(amount * (1f - Math.min(MAX_BLOCK, blocked)));
        });
        // after armour and enchantments: never below 5% of the original hit while any gold is worn
        gameBus.addListener(LivingDamageEvent.Pre.class, e -> {
            if (!(e.getEntity() instanceof Player p) || e.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) return;
            boolean gold = false;
            for (EquipmentSlot slot : ARMOR) gold |= is(p.getItemBySlot(slot));
            if (!gold) return;
            float floor = e.getContainer().getOriginalDamage() * (1f - MAX_BLOCK);
            if (e.getNewDamage() < floor) e.setNewDamage(floor);
        });
    }

    /** "Blocks 35% of damage (full set: 95%)" on each piece */
    public static void tooltip(ItemTooltipEvent event) {
        float sh = share(event.getItemStack());
        if (sh <= 0) return;
        event.getToolTip().add(Component.translatable("tooltip.quasarmc_unbound.golden_armor", Math.round(sh * 100))
            .withStyle(ChatFormatting.GOLD));
        event.getToolTip().add(Component.translatable("tooltip.quasarmc_unbound.golden_armor_wear").withStyle(ChatFormatting.GRAY));
    }
}
