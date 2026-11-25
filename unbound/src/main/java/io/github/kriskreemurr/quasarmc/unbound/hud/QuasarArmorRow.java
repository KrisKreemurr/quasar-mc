package io.github.kriskreemurr.quasarmc.unbound.hud;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Quasar's armor row: the user's shields replace vanilla's, and Protection shows as bulky
 * lapis shields. Protection points = sum of Protection levels on worn armor, capped at 20
 * (vanilla's cap); each icon covers 2 points like armor does. Enchanted shields only go on
 * icons that have armor, so the row never claims armor you don't have.
 *
 * Sprites are 11x11 (normal shields have a 1 px clear border, protect shields fill it),
 * drawn 1 px up-left of vanilla's 9x9 icon position.
 */
public final class QuasarArmorRow {
    private static Identifier sprite(String n) {
        return Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "hud/armor/" + n);
    }
    private static final Identifier FULL = sprite("armor_full"), HALF = sprite("armor_half"), EMPTY = sprite("armor_empty");
    private static final Identifier PROT_FULL = sprite("protect_full"), PROT_HALF = sprite("protect_half");
    private static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    /** Extra lift for the armor row above the hearts (user tuned). */
    static final int RAISE = 1;

    private QuasarArmorRow() {}

    /**
     * Draws one armor icon in place of vanilla's.
     * @param vanillaSprite vanilla's armor_full / armor_half / armor_empty id
     * @param vanillaX vanilla's x (before Quasar's respacing), used to find the icon's index
     */
    public static void draw(GuiGraphicsExtractor g, Identifier vanillaSprite, int vanillaX, int y) {
        String path = vanillaSprite.getPath();
        int x = QuasarHotbar.respaceLeft(vanillaX) - 1;
        int iy = y - 1 - RAISE;
        int index = QuasarHotbar.leftIconIndex(vanillaX);
        int prot = protectionPoints();
        boolean protFull = prot >= index * 2 + 2;
        boolean protHalf = !protFull && prot == index * 2 + 1;

        if (path.endsWith("armor_full")) {
            if (protFull) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, PROT_FULL, x, iy, 11, 11);
            } else {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, FULL, x, iy, 11, 11);
                if (protHalf) g.blitSprite(RenderPipelines.GUI_TEXTURED, PROT_HALF, x, iy, 11, 11);
            }
        } else if (path.endsWith("armor_half")) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, HALF, x, iy, 11, 11);
            if (protFull || protHalf) g.blitSprite(RenderPipelines.GUI_TEXTURED, PROT_HALF, x, iy, 11, 11);
        } else {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY, x, iy, 11, 11);
        }
    }

    /** Sum of Protection levels on worn armor, capped at 20. */
    static int protectionPoints() {
        Player p = Minecraft.getInstance().player;
        if (p == null) return 0;
        Holder<Enchantment> protection = p.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
        int sum = 0;
        for (EquipmentSlot slot : ARMOR) sum += EnchantmentHelper.getItemEnchantmentLevel(protection, p.getItemBySlot(slot));
        return Math.min(20, sum);
    }
}
