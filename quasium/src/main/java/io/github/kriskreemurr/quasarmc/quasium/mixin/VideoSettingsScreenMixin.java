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
        this.list.addBig(faceCulling);
    }

    @Unique
    private static Component quasium$faceCullingLabel() {
        return CommonComponents.optionNameValue(
                Component.translatable("quasarmc_quasium.options.face_culling"),
                QuasiumConfig.faceCulling ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }
}
