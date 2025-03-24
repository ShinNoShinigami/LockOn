package com.shin.lockon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Quaternion;
import com.shin.lockon.LockOnHandler;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//thanks neat
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Shadow
    public abstract Quaternion cameraOrientation();

    /**
     * Hooks right after the main entity renderer runs.
     * Here we have a good GL state set up, the buffers are still available for fabulous mode, etc.
     * It's a much better point to render our bars than something like RenderLevelLastEvent.
     */
    @Inject(
            method = "render", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            shift = At.Shift.AFTER
    )
    )
    private void renderLockOnIcon(Entity entity, double worldX, double worldY, double worldZ, float entityYRot, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        LockOnHandler.renderWorldLast(entity, poseStack, buffers, cameraOrientation());
    }
}