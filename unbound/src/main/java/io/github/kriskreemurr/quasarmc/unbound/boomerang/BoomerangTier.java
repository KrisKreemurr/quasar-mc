package io.github.kriskreemurr.quasarmc.unbound.boomerang;

/**
 * Boomerang tiers. Damage per hit = that tier's sword + 1, always below the same tier's axe hit (golden 11 vs golden
 * axe 12). Durability = the tier's tool durability; one point per throw.
 */
public enum BoomerangTier {
    WOODEN("wooden_boomerang", 5f, 59, false, 15),
    STONE("stone_boomerang", 6f, 131, false, 5),
    COPPER("copper_boomerang", 6f, 190, false, 13),
    IRON("iron_boomerang", 7f, 250, false, 14),
    DIAMOND("diamond_boomerang", 8f, 1561, false, 10),
    NETHERITE("netherite_boomerang", 9f, 2031, false, 15),
    /** gold: hits on the way back too (two hits per throw), 32 uses, never repaired */
    GOLDEN("golden_boomerang", 11f, 32, true, 22),
    /**
     * the Tidalrang (user's): fish fins on a bone. The mirror of the others - it only flies underwater (head under) and
     * the water's surface is its wall; holding two makes you swim like a fish ({@link TidalSwim})
     */
    TIDAL("tidalrang", 7f, 200, false, 12, true),
    /**
     * the Sniper-Rang (user's): a spyglass on a copper boomerang. Zooms while winding up, flies straight up to 64 blocks,
     * and hits harder the farther it got: {@link BoomerangEntity#sniperDamage} (4 close, 12 at 56+ blocks; this 12 is
     * the most). Copper durability and enchantability.
     */
    SNIPER("sniper_rang", 12f, 190, false, 13, false);

    public final String id;
    public final float damage;
    public final int durability;
    public final boolean hitsOnReturn;
    /** enchanting-table enchantability (the tier's tool value) */
    public final int enchantability;
    /** only thrown underwater, and stays in the water (the Tidalrang); the others can't be thrown underwater */
    public final boolean underwater;

    public boolean sniper() {
        return this == SNIPER;
    }

    BoomerangTier(String id, float damage, int durability, boolean hitsOnReturn, int enchantability) {
        this(id, damage, durability, hitsOnReturn, enchantability, false);
    }

    BoomerangTier(String id, float damage, int durability, boolean hitsOnReturn, int enchantability, boolean underwater) {
        this.underwater = underwater;
        this.enchantability = enchantability;
        this.id = id;
        this.damage = damage;
        this.durability = durability;
        this.hitsOnReturn = hitsOnReturn;
    }
}
