package com.cbc_more_content.block;

import com.cbc_more_content.bomb.BombSize;
import com.cbc_more_content.compat.RadarCompat;
import com.cbc_more_content.compat.SableDropCompat;
import com.cbc_more_content.config.WarnauticsConfig;
import com.cbc_more_content.item.BombSettingsKeyItem;
import com.cbc_more_content.item.CruiseMissileItem;
import com.cbc_more_content.item.DropBombItem;
import com.cbc_more_content.item.HardpointItem;
import com.cbc_more_content.munitions.Aim9Projectile;
import com.cbc_more_content.munitions.CruiseMissileProjectile;
import com.cbc_more_content.registry.ModEntityTypes;
import com.cbc_more_content.registry.ModSounds;
import com.cbc_more_content.util.DropGraceVisibility;
import com.cbc_more_content.util.WaterPlacement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
    public static final MapCodec<HardpointBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    propertiesCodec(), Codec.INT.fieldOf("slots").forGetter(HardpointBlock::slotCount))
            .apply(instance, HardpointBlock::new));
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final DirectionProperty NOSE = DirectionProperty.create("nose", Direction.Plane.HORIZONTAL);

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final VoxelShape SHAPE_UP = Block.box(2, 0, 0, 14, 8, 16);

    private static final VoxelShape SHAPE_DOWN = Block.box(2, 8, 0, 14, 16, 16);
    private static final VoxelShape SHAPE_NORTH = Block.box(2, 0, 8, 14, 16, 16);
    private static final VoxelShape SHAPE_SOUTH = Block.box(2, 0, 0, 14, 16, 8);
    private static final VoxelShape SHAPE_EAST = Block.box(0, 0, 2, 8, 16, 14);
    private static final VoxelShape SHAPE_WEST = Block.box(8, 0, 2, 16, 16, 14);

    private static final VoxelShape RAIL_SHAPE_UP = Block.box(2, 0, 0, 14, 5, 16);

    private static final VoxelShape RAIL_SHAPE_DOWN = Block.box(2, 11, 0, 14, 16, 16);
    private static final VoxelShape RAIL_SHAPE_NORTH = Block.box(2, 0, 11, 14, 16, 16);
    private static final VoxelShape RAIL_SHAPE_SOUTH = Block.box(2, 0, 0, 14, 16, 5);
    private static final VoxelShape RAIL_SHAPE_EAST = Block.box(0, 0, 2, 5, 16, 14);
    private static final VoxelShape RAIL_SHAPE_WEST = Block.box(11, 0, 2, 16, 16, 14);

    private static final Vec3 DROP_HEADING = new Vec3(0.0D, -1.0D, 0.0D);

    private static final Direction[] DROP_ORDER = {
        Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
    };

    private final int slotCount;

    public HardpointBlock(Properties properties) {
        this(properties, 1);
    }

    public HardpointBlock(Properties properties, int slotCount) {
        super(properties);
        this.slotCount = Math.max(1, slotCount);
        this.registerDefaultState(this.stateDefinition
                .any()
                .setValue(FACING, Direction.DOWN)
                .setValue(NOSE, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public int slotCount() {
        return this.slotCount;
    }

    public static final double RAIL_SPACING = 8.0D / 16.0D;

    public static double railOffset(int slot, int slots) {
        if (slots <= 1) {
            return 0.0D;
        }
        double step = RAIL_SPACING / (slots - 1);
        return (slot - (slots - 1) / 2.0D) * step;
    }

    public static Direction lateral(BlockState state) {
        Direction facing = state.getValue(FACING);
        return (facing.getAxis() == Direction.Axis.Y ? state.getValue(NOSE) : facing).getClockWise();
    }

    public boolean acceptsPayload(ItemStack stack) {
        if (!mountable(stack)) {
            return false;
        }
        return this.slotCount == 1 || !tooHeavyForRails(stack);
    }

    private static boolean isCruiseMissile(ItemStack stack) {
        return stack.getItem() instanceof CruiseMissileItem missileItem
                && missileItem.getBlock() instanceof CruiseMissileBlock;
    }

    private static boolean tooHeavyForRails(ItemStack stack) {
        if (isCruiseMissile(stack)) {
            return true;
        }
        if (stack.getItem() instanceof DropBombItem bombItem && bombItem.getBlock() instanceof DropBombBlock bomb) {
            BombSize size = bomb.getBombSize();
            return size == BombSize.LARGE || size == BombSize.MOAB;
        }
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, NOSE, POWERED, WATERLOGGED);
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
        Direction clicked = context.getClickedFace();
        return this.defaultBlockState()
                .setValue(FACING, clicked)
                .setValue(NOSE, noseForPlacement(context, clicked))
                .setValue(POWERED, DropBombBlock.isReceivingPower(level, pos))
                .setValue(WATERLOGGED, WaterPlacement.sourceAt(level, pos));
    }

    private static Direction noseForPlacement(BlockPlaceContext context, Direction clicked) {
        if (clicked.getAxis() != Direction.Axis.Y) {
            return clicked;
        }
        Direction look = context.getHorizontalDirection();
        return look.getAxis() == Direction.Axis.Y ? Direction.NORTH : look;
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
        if (level.getBlockEntity(pos) instanceof HardpointBlockEntity be && be.isReleasing()) {
            releasePayload(level, pos);
            return;
        }
        if (!DropBombBlock.isReceivingPower(level, pos)) {
            if (state.getValue(POWERED)) {
                level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_CLIENTS);
            }
            return;
        }
        releasePayload(level, pos);
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
        if (stack.getItem() instanceof BombSettingsKeyItem key) {
            if (!(level.getBlockEntity(pos) instanceof HardpointBlockEntity be)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            key.openBundleDial(level, pos, be);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!acceptsPayload(stack)
                || !(level.getBlockEntity(pos) instanceof HardpointBlockEntity be)
                || be.firstEmptySlot() < 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            ItemStack mounted = stack.copy();
            mounted.setCount(1);
            be.mount(mounted);
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
            for (ItemStack payload : be.removePayloads()) {
                player.getInventory().placeItemBackInInventory(payload);
            }
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

    private void releasePayload(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof HardpointBlockEntity be)) {
            return;
        }
        if (!be.hasPayload()) {
            be.clearRolling();
            return;
        }
        if (be.isReleasing()) {
            releaseRollingSlots(level, pos, be);
            return;
        }
        if (be.releaseAllAtOnce()) {
            for (int slot = 0; slot < be.slotCount(); slot++) {
                if (!be.payloadIn(slot).isEmpty()) {
                    releaseSlot(level, pos, be, slot);
                }
            }
            return;
        }
        int slot = be.firstOccupiedSlot();
        if (slot >= 0) {
            releaseSlot(level, pos, be, slot);
        }
    }

    private void releaseRollingSlots(ServerLevel level, BlockPos pos, HardpointBlockEntity be) {
        boolean anyRemaining = false;
        for (int slot = 0; slot < be.slotCount(); slot++) {
            if (!be.isSlotRolling(slot)) {
                continue;
            }
            ItemStack payload = be.payloadIn(slot);
            if (!(payload.getItem() instanceof DropBombItem bombItem)
                    || !(bombItem.getBlock() instanceof DropBombBlock bomb)) {
                be.setSlotRolling(slot, false);
                continue;
            }
            BombSize size = bomb.getBombSize();
            var launch = prepareDrop(
                    level,
                    pos,
                    size.entitySize * 0.5D,
                    size.entitySize * 0.5D,
                    size.launchDown * WarnauticsConfig.releaseImpulse(),
                    DROP_HEADING,
                    slot,
                    be.slotCount());
            DropGraceVisibility.grant(bomb.launchPrepared(size, launch));
            be.consumeOneBomb(slot);
            if (be.payloadIn(slot).isEmpty()) {
                be.setSlotRolling(slot, false);
            } else {
                anyRemaining = true;
            }
        }
        if (anyRemaining) {
            level.scheduleTick(pos, this, be.releaseDelayTicks());
        }
    }

    private void releaseSlot(ServerLevel level, BlockPos pos, HardpointBlockEntity be, int slot) {
        ItemStack payload = be.payloadIn(slot);
        BlockPos link = be.radarLink();

        if (payload.getItem() instanceof DropBombItem bombItem && bombItem.getBlock() instanceof DropBombBlock bomb) {
            releaseBundle(level, pos, be, slot, bomb);
            return;
        }
        if (payload.getItem() instanceof CruiseMissileItem missileItem) {
            if (missileItem.getBlock() instanceof Aim9Block) {
                releaseAim9(level, pos, link, be, slot);
            } else {
                releaseCruiseMissile(level, pos, link, be, slot);
            }
        }
    }

    private void releaseBundle(ServerLevel level, BlockPos pos, HardpointBlockEntity be, int slot, DropBombBlock bomb) {
        boolean staggered = be.releaseDelayTicks() > 0;
        int count = staggered ? 1 : DropBombItem.getCassette(be.payloadIn(slot));
        BombSize size = bomb.getBombSize();

        for (int released = 0; released < count; released++) {
            var launch = prepareDrop(
                    level,
                    pos,
                    size.entitySize * 0.5D,
                    size.entitySize * 0.5D,
                    size.launchDown * WarnauticsConfig.releaseImpulse(),
                    DROP_HEADING,
                    slot,
                    be.slotCount());
            if (count > 1) {
                launch = new SableDropCompat.LaunchFrame(
                        launch.level(),
                        launch.pos()
                                .add(
                                        (level.getRandom().nextDouble() - 0.5D) * 0.24D,
                                        0.0D,
                                        (level.getRandom().nextDouble() - 0.5D) * 0.24D),
                        launch.vel(),
                        launch.orientation());
            }
            DropGraceVisibility.grant(bomb.launchPrepared(size, launch));
        }

        if (staggered) {
            be.consumeOneBomb(slot);
            if (!be.payloadIn(slot).isEmpty()) {
                be.setSlotRolling(slot, true);
                level.scheduleTick(pos, this, be.releaseDelayTicks());
            }
            return;
        }
        be.clearSlot(slot);
    }

    private static SableDropCompat.LaunchFrame prepareDrop(
            ServerLevel level,
            BlockPos pos,
            double halfWidth,
            double halfHeight,
            double separation,
            Vec3 heading,
            int slot,
            int slots) {
        Vec3 spawn = dropSpawn(level, pos, halfWidth, halfHeight, railOffset(slot, slots));
        return SableDropCompat.resolveLaunch(level, pos, spawn, new Vec3(0.0D, -separation, 0.0D), heading);
    }

    public static Direction releaseNose(BlockState state) {
        Direction facing = state.getValue(FACING);
        return facing.getAxis() == Direction.Axis.Y ? state.getValue(NOSE) : facing;
    }

    private static Vec3 releaseHeading(ServerLevel level, BlockPos pos) {
        return Vec3.atLowerCornerOf(releaseNose(level.getBlockState(pos)).getNormal());
    }

    private static Vec3 dropSpawn(
            ServerLevel level, BlockPos pos, double halfWidth, double halfHeight, double lateralOffset) {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 shift = Vec3.ZERO;
        if (lateralOffset != 0.0D) {
            Direction lateral = lateral(level.getBlockState(pos));
            shift = new Vec3(lateral.getStepX() * lateralOffset, 0.0D, lateral.getStepZ() * lateralOffset);
        }
        Vec3 fallback = null;
        for (Direction dir : DROP_ORDER) {
            Vec3 candidate =
                    center.add(dir.getStepX(), dir.getStepY(), dir.getStepZ()).add(shift);
            AABB box = new AABB(candidate, candidate).inflate(halfWidth, halfHeight, halfWidth);
            if (fallback == null) {
                fallback = candidate;
            }
            if (level.noCollision(box)) {
                return candidate;
            }
        }
        return fallback == null ? center.add(shift) : fallback;
    }

    private static void releaseCruiseMissile(
            ServerLevel level, BlockPos pos, @Nullable BlockPos link, HardpointBlockEntity be, int slot) {
        Vec3 heading = releaseHeading(level, pos);
        var frame = prepareDrop(level, pos, 0.5D, 0.4D, 0.0D, heading, slot, be.slotCount());
        CruiseMissileProjectile missile = ModEntityTypes.CRUISE_MISSILE.get().create(frame.level());
        if (missile == null) {
            return;
        }

        UUID hull = relayedHull(level, link);
        if (hull != null) {
            missile.setGuidance(CruiseMissileBlockEntity.Guidance.INTERCEPT, null, -1);
            missile.setController(link);
            missile.setContact(relayedContact(level, link));
        } else {
            missile.setTargetEntity(relayedTarget(level, link));
        }

        missile.setPos(frame.pos());
        Vec3 forward = frame.orientation() == null ? heading : frame.orientation();
        missile.drop(forward, frame.vel());
        frame.level().addFreshEntity(missile);
        DropGraceVisibility.grant(missile);
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
        be.clearSlot(slot);
    }

    private static void releaseAim9(
            ServerLevel level, BlockPos pos, @Nullable BlockPos link, HardpointBlockEntity be, int slot) {
        Vec3 heading = releaseHeading(level, pos);
        var frame = prepareDrop(level, pos, 0.12D, 0.12D, 0.0D, heading, slot, be.slotCount());
        Aim9Projectile missile = ModEntityTypes.AIM9.get().create(frame.level());
        if (missile == null) {
            return;
        }
        missile.setPos(frame.pos());
        Vec3 forward = frame.orientation() == null ? heading : frame.orientation();
        UUID hull = relayedHull(level, link);
        if (hull != null) {
            missile.lockHull(hull);
        }
        missile.drop(hull == null ? relayedTarget(level, link) : null, frame.vel(), forward);
        frame.level().addFreshEntity(missile);
        DropGraceVisibility.grant(missile);
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
        be.clearSlot(slot);
    }

    @Nullable
    private static String relayedContact(ServerLevel level, @Nullable BlockPos link) {
        if (link == null || !RadarCompat.loaded()) {
            return null;
        }
        return RadarCompat.selectedContact(level, link);
    }

    @Nullable
    private static UUID relayedHull(ServerLevel level, @Nullable BlockPos link) {
        String contact = relayedContact(level, link);
        if (contact == null) {
            return null;
        }
        try {
            UUID ship = UUID.fromString(contact);
            return SableDropCompat.subLevelByUuid(level, ship) == null ? null : ship;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    private static Entity relayedTarget(ServerLevel level, @Nullable BlockPos link) {
        if (link == null || !RadarCompat.loaded()) {
            return null;
        }
        String contact = relayedContact(level, link);
        if (contact != null) {
            try {
                Entity tracked = level.getEntity(UUID.fromString(contact));
                if (tracked != null && tracked.isAlive()) {
                    return tracked;
                }
            } catch (IllegalArgumentException ignored) {
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
            for (ItemStack payload : be.payloads()) {
                if (payload.isEmpty()) {
                    continue;
                }
                server.addFreshEntity(
                        new ItemEntity(server, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, payload));
            }
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
        if (this.slotCount > 1) {
            return switch (state.getValue(FACING)) {
                case UP -> RAIL_SHAPE_UP;
                case DOWN -> RAIL_SHAPE_DOWN;
                case NORTH -> RAIL_SHAPE_NORTH;
                case SOUTH -> RAIL_SHAPE_SOUTH;
                case EAST -> RAIL_SHAPE_EAST;
                case WEST -> RAIL_SHAPE_WEST;
            };
        }
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
        BlockState rotated;
        if (facing.getAxis() == Direction.Axis.Y && face.getAxis() == Direction.Axis.Y) {
            rotated = state.setValue(NOSE, state.getValue(NOSE).getClockWise(Direction.Axis.Y));
        } else {
            Direction.Axis axis = face.getAxis();
            if (axis == facing.getAxis()) {
                axis = axis == Direction.Axis.Y ? Direction.Axis.X : Direction.Axis.Y;
            }
            Direction tipped = facing.getClockWise(axis);
            rotated = state.setValue(FACING, tipped)
                    .setValue(
                            NOSE,
                            tipped.getAxis() != Direction.Axis.Y
                                    ? tipped
                                    : facing.getAxis() == Direction.Axis.Y ? state.getValue(NOSE) : facing);
        }
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
            ItemStack drop = new ItemStack(this.asItem());
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
