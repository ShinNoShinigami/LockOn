package com.shin.lockon;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static com.shin.lockon.LockOn.MODID;
import static net.minecraftforge.common.MinecraftForge.EVENT_BUS;


public class LockOnHandler {

    private static final Logger log = LoggerFactory.getLogger(LockOnHandler.class);
    public static KeyMapping LOCK_ON;
    public static KeyMapping TAB;

    public static List<LivingEntity> list = new ArrayList<>();
    private static final Minecraft mc = Minecraft.getInstance();

    private static boolean lockedOn;
    private static Entity targeted;
    private static int cycle = -1;

    public static boolean lockY = true;
    private static final Predicate<LivingEntity> ENTITY_PREDICATE = entity -> entity instanceof LivingEntity && !entity.isInvisible();

    public static void client(FMLClientSetupEvent e) {
        EVENT_BUS.addListener(LockOnHandler::logOff);
        EVENT_BUS.addListener(LockOnHandler::tick);
    }

    public static void keyBind(RegisterKeyMappingsEvent e) {
        LOCK_ON = new KeyMapping("key." + MODID + ".lock_on", GLFW.GLFW_KEY_O, "key.categories." + MODID);
        TAB = new KeyMapping("key." + MODID + ".tab", GLFW.GLFW_KEY_TAB, "key.categories." + MODID);
        e.register(LOCK_ON);
        e.register(TAB);
    }

    public static void renderWorldLast(Entity entity, PoseStack poseStack, MultiBufferSource buffers, Quaternion quaternion) {
        if (targeted == entity && LockOn.ClientConfig.renderIcons.get()) {
            VertexConsumer builder = buffers.getBuffer(ModRenderType.RENDER_TYPE);
            poseStack.pushPose();
            poseStack.translate(0, entity.getBbHeight() / 2, 0);
            poseStack.mulPose(quaternion);
            poseStack.mulPose(Vector3f.ZP.rotationDegrees(Util.getNanos() / -8_000_000f));

            float w = (float) (double) LockOn.ClientConfig.width.get();
            float h = (float) (double) LockOn.ClientConfig.height.get();
            int color = getColorConfig();

            RenderSystem.disableCull();
            fillTriangles(builder, poseStack.last().pose(), 0, entity.getBbHeight() / 2f, -w / 2f, h, entity.getBbHeight() / 2f, 0, color);
            poseStack.popPose();
        }
    }

    private static int getColorConfig() {
        try {
            return Integer.decode(LockOn.ClientConfig.color.get());
        } catch (NumberFormatException e) {
            log.error("Error decoding color: ", e);
            return 0xffffff00;
        }
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.START) {
            while (LOCK_ON.consumeClick()) {
                if (lockedOn) {
                    leaveLockOn();
                } else {
                    attemptEnterLockOn(mc.player);
                }
            }

            while (TAB.consumeClick()) {
                tabToNextEnemy(mc.player);
            }
            tickLockedOn();
        }
    }

    private static void logOff(ClientPlayerNetworkEvent.LoggingOut e) {
        leaveLockOn();
    }

    public static boolean handleKeyPress(Player player, double d2, double d3) {
        if (player != null && !mc.isPaused() && targeted != null) {
            Vec3 targetPos = targeted.position().add(0, targeted.getEyeHeight(), 0);
            Vec3 targetVec = targetPos.subtract(player.position().add(0, player.getEyeHeight(), 0)).normalize();
            double targetAngleX = Mth.wrapDegrees(Math.atan2(-targetVec.x, targetVec.z) * 180 / Math.PI);
            double targetAngleY = Math.atan2(targetVec.y, targetVec.horizontalDistance()) * 180 / Math.PI;
            double toTurnX = Mth.wrapDegrees(player.getYRot() - targetAngleX);
            double toTurnY = Mth.wrapDegrees(player.getXRot() + targetAngleY);

            player.turn(-toTurnX, -toTurnY);
            return true;
        }
        return false;
    }

    private static void attemptEnterLockOn(Player player) {
        tabToNextEnemy(player);
        lockedOn = targeted != null;
    }

    private static void tickLockedOn() {
        list.removeIf(livingEntity -> !livingEntity.isAlive());
        if (targeted != null && !targeted.isAlive()) {
            leaveLockOn();
        }
    }

    public static Entity findNearby(Player player) {
        int range = LockOn.ClientConfig.range.get();
        TargetingConditions enemyCondition = TargetingConditions.forCombat().range(range).selector(ENTITY_PREDICATE);
        List<LivingEntity> entities = player.level
                .getNearbyEntities(LivingEntity.class, enemyCondition, player, player.getBoundingBox().inflate(range))
                .stream().filter(player::hasLineOfSight).toList();

        if (lockedOn) {
            cycle++;
            if (cycle >= list.size()) {
                cycle = 0;
            }
            return !list.isEmpty() ? list.get(cycle) : null;
        } else if (!entities.isEmpty()) {
            LivingEntity first = entities.get(0);
            list.add(first);
            return first;
        }
        return null;
    }

    private static void tabToNextEnemy(Player player) {
        targeted = findNearby(player);
    }

    private static void leaveLockOn() {
        targeted = null;
        lockedOn = false;
        list.clear();
    }

    public enum Dir {
        UP, DOWN, LEFT, RIGHT
    }

    public static void fillTriangles(VertexConsumer builder, Matrix4f matrix, float x, float y, float width, float height, float bbHeight, float z, int color) {
        float a = (color >> 24 & 0xff) / 255f;
        float r = (color >> 16 & 0xff) / 255f;
        float g = (color >> 8 & 0xff) / 255f;
        float b = (color & 0xff) / 255f;

        for (Dir dir : Dir.values()) {
            fillTriangle(builder, matrix, x, y, width, height, bbHeight, z, r, g, b, a, dir);
        }
    }

    public static void fillTriangle(VertexConsumer builder, Matrix4f matrix, float x, float y, float width, float height, float bbHeight, float z, float r, float g, float b, float a, Dir dir) {
        switch (dir) {
            case UP -> drawTriangle(builder, matrix, x, y, width, height, z, r, g, b, a, 1);
            case DOWN -> drawTriangle(builder, matrix, x, y, width, -height, z, r, g, b, a, 1);
            case LEFT -> drawTriangle(builder, matrix, x - bbHeight, y, -height, width, z, r, g, b, a, 0);
            case RIGHT -> drawTriangle(builder, matrix, x + bbHeight, y, height, width, z, r, g, b, a, 0);
        }
    }

    private static void drawTriangle(VertexConsumer builder, Matrix4f matrix, float x, float y, float width, float height, float z, float r, float g, float b, float a, int vertical) {
        builder.vertex(matrix, x, y, z).color(r, g, b, a).endVertex();
        builder.vertex(matrix, x + (width / 2) * vertical, y + height, z).color(r, g, b, a).endVertex();
        builder.vertex(matrix, x - (width / 2) * vertical, y + height, z).color(r, g, b, a).endVertex();
    }
}