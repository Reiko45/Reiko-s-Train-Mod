package com.reiko.trains.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reiko.trains.ReikoTrains;
import com.reiko.trains.wire.CatenaryMastBlockEntity;
import java.util.Map;
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
 * Draws an overhead line mast above its block: the rest of the steel pole, and the cantilever reaching out over the
 * track (a level stay tube along the top, a sloping main tube under it, and a registration tube low down with its steady
 * arm holding the contact wire), each insulated where it leaves the pole. And, for each span it's the lower end of (so
 * each is drawn once), the wire to the next mast: the contact wire running level for the pantographs, the messenger wire
 * sagging above it, and droppers between them every couple of blocks.
 *
 * <p>Everything's worked out in blocks, from the mast block's corner.
 */
public class CatenaryMastRenderer implements BlockEntityRenderer<CatenaryMastBlockEntity> {
    /** The pole, in blocks: how far it is from its middle to each side, and where the block's own model of it stops. */
    private static final double POLE = 0.125;
    private static final double MODEL_TOP = 2;
    /** How high the messenger wire is above the contact wire, at the masts, and how far it sags towards mid-span. */
    private static final double MESSENGER = 1.1;
    private static final double MAX_SAG = 0.75;
    /** About how far apart the droppers are. */
    private static final double DROPPER_EVERY = 2.5;

    public CatenaryMastRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CatenaryMastBlockEntity mast, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = mast.getLevel();
        Vec3 wireAt = mast.wire();
        if (level == null || wireAt == null) {
            return;
        }
        BlockPos pos = mast.getBlockPos();
        Vec3 origin = Vec3.atLowerCornerOf(pos);
        Vec3 wire = wireAt.subtract(origin);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose pose = poseStack.last();
        Parts parts = new Parts(consumer, pose, level, origin);
        TextureAtlasSprite steel = sprite("block/mast_steel");
        TextureAtlasSprite insulator = sprite("block/catenary_insulator");
        TextureAtlasSprite copper = sprite("block/catenary_wire");

        // The pole, on up from where the block's model stops to a little above the messenger wire, with a cap.
        double stay = wire.y + MESSENGER + 0.07;
        double top = Math.max(MODEL_TOP + 0.25, stay + 0.35);
        for (double y = MODEL_TOP; y < top; y += 1) {
            parts.beam(new Vec3(0.5, y, 0.5), new Vec3(0.5, Math.min(top, y + 1), 0.5), POLE, steel);
        }
        parts.beam(new Vec3(0.5, top, 0.5), new Vec3(0.5, top + 0.06, 0.5), POLE + 0.03, steel);

        Vec3 out = new Vec3(wire.x - 0.5, 0, wire.z - 0.5);
        double reach = out.length();
        if (reach < 0.4) {
            // Right over the track itself: nothing to reach out with, it just hangs the wire from the pole.
            parts.beam(new Vec3(wire.x, stay, wire.z), wire, 0.02, steel);
        } else {
            Vec3 dir = out.scale(1 / reach);
            Vec3 foot = new Vec3(0.5, 0, 0.5);
            // Along the top, level, the stay tube, over the wire.
            Vec3 stayEnd = foot.add(dir.scale(reach + 0.15)).add(0, stay, 0);
            arm(parts, foot.add(0, stay, 0), stayEnd, dir, 0.035, steel, insulator);
            // The main tube, from low on the pole up to the stay's end, where the messenger wire hangs.
            Vec3 mainFrom = foot.add(0, wire.y + 0.45, 0);
            Vec3 mainTo = foot.add(dir.scale(reach)).add(0, stay - 0.03, 0);
            arm(parts, mainFrom, mainTo, dir, 0.03, steel, insulator);
            // And the messenger wire's clamp, just under it.
            Vec3 clamp = new Vec3(wire.x, wire.y + MESSENGER + 0.02, wire.z);
            parts.beam(clamp.add(0, -0.04, 0), clamp.add(0, 0.05, 0), 0.04, steel);
            // The registration tube, low down and level, stopping short of the wire, and the steady arm from its end
            // down to the contact wire, holding it in place.
            double low = wire.y + 0.12;
            Vec3 regEnd = foot.add(dir.scale(Math.max(POLE + 0.4, reach - 0.4))).add(0, low, 0);
            arm(parts, foot.add(0, low, 0), regEnd, dir, 0.025, steel, insulator);
            parts.beam(regEnd, wire.add(0, 0.03, 0), 0.015, steel);
            // The brackets round the pole that the tubes are hung from.
            for (double y : new double[] {stay, wire.y + 0.45, low}) {
                parts.beam(new Vec3(0.5, y - 0.05, 0.5), new Vec3(0.5, y + 0.05, 0.5), POLE + 0.025, steel);
            }
        }

        for (Map.Entry<BlockPos, Vec3> link : mast.links().entrySet()) {
            if (pos.asLong() < link.getKey().asLong()) {
                span(parts, wire, link.getValue().subtract(origin), copper);
            }
        }
    }

    /** One of the cantilever's tubes, from the pole out: insulated where it leaves the pole, steel after. */
    private static void arm(Parts parts, Vec3 from, Vec3 to, Vec3 dir, double half, TextureAtlasSprite steel, TextureAtlasSprite insulator) {
        Vec3 along = to.subtract(from);
        double length = along.length();
        Vec3 unit = along.scale(1 / length);
        // Out from the pole's face (which is POLE out along dir; further along the tube if it slopes).
        double start = POLE / Math.max(0.2, unit.dot(dir));
        double insulated = Math.min(length - 0.1, start + 0.32);
        Vec3 a = from.add(unit.scale(start));
        Vec3 b = from.add(unit.scale(insulated));
        parts.beam(a, b, half + 0.012, insulator);
        // Its sheds: three discs along the insulator.
        for (int i = 0; i < 3; i++) {
            double t = start + 0.07 + i * 0.09;
            parts.beam(from.add(unit.scale(t - 0.014)), from.add(unit.scale(t + 0.014)), half + 0.045, insulator);
        }
        parts.beam(b, to, half, steel);
    }

    /**
     * A span of wire from this mast's wire point to another's (both from this mast's corner): the contact wire straight
     * across, the messenger wire above sagging towards the middle, and droppers between.
     */
    private static void span(Parts parts, Vec3 a, Vec3 b, TextureAtlasSprite copper) {
        double length = b.subtract(a).length();
        if (length < 0.01) {
            return;
        }
        parts.beam(a, b, 0.025, copper);
        double sag = Math.min(MAX_SAG, length * 0.03);
        int pieces = Math.max(4, (int) Math.ceil(length));
        Vec3 previous = messenger(a, b, 0, sag);
        for (int i = 1; i <= pieces; i++) {
            Vec3 next = messenger(a, b, (double) i / pieces, sag);
            parts.beam(previous, next, 0.022, copper);
            previous = next;
        }
        int droppers = Math.max(1, (int) Math.round(length / DROPPER_EVERY));
        for (int i = 0; i < droppers; i++) {
            double t = (i + 0.5) / droppers;
            Vec3 low = a.lerp(b, t);
            parts.beam(low, messenger(a, b, t, sag), 0.01, copper);
        }
    }

    /** Where the messenger wire is, {@code t} of the way along a span. */
    private static Vec3 messenger(Vec3 a, Vec3 b, double t, double sag) {
        return a.lerp(b, t).add(0, MESSENGER - sag * 4 * t * (1 - t), 0);
    }

    private static TextureAtlasSprite sprite(String path) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ReikoTrains.id(path));
    }

    /** What the parts are drawn into, and the world light at them. */
    private record Parts(VertexConsumer consumer, PoseStack.Pose pose, Level level, Vec3 origin) {
        /**
         * A square-sectioned beam {@code 2 × half} across, from {@code a} to {@code b}, its sides turned square to the
         * level (for anything that isn't upright), each face showing as much of the sprite as it's long, up to all of it.
         */
        void beam(Vec3 a, Vec3 b, double half, TextureAtlasSprite sprite) {
            Vec3 axis = b.subtract(a);
            double length = axis.length();
            if (length < 1.0E-4) {
                return;
            }
            Vec3 u = axis.scale(1 / length);
            Vec3 side = u.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 up = side.cross(u).normalize();
            Vec3 s = side.scale(half);
            Vec3 t = up.scale(half);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(origin.add(a.lerp(b, 0.5))));
            float uLong = sprite.getU((float) Math.min(1, length));
            float uWide = sprite.getU((float) Math.min(1, half * 2));
            float u0 = sprite.getU(0);
            float v0 = sprite.getV(0);
            float v1 = sprite.getV((float) Math.min(1, half * 2));
            // The four sides, going round, then the ends.
            Vec3[] around = {s.add(t), s.subtract(t), s.reverse().subtract(t), s.reverse().add(t)};
            Vec3[] normals = {side, up.reverse(), side.reverse(), up};
            for (int i = 0; i < 4; i++) {
                Vec3 p = around[i];
                Vec3 q = around[(i + 1) % 4];
                Vec3 n = normals[i];
                quad(a.add(p), b.add(p), b.add(q), a.add(q), n, u0, v0, uLong, v1, light);
            }
            quad(a.add(around[0]), a.add(around[1]), a.add(around[2]), a.add(around[3]), u.reverse(), u0, v0, uWide, v1, light);
            quad(b.add(around[3]), b.add(around[2]), b.add(around[1]), b.add(around[0]), u, u0, v0, uWide, v1, light);
        }

        private void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 n, float u0, float v0, float u1, float v1, int light) {
            vertex(a, u0, v1, light, n);
            vertex(b, u1, v1, light, n);
            vertex(c, u1, v0, light, n);
            vertex(d, u0, v0, light, n);
        }

        private void vertex(Vec3 p, float u, float v, int light, Vec3 n) {
            consumer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(255, 255, 255, 255).setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
        }
    }

    @Override
    public AABB getRenderBoundingBox(CatenaryMastBlockEntity mast) {
        AABB box = new AABB(mast.getBlockPos()).expandTowards(0, 2, 0);
        Vec3 wire = mast.wire();
        if (wire != null) {
            box = box.minmax(new AABB(wire, wire).inflate(0.5).expandTowards(0, 2, 0));
        }
        for (Vec3 other : mast.links().values()) {
            box = box.minmax(new AABB(other, other).inflate(0.5).expandTowards(0, 1.5, 0));
        }
        return box;
    }

    @Override
    public boolean shouldRenderOffScreen(CatenaryMastBlockEntity mast) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
