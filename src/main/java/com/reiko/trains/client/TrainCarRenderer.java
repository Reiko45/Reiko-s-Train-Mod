package com.reiko.trains.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reiko.trains.ReikoTrains;
import com.reiko.trains.train.TrainCarEntity;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws a train car: its body where the car is (halfway between its bogies, turned and tilted to match), and, for
 * cars that have them, the two bogies under it, each turned to follow the track under it.
 */
public class TrainCarRenderer<T extends TrainCarEntity> extends EntityRenderer<T> {
    public static final ResourceLocation BOGIE_TEXTURE = ReikoTrains.id("textures/entity/bogie.png");

    private final TrainModel body;
    @Nullable
    private final TrainModel bogie;
    private final ResourceLocation texture;
    private final ResourceLocation bogieTexture;

    public TrainCarRenderer(EntityRendererProvider.Context context, Function<EntityRendererProvider.Context, TrainModel> body,
                            @Nullable Function<EntityRendererProvider.Context, TrainModel> bogie, ResourceLocation texture) {
        this(context, body, bogie, texture, BOGIE_TEXTURE);
    }

    public TrainCarRenderer(EntityRendererProvider.Context context, Function<EntityRendererProvider.Context, TrainModel> body,
                            @Nullable Function<EntityRendererProvider.Context, TrainModel> bogie, ResourceLocation texture,
                            ResourceLocation bogieTexture) {
        super(context);
        this.body = body.apply(context);
        this.bogie = bogie == null ? null : bogie.apply(context);
        this.texture = texture;
        this.bogieTexture = bogieTexture;
        this.shadowRadius = 1.6F;
    }

    @Override
    public void render(T car, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        super.render(car, entityYaw, partialTick, poseStack, buffers, light);
        float yaw = Mth.rotLerp(partialTick, car.yRotO, car.getYRot());
        float pitch = Mth.lerp(partialTick, car.xRotO, car.getXRot());
        poseStack.pushPose();
        // Local +x is the car's front, +y up, +z its right; the models are built with their front at -x and -y up.
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F - yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-pitch));
        float hurt = car.getHurtTime() - partialTick;
        float damage = Math.max(0.0F, car.getDamage() - partialTick);
        if (hurt > 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(hurt) * hurt * damage / 40.0F * car.getHurtDir()));
        }
        float rolled = car.rolled(partialTick);
        int load = car.renderLoad();

        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        body.setup(rolled, load);
        body.pantograph(car instanceof com.reiko.trains.train.ElectricLocomotive electric ? electric.pantograph(partialTick) : 0.0F);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        body.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        if (bogie != null) {
            VertexConsumer bogies = buffers.getBuffer(RenderType.entityCutoutNoCull(bogieTexture));
            renderBogie(poseStack, bogies, light, (float) car.bogieOffset(), car.frontBogieYaw(), rolled);
            renderBogie(poseStack, bogies, light, (float) -car.bogieOffset(), car.rearBogieYaw(), rolled);
        }
        poseStack.popPose();
    }

    private void renderBogie(PoseStack poseStack, VertexConsumer consumer, int light, float along, float yaw, float rolled) {
        poseStack.pushPose();
        poseStack.translate(along, 0.0F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        bogie.setup(rolled, 0);
        bogie.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(T car) {
        return texture;
    }
}
