package com.reiko.trains.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reiko.trains.signal.TrackSensorBlock;
import com.reiko.trains.signal.TrackSensorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Shows a track sensor's filter item on its back, the side away from the track, like an item in a frame. */
public class TrackSensorRenderer implements BlockEntityRenderer<TrackSensorBlockEntity> {
    private final ItemRenderer items;

    public TrackSensorRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(TrackSensorBlockEntity sensor, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        ItemStack filter = sensor.filter();
        if (filter.isEmpty() || sensor.getLevel() == null) {
            return;
        }
        Direction facing = sensor.getBlockState().getValue(TrackSensorBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        // Its back faces away from the track: the way it faces, turned round.
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.getOpposite().toYRot()));
        poseStack.translate(0, 8.5 / 16, 4.15 / 16);
        // Items in frames face back the other way: turn it round to face out.
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        poseStack.scale(0.3F, 0.3F, 0.3F);
        items.renderStatic(filter, ItemDisplayContext.FIXED, light, overlay, poseStack, buffers, sensor.getLevel(), (int) sensor.getBlockPos().asLong());
        poseStack.popPose();
    }
}
