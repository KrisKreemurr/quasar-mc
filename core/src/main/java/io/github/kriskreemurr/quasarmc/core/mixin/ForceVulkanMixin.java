package io.github.kriskreemurr.quasarmc.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Quasar MC runs on Mojang's Vulkan backend. 26.3 drops back to OpenGL after a startup crash,
 * so we pin the backend the same way the --graphicsBackend launch argument does.
 * Escape hatch: launch with JVM arg -Dquasarmc.graphicsBackend=opengl (or default).
 */
@Mixin(Minecraft.class)
public class ForceVulkanMixin {
    @ModifyExpressionValue(method = "<init>", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/main/GameConfig$GameData;forcedGraphicsApi:Lnet/minecraft/client/PreferredGraphicsApi;"))
    private PreferredGraphicsApi quasarmc$forceVulkan(PreferredGraphicsApi original) {
        if (original != null) return original; // an explicit --graphicsBackend still wins
        String override = System.getProperty("quasarmc.graphicsBackend", "vulkan");
        return switch (override.toLowerCase()) {
            case "opengl" -> PreferredGraphicsApi.OPENGL;
            case "default" -> null;
            default -> PreferredGraphicsApi.VULKAN;
        };
    }
}
