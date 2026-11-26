package io.github.kriskreemurr.quasarmc.unbound.boomerang.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.kriskreemurr.quasarmc.unbound.boomerang.BoomerangEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/** The boomerang's own item sprite, lying nearly flat and tilted, spinning fast around its centre. */
public class BoomerangRenderer extends EntityRenderer<BoomerangEntity, BoomerangRenderState> {
    /** degrees per tick */
    private static final float SPIN = 50f;
    /** tilt from flat, degrees */
    private static final float TILT = 20f;

    private final ItemModelResolver itemModels;

    public BoomerangRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemModels = ctx.getItemModelResolver();
    }

    @Override
    public BoomerangRenderState createRenderState() {
        return new BoomerangRenderState();
    }

    @Override
    public void extractRenderState(BoomerangEntity e, BoomerangRenderState s, float partialTick) {
        super.extractRenderState(e, s, partialTick);
        itemModels.updateForNonLiving(s.item, e.getItem(), ItemDisplayContext.GROUND, e);
        Vec3 v = e.getDeltaMovement();
        if (v.horizontalDistanceSqr() > 1e-6) s.yaw = (float) Math.toDegrees(Math.atan2(v.x, v.z));
        s.spin = (e.tickCount + partialTick) * SPIN;
    }

    @Override
    public void submit(BoomerangRenderState s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.rotate(Axis.YP.rotationDegrees(s.yaw));
        pose.rotate(Axis.XP.rotationDegrees(90f - TILT));
        pose.rotate(Axis.ZP.rotationDegrees(s.spin));
        pose.scale(1.2f, 1.2f, 1.2f);
        s.item.submit(pose, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
        pose.popPose();
        super.submit(s, pose, collector, camera);
    }
}
