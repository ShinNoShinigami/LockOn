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

            poseStack.translate(0, entity.getBbHeight()/2, 0);

            poseStack.mulPose(quaternion);


            float rotate = (Util.getNanos() /-8_000_000f);

            poseStack.mulPose(Vector3f.ZP.rotationDegrees(rotate));


            float w = (float)(double)LockOn.ClientConfig.width.get();float h = (float)(double)LockOn.ClientConfig.height.get();

            RenderSystem.disableCull();
            fillTriangle(builder,poseStack.last().pose(),0,entity.getBbHeight() / 2f, -w/2f, h,entity.getBbHeight() / 2f,0);
            poseStack.popPose();
            //     RenderSystem.enableCull();
        }
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.START) {
            while (LOCK_ON.consumeClick()) {
                if (lockedOn) {
                    leaveLockOn();
                } else {
                    attemptEnterLockOn(Minecraft.getInstance().player);
                }
            }

            while (TAB.consumeClick()) {
                tabToNextEnemy(Minecraft.getInstance().player);
            }
            tickLockedOn();
        }
    }

    private static void logOff(ClientPlayerNetworkEvent.LoggingOut e) {
        leaveLockOn();
    }

    public static boolean lockedOn;
    private static Entity targeted;

    public static boolean lockY = true;

    public static boolean handleKeyPress(Player player, double d2, double d3) {
        if (player != null && !mc.isPaused()) {
            if (targeted != null) {
                Vec3 targetPos = targeted.position().add(0,targeted.getEyeHeight(),0);
                Vec3 targetVec = targetPos.subtract(player.position().add(0,player.getEyeHeight(),0)).normalize();
                double targetAngleX = Mth.wrapDegrees(Math.atan2(-targetVec.x, targetVec.z) * 180 / Math.PI);
                double targetAngleY = Math.atan2(targetVec.y , targetVec.horizontalDistance()) * 180 / Math.PI;
                double xRot = Mth.wrapDegrees(player.getXRot());
                double yRot = Mth.wrapDegrees(player.getYRot());
                double toTurnX = Mth.wrapDegrees(yRot - targetAngleX);
                double toTurnY = Mth.wrapDegrees(xRot + targetAngleY);

                player.turn(-toTurnX,-toTurnY);
                return true;
            }
        }
        return false;
    }

    private static void attemptEnterLockOn(Player player) {
        tabToNextEnemy(player);
        if (targeted != null) {
            lockedOn = true;
        }
    }

    private static void tickLockedOn() {
        list.removeIf(livingEntity -> !livingEntity.isAlive());
        if (targeted != null && !targeted.isAlive()) {
            targeted = null;
            lockedOn = false;
        }
    }

    private static final Predicate<LivingEntity> ENTITY_PREDICATE = entity -> entity instanceof LivingEntity && !entity.isInvisible();

    private static int cycle = -1;

    public static Entity findNearby(Player player) {

        int r = LockOn.ClientConfig.range.get();

        final TargetingConditions ENEMY_CONDITION = TargetingConditions.forCombat().range(r).selector(ENTITY_PREDICATE);

        List<LivingEntity> entities = player.level
                .getNearbyEntities(LivingEntity.class, ENEMY_CONDITION, player, player.getBoundingBox().inflate(r)).stream().filter(player::hasLineOfSight).toList();
        if (lockedOn) {
            cycle++;
            for (LivingEntity entity : entities) {
                if (!list.contains(entity)) {
                    list.add(entity);
                    return entity;
                }
            }

           //cycle existing entity
            if (cycle >= list.size()) {
                cycle = 0;
            }
            return list.get(cycle);
        } else {
            if (!entities.isEmpty()) {
                list.add(entities.get(0));
                return entities.get(0);
            } else {
                return null;
            }
        }
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
        up,down,left,right;
    }


    public static void fillTriangle(VertexConsumer builder, Matrix4f matrix4f, float x, float y, float width, float height,float bbHeight, float z) {
        String colorHex = LockOn.ClientConfig.color.get().substring(1);
        int r = Integer.valueOf(colorHex.substring(0, 2), 16);
        int g = Integer.valueOf(colorHex.substring(2, 4), 16);
        int b = Integer.valueOf(colorHex.substring(4, 6), 16);
        int a = Integer.valueOf(colorHex.substring(6, 8), 16);

        for (Dir dir : Dir.values()) {
            fillTriangle(builder, matrix4f, x, y, width, height, bbHeight, z, r, g, b, a, dir);
        }
    }

    public static void fillTriangle(VertexConsumer builder, Matrix4f matrix, float x, float y, float width, float height, float bbHeight, float z, float r, float g, float b, float a, Dir dir) {
        switch (dir) {
            case up -> drawTriangle(builder, matrix, x, y, width, height, z, r, g, b, a, 1);
            case down -> drawTriangle(builder, matrix, x, y, width, -height, z, r, g, b, a, 1);
            case left -> drawTriangle(builder, matrix, x - bbHeight, y, -height, width, z, r, g, b, a, 0);
            case right -> drawTriangle(builder, matrix, x + bbHeight, y, height, width, z, r, g, b, a, 0);
        }
    }

    private static void drawTriangle(VertexConsumer builder, Matrix4f matrix, float x, float y, float width, float height, float z, float r, float g, float b, float a, int vertical) {
        builder.vertex(matrix, x, y, z).color(r, g, b, a).endVertex();
        builder.vertex(matrix, x + (width / 2) * vertical, y + height, z).color(r, g, b, a).endVertex();
        builder.vertex(matrix, x - (width / 2) * vertical, y + height, z).color(r, g, b, a).endVertex();
    }
}
