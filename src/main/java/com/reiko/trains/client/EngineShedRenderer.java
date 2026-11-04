package com.reiko.trains.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reiko.trains.shed.EngineShedBlock;
import com.reiko.trains.shed.EngineShedBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws an engine shed's two doors from the middle of its lintel: tall ledged-and-braced timber doors, each half the
 * doorway wide, with a pair of windows up top and black strap hinges, swinging outwards on their hinges at the sides of
 * the doorway (smoothly, as they open and shut).
 *
 * <p>Worked out in blocks from the bottom middle of the doorway: x across it (to the right, seen from outside), y up,
 * z into the shed; the frame's outer face is at z = -0.5.
 */
public class EngineShedRenderer implements BlockEntityRenderer<EngineShedBlockEntity> {
    private static final double HALF_WIDTH = 1.5;
    private static final double HINGE_Z = -0.375;
    private static final double THICK = 0.0625;
    private static final double BOTTOM = 0.2;
    private static final double TOP = 3.95;
    /** How far they swing open, in degrees. */
    private static final float OPEN_ANGLE = 100;

    public EngineShedRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EngineShedBlockEntity shed, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = shed.getLevel();
        BlockState state = shed.getBlockState();
        if (level == null || !(state.getBlock() instanceof EngineShedBlock)) {
            return;
        }
        Direction facing = state.getValue(EngineShedBlock.FACING);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        BlockPos track = shed.track();
        int[] lights = new int[4];
        for (int up = 0; up < 4; up++) {
            lights[up] = LevelRenderer.getLightColor(level, track.relative(facing).above(up));
        }
        float swing = shed.openness(partialTick);
        swing = swing * swing * (3 - 2 * swing); // easing in and out
        poseStack.pushPose();
        poseStack.translate(0.5, -4, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        for (int side = -1; side <= 1; side += 2) {
            poseStack.pushPose();
            poseStack.translate(side * HALF_WIDTH, 0, HINGE_Z);
            // The left door (hinged at -1.5) reaches right from its hinge, and the right one left; both swing outwards (-z).
            poseStack.mulPose(Axis.YP.rotationDegrees(-side * OPEN_ANGLE * swing));
            door(new Boxes(consumer, poseStack.last()), poseStack, consumer, -side, lights);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /** One door, in its own space: from its hinge (x = 0) reaching {@code dir} (1 right, -1 left) along x. */
    private static void door(Boxes b, PoseStack poseStack, VertexConsumer consumer, int dir, int[] lights) {
        TextureAtlasSprite boards = Boxes.sprite("block/engine_shed_door");
        TextureAtlasSprite trim = Boxes.sprite("block/engine_shed_trim");
        TextureAtlasSprite window = Boxes.sprite("block/turntable_window");
        TextureAtlasSprite iron = Boxes.sprite("block/turntable_iron");
        double w = HALF_WIDTH - 0.02;
        // The boards, a block at a time
        for (double y = BOTTOM; y < TOP - 1.0E-6; y += 1) {
            double y1 = Math.min(TOP, y + 1);
            int light = lights[Math.min(3, (int) y)];
            for (double x = 0; x < w - 1.0E-6; x += 1) {
                double x1 = Math.min(w, x + 1);
                b.box(lo(dir, x, x1), y, -THICK, hi(dir, x, x1), y1, THICK, boards, light);
            }
        }
        // On the outside: the frame round it, the ledges across, and the windows up top
        double out = -THICK - 0.03;
        int low = lights[0];
        int high = lights[3];
        b.box(lo(dir, 0, 0.12), BOTTOM, out, hi(dir, 0, 0.12), TOP, -THICK, trim, lights[1]);
        b.box(lo(dir, w - 0.12, w), BOTTOM, out, hi(dir, w - 0.12, w), TOP, -THICK, trim, lights[1]);
        for (double y : new double[] {BOTTOM, 1.95, 2.45, TOP - 0.12}) {
            b.box(lo(dir, 0, w), y, out, hi(dir, 0, w), y + 0.12, -THICK, trim, lights[Math.min(3, (int) y)]);
        }
        for (double x0 : new double[] {0.24, 0.8}) {
            for (double z : new double[] {out - 0.005, THICK}) {
                b.box(lo(dir, x0, x0 + 0.46), 2.62, z, hi(dir, x0, x0 + 0.46), 3.66, z + 0.035, window, high);
            }
        }
        // The brace, from low by the hinge up to the middle ledge at the far side
        double dx = w - 0.36;
        double dy = 1.95 - (BOTTOM + 0.12) - 0.1;
        double length = Math.hypot(dx, dy);
        poseStack.pushPose();
        poseStack.translate(dir * 0.18, BOTTOM + 0.12 + 0.05, 0);
        poseStack.mulPose(Axis.ZP.rotation((float) (dir * Math.atan2(dy, dx))));
        Boxes brace = new Boxes(consumer, poseStack.last());
        brace.box(lo(dir, 0, length), -0.06, out, hi(dir, 0, length), 0.06, -THICK, trim, low);
        poseStack.popPose();
        // Strap hinges
        for (double y : new double[] {0.55, 3.35}) {
            b.box(lo(dir, -0.04, 0.95), y, out - 0.015, hi(dir, -0.04, 0.95), y + 0.1, out, iron, lights[Math.min(3, (int) y)]);
        }
    }

    private static double lo(int dir, double a, double b) {
        return dir > 0 ? a : -b;
    }

    private static double hi(int dir, double a, double b) {
        return dir > 0 ? b : -a;
    }

    @Override
    public AABB getRenderBoundingBox(EngineShedBlockEntity shed) {
        return shed.renderBox();
    }

    @Override
    public boolean shouldRenderOffScreen(EngineShedBlockEntity shed) {
        return true;
    }
}
