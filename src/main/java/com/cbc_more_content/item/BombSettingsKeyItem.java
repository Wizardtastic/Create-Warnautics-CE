package com.cbc_more_content.item;

import com.cbc_more_content.block.Aim9Block;
import com.cbc_more_content.block.C4Block;
import com.cbc_more_content.block.CruiseMissileBlock;
import com.cbc_more_content.block.CruiseMissileBlockEntity;
import com.cbc_more_content.block.DropBombBlock;
import com.cbc_more_content.block.HardpointBlock;
import com.cbc_more_content.block.HardpointBlockEntity;
import com.cbc_more_content.block.MissileLaunchpadBlock;
import com.cbc_more_content.block.SirenBlock;
import com.cbc_more_content.compat.RadarCompat;
import com.cbc_more_content.network.OpenRadarSettingsPayload;
import com.cbc_more_content.radar.InterceptSettingsStore;
import com.cbc_more_content.util.ReflectiveDispatcher;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public class BombSettingsKeyItem extends Item {
    public BombSettingsKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        ItemStack key = context.getItemInHand();

        var radarBlockEntity = level.getBlockEntity(pos);
        if (RadarCompat.isRadarModBlock(radarBlockEntity)) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            Player player = context.getPlayer();
            if (player != null && player.isShiftKeyDown()) {
                openRadarSettings(level, pos, player);
                return InteractionResult.CONSUME;
            }
            setBindingSet(key, pos);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.8f, 1.7f);
            say(context.getPlayer(), "message.cbc_more_content.key.bind_mode", ChatFormatting.AQUA);
            return InteractionResult.CONSUME;
        }

        BlockPos bindingSet = bindingSet(key);
        if (bindingSet != null && state.getBlock() instanceof CruiseMissileBlock) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            BlockPos body = CruiseMissileBlock.bodyOf(state, pos);
            if (!(level.getBlockEntity(body) instanceof CruiseMissileBlockEntity guidance)) {
                return InteractionResult.PASS;
            }
            if (guidance.guidance() != CruiseMissileBlockEntity.Guidance.INTERCEPT) {
                say(context.getPlayer(), "message.cbc_more_content.key.not_intercept", ChatFormatting.RED);
                return InteractionResult.CONSUME;
            }
            boolean rebound = guidance.bindController(bindingSet);
            level.playSound(
                    null, body, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, rebound ? 1.1f : 1.5f);
            say(
                    context.getPlayer(),
                    rebound ? "message.cbc_more_content.key.rebound" : "message.cbc_more_content.key.bound",
                    ChatFormatting.GREEN);
            return InteractionResult.CONSUME;
        }

        if (state.getBlock() instanceof Aim9Block) {
            if (level.isClientSide) {
                ReflectiveDispatcher.invoke(
                        "com.cbc_more_content.client.gui.Aim9SettingsClient",
                        "open",
                        new Class<?>[] {BlockPos.class},
                        Aim9Block.bodyOf(state, pos));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (state.getBlock() instanceof SirenBlock) {
            if (level.isClientSide) {
                openSirenScreen(pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (state.getBlock() instanceof C4Block) {
            if (level.isClientSide) {
                openC4CodeScreen(pos, state.getValue(C4Block.STATE) != C4Block.Fuse.IDLE);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (state.getBlock() instanceof MissileLaunchpadBlock) {
            if (level.isClientSide) {
                openLaunchpadScreen(pos);
            } else {
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.5f, 1.4f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (state.getBlock() instanceof HardpointBlock) {
            if (level.getBlockEntity(pos) instanceof HardpointBlockEntity be) {
                openBundleDial(level, pos, be);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return InteractionResult.PASS;
        }
        if (!(state.getBlock() instanceof DropBombBlock bomb) || !bomb.allowsCassette()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            openScreen(pos, state.getValue(DropBombBlock.RELEASE_DELAY), state.getValue(DropBombBlock.CASSETTE), false);
        } else {
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.5f, 1.4f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack key = player.getItemInHand(hand);
        if (!level.isClientSide && player.isShiftKeyDown() && bindingSet(key) != null) {
            key.remove(DataComponents.CUSTOM_DATA);
            say(player, "message.cbc_more_content.key.bind_cancel", ChatFormatting.GRAY);
        }
        return InteractionResultHolder.sidedSuccess(key, level.isClientSide());
    }

    private static void openRadarSettings(Level level, BlockPos controller, Player player) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer viewer)) {
            return;
        }
        var settings = InterceptSettingsStore.get(server).forController(controller);
        PacketDistributor.sendToPlayer(viewer, new OpenRadarSettingsPayload(controller, settings));
    }

    @Nullable
    public static BlockPos bindingSet(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag().getCompound("BindingSet");
        return tag.contains("X") ? new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")) : null;
    }

    private static void setBindingSet(ItemStack stack, BlockPos set) {
        CompoundTag inner = new CompoundTag();
        inner.putInt("X", set.getX());
        inner.putInt("Y", set.getY());
        inner.putInt("Z", set.getZ());
        CompoundTag root = new CompoundTag();
        root.put("BindingSet", inner);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static void say(@Nullable Player player, String key, ChatFormatting colour) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key).withStyle(colour), true);
        }
    }

    /**
     * The dial is only worth opening where a small bomb pack is mounted — its interval
     * is the only thing the dial sets. A double or triple rail carrying nothing else
     * has one question instead: fire every loaded rail at once, or only one per pulse.
     */
    public void openBundleDial(Level level, BlockPos pos, HardpointBlockEntity hardpoint) {
        ItemStack pack = hardpoint.firstSmallBombPack();
        boolean multiRail =
                hardpoint.getBlockState().getBlock() instanceof HardpointBlock block && block.slotCount() > 1;
        if (level.isClientSide) {
            if (pack != null) {
                openScreen(pos, hardpoint.releaseDelayTicks(), DropBombItem.getCassette(pack), true);
            } else if (multiRail) {
                openModeScreen(pos);
            }
            return;
        }
        if (pack != null || multiRail) {
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.5f, 1.4f);
        }
    }

    private static void openModeScreen(BlockPos pos) {
        ReflectiveDispatcher.invoke(
                "com.cbc_more_content.client.gui.HardpointModeClient", "open", new Class<?>[] {BlockPos.class}, pos);
    }

    private static void openScreen(BlockPos pos, int storedDelay, int cassette, boolean allowAllAtOnce) {
        ReflectiveDispatcher.invoke(
                "com.cbc_more_content.client.gui.BombSettingsClient",
                "open",
                new Class<?>[] {BlockPos.class, int.class, int.class, boolean.class},
                pos,
                storedDelay,
                cassette,
                allowAllAtOnce);
    }

    private static void openLaunchpadScreen(BlockPos pos) {
        ReflectiveDispatcher.invoke(
                "com.cbc_more_content.client.gui.MissileLaunchpadClient", "open", new Class<?>[] {BlockPos.class}, pos);
    }

    private static void openSirenScreen(BlockPos pos) {
        ReflectiveDispatcher.invoke(
                "com.cbc_more_content.client.gui.SirenSettingsClient", "open", new Class<?>[] {BlockPos.class}, pos);
    }

    private static void openC4CodeScreen(BlockPos pos, boolean disarming) {
        ReflectiveDispatcher.invoke(
                "com.cbc_more_content.client.gui.C4CodeClient",
                "open",
                new Class<?>[] {BlockPos.class, boolean.class},
                pos,
                disarming);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (bindingSet(stack) != null) {
            tooltip.add(Component.translatable("message.cbc_more_content.key.bind_mode")
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
