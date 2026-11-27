package io.github.kriskreemurr.quasarmc.unbound.boomerang.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * Holding a boomerang: vanilla's "item in hand" arm, tilted slightly outward (about 7 degrees). Added to
 * {@code HumanoidModel.ArmPose} through NeoForge's enum extension (META-INF/enumextensions.json). Client only.
 */
public final class BoomerangArmPose {
    /** sideways tilt, radians */
    private static final float TILT = (float) Math.toRadians(7);

    public static final EnumProxy<HumanoidModel.ArmPose> HOLD = new EnumProxy<>(HumanoidModel.ArmPose.class,
        false, false, (IArmPoseTransformer) (model, state, arm) -> {
            if (arm == HumanoidArm.RIGHT) {
                model.rightArm.xRot = model.rightArm.xRot * 0.5f - (float) Math.PI / 10f;
                model.rightArm.zRot += TILT;
            }
            else {
                model.leftArm.xRot = model.leftArm.xRot * 0.5f - (float) Math.PI / 10f;
                model.leftArm.zRot -= TILT;
            }
        });

    private BoomerangArmPose() {}
}
