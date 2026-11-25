package io.github.kriskreemurr.quasarmc.unbound.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import io.github.kriskreemurr.quasarmc.unbound.hud.QuasarArmorRow;
import io.github.kriskreemurr.quasarmc.unbound.hud.QuasarHotbar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.Hud;

/**
 * Respaces vanilla's stat icons for Quasar's wider hotbar: hearts and armor start at the
 * hotbar's left edge, food, air and mount hearts end at its right edge, and icons sit
 * {@link QuasarHotbar#ICON_PITCH} px apart (vanilla packs 9-px icons 8 apart, overlapping by 1).
 * Only the x of each icon changes, so every vanilla heart/food effect keeps working.
 */
@Mixin(Hud.class)
public abstract class HudIconSpacingMixin {
    private static final String BLIT =
            "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V";

    @ModifyArg(method = "extractHeart", at = @At(value = "INVOKE", target = BLIT), index = 2)
    private int quasar$heartX(int x) {
        return QuasarHotbar.respaceLeft(x);
    }

    /** Armor icons: Quasar's shields plus Protection glint, at the respaced position. */
    @WrapOperation(method = "extractArmor", at = @At(value = "INVOKE", target = BLIT))
    private static void quasar$armorIcon(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite,
            int x, int y, int w, int h, Operation<Void> original) {
        QuasarArmorRow.draw(g, sprite, x, y);
    }

    @ModifyArg(method = { "extractFood", "extractAirBubbles", "extractVehicleHealth" },
            at = @At(value = "INVOKE", target = BLIT), index = 2)
    private int quasar$rightX(int x) {
        return QuasarHotbar.respaceRight(x);
    }
}
