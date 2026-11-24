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
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Quasium: turns Video Settings into a Sodium-style screen with pages in a sidebar
 * (General / Quality / Quasium). Vanilla still builds every option; we only choose which
 * sections land on the current page, so vanilla's own logic (fullscreen modes, graphics
 * warnings, restart notice) keeps working.
 */
@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {
    @Unique private static final int PAGE_GENERAL = 0, PAGE_QUALITY = 1, PAGE_QUASIUM = 2;
    @Unique private static final String[] PAGE_KEYS = {
            "quasarmc_quasium.options.page.general",
            "quasarmc_quasium.options.page.quality",
            "quasarmc_quasium.options.page.quasium"};
    /** Page shown; kept while the game runs so reopening lands where you left off. */
    @Unique private static int quasium$page = PAGE_GENERAL;
    /** Vanilla section being built in addOptions: 1 display, 2 quality, 3 preferences. */
    @Unique private int quasium$section;
    @Unique private final List<Button> quasium$tabs = new ArrayList<>();

    @Shadow @Final private LinearLayout header;
    @Shadow private StringWidget restartWarning;

    protected VideoSettingsScreenMixin(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    @Unique
    private boolean quasium$sectionShown() {
        return switch (quasium$section) {
            case 1, 3 -> quasium$page == PAGE_GENERAL;
            case 2 -> quasium$page == PAGE_QUALITY;
            default -> true;
        };
    }

    @Inject(method = "addOptions", at = @At("HEAD"))
    private void quasium$startOptions(CallbackInfo ci) {
        quasium$section = 0;
    }

    @Redirect(method = "addOptions", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/OptionsList;addHeader(Lnet/minecraft/network/chat/Component;)V"))
    private void quasium$header(OptionsList list, Component text) {
        quasium$section++;
        if (quasium$sectionShown()) list.addHeader(text);
    }

    @Redirect(method = "addOptions", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/OptionsList;addBig(Lnet/minecraft/client/OptionInstance;)V"))
    private void quasium$big(OptionsList list, OptionInstance<?> option) {
        if (quasium$sectionShown()) list.addBig(option);
    }

    @Redirect(method = "addOptions", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/OptionsList;addSmall([Lnet/minecraft/client/OptionInstance;)V"))
    private void quasium$small(OptionsList list, OptionInstance<?>[] options) {
        if (quasium$sectionShown()) list.addSmall(options);
    }

    @Override
    protected void init() {
        super.init();
        quasium$tabs.clear();
        for (int i = 0; i < PAGE_KEYS.length; i++) {
            final int page = i;
            Component name = Component.translatable(PAGE_KEYS[i]);
            Button tab = Button.builder(page == quasium$page ? Component.literal("» ").append(name).append(" «") : name,
                    b -> quasium$switchPage(page)).width(100).build();
            quasium$tabs.add(addRenderableWidget(tab));
        }
        quasium$layoutSidebar();
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void quasium$reposition(CallbackInfo ci) {
        quasium$layoutSidebar();
    }

    /** Sidebar on the left, option list in the space to its right. */
    @Unique
    private void quasium$layoutSidebar() {
        int sideWidth = Math.max(70, Math.min(110, this.width - 330));
        int top = this.layout.getHeaderHeight() + 6;
        for (int i = 0; i < quasium$tabs.size(); i++) {
            Button tab = quasium$tabs.get(i);
            tab.setWidth(sideWidth - 10);
            tab.setX(6);
            tab.setY(top + i * 24);
        }
        if (this.list != null && !quasium$tabs.isEmpty()) {
            this.list.updateSizeAndPosition(this.width - sideWidth, this.layout.getContentHeight(), sideWidth, this.layout.getHeaderHeight());
        }
    }

    @Unique
    private void quasium$switchPage(int page) {
        if (page == quasium$page) return;
        if (this.list != null) this.list.applyUnsavedChanges();
        quasium$page = page;
        // same reset vanilla does before rebuilding this screen
        this.layout.removeChildren();
        this.header.removeChildren();
        this.restartWarning = null;
        this.rebuildWidgets();
    }

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void quasium$addOptions(CallbackInfo ci) {
        if (quasium$page != PAGE_QUASIUM) return;
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

        Button entityLod = Button.builder(quasium$entityLodLabel(), button -> {
                    QuasiumConfig.entityLod = !QuasiumConfig.entityLod;
                    QuasiumConfig.save();
                    button.setMessage(quasium$entityLodLabel());
                })
                .tooltip(Tooltip.create(Component.translatable("quasarmc_quasium.options.entity_lod.tooltip")))
                .width(150)
                .build();

        Button smoothLight = Button.builder(quasium$smoothLightLabel(), button -> {
                    QuasiumConfig.smoothEntityLight = !QuasiumConfig.smoothEntityLight;
                    QuasiumConfig.save();
                    button.setMessage(quasium$smoothLightLabel());
                })
                .tooltip(Tooltip.create(Component.translatable("quasarmc_quasium.options.smooth_entity_light.tooltip")))
                .width(310)
                .build();

        this.list.addSmall(faceCulling, entityLod);
        this.list.addBig(smoothLight);
        this.list.addSmall(panorama, panoramaStart);

        this.list.addHeader(Component.translatable("quasarmc_quasium.options.color_header"));
        var saturation = new io.github.kriskreemurr.quasarmc.quasium.client.QuasiumSlider(150, "quasarmc_quasium.options.saturation", 0, 200,
                () -> QuasiumConfig.saturation, v -> { QuasiumConfig.saturation = v; quasium$gradeChanged(); });
        saturation.setTooltip(Tooltip.create(Component.translatable("quasarmc_quasium.options.saturation.tooltip")));
        var contrast = new io.github.kriskreemurr.quasarmc.quasium.client.QuasiumSlider(150, "quasarmc_quasium.options.contrast", 50, 150,
                () -> QuasiumConfig.contrast, v -> { QuasiumConfig.contrast = v; quasium$gradeChanged(); });
        contrast.setTooltip(Tooltip.create(Component.translatable("quasarmc_quasium.options.contrast.tooltip")));
        var whiteness = new io.github.kriskreemurr.quasarmc.quasium.client.QuasiumSlider(150, "quasarmc_quasium.options.whiteness", 0, 100,
                () -> QuasiumConfig.whiteness, v -> { QuasiumConfig.whiteness = v; quasium$gradeChanged(); });
        whiteness.setTooltip(Tooltip.create(Component.translatable("quasarmc_quasium.options.whiteness.tooltip")));
        Button reset = Button.builder(Component.translatable("quasarmc_quasium.options.color_reset"), button -> {
                    QuasiumConfig.saturation = 100;
                    QuasiumConfig.contrast = 100;
                    QuasiumConfig.whiteness = 50;
                    quasium$gradeChanged();
                    this.rebuildWidgets();
                })
                .width(150)
                .build();
        this.list.addSmall(saturation, contrast);
        this.list.addSmall(whiteness, reset);
    }

    @Unique
    private static void quasium$gradeChanged() {
        QuasiumConfig.save();
        io.github.kriskreemurr.quasarmc.quasium.client.ColorGrade.update();
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
    private static Component quasium$entityLodLabel() {
        return CommonComponents.optionNameValue(
                Component.translatable("quasarmc_quasium.options.entity_lod"),
                QuasiumConfig.entityLod ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    @Unique
    private static Component quasium$smoothLightLabel() {
        return CommonComponents.optionNameValue(
                Component.translatable("quasarmc_quasium.options.smooth_entity_light"),
                QuasiumConfig.smoothEntityLight ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    @Unique
    private static Component quasium$faceCullingLabel() {
        return CommonComponents.optionNameValue(
                Component.translatable("quasarmc_quasium.options.face_culling"),
                QuasiumConfig.faceCulling ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }
}
