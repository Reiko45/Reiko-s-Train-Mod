package com.reiko.trains.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reiko.trains.ReikoTrains;
import com.reiko.trains.track.EdgeRef;
import com.reiko.trains.track.TrackBlockEntity;
import com.reiko.trains.track.TrackEdge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the curves (and ramps, and long straights) drawn from a track block: sleepers every half block and two rails
 * along the curve, in the same textures as the straight track blocks.
 */
public class TrackRenderer implements BlockEntityRenderer<TrackBlockEntity> {
    /** Half the distance between the rails' centres, in blocks. */
    static final double GAUGE = 7.0 / 16;
    static final double RAIL_HALF_WIDTH = 1.0 / 16;
    static final double RAIL_HEIGHT = 2.0 / 16;
    static final double SLEEPER_HALF_LENGTH = 12.0 / 16;
    static final double SLEEPER_HALF_WIDTH = 1.5 / 16;
    static final double SLEEPER_HEIGHT = 2.0 / 16;
    static final double SLEEPER_SPACING = 0.5;

    public TrackRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TrackBlockEntity track, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = track.getLevel();
        if (level == null) {
            return;
        }
        TextureAtlasSprite rail = sprite("block/track_rail");
        TextureAtlasSprite sleeper = sprite("block/track_sleeper");
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        BlockPos origin = track.getBlockPos();
        PoseStack.Pose pose = poseStack.last();
        for (EdgeRef edge : track.ownedEdges()) {
            TrackEdge shape = edge.geometry();
            double length = shape.length();
            // Sleepers
            int count = Math.max(1, (int) Math.round(length / SLEEPER_SPACING));
            for (int i = 0; i <= count; i++) {
                double s = length * i / count;
                Vec3 p = shape.point(s);
                Vec3 t = shape.tangent(s);
                int at = lightAt(level, p);
                Vec3 base = p.subtract(0, TrackEdge.RAIL_HEIGHT, 0);
                drawBox(consumer, pose, origin, base, t, -SLEEPER_HALF_WIDTH, SLEEPER_HALF_WIDTH, -SLEEPER_HALF_LENGTH, SLEEPER_HALF_LENGTH,
                        0, SLEEPER_HEIGHT, sleeper, at);
            }
            // Rails, in short straight pieces
            int pieces = Math.max(1, (int) Math.ceil(length / 0.5));
            for (int i = 0; i < pieces; i++) {
                double s0 = length * i / pieces;
                double s1 = length * (i + 1) / pieces;
                Vec3 a = shape.point(s0);
                Vec3 b = shape.point(s1);
                int at = lightAt(level, a.add(b).scale(0.5));
                for (int side = -1; side <= 1; side += 2) {
                    drawRail(consumer, pose, origin, a, shape.tangent(s0), b, shape.tangent(s1), side * GAUGE, rail, at);
                }
            }
        }
    }

    private static TextureAtlasSprite sprite(String path) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ReikoTrains.id(path));
    }

    private static int lightAt(Level level, Vec3 p) {
        return LevelRenderer.getLightColor(level, BlockPos.containing(p.x, p.y + 0.2, p.z));
    }

    /** Flat sideways from a direction: the right-hand side, looking along it. */
    private static Vec3 right(Vec3 t) {
        double flat = Math.sqrt(t.x * t.x + t.z * t.z);
        return flat < 1.0E-6 ? new Vec3(1, 0, 0) : new Vec3(-t.z / flat, 0, t.x / flat);
    }

    /** A rail from {@code a} to {@code b}, offset sideways; its ends follow the curve so the pieces meet. */
    private static void drawRail(VertexConsumer consumer, PoseStack.Pose pose, BlockPos origin, Vec3 a, Vec3 ta, Vec3 b, Vec3 tb, double offset,
                                 TextureAtlasSprite sprite, int light) {
        Vec3 ra = right(ta);
        Vec3 rb = right(tb);
        double w = RAIL_HALF_WIDTH;
        Vec3 aIn = a.add(ra.scale(offset - w)).subtract(0, RAIL_HEIGHT, 0);
        Vec3 aOut = a.add(ra.scale(offset + w)).subtract(0, RAIL_HEIGHT, 0);
        Vec3 bIn = b.add(rb.scale(offset - w)).subtract(0, RAIL_HEIGHT, 0);
        Vec3 bOut = b.add(rb.scale(offset + w)).subtract(0, RAIL_HEIGHT, 0);
        Vec3 up = new Vec3(0, RAIL_HEIGHT, 0);
        float u0 = sprite.getU(0);
        float u1 = sprite.getU(2 / 16f);
        float v0 = sprite.getV(0);
        float v1 = sprite.getV(1);
        float sideU0 = sprite.getU(2 / 16f);
        float sideU1 = sprite.getU(4 / 16f);
        // top
        quad(consumer, pose, origin, aIn.add(up), bIn.add(up), bOut.add(up), aOut.add(up), u0, v0, u1, v1, light, 0, 1, 0);
        // sides
        Vec3 n = ra;
        quad(consumer, pose, origin, aOut, aOut.add(up), bOut.add(up), bOut, sideU0, v0, sideU1, v1, light, (float) n.x, 0, (float) n.z);
        quad(consumer, pose, origin, bIn, bIn.add(up), aIn.add(up), aIn, sideU0, v0, sideU1, v1, light, (float) -n.x, 0, (float) -n.z);
    }

    /** A box lined up with direction {@code t}: {@code x} along it, {@code z} across it, {@code y} up. */
    private static void drawBox(VertexConsumer consumer, PoseStack.Pose pose, BlockPos origin, Vec3 base, Vec3 t, double x0, double x1, double z0,
                                double z1, double y0, double y1, TextureAtlasSprite sprite, int light) {
        double flat = Math.sqrt(t.x * t.x + t.z * t.z);
        Vec3 along = flat < 1.0E-6 ? new Vec3(0, 0, 1) : new Vec3(t.x / flat, 0, t.z / flat);
        Vec3 across = new Vec3(-along.z, 0, along.x);
        Vec3[] c = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? x0 : x1;
            double z = (i & 2) == 0 ? z0 : z1;
            double y = (i & 4) == 0 ? y0 : y1;
            c[i] = base.add(along.scale(x)).add(across.scale(z)).add(0, y, 0);
        }
        float u0 = sprite.getU(0);
        float u1 = sprite.getU(1);
        float v0 = sprite.getV(0);
        float v1 = sprite.getV(3 / 16f);
        float sv1 = sprite.getV(5 / 16f);
        float sv0 = sprite.getV(3 / 16f);
        quad(consumer, pose, origin, c[4], c[5], c[7], c[6], u0, v0, u1, v1, light, 0, 1, 0);
        quad(consumer, pose, origin, c[0], c[4], c[6], c[2], u0, sv0, u1, sv1, light, (float) -along.x, 0, (float) -along.z);
        quad(consumer, pose, origin, c[3], c[7], c[5], c[1], u0, sv0, u1, sv1, light, (float) along.x, 0, (float) along.z);
        quad(consumer, pose, origin, c[1], c[5], c[4], c[0], u0, sv0, u1, sv1, light, (float) -across.x, 0, (float) -across.z);
        quad(consumer, pose, origin, c[2], c[6], c[7], c[3], u0, sv0, u1, sv1, light, (float) across.x, 0, (float) across.z);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, BlockPos origin, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             float u0, float v0, float u1, float v1, int light, float nx, float ny, float nz) {
        vertex(consumer, pose, origin, a, u0, v1, light, nx, ny, nz);
        vertex(consumer, pose, origin, b, u0, v0, light, nx, ny, nz);
        vertex(consumer, pose, origin, c, u1, v0, light, nx, ny, nz);
        vertex(consumer, pose, origin, d, u1, v1, light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, BlockPos origin, Vec3 p, float u, float v, int light,
                               float nx, float ny, float nz) {
        consumer.addVertex(pose, (float) (p.x - origin.getX()), (float) (p.y - origin.getY()), (float) (p.z - origin.getZ()))
                .setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    @Override
    public boolean shouldRenderOffScreen(TrackBlockEntity track) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(TrackBlockEntity track) {
        return track.renderBox();
    }
}
