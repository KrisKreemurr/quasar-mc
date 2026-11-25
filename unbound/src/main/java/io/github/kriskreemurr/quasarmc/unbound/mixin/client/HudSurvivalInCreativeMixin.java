package io.github.kriskreemurr.quasarmc.unbound.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.Hud;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;

/**
 * Keeps the survival HUD (hearts, hunger, armor, air, XP bar and level) in creative, so the
 * survival systems can be tested there. Spectator stays vanilla. A creative-mode marker on the
 * HUD comes later.
 */
@Mixin(Hud.class)
public abstract class HudSurvivalInCreativeMixin {
    private static boolean quasar$showSurvival(MultiPlayerGameMode mode) {
        return !mode.isSpectator();
    }

    @WrapOperation(method = { "lambda$registerVanillaLayers$1", "extractSelectedItemName" },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;canHurtPlayer()Z"))
    private boolean quasar$canHurt(MultiPlayerGameMode mode, Operation<Boolean> original) {
        return quasar$showSurvival(mode);
    }

    @WrapOperation(method = { "extractExperienceLevel", "nextContextualInfoState" },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;hasExperience()Z"))
    private boolean quasar$hasXp(MultiPlayerGameMode mode, Operation<Boolean> original) {
        return quasar$showSurvival(mode);
    }
}
