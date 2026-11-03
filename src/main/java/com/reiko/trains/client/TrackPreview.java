package com.reiko.trains.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reiko.trains.registry.ModDataComponents;
import com.reiko.trains.track.TrackBuilder;
import com.reiko.trains.track.TrackEdge;
import com.reiko.trains.track.TrackItem;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * While you're drawing a curve with the track item, shows where it would go (green if it can be built, red if not)
 * from the track block you picked to the block you're looking at, and says how long it is and what it costs.
 */
public final class TrackPreview {
    private static int messageCooldown;

    private TrackPreview() {
    }

    @Nullable
    private static TrackBuilder.Plan currentPlan() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return null;
        }
        BlockPos start = null;
        boolean street = false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof TrackItem track && stack.has(ModDataComponents.TRACK_START.get())) {
                start = stack.get(ModDataComponents.TRACK_START.get());
                street = track.street();
                break;
            }
        }
        if (start == null || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || hit.getBlockPos().equals(start)) {
            return null;
        }
        BlockPos end = TrackBuilder.endFor(minecraft.level, hit.getBlockPos(), hit.getDirection(), street);
        return TrackBuilder.plan(minecraft.level, start, end, player.getLookAngle(), player.isShiftKeyDown(), street);
    }

    /** Client tick: what the curve would be, on the action bar. */
    static void tick() {
        if (--messageCooldown > 0) {
            return;
        }
        messageCooldown = 5;
        TrackBuilder.Plan plan = currentPlan();
        LocalPlayer player = Minecraft.getInstance().player;
        if (plan == null || player == null) {
            return;
        }
        Component message = plan.ok()
                ? Component.translatable("message.reikotrains.track.preview", String.format("%.0f", plan.shape().length()), plan.cost())
                .withStyle(ChatFormatting.GREEN)
                : plan.problem().copy().withStyle(ChatFormatting.RED);
        player.displayClientMessage(message, true);
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        TrackBuilder.Plan plan = currentPlan();
        if (plan == null || plan.shape() == null) {
            if (plan != null) {
                drawEnd(event, plan.end(), false);
            }
            return;
        }
        TrackEdge shape = plan.shape();
        boolean ok = plan.ok();
        float r = ok ? 0.3F : 1.0F;
        float g = ok ? 1.0F : 0.25F;
        float b = ok ? 0.4F : 0.2F;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();
        int steps = Math.max(4, (int) Math.ceil(shape.length() * 2));
        for (int side = -1; side <= 1; side += 2) {
            Vec3 prev = null;
            for (int i = 0; i <= steps; i++) {
                double s = shape.length() * i / steps;
                Vec3 p = shape.point(s);
                Vec3 t = shape.tangent(s);
                double flat = Math.sqrt(t.x * t.x + t.z * t.z);
                Vec3 right = flat < 1.0E-6 ? Vec3.ZERO : new Vec3(-t.z / flat, 0, t.x / flat);
                Vec3 q = p.add(right.scale(side * TrackRenderer.GAUGE)).add(0, 0.02, 0);
                if (prev != null) {
                    line(lines, pose, prev, q, r, g, b);
                }
                prev = q;
            }
        }
        // Sleepers every two blocks, so the shape reads at a glance.
        for (double s = 0; s <= shape.length(); s += 2) {
            Vec3 p = shape.point(s).add(0, 0.02, 0);
            Vec3 t = shape.tangent(s);
            double flat = Math.sqrt(t.x * t.x + t.z * t.z);
            Vec3 right = flat < 1.0E-6 ? Vec3.ZERO : new Vec3(-t.z / flat, 0, t.x / flat);
            line(lines, pose, p.add(right.scale(-TrackRenderer.SLEEPER_HALF_LENGTH)), p.add(right.scale(TrackRenderer.SLEEPER_HALF_LENGTH)), r, g, b);
        }
        if (plan.newEnd()) {
            LevelRenderer.renderLineBox(poseStack, lines, new AABB(plan.end()).setMaxY(plan.end().getY() + 0.25), r, g, b, 1.0F);
        }
        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void drawEnd(RenderLevelStageEvent event, BlockPos end, boolean ok) {
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), new AABB(end).setMaxY(end.getY() + 0.25),
                ok ? 0.3F : 1.0F, ok ? 1.0F : 0.25F, ok ? 0.4F : 0.2F, 1.0F);
        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void line(VertexConsumer lines, PoseStack.Pose pose, Vec3 a, Vec3 b, float r, float g, float bl) {
        Vec3 n = b.subtract(a).normalize();
        lines.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, 1.0F).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
        lines.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, 1.0F).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }
}
