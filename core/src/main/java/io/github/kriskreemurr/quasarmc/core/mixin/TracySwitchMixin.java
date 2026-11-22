package io.github.kriskreemurr.quasarmc.core.mixin;

import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Arrays;

/**
 * Tracy switch: Minecraft 26.3 ships Tracy profiler support behind the --tracy launch argument,
 * which MultiMC can't pass. This adds it when the JVM argument is set:
 *
 *   -Dquasarmc.tracy=true       Tracy on, with frame screenshots in the timeline
 *   -Dquasarmc.tracy=noimages   Tracy on, without screenshots (less overhead)
 *
 * Every Minecraft profiler section (including Distant Horizons' "LOD Opaque", "LOD SSAO", "LOD Fog", ...)
 * then shows up as a zone in the Tracy viewer.
 */
@Mixin(Main.class)
public class TracySwitchMixin {

    @ModifyVariable(method = "main", at = @At("HEAD"), argsOnly = true)
    private static String[] quasarmc$addTracyArgs(String[] args) {
        String mode = System.getProperty("quasarmc.tracy", "").trim().toLowerCase();
        if (mode.isEmpty() || mode.equals("false") || mode.equals("off"))
            return args;
        if (Arrays.asList(args).contains("--tracy"))
            return args;

        boolean noImages = mode.equals("noimages");
        String[] out = Arrays.copyOf(args, args.length + (noImages ? 2 : 1));
        out[args.length] = "--tracy";
        if (noImages) out[args.length + 1] = "--tracyNoImages";
        System.out.println("[Quasar MC Core] Tracy enabled (" + (noImages ? "no images" : "with images") + ")");
        return out;
    }
}
