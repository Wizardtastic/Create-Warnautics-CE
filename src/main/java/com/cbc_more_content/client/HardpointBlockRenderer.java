package com.cbc_more_content.client;

import com.cbc_more_content.block.Aim9Block;
import com.cbc_more_content.block.CruiseMissileBlock;
import com.cbc_more_content.block.DropBombBlock;
import com.cbc_more_content.block.HardpointBlock;
import com.cbc_more_content.block.HardpointBlockEntity;
import com.cbc_more_content.bomb.BombSize;
import com.cbc_more_content.item.DropBombItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class HardpointBlockRenderer implements BlockEntityRenderer<HardpointBlockEntity> {
    public HardpointBlockRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(
            HardpointBlockEntity hardpoint,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay) {
        ItemStack payload = hardpoint.payload();
        if (payload.isEmpty() || !(payload.getItem() instanceof BlockItem blockItem)) {
            return;
        }
        Block block = blockItem.getBlock();
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction identityFacing = block instanceof DropBombBlock
                    ? Direction.UP
                    : block instanceof Aim9Block
                            ? Direction.NORTH
                            : block instanceof CruiseMissileBlock
                                    ? Direction.WEST
                                    : state.getValue(BlockStateProperties.FACING);
            state = state.setValue(BlockStateProperties.FACING, identityFacing);
        }
        if (payload.getItem() instanceof DropBombItem && state.hasProperty(DropBombBlock.CASSETTE)) {
            state = state.setValue(
                    DropBombBlock.CASSETTE, DropBombBlock.clampCassette(DropBombItem.getCassette(payload)));
        }
        BlockState mount = hardpoint.getBlockState();
        Direction facing = mount.getValue(HardpointBlock.FACING);
        Direction nose = mount.hasProperty(HardpointBlock.NOSE) ? mount.getValue(HardpointBlock.NOSE) : Direction.NORTH;

        pose.pushPose();
        MountShift shift = mountShiftFor(block);
        if (facing.getAxis() == Direction.Axis.Y) {
            pose.translate(nose.getStepX() * shift.forward(), -shift.down(), nose.getStepZ() * shift.forward());
            pose.translate(0.5D, 0.5D, 0.5D);
            if (block instanceof CruiseMissileBlock) {
                rotateCruise(pose, nose);
            } else if (block instanceof Aim9Block) {
                rotateNorthIdentity(pose, nose);
            } else {
                rotateNorthIdentity(pose, nose);
                pose.mulPose(Axis.XP.rotationDegrees(-90));
                if (isMoab(block)) {
                    pose.mulPose(Axis.YP.rotationDegrees(-90));
                }
            }
            pose.translate(-0.5D, -0.5D, -0.5D);
            double tuck = flatTuckFor(block, state);
            pose.translate(0.0D, facing == Direction.UP ? tuck : -tuck, 0.0D);
        } else {
            pose.translate(facing.getStepX() * shift.forward(), -shift.down(), facing.getStepZ() * shift.forward());
            double slide = slideFor(block);
            pose.translate(facing.getStepX() * slide, facing.getStepY() * slide, facing.getStepZ() * slide);
            pose.translate(0.5D, 0.5D, 0.5D);
            if (block instanceof Aim9Block) {
                rotateNorthIdentity(pose, facing);
            } else if (block instanceof CruiseMissileBlock) {
                rotateCruise(pose, facing);
            } else {
                rotateBomb(pose, facing);
                if (isMoab(block)) {
                    pose.mulPose(Axis.YP.rotationDegrees(-90));
                }
            }
            pose.translate(-0.5D, -0.5D, -0.5D);
        }

        var renderer = Minecraft.getInstance().getBlockRenderer();
        renderer.getModelRenderer()
                .renderModel(
                        pose.last(),
                        buffers.getBuffer(RenderType.cutout()),
                        state,
                        renderer.getBlockModel(state),
                        1,
                        1,
                        1,
                        packedLight,
                        OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private static double slideFor(Block block) {
        if (block instanceof Aim9Block) {
            return 0.5 + 14.6 / 16.0 - 5.0 / 16.0;
        }
        if (block instanceof CruiseMissileBlock) {
            return 0.5 + 16.0 / 16.0 - 1.0 / 16.0;
        }
        if (block instanceof DropBombBlock bomb) {
            return switch (bomb.getBombSize()) {
                case LARGE -> 0.5 + 3.0 / 16.0;
                case MOAB -> 0.5 + 16.0 / 16.0;
                default -> 0.5;
            };
        }
        return 0.5;
    }

    private record MountShift(double down, double forward) {
        private static final MountShift NONE = new MountShift(0.0D, 0.0D);
    }

    private static boolean isMoab(Block block) {
        return block instanceof DropBombBlock bomb && bomb.getBombSize() == BombSize.MOAB;
    }

    private static MountShift mountShiftFor(Block block) {
        if (block instanceof DropBombBlock bomb) {
            return switch (bomb.getBombSize()) {
                case SMALL -> new MountShift(3.0D / 16.0D, 4.0D / 16.0D);
                case MEDIUM -> new MountShift(4.0D / 16.0D, 6.0D / 16.0D);
                case LARGE -> new MountShift(5.0D / 16.0D, 5.0D / 16.0D);
                case MOAB -> new MountShift(3.0D / 16.0D, 15.0D / 16.0D);
                default -> MountShift.NONE;
            };
        }
        return MountShift.NONE;
    }

    private static double flatTuckFor(Block block, BlockState state) {
        if (block instanceof Aim9Block) {
            return 0.375 - 5.0 / 16.0;
        }
        if (block instanceof CruiseMissileBlock) {
            return 0.25 - 1.0 / 16.0;
        }
        if (block instanceof DropBombBlock bomb) {
            if (bomb.getBombSize() == BombSize.SMALL) {
                int cassette = state.getValue(DropBombBlock.CASSETTE);
                return cassette >= 3 ? 0.559375 : 0.278125;
            }
            return switch (bomb.getBombSize()) {
                case MEDIUM -> 0.34;
                case LARGE -> 0.375625;
                case SEA -> 0.375;
                case MOAB -> 0.815625;
                default -> 0.278125;
            };
        }
        return 0.5;
    }

    private static void rotateBomb(PoseStack pose, Direction facing) {
        int x =
                switch (facing) {
                    case DOWN -> 180;
                    case UP -> 0;
                    default -> 90;
                };
        int y =
                switch (facing) {
                    case SOUTH -> 180;
                    case EAST -> 90;
                    case WEST -> 270;
                    default -> 0;
                };
        pose.mulPose(Axis.YP.rotationDegrees(-y));
        pose.mulPose(Axis.XP.rotationDegrees(-x));
    }

    private static void rotateCruise(PoseStack pose, Direction facing) {
        switch (facing) {
            case WEST -> {}
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(180));
            case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(-90));
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(-270));
            case UP -> pose.mulPose(Axis.ZP.rotationDegrees(-90));
            case DOWN -> pose.mulPose(Axis.ZP.rotationDegrees(90));
        }
    }

    private static void rotateNorthIdentity(PoseStack pose, Direction facing) {
        switch (facing) {
            case NORTH -> {}
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(-270));
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(90));
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(-90));
        }
    }

    @Override
    public boolean shouldRenderOffScreen(HardpointBlockEntity hardpoint) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
