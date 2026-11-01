package com.reiko.trains.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.TrainCar;
import java.util.function.Function;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a train car the way the minecart renderer draws a minecart (lined up with the rails under it, tilted on
 * slopes, shaking when hit), except that a train car has a front: the minecart renderer takes its heading from the
 * rails, which run both ways, so here it's turned to whichever way along them is closer to where the car faces.
 */
public class TrainCarRenderer<T extends AbstractMinecart & TrainCar> extends EntityRenderer<T> {
    /** From the car's position (the top of the rails) to model space's origin: the rails are at y = 5 in the model. */
    private static final float MODEL_OFFSET = 5.0F / 16.0F;

    private final TrainModel model;
    private final ResourceLocation texture;

    public TrainCarRenderer(EntityRendererProvider.Context context, Function<EntityRendererProvider.Context, TrainModel> model,
                            ResourceLocation texture) {
        super(context);
        this.model = model.apply(context);
        this.texture = texture;
        this.shadowRadius = 0.9F;
    }

    @Override
    public void render(T car, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        super.render(car, entityYaw, partialTick, poseStack, buffers, light);
        poseStack.pushPose();
        // The same tiny per-car nudge the minecart renderer uses, so two cars never z-fight.
        long seed = (long) car.getId() * 493286711L;
        seed = seed * seed * 4392167121L + seed * 98761L;
        poseStack.translate((((seed >> 16 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F, (((seed >> 20 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F,
                (((seed >> 24 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F);

        double x = Mth.lerp(partialTick, car.xOld, car.getX());
        double y = Mth.lerp(partialTick, car.yOld, car.getY());
        double z = Mth.lerp(partialTick, car.zOld, car.getZ());
        float yaw = Mth.rotLerp(partialTick, car.yRotO, car.getYRot());
        float pitch = Mth.lerp(partialTick, car.xRotO, car.getXRot());
        Vec3 onRail = car.getPos(x, y, z);
        if (onRail != null) {
            Vec3 ahead = car.getPosOffs(x, y, z, 0.3F);
            Vec3 behind = car.getPosOffs(x, y, z, -0.3F);
            if (ahead == null) {
                ahead = onRail;
            }
            if (behind == null) {
                behind = onRail;
            }
            poseStack.translate(onRail.x - x, (ahead.y + behind.y) / 2.0 - y, onRail.z - z);
            Vec3 along = behind.subtract(ahead);
            if (along.length() != 0.0) {
                along = along.normalize();
                float railYaw = (float) (Math.atan2(along.z, along.x) * Mth.RAD_TO_DEG);
                float railPitch = (float) (Math.atan(along.y) * 73.0);
                if (Math.abs(Mth.wrapDegrees(railYaw - yaw)) > 90.0F) {
                    railYaw += 180.0F;
                    railPitch = -railPitch;
                }
                yaw = railYaw;
                pitch = railPitch;
            }
        }

        poseStack.translate(0.0F, MODEL_OFFSET, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-pitch));
        float hurt = car.getHurtTime() - partialTick;
        float damage = Math.max(0.0F, car.getDamage() - partialTick);
        if (hurt > 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(hurt) * hurt * damage / 10.0F * car.getHurtDir()));
        }
        poseStack.scale(-1.0F, -1.0F, 1.0F);

        model.setup(car.trainState().distance(partialTick), car instanceof FreightWagon wagon ? wagon.getLoad() : 0);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        model.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(T car) {
        return texture;
    }
}
