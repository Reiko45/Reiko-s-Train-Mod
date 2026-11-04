package com.reiko.trains.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reiko.trains.turntable.TurntableBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Draws a turntable from its pivot: the ring rail round the floor of the pit, and the bridge over it, turned the way
 * it points (as it turns, smoothly). The bridge is a pair of deep black plate girders, deepest in the middle, under a
 * deck of timbers carrying the rails level with the track beyond the pit; a walkway with a handrail along each side;
 * a carriage at each end with two wheels running on the ring rail; and, at its front end, a little green hut.
 *
 * <p>The bridge is worked out in its own space, in blocks: x along it (its front end at +x), y up from the rail tops,
 * z across it.
 */
public class TurntableRenderer implements BlockEntityRenderer<TurntableBlockEntity> {
    /** How far the bridge reaches each way from the middle, and the ring rail's radius. */
    private static final double HALF = 7.45;
    private static final double RING = 7.12;
    /** Where the rail tops are, above the bottom of the pivot's block (a block up, and a quarter). */
    private static final double DECK = 1.25;
    /** The ring rail's top, and the main girders' sides (from the middle of the bridge). */
    private static final double FLOOR = 2.0 / 16;
    private static final double GIRDER = 0.82;
    private static final double WALK_IN = 0.92;
    private static final double WALK_OUT = 1.32;

    public TurntableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TurntableBlockEntity table, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = table.getLevel();
        if (level == null) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        BlockPos pivot = table.getBlockPos();
        TextureAtlasSprite rail = Boxes.sprite("block/track_rail");
        TextureAtlasSprite sleeper = Boxes.sprite("block/track_sleeper");
        TextureAtlasSprite girder = Boxes.sprite("block/turntable_girder");
        TextureAtlasSprite deck = Boxes.sprite("block/turntable_deck");
        TextureAtlasSprite iron = Boxes.sprite("block/turntable_iron");
        TextureAtlasSprite hut = Boxes.sprite("block/turntable_hut");
        TextureAtlasSprite window = Boxes.sprite("block/turntable_window");
        TextureAtlasSprite roof = Boxes.sprite("block/water_tower_roof");

        // The ring rail round the pit floor, in short straight pieces.
        int pieces = 72;
        double piece = 2 * Math.PI * RING / pieces;
        for (int i = 0; i < pieces; i++) {
            double a = 360.0 * i / pieces;
            poseStack.pushPose();
            poseStack.translate(0.5, 0, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees((float) -a));
            Boxes boxes = new Boxes(consumer, poseStack.last());
            int at = light(level, pivot, a, RING, 0.5);
            boxes.box(RING - 0.06, FLOOR, -piece / 2 - 0.01, RING + 0.06, FLOOR + 0.12, piece / 2 + 0.01, rail, at);
            poseStack.popPose();
        }

        double time = level.getGameTime() + partialTick;
        float angle = table.angleAt(time);
        poseStack.pushPose();
        poseStack.translate(0.5, DECK, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-angle));
        Boxes b = new Boxes(consumer, poseStack.last());

        // Rails and the timbers under them
        for (double x = -HALF; x < HALF - 1.0E-6; x += 1) {
            double x1 = Math.min(HALF, x + 1);
            int at = light(level, pivot, angle, x + 0.5, 0);
            for (int side = -1; side <= 1; side += 2) {
                double z = side * TrackRenderer.GAUGE;
                b.box(x, -TrackRenderer.RAIL_HEIGHT, z - TrackRenderer.RAIL_HALF_WIDTH, x1, 0, z + TrackRenderer.RAIL_HALF_WIDTH, rail, at);
            }
        }
        for (double x = -7.25; x <= 7.25 + 1.0E-6; x += 0.5) {
            b.box(x - 0.09, -0.25, -0.75, x + 0.09, -TrackRenderer.RAIL_HEIGHT, 0.75, sleeper, light(level, pivot, angle, x, 0));
        }
        // The main girders, a block at a time, deepest in the middle
        for (double x = -7.3; x < 7.3 - 1.0E-6; x += 0.5) {
            double x1 = Math.min(7.3, x + 0.5);
            double mid = (x + x1) / 2;
            double bottom = -0.42 - 0.58 * (1 - (mid / 7.3) * (mid / 7.3));
            int at = light(level, pivot, angle, mid, 0);
            for (int side = -1; side <= 1; side += 2) {
                double z = side * GIRDER;
                b.box(x, bottom, z - 0.05, x1, -0.14, z + 0.05, girder, at);
                b.box(x, -0.14, z - 0.13, x1, -0.08, z + 0.13, girder, at);
                b.box(x, bottom - 0.05, z - 0.1, x1, bottom, z + 0.1, girder, at);
            }
        }
        // Cross girders between them
        for (double x = -6; x <= 6 + 1.0E-6; x += 1.5) {
            b.box(x - 0.06, -0.42, -GIRDER, x + 0.06, -0.25, GIRDER, girder, light(level, pivot, angle, x, 0));
        }
        // The bearing on the pivot
        b.box(-0.45, -0.4, -0.45, 0.45, -0.25, 0.45, iron, light(level, pivot, angle, 0, 0));
        // Walkways, their brackets, and the handrails
        for (int side = -1; side <= 1; side += 2) {
            double in = side * WALK_IN;
            double out = side * WALK_OUT;
            double z0 = Math.min(in, out);
            double z1 = Math.max(in, out);
            for (double x = -6.9; x < 6.9 - 1.0E-6; x += 1) {
                double x1 = Math.min(6.9, x + 1);
                int at = light(level, pivot, angle, x + 0.5, side * 1.1);
                b.box(x, -0.2, z0, x1, -0.12, z1, deck, at);
                boolean byHut = side > 0 && x1 > 5.0;
                if (!byHut) {
                    double zr = side * (WALK_OUT - 0.04);
                    b.box(x, 0.8, zr - 0.025, x1, 0.85, zr + 0.025, iron, at);
                    b.box(x, 0.42, zr - 0.02, x1, 0.46, zr + 0.02, iron, at);
                }
            }
            for (double x = -6.9; x <= 6.9 + 1.0E-6; x += 1.15) {
                int at = light(level, pivot, angle, x, side * 1.1);
                b.box(x - 0.04, -0.4, Math.min(side * 0.87, side * 1.28), x + 0.04, -0.2, Math.max(side * 0.87, side * 1.28), girder, at);
                if (!(side > 0 && x > 5.0)) {
                    double zr = side * (WALK_OUT - 0.04);
                    b.box(x - 0.03, -0.12, zr - 0.03, x + 0.03, 0.85, zr + 0.03, iron, at);
                }
            }
        }
        // A carriage at each end, with its two wheels on the ring rail
        double wheelY = FLOOR + 0.12 - DECK;
        for (int end = -1; end <= 1; end += 2) {
            double x = end * 7.05;
            int at = light(level, pivot, angle, x, 0);
            b.box(x - 0.18, -0.58, -0.98, x + 0.18, -0.4, 0.98, girder, at);
            for (int side = -1; side <= 1; side += 2) {
                double z = side * 0.74;
                b.box(x - 0.05, wheelY + 0.18, z - 0.035, x + 0.05, -0.58, z + 0.035, iron, at);
                b.box(x - 0.17, wheelY, z - 0.06, x + 0.17, wheelY + 0.34, z + 0.06, iron, at);
                b.box(x - 0.11, wheelY + 0.06, z - 0.08, x + 0.11, wheelY + 0.28, z + 0.08, iron, at);
            }
        }
        // The hut at the front end, on the right, with a window out over the pit and one facing the front
        int at = light(level, pivot, angle, 6.0, 1.4);
        b.box(5.15, -0.2, 1.08, 6.75, -0.12, 1.8, deck, at);
        b.box(5.2, -0.12, 1.12, 6.7, 1.55, 1.76, hut, at);
        b.box(5.6, 0.62, 1.76, 6.3, 1.22, 1.775, window, at);
        b.box(6.7, 0.62, 1.22, 6.715, 1.22, 1.66, window, at);
        b.box(5.1, 1.55, 1.02, 6.8, 1.68, 1.86, roof, at);
        b.box(5.4, 1.68, 1.2, 6.5, 1.74, 1.68, roof, at);
        poseStack.popPose();
    }

    /** The light at a point of the bridge (along it, across it, at track level), as it points now. */
    private static int light(Level level, BlockPos pivot, double angle, double along, double across) {
        double a = Math.toRadians(angle);
        double x = pivot.getX() + 0.5 + Math.cos(a) * along - Math.sin(a) * across;
        double z = pivot.getZ() + 0.5 + Math.sin(a) * along + Math.cos(a) * across;
        return LevelRenderer.getLightColor(level, BlockPos.containing(x, pivot.getY() + 1.1, z));
    }

    @Override
    public AABB getRenderBoundingBox(TurntableBlockEntity table) {
        return table.renderBox();
    }

    @Override
    public boolean shouldRenderOffScreen(TurntableBlockEntity table) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
