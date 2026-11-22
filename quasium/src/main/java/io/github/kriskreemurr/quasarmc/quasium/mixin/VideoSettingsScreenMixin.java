package io.github.kriskreemurr.quasarmc.quasium.mixin;

import io.github.kriskreemurr.quasarmc.quasium.QuasiumConfig;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a "Quasium" section to the bottom of Video Settings. */
@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {

    protected VideoSettingsScreenMixin(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void quasium$addOptions(CallbackInfo ci) {
        this.list.addHeader(Component.translatable("quasarmc_quasium.options.header"));

        Button faceCulling = Button.builder(quasium$faceCullingLabel(), button -> {
                    QuasiumConfig.faceCulling = !QuasiumConfig.faceCulling;
                    QuasiumConfig.save();
                    button.setMessage(quasium$faceCullingLabel());
                })
                .tooltip(Tooltip.create(Component.translatable("quasarmc_quasium.options.face_culling.tooltip")))
                .width(150)
                .build();

        Button panorama = Button.builder(quasium$panoramaLabel(), button -> {
                    QuasiumConfig.lodPanorama = !QuasiumConfig.lodPanorama;
                    QuasiumConfig.save();
                    button.setMessage(quasium$panoramaLabel());
                })
                .tooltip(Tooltip.create(Component.translatable("quasarmc_quasium.options.lod_panorama.tooltip")))
                .width(150)
                .build();
        Button panoramaStart = Button.builder(quasium$panoramaStartLabel(), button -> {
                    int[] starts = QuasiumConfig.PANORAMA_STARTS;
                    int next = starts[0];
                    for (int i = 0; i < starts.length; i++) {
                        if (starts[i] == QuasiumConfig.panoramaStart) {
                            next = starts[(i + 1) % starts.length];
                            break;
                        }
                    }
                    QuasiumConfig.panoramaStart = next;
                    QuasiumConfig.save();
                    button.setMessage(quasium$panoramaStartLabel());
                })
                .tooltip(Tooltip.create(Component.translatable("quasarmc_quasium.options.panorama_start.tooltip")))
                .width(150)
                .build();

        this.list.addSmall(faceCulling, panorama);
        this.list.addBig(panoramaStart);
    }

    @Unique
    private static Component quasium$panoramaLabel() {
        return CommonComponents.optionNameValue(
                Component.translatable("quasarmc_quasium.options.lod_panorama"),
                QuasiumConfig.lodPanorama ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    @Unique
    private static Component quasium$panoramaStartLabel() {
        return CommonComponents.optionNameValue(
                Component.translatable("quasarmc_quasium.options.panorama_start"),
                Component.translatable("quasarmc_quasium.options.blocks", QuasiumConfig.panoramaStart));
    }

    @Unique
    private static Component quasium$faceCullingLabel() {
        return CommonComponents.optionNameValue(
                Component.translatable("quasarmc_quasium.options.face_culling"),
                QuasiumConfig.faceCulling ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }
}
