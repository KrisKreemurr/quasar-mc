package io.github.kriskreemurr.quasarmc.unbound.hoe;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;

/**
 * Hoes as crowd control: every hoe can sweep like a sword (same rules: fully
 * charged, on the ground, not sprinting). Sweep damage is based on the weapon's
 * damage, so it stays small on a hoe, which is the point.
 *
 * Knockback moves from swords to hoes. That part is data and one mixin:
 *   data/minecraft/enchantment/knockback.json  -> only hoes can get Knockback
 *   data/minecraft/tags/item/enchantable/sweeping.json -> hoes can get Sweeping Edge
 *   mixin/NoSwordKnockbackMixin -> Knockback already on a sword does nothing
 * Swords keep their own sweep.
 */
public final class HoeCombat {
    /** Vanilla needs a near-full swing to sweep. */
    private static final float FULL_SWING = 0.9F;

    // Attack strength is reset before the sweep check, so remember it from the start of the attack.
    private static final Map<Player, Float> SWING_STRENGTH = Collections.synchronizedMap(new WeakHashMap<>());

    private HoeCombat() {
    }

    public static void register(IEventBus gameEventBus) {
        gameEventBus.addListener(HoeCombat::onAttack);
        gameEventBus.addListener(HoeCombat::onSweep);
    }

    private static void onAttack(AttackEntityEvent event) {
        SWING_STRENGTH.put(event.getEntity(), event.getEntity().getAttackStrengthScale(0.5F));
    }

    private static void onSweep(SweepAttackEvent event) {
        Player player = event.getEntity();
        if (event.isVanillaSweep() || !player.getMainHandItem().is(ItemTags.HOES)) {
            return;
        }
        float strength = SWING_STRENGTH.getOrDefault(player, 0.0F);
        if (strength > FULL_SWING && player.onGround() && !player.isSprinting()) {
            event.setSweeping(true);
        }
    }
}
