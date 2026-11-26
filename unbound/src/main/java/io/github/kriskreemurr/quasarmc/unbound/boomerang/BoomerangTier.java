package io.github.kriskreemurr.quasarmc.unbound.boomerang;

/**
 * Boomerang tiers. Damage per hit = that tier's sword + 1, always below the same tier's axe hit (golden 11 vs golden
 * axe 12). Durability = the tier's tool durability; one point per throw.
 */
public enum BoomerangTier {
    WOODEN("wooden_boomerang", 5f, 59, false),
    STONE("stone_boomerang", 6f, 131, false),
    COPPER("copper_boomerang", 6f, 190, false),
    IRON("iron_boomerang", 7f, 250, false),
    DIAMOND("diamond_boomerang", 8f, 1561, false),
    NETHERITE("netherite_boomerang", 9f, 2031, false),
    /** gold: hits on the way back too (two hits per throw), 32 uses, never repaired */
    GOLDEN("golden_boomerang", 11f, 32, true);

    public final String id;
    public final float damage;
    public final int durability;
    public final boolean hitsOnReturn;

    BoomerangTier(String id, float damage, int durability, boolean hitsOnReturn) {
        this.id = id;
        this.damage = damage;
        this.durability = durability;
        this.hitsOnReturn = hitsOnReturn;
    }
}
