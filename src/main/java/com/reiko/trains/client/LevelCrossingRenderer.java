package com.reiko.trains.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reiko.trains.ReikoTrains;
import com.reiko.trains.signal.LevelCrossingBlock;
import com.reiko.trains.signal.LevelCrossingBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a level crossing's moving parts: the barrier arm, red and white, swinging between upright and across the
 * road on its pivot beside the post, with its counterweight; and, while it's warning, the two lamps on each face of
 * the board flashing turn about. Everything's worked out in the post's own pixels, as if it faced north (the arm
 * coming down towards -z), and turned to face the way it does.
 */
public class LevelCrossingRenderer implements BlockEntityRenderer<LevelCrossingBlockEntity> {
    /** The arm's pivot, in the post's pixels: beside the mechanism box, on the post's right. */
    private static final float PIVOT_X = 13.5F;
    private static final float PIVOT_Y = 15F;
    private static final float PIVOT_Z = 8F;
    /** How long each red or white band of the arm is, in pixels. */
    private static final float BAND = 8F;
    /** The lamps: their middles along the board, and how high they are. */
    private static final float[] LAMP_Z = {4F, 12F};
    private static final float LAMP_Y0 = 22F;
    private static final float LAMP_Y1 = 27F;
    private static final float LAMP_HALF = 2.5F;
    /** How long each lamp stays lit before the other takes over, in ticks. */
    private static final int FLASH_TICKS = 10;

    public LevelCrossingRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LevelCrossingBlockEntity crossing, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = crossing.getBlockState();
        Level level = crossing.getLevel();
        if (!(state.getBlock() instanceof LevelCrossingBlock) || level == null) {
            return;
        }
        Direction facing = state.getValue(LevelCrossingBlock.FACING);
        int length = state.getValue(LevelCrossingBlock.LENGTH);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + 180));
        poseStack.translate(-0.5, 0, -0.5);
        poseStack.scale(1 / 16F, 1 / 16F, 1 / 16F);

        if (state.getValue(LevelCrossingBlock.WARNING)) {
            int lit = (int) ((level.getGameTime() / FLASH_TICKS) % 2);
            TextureAtlasSprite lamp = sprite("block/signal_red_on");
            float z = LAMP_Z[lit];
            // Just in front of the unlit lens on each face, the whole lamp texture across it, glowing.
            PoseStack.Pose pose = poseStack.last();
            face(consumer, pose, lamp, LightTexture.FULL_BRIGHT, 16, 16, 1, 0, 0, 9.3F, LAMP_Y0, z - LAMP_HALF, 9.3F, LAMP_Y1, z - LAMP_HALF,
                    9.3F, LAMP_Y1, z + LAMP_HALF, 9.3F, LAMP_Y0, z + LAMP_HALF);
            face(consumer, pose, lamp, LightTexture.FULL_BRIGHT, 16, 16, -1, 0, 0, 6.7F, LAMP_Y0, z + LAMP_HALF, 6.7F, LAMP_Y1, z + LAMP_HALF,
                    6.7F, LAMP_Y1, z - LAMP_HALF, 6.7F, LAMP_Y0, z - LAMP_HALF);
        }

        float swing = crossing.swing(partialTick);
        BlockPos pos = crossing.getBlockPos();
        Vec3 right = Vec3.atLowerCornerOf(facing.getClockWise().getNormal());
        Vec3 out = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 pivot = Vec3.atBottomCenterOf(pos).add(right.scale((PIVOT_X - 8) / 16)).add(0, PIVOT_Y / 16, 0);
        double angle = swing * Math.PI / 2;
        Vec3 along = new Vec3(0, Math.cos(angle), 0).add(out.scale(Math.sin(angle)));

        poseStack.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90 * swing));
        PoseStack.Pose pose = poseStack.last();
        TextureAtlasSprite metal = sprite("block/signal_metal");
        TextureAtlasSprite dark = sprite("block/signal_head");
        int atPivot = lightAt(level, pivot);
        box(consumer, pose, -1.5F, -1.5F, -1.5F, 1.5F, 1.5F, 1.5F, dark, atPivot);
        // The counterweight, on a short stub behind the pivot.
        int behind = lightAt(level, pivot.subtract(along.scale(0.4)));
        box(consumer, pose, -1F, -5F, -1F, 1F, -1.5F, 1F, metal, behind);
        box(consumer, pose, -1.75F, -9F, -2.25F, 1.75F, -5F, 2.25F, dark, behind);
        // The arm, in red and white bands, reaching just past the middle of the last block it's over.
        TextureAtlasSprite white = sprite("block/crossing_arm_white");
        TextureAtlasSprite red = sprite("block/crossing_arm_red");
        float reach = length * 16 + 4;
        int band = 0;
        for (float y = 1.5F; y < reach; y += BAND, band++) {
            float end = Math.min(reach, y + BAND);
            int at = lightAt(level, pivot.add(along.scale((y + end) / 2 / 16)));
            box(consumer, pose, -1F, y, -1.5F, 1F, end, 1.5F, band % 2 == 0 ? white : red, at);
        }
        poseStack.popPose();
    }

    private static TextureAtlasSprite sprite(String path) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ReikoTrains.id(path));
    }

    private static int lightAt(Level level, Vec3 p) {
        return LevelRenderer.getLightColor(level, BlockPos.containing(p));
    }

    /** A box, in pixels, each face showing as much of the texture as it's big (up to the whole of it). */
    private static void box(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
                            TextureAtlasSprite sprite, int light) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        face(consumer, pose, sprite, light, dx, dy, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        face(consumer, pose, sprite, light, dx, dy, 0, 0, 1, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
        face(consumer, pose, sprite, light, dz, dy, -1, 0, 0, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
        face(consumer, pose, sprite, light, dz, dy, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        face(consumer, pose, sprite, light, dx, dz, 0, 1, 0, x0, y1, z1, x0, y1, z0, x1, y1, z0, x1, y1, z1);
        face(consumer, pose, sprite, light, dx, dz, 0, -1, 0, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0);
    }

    /** One face: corners bottom-left, top-left, top-right, bottom-right (as seen on the texture). */
    private static void face(VertexConsumer consumer, PoseStack.Pose pose, TextureAtlasSprite sprite, int light, float width, float height,
                             float nx, float ny, float nz, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
                             float cz, float dx, float dy, float dz) {
        float u0 = sprite.getU(0);
        float u1 = sprite.getU(Math.min(16, width) / 16);
        float v0 = sprite.getV(0);
        float v1 = sprite.getV(Math.min(16, height) / 16);
        vertex(consumer, pose, ax, ay, az, u0, v1, light, nx, ny, nz);
        vertex(consumer, pose, bx, by, bz, u0, v0, light, nx, ny, nz);
        vertex(consumer, pose, cx, cy, cz, u1, v0, light, nx, ny, nz);
        vertex(consumer, pose, dx, dy, dz, u1, v1, light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light, float nx,
                               float ny, float nz) {
        consumer.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    @Override
    public AABB getRenderBoundingBox(LevelCrossingBlockEntity crossing) {
        int reach = LevelCrossingBlock.MAX_LENGTH + 1;
        return new AABB(crossing.getBlockPos()).inflate(reach, 0, reach).expandTowards(0, reach + 1, 0);
    }
}
