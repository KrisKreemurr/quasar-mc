package io.github.kriskreemurr.quasarmc.unbound.boomerang;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * A pair of Tidalrangs (user's perk): with one in <b>each</b> hand and in water, you swim like a fish - Dolphin's Grace
 * speed (the real effect, hidden and topped up each tick, so a dolphin's own buff doesn't stack with it) and Depth
 * Strider III on the sea floor. One Tidalrang does nothing; throw one and the perk is off until it's back in hand.
 */
public final class TidalSwim {
    private static final Identifier FLOOR = Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "tidal_pair");

    private TidalSwim() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, e -> tick(e.getEntity()));
    }

    static boolean pair(Player p) {
        return tidal(p.getMainHandItem()) && tidal(p.getOffhandItem());
    }

    private static boolean tidal(ItemStack s) {
        return s.getItem() instanceof BoomerangItem b && b.tier.underwater;
    }

    private static void tick(Player p) {
        boolean on = p.isInWater() && pair(p);
        AttributeInstance floor = p.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
        if (floor != null) {
            // +1 tops the attribute out (it caps at 1): Depth Strider III, and no stacking with real Depth Strider
            if (on) floor.addOrUpdateTransientModifier(new AttributeModifier(FLOOR, 1.0, AttributeModifier.Operation.ADD_VALUE));
            else if (floor.hasModifier(FLOOR)) floor.removeModifier(FLOOR);
        }
        if (on && !p.level().isClientSide()) {
            // short and hidden (no icon, no particles), refreshed every tick: it ends within a moment of letting go
            p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 5, 0, true, false, false));
        }
    }
}
