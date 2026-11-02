package com.reiko.trains.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reiko.trains.signal.StationBlock;
import com.reiko.trains.signal.StationBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.AABB;

/** Writes the station's name on both faces of its board, shrinking it to fit. */
public class StationRenderer implements BlockEntityRenderer<StationBlockEntity> {
    /** The board's face, in pixels: 16 wide, from 19 to 28 up, half a pixel either side of the middle. */
    private static final float BOARD_WIDTH = 12.5F;
    private static final float BOARD_MIDDLE = 23.5F;
    private static final float BOARD_HALF_THICKNESS = 1.0F;
    private final Font font;

    public StationRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(StationBlockEntity station, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = station.getBlockState().getValue(StationBlock.FACING);
        FormattedCharSequence text = FormattedCharSequence.forward(station.name(), net.minecraft.network.chat.Style.EMPTY);
        int width = Math.max(1, font.width(text));
        float scale = Math.min(1 / 16F * 0.6F, BOARD_WIDTH / 16F / width);
        for (int face = 0; face < 2; face++) {
            poseStack.pushPose();
            poseStack.translate(0.5, BOARD_MIDDLE / 16, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + (face == 0 ? 180 : 0)));
            poseStack.translate(0, 0, (BOARD_HALF_THICKNESS + 0.05F) / 16);
            poseStack.scale(scale, -scale, scale);
            font.drawInBatch(text, -width / 2F, -font.lineHeight / 2F + 1, 0xFFF4F0E0, false, poseStack.last().pose(), buffers,
                    Font.DisplayMode.POLYGON_OFFSET, 0, light);
            poseStack.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(StationBlockEntity station) {
        return new AABB(station.getBlockPos()).expandTowards(0, 1, 0);
    }
}
