package io.github.kriskreemurr.quasarmc.unbound.boomerang.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * Holding a boomerang: the arm hangs as usual and opens about 7 degrees out to the side (no forward raise). Added to
 * {@code HumanoidModel.ArmPose} through NeoForge's enum extension (META-INF/enumextensions.json). Client only.
 */
public final class BoomerangArmPose {
    /** sideways tilt, radians */
    private static final float TILT = (float) Math.toRadians(7);

    public static final EnumProxy<HumanoidModel.ArmPose> HOLD = new EnumProxy<>(HumanoidModel.ArmPose.class,
        false, false, (IArmPoseTransformer) (model, state, arm) -> {
            // only out to the side (right arm +z, left arm -z, the same way vanilla's arm bob opens the arms);
            // no forward raise
            if (arm == HumanoidArm.RIGHT) model.rightArm.zRot += TILT;
            else model.leftArm.zRot -= TILT;
        });

    /** gliding: both arms straight up, boomerangs overhead like a rotor */
    public static final EnumProxy<HumanoidModel.ArmPose> GLIDE = new EnumProxy<>(HumanoidModel.ArmPose.class,
        false, false, (IArmPoseTransformer) (model, state, arm) -> {
            if (arm == HumanoidArm.RIGHT) {
                model.rightArm.xRot = (float) -Math.PI + 0.15f;
                model.rightArm.zRot = -0.15f;
            }
            else {
                model.leftArm.xRot = (float) -Math.PI + 0.15f;
                model.leftArm.zRot = 0.15f;
            }
        });

    private BoomerangArmPose() {}
}
