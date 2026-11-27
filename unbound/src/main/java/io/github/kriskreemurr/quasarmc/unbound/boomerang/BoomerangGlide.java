package io.github.kriskreemurr.quasarmc.unbound.boomerang;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Glide I-III (user's enchantment): with a Glide boomerang in <b>both</b> hands, press and hold jump again while in
 * the air (the jump that took you off the ground doesn't count) to glide down
 * safely, the boomerangs held overhead like a rotor (Ty's glide). Not slow falling: it only works while you hold jump
 * (let go and you drop, fall damage and all), you keep your forward momentum, and throwing either boomerang ends it.
 * The lower of the two levels counts; each level glides down slower.
 */
public final class BoomerangGlide {
    public static final ResourceKey<Enchantment> GLIDE =
        ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "glide"));
    /** fastest fall while gliding, blocks per tick, by level (normal falling tops out near 3.9) */
    private static final double[] MAX_FALL = {0, 0.30, 0.18, 0.09};

    /**
     * Per player: jump was down last tick, and a glide has been started this fall. Gliding needs a fresh press of jump
     * while airborne (the jump that left the ground doesn't count), then holding it; landing or letting go ends it.
     */
    private record JumpState(boolean wasDown, boolean armed) {}
    private static final java.util.Map<Player, JumpState> STATES = new java.util.WeakHashMap<>();

    private BoomerangGlide() {}

    /** fresh-press logic; returns whether this tick counts as "holding glide" */
    private static boolean armed(Player p, boolean jumpDown) {
        JumpState s = STATES.getOrDefault(p, new JumpState(false, false));
        boolean armed = s.armed();
        if (p.onGround() || !jumpDown) armed = false;
        else if (!s.wasDown()) armed = true; // pressed again while in the air
        STATES.put(p, new JumpState(jumpDown, armed));
        return armed;
    }

    /** the local player's glide state as last decided (arm pose) */
    public static boolean armedNow(Player p) {
        JumpState s = STATES.get(p);
        return s != null && s.armed();
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, e -> tick(e.getEntity()));
    }

    /** the Glide level both hands give (the lower one), 0 unless both hands hold a Glide boomerang */
    public static int level(Player p) {
        ItemStack a = p.getMainHandItem(), b = p.getOffhandItem();
        if (!(a.getItem() instanceof BoomerangItem) || !(b.getItem() instanceof BoomerangItem)) return 0;
        Holder<Enchantment> glide = p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(GLIDE).orElse(null);
        if (glide == null) return 0;
        return Math.min(3, Math.min(EnchantmentHelper.getItemEnchantmentLevel(glide, a),
            EnchantmentHelper.getItemEnchantmentLevel(glide, b)));
    }

    /** gliding right now: airborne, falling, holding jump, both hands Glide boomerangs, nothing else holding you up */
    public static boolean gliding(Player p, boolean jumpHeld) {
        return jumpHeld && !p.onGround() && p.getDeltaMovement().y < 0 && !p.isInWater() && !p.isInLava()
            && !p.getAbilities().flying && !p.isFallFlying() && !p.isPassenger() && level(p) > 0;
    }

    private static void tick(Player p) {
        boolean jump;
        if (p instanceof ServerPlayer sp) jump = sp.getLastClientInput().jump();
        else if (p.level().isClientSide() && p.isLocalPlayer())
            jump = io.github.kriskreemurr.quasarmc.unbound.boomerang.client.BoomerangClient.jumpHeld();
        else return;
        if (!gliding(p, armed(p, jump))) return;
        // the client moves its own player: cap the fall there; both sides forget the height fallen so far
        double max = MAX_FALL[level(p)];
        Vec3 v = p.getDeltaMovement();
        if (v.y < -max) p.setDeltaMovement(v.x, -max, v.z);
        p.resetFallDistance();
    }
}
