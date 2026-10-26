package com.cbc_more_content.item;

import com.cbc_more_content.compat.RadarCompat;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class MissileLaunchpadItem extends BlockItem {
    private static final String LINK_KEY = "LaunchpadRadar";

    public MissileLaunchpadItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        return true;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos controller = RadarCompat.monitorController(level, context.getClickedPos());
        if (controller == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            setLink(stack, controller);
            level.playSound(
                    null, context.getClickedPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.7f, 1.3f);
            if (context.getPlayer() != null) {
                context.getPlayer()
                        .displayClientMessage(
                                Component.translatable(
                                        "message.cbc_more_content.launchpad.linked",
                                        controller.getX(),
                                        controller.getY(),
                                        controller.getZ()),
                                true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static ItemStack withLink(ItemStack stack, @Nullable BlockPos link) {
        ItemStack copy = stack.copy();
        setLink(copy, link);
        return copy;
    }

    private static void setLink(ItemStack stack, @Nullable BlockPos link) {
        if (link == null) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(LINK_KEY));
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(LINK_KEY, link.asLong()));
    }

    @Nullable
    public static BlockPos linkOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(LINK_KEY) ? BlockPos.of(tag.getLong(LINK_KEY)) : null;
    }

    @Override
    public void appendHoverText(
            ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BlockPos link = linkOf(stack);
        if (link != null) {
            tooltip.add(Component.translatable(
                            "tooltip.cbc_more_content.launchpad.linked", link.getX(), link.getY(), link.getZ())
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
