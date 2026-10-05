package com.cbc_more_content.block;

import com.cbc_more_content.compat.RadarCompat;
import com.cbc_more_content.compat.SableDropCompat;
import com.cbc_more_content.item.CruiseMissileItem;
import com.cbc_more_content.item.DropBombItem;
import com.cbc_more_content.item.HardpointItem;
import com.cbc_more_content.munitions.Aim9Projectile;
import com.cbc_more_content.munitions.CruiseMissileProjectile;
import com.cbc_more_content.registry.ModEntityTypes;
import com.cbc_more_content.registry.ModItems;
import com.cbc_more_content.registry.ModSounds;
import com.cbc_more_content.util.WaterPlacement;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

public class HardpointBlock extends BaseEntityBlock implements SimpleWaterloggedBlock, IWrenchable {
    public static final MapCodec<HardpointBlock> CODEC = simpleCodec(HardpointBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** Tracks the previous signal so only a rising edge releases. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final VoxelShape SHAPE_UP = Block.box(2, 0, 0, 14, 8, 16);

    private static final VoxelShape SHAPE_DOWN = Block.box(2, 8, 0, 14, 16, 16);
    private static final VoxelShape SHAPE_NORTH = Block.box(2, 0, 8, 14, 16, 16);
    private static final VoxelShape SHAPE_SOUTH = Block.box(2, 0, 0, 14, 16, 8);
    private static final VoxelShape SHAPE_EAST = Block.box(0, 0, 2, 8, 16, 14);
    private static final VoxelShape SHAPE_WEST = Block.box(8, 0, 2, 16, 16, 14);

    public HardpointBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition
                .any()
                .setValue(FACING, Direction.DOWN)
                .setValue(POWERED, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HardpointBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(
            Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockPos link = HardpointItem.linkOf(stack);
        if (link != null && level.getBlockEntity(pos) instanceof HardpointBlockEntity be) {
            be.setRadarLink(link);
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return this.defaultBlockState()
                .setValue(FACING, context.getClickedFace())
                .setValue(POWERED, DropBombBlock.isReceivingPower(level, pos))
                .setValue(WATERLOGGED, WaterPlacement.sourceAt(level, pos));
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
        boolean powered = DropBombBlock.isReceivingPower(level, pos);
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
        if (!DropBombBlock.isReceivingPower(level, pos)) {
            if (state.getValue(POWERED)) {
                level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_CLIENTS);
            }
            return;
        }
        releasePayload(level, pos, state);
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
        if (!mountable(stack) || !(level.getBlockEntity(pos) instanceof HardpointBlockEntity be) || be.hasPayload()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            ItemStack mounted = stack.copy();
            mounted.setCount(1);
            be.setPayload(mounted);
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
        if (!(level.getBlockEntity(pos) instanceof HardpointBlockEntity be) || !be.hasPayload()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack payload = be.payload();
            be.setPayload(ItemStack.EMPTY);
            player.getInventory().placeItemBackInInventory(payload);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.0f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static boolean mountable(ItemStack stack) {
        if (stack.getItem() instanceof DropBombItem bombItem) {
            return bombItem.getBlock() instanceof DropBombBlock;
        }
        return stack.getItem() instanceof CruiseMissileItem;
    }

    private static Direction releaseDirection(Direction facing) {
        return facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
    }

    private void releasePayload(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof HardpointBlockEntity be) || !be.hasPayload()) {
            return;
        }
        ItemStack payload = be.payload();
        Direction nose = releaseDirection(state.getValue(FACING));
        BlockPos link = be.radarLink();

        if (payload.getItem() instanceof DropBombItem bombItem && bombItem.getBlock() instanceof DropBombBlock bomb) {
            var launch = bomb.prepareLaunchAlongNose(level, pos, nose, bomb.getBombSize());
            bomb.launchPrepared(bomb.getBombSize(), launch);
            be.consumeOneBomb();
            return;
        }
        if (payload.getItem() instanceof CruiseMissileItem missileItem) {
            if (missileItem.getBlock() instanceof Aim9Block) {
                releaseAim9(level, pos, nose, link, be);
            } else {
                releaseCruiseMissile(level, pos, nose, link, be);
            }
        }
    }

    private static Vec3 releaseSpawn(BlockPos pos, Direction nose) {
        return Vec3.atCenterOf(pos).add(nose.getStepX(), nose.getStepY(), nose.getStepZ());
    }

    private static void releaseCruiseMissile(
            ServerLevel level, BlockPos pos, Direction nose, @Nullable BlockPos link, HardpointBlockEntity be) {
        Vec3 heading = new Vec3(nose.getStepX(), nose.getStepY(), nose.getStepZ());
        var frame = SableDropCompat.resolveLaunch(level, pos, releaseSpawn(pos, nose), Vec3.ZERO, heading);
        CruiseMissileProjectile missile = ModEntityTypes.CRUISE_MISSILE.get().create(frame.level());
        if (missile == null) {
            return;
        }

        missile.setTargetEntity(relayedTarget(level, link));

        missile.setPos(frame.pos());
        missile.launch(frame.orientation(), frame.vel());
        frame.level().addFreshEntity(missile);
        frame.level()
                .playSound(
                        null,
                        frame.pos().x,
                        frame.pos().y,
                        frame.pos().z,
                        ModSounds.CRUISE_MISSILE_LAUNCH.get(),
                        SoundSource.BLOCKS,
                        4.0f,
                        1.0f);
        be.setPayload(ItemStack.EMPTY);
    }

    private static void releaseAim9(
            ServerLevel level, BlockPos pos, Direction nose, @Nullable BlockPos link, HardpointBlockEntity be) {
        Vec3 heading = new Vec3(nose.getStepX(), nose.getStepY(), nose.getStepZ());
        var frame = SableDropCompat.resolveLaunch(level, pos, releaseSpawn(pos, nose), Vec3.ZERO, heading);
        Aim9Projectile missile = ModEntityTypes.AIM9.get().create(frame.level());
        if (missile == null) {
            return;
        }
        missile.setPos(frame.pos());
        missile.launch(relayedTarget(level, link), frame.vel(), heading);
        frame.level().addFreshEntity(missile);
        frame.level()
                .playSound(
                        null,
                        frame.pos().x,
                        frame.pos().y,
                        frame.pos().z,
                        SoundEvents.FIREWORK_ROCKET_LAUNCH,
                        SoundSource.HOSTILE,
                        1.5f,
                        0.65f);
        be.setPayload(ItemStack.EMPTY);
    }

    @Nullable
    private static Entity relayedTarget(ServerLevel level, @Nullable BlockPos link) {
        if (link == null || !RadarCompat.loaded()) {
            return null;
        }
        String contact = RadarCompat.selectedContact(level, link);
        if (contact != null) {
            try {
                Entity tracked = level.getEntity(UUID.fromString(contact));
                if (tracked != null && tracked.isAlive()) {
                    return tracked;
                }
            } catch (IllegalArgumentException ignored) {
                // Synthetic track ids are not UUIDs; fall through to the position.
            }
        }
        Vec3 at = RadarCompat.selectedTargetPos(level, link);
        if (at == null) {
            return null;
        }
        Entity nearest = null;
        double best = 4.0D;
        for (Entity candidate : level.getEntitiesOfClass(
                Entity.class, new AABB(at, at).inflate(2.0D), e -> e.isAlive() && !(e instanceof Player))) {
            double distance = candidate.position().distanceToSqr(at);
            if (distance < best) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()
                && level instanceof ServerLevel server
                && server.getBlockEntity(pos) instanceof HardpointBlockEntity be
                && be.hasPayload()) {
            server.addFreshEntity(
                    new ItemEntity(server, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, be.payload()));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case UP -> SHAPE_UP;
            case DOWN -> SHAPE_DOWN;
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
        };
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        Direction facing = state.getValue(FACING);
        Direction.Axis axis = face.getAxis();
        if (axis == facing.getAxis()) {
            axis = axis == Direction.Axis.Y ? Direction.Axis.X : Direction.Axis.Y;
        }
        BlockState rotated = state.setValue(FACING, facing.getClockWise(axis));
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        level.setBlock(pos, rotated, Block.UPDATE_ALL);
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
            ItemStack drop = new ItemStack(ModItems.HARDPOINT.get());
            if (server.getBlockEntity(pos) instanceof HardpointBlockEntity be) {
                drop = HardpointItem.withLink(drop, be.radarLink());
            }
            player.getInventory().placeItemBackInInventory(drop);
        }
        server.destroyBlock(pos, false);
        AllSoundEvents.WRENCH_REMOVE.playOnServer(
                server, pos, 1.0f, server.getRandom().nextFloat() * 0.5f + 0.5f);
        return InteractionResult.SUCCESS;
    }
}
