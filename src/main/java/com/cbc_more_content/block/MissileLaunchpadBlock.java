package com.cbc_more_content.block;

import com.cbc_more_content.block.CruiseMissileBlockEntity.Guidance;
import com.cbc_more_content.compat.RadarCompat;
import com.cbc_more_content.compat.SableDropCompat;
import com.cbc_more_content.item.CruiseMissileItem;
import com.cbc_more_content.item.MissileLaunchpadItem;
import com.cbc_more_content.munitions.CruiseMissileProjectile;
import com.cbc_more_content.registry.ModBlockEntities;
import com.cbc_more_content.registry.ModEntityTypes;
import com.cbc_more_content.registry.ModItems;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

public class MissileLaunchpadBlock extends BaseEntityBlock implements IWrenchable {
    public static final MapCodec<MissileLaunchpadBlock> CODEC = simpleCodec(MissileLaunchpadBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public static final int DEFAULT_RANGE = 48;

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 5.0D, 16.0D);

    public MissileLaunchpadBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MissileLaunchpadBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(
                type, ModBlockEntities.MISSILE_LAUNCHPAD.get(), MissileLaunchpadBlockEntity::serverTick);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    public void setPlacedBy(
            Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level.getBlockEntity(pos) instanceof MissileLaunchpadBlockEntity be)) {
            return;
        }
        BlockPos link = MissileLaunchpadItem.linkOf(stack);
        if (link != null) {
            be.setRadarLink(link);
        }
        if (be.target() == null) {
            be.setTarget(pos.relative(state.getValue(FACING), DEFAULT_RANGE));
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (!mountable(stack)
                || !(level.getBlockEntity(pos) instanceof MissileLaunchpadBlockEntity be)
                || be.hasMissile()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            ItemStack mounted = stack.copy();
            mounted.setCount(1);
            be.setMissile(mounted);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.1f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MissileLaunchpadBlockEntity be) || !be.hasMissile()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack missile = be.missile();
            be.setMissile(ItemStack.EMPTY);
            player.getInventory().placeItemBackInInventory(missile);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.0f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static boolean mountable(ItemStack stack) {
        return stack.getItem() instanceof CruiseMissileItem missileItem
                && missileItem.getBlock() instanceof CruiseMissileBlock;
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston) {
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        boolean wasPowered = state.getValue(POWERED);
        if (powered != wasPowered) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
        if (powered && !wasPowered) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.hasNeighborSignal(pos)) {
            launch(level, pos, state);
        } else if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_CLIENTS);
        }
    }

    public static Result launch(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof MissileLaunchpadBlockEntity be) || !be.hasMissile()) {
            return Result.EMPTY;
        }

        Guidance guidance = Guidance.COORDINATES;
        BlockPos aim = null;
        BlockPos controller = null;
        String contact = null;
        UUID hull = null;
        Entity tracked = null;

        if (be.mode() == MissileLaunchpadBlockEntity.Mode.RADAR) {
            BlockPos link = be.radarLink();
            if (link == null) {
                return Result.NO_RADAR;
            }
            contact = RadarCompat.selectedContact(level, link);
            Vec3 point = null;
            if (contact != null) {
                hull = hullOf(level, contact);
                if (hull != null) {
                    point = SableDropCompat.subLevelCentreByUuid(level, hull);
                } else {
                    tracked = entityOf(level, contact);
                    if (tracked != null) {
                        point = tracked.position();
                    }
                }
            }
            if (point == null) {
                point = RadarCompat.selectedTargetPos(level, link);
            }
            if (point == null) {
                return Result.NO_TARGET;
            }
            aim = BlockPos.containing(point);
            if (hull != null) {
                guidance = Guidance.INTERCEPT;
                controller = link;
            }
        } else {
            aim = be.target();
            if (aim == null) {
                return Result.NO_TARGET;
            }
        }

        Vec3 lift = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 spawn = Vec3.atCenterOf(pos).add(0.0D, 1.5D, 0.0D);
        var frame = SableDropCompat.resolveLaunch(level, pos, spawn, Vec3.ZERO, lift);
        CruiseMissileProjectile missile = ModEntityTypes.CRUISE_MISSILE.get().create(frame.level());
        if (missile == null) {
            return Result.EMPTY;
        }

        missile.setGuidance(guidance, aim, -1);
        if (controller != null) {
            missile.setController(controller);
            missile.setContact(contact);
        }
        if (tracked != null) {
            missile.setTargetEntity(tracked);
        }
        missile.setPos(frame.pos());
        missile.igniteOnPad(frame.orientation() == null ? lift : frame.orientation(), frame.vel());
        frame.level().addFreshEntity(missile);

        be.setMissile(ItemStack.EMPTY);
        return Result.LAUNCHED;
    }

    public static boolean hasSelectedTarget(ServerLevel level, BlockPos link) {
        if (!RadarCompat.loaded()) {
            return false;
        }
        return RadarCompat.selectedContact(level, link) != null || RadarCompat.selectedTargetPos(level, link) != null;
    }

    @Nullable
    private static UUID hullOf(ServerLevel level, String contact) {
        try {
            UUID ship = UUID.fromString(contact);
            return SableDropCompat.subLevelByUuid(level, ship) == null ? null : ship;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    private static Entity entityOf(ServerLevel level, String contact) {
        try {
            Entity tracked = level.getEntity(UUID.fromString(contact));
            return tracked != null && tracked.isAlive() ? tracked : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()
                && level instanceof ServerLevel server
                && server.getBlockEntity(pos) instanceof MissileLaunchpadBlockEntity be
                && be.hasMissile()) {
            ItemStack missile = be.missile();
            server.addFreshEntity(
                    new ItemEntity(server, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, missile));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        level.setBlock(
                pos, state.setValue(FACING, state.getValue(FACING).getClockWise(Direction.Axis.Y)), Block.UPDATE_ALL);
        IWrenchable.playRotateSound(level, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }

        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(server, pos, state, player);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            return InteractionResult.SUCCESS;
        }

        if (player != null && !player.isCreative()) {
            ItemStack drop = new ItemStack(ModItems.MISSILE_LAUNCHPAD.get());
            if (server.getBlockEntity(pos) instanceof MissileLaunchpadBlockEntity be) {
                drop = MissileLaunchpadItem.withLink(drop, be.radarLink());
            }
            player.getInventory().placeItemBackInInventory(drop);
        }
        server.destroyBlock(pos, false);
        AllSoundEvents.WRENCH_REMOVE.playOnServer(
                server, pos, 1.0f, server.getRandom().nextFloat() * 0.5f + 0.5f);
        return InteractionResult.SUCCESS;
    }

    public enum Result {
        EMPTY,
        NO_RADAR,
        NO_TARGET,
        LAUNCHED
    }
}
