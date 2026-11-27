package io.github.kriskreemurr.quasarmc.unbound.boomerang;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * A pair of Tidalrangs (user's perk): with one in <b>each</b> hand and in water, you swim like a fish: Depth Strider II
 * (real Depth Strider III still beats it). Only that: Dolphin's Grace on top multiplied into ~30 blocks/s (user,
 * 2026-11-27); a real dolphin's buff still adds on top, like it would with Depth Strider boots. One Tidalrang does nothing; throw one and the perk is off until it's back in hand.
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
            // Depth Strider II (each level is +1/3); real Depth Strider III stays the best on the sea floor (the attribute caps at 1)
            if (on) floor.addOrUpdateTransientModifier(new AttributeModifier(FLOOR, 2.0 / 3.0, AttributeModifier.Operation.ADD_VALUE));
            else if (floor.hasModifier(FLOOR)) floor.removeModifier(FLOOR);
        }
    }
}
