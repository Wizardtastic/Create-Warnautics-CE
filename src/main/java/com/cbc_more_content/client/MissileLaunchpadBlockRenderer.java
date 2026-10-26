package com.cbc_more_content.client;

import com.cbc_more_content.block.CruiseMissileBlock;
import com.cbc_more_content.block.MissileLaunchpadBlockEntity;
import com.cbc_more_content.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MissileLaunchpadBlockRenderer implements BlockEntityRenderer<MissileLaunchpadBlockEntity> {
    private static final double DECK_Y = 8.0D / 16.0D;

    private static final double HALF_LENGTH = 1.5D;

    public MissileLaunchpadBlockRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(
            MissileLaunchpadBlockEntity pad,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay) {
        ItemStack mounted = pad.missile();
        if (mounted.isEmpty()
                || !(mounted.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof CruiseMissileBlock)) {
            return;
        }
        BlockState airframe = ModBlocks.CRUISE_MISSILE.get().defaultBlockState();

        pose.pushPose();
        pose.translate(0.5D, DECK_Y + HALF_LENGTH, 0.5D);
        pose.mulPose(Axis.ZP.rotationDegrees(-90.0f));
        pose.translate(-0.5D, -0.5D, -0.5D);

        Minecraft mc = Minecraft.getInstance();
        var renderer = mc.getBlockRenderer();
        VertexConsumer consumer = buffers.getBuffer(RenderType.cutout());
        renderer.getModelRenderer()
                .renderModel(
                        pose.last(),
                        consumer,
                        airframe,
                        renderer.getBlockModel(airframe),
                        1.0f,
                        1.0f,
                        1.0f,
                        packedLight,
                        OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(MissileLaunchpadBlockEntity pad) {
        return new AABB(pad.getBlockPos()).expandTowards(0.0D, 3.6D, 0.0D).inflate(0.5D);
    }

    @Override
    public boolean shouldRenderOffScreen(MissileLaunchpadBlockEntity pad) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
