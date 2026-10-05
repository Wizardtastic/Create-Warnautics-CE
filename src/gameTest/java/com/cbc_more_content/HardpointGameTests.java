package com.cbc_more_content;

import com.cbc_more_content.block.HardpointBlock;
import com.cbc_more_content.block.HardpointBlockEntity;
import com.cbc_more_content.item.HardpointItem;
import com.cbc_more_content.munitions.Aim9Projectile;
import com.cbc_more_content.munitions.CruiseMissileProjectile;
import com.cbc_more_content.munitions.DropBombProjectile;
import com.cbc_more_content.registry.ModBlocks;
import com.cbc_more_content.registry.ModEntityTypes;
import com.cbc_more_content.registry.ModItems;
import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CBCMoreContent.MOD_ID)
@PrefixGameTestTemplate(false)
public class HardpointGameTests {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final BlockPos SUPPORT = new BlockPos(12, 8, 12);

    private static FakePlayer player(GameTestHelper helper, ItemStack stack) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "hardpoint-test"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absolutePos(SUPPORT).above().getCenter());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    private static UseOnContext context(FakePlayer player, BlockPos pos, Direction face) {
        return new UseOnContext(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        pos.getCenter().add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5),
                        face,
                        pos,
                        false));
    }

    private static FakePlayer playerAt(GameTestHelper helper, BlockPos pos, ItemStack stack) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "hardpoint-test"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(pos.getCenter());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    private static BlockPos place(GameTestHelper helper, FakePlayer player) {
        var level = helper.getLevel();
        BlockPos ceiling = helper.absolutePos(SUPPORT).above();
        level.setBlockAndUpdate(ceiling, Blocks.STONE.defaultBlockState());
        InteractionResult result = player.getMainHandItem().getItem().useOn(context(player, ceiling, Direction.DOWN));
        helper.assertTrue(result.consumesAction(), "The hardpoint must place against the ceiling");
        BlockPos placed = ceiling.below();
        helper.assertTrue(
                level.getBlockState(placed).is(ModBlocks.HARDPOINT.get()), "Placement lands in the clicked cell");
        return placed;
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(pos.getCenter(), Direction.UP, pos, false);
    }

    @GameTest(template = "empty", batch = "hardpoint_load", timeoutTicks = 120)
    public static void mountAndPulseReleasesOneChargePerRisingEdge(GameTestHelper helper) {
        var level = helper.getLevel();
        var placer = player(helper, new ItemStack(ModItems.HARDPOINT.get()));
        BlockPos pylon = place(helper, placer);
        var bombs = new ItemStack(ModItems.SMALL_BOMB.get(), 4);
        placer.setItemInHand(InteractionHand.MAIN_HAND, bombs);
        var player = placer;
        helper.assertTrue(
                level.getBlockEntity(pylon) instanceof HardpointBlockEntity fresh && !fresh.hasPayload(),
                "A fresh pylon holds nothing");

        var mount = level.getBlockState(pylon)
                .useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit(pylon));
        helper.assertTrue(mount.consumesAction(), "Right-click with a bomb must mount it");
        helper.assertTrue(bombs.getCount() == 3, "Mounting costs exactly one item");
        var loaded = (HardpointBlockEntity) level.getBlockEntity(pylon);
        helper.assertTrue(loaded.hasPayload(), "The mounted bomb rides on the block entity");
        helper.assertTrue(
                level.getBlockState(pylon).getValue(HardpointBlock.FACING) == Direction.DOWN,
                "Clicking the ceiling points the pylon down");

        BlockPos wire = pylon.east();
        level.setBlockAndUpdate(wire, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(3, () -> {
            var afterPulse = (HardpointBlockEntity) level.getBlockEntity(pylon);
            helper.assertFalse(afterPulse.hasPayload(), "The rising edge releases the mounted bomb");
            helper.assertFalse(
                    afterPulse.getUpdateTag(level.registryAccess()).isEmpty(),
                    "A cleared pylon must serialise a non-empty update tag, or the client never redraws");
            helper.assertTrue(
                    level.getEntitiesOfClass(DropBombProjectile.class, new AABB(pylon).inflate(24))
                                    .size()
                            >= 1,
                    "A bomb projectile must be in the air");

            var reload = level.getBlockState(pylon)
                    .useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit(pylon));
            helper.assertTrue(reload.consumesAction(), "Reloading a live pylon still mounts");
            helper.runAfterDelay(4, () -> {
                helper.assertTrue(
                        ((HardpointBlockEntity) level.getBlockEntity(pylon)).hasPayload(),
                        "A held signal must not fire a second time");
                level.removeBlock(wire, false);
                helper.runAfterDelay(2, () -> {
                    helper.assertTrue(
                            ((HardpointBlockEntity) level.getBlockEntity(pylon)).hasPayload(),
                            "Dropping the signal leaves the reload untouched");
                    level.setBlockAndUpdate(wire, Blocks.REDSTONE_BLOCK.defaultBlockState());
                    helper.runAfterDelay(4, () -> {
                        helper.assertFalse(
                                ((HardpointBlockEntity) level.getBlockEntity(pylon)).hasPayload(),
                                "The next rising edge releases the reloaded bomb");
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "hardpoint_tools", timeoutTicks = 100)
    public static void wrenchRotatesAndPickupKeepsLinkAndDropsPayload(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos screen = helper.absolutePos(SUPPORT).above(3);
        var pylonItem = HardpointItem.withLink(new ItemStack(ModItems.HARDPOINT.get()), screen);
        helper.assertTrue(
                screen.equals(HardpointItem.linkOf(pylonItem)),
                "withLink must store the screen; stored=" + HardpointItem.linkOf(pylonItem));
        var player = player(helper, pylonItem);
        BlockPos pylon = place(helper, player);

        var be = (HardpointBlockEntity) level.getBlockEntity(pylon);
        helper.assertTrue(screen.equals(be.radarLink()), "Placement must carry the screen link over");

        var state = level.getBlockState(pylon);
        ((HardpointBlock) state.getBlock()).onWrenched(state, context(player, pylon, Direction.EAST));
        helper.assertTrue(
                level.getBlockState(pylon).getValue(HardpointBlock.FACING) != Direction.DOWN,
                "The wrench turns the pylon on the clicked axis");

        var mount = level.getBlockState(pylon)
                .useItemOn(
                        new ItemStack(ModItems.SMALL_BOMB.get()), level, player, InteractionHand.MAIN_HAND, hit(pylon));
        helper.assertTrue(mount.consumesAction(), "The pylon still takes ordnance after turning");

        ((HardpointBlock) level.getBlockState(pylon).getBlock())
                .onSneakWrenched(level.getBlockState(pylon), context(player, pylon, Direction.UP));
        helper.assertTrue(level.getBlockState(pylon).isAir(), "Sneak-wrench picks the pylon up");

        ItemStack recovered = null;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.HARDPOINT.get())) {
                recovered = stack.copy();
            }
        }
        helper.assertTrue(recovered != null, "The player keeps the hardpoint");
        helper.assertTrue(screen.equals(HardpointItem.linkOf(recovered)), "The recovered pylon keeps its screen link");

        var droppedPayload =
                level.getEntitiesOfClass(ItemEntity.class, new AABB(pylon).inflate(4), drop -> drop.getItem()
                        .is(ModItems.SMALL_BOMB.get()));
        helper.assertTrue(!droppedPayload.isEmpty(), "The mounted bomb is dropped, not destroyed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "hardpoint_placement", timeoutTicks = 100)
    public static void placesAgainstEveryFaceEvenWithABodyInTheWay(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(SUPPORT);
        level.setBlockAndUpdate(anchor, Blocks.STONE.defaultBlockState());
        for (Direction face : Direction.values()) {
            BlockPos target = anchor.relative(face);
            var blocker = EntityType.ARMOR_STAND.create(level);
            helper.assertTrue(blocker != null, "Armour stand fixture spawns");
            blocker.setPos(target.getCenter());
            level.addFreshEntity(blocker);

            var placer = playerAt(helper, target, new ItemStack(ModItems.HARDPOINT.get()));
            InteractionResult result = placer.getMainHandItem().getItem().useOn(context(placer, anchor, face));
            helper.assertTrue(result.consumesAction(), "Must place against the " + face + " face");
            var placed = level.getBlockState(target);
            helper.assertTrue(placed.is(ModBlocks.HARDPOINT.get()), "Placement lands beyond the " + face + " face");
            helper.assertTrue(
                    placed.getValue(HardpointBlock.FACING) == face,
                    "The clamp points along the clicked face: " + placed.getValue(HardpointBlock.FACING));

            blocker.discard();
            level.removeBlock(target, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "hardpoint_placement", timeoutTicks = 100)
    public static void wrenchReachesEveryFacing(GameTestHelper helper) {
        var level = helper.getLevel();
        var wrencher = player(helper, new ItemStack(ModItems.HARDPOINT.get()));
        BlockPos pylon = place(helper, wrencher);
        var seen = EnumSet.of(Direction.DOWN);
        var pending = new ArrayDeque<Direction>();
        pending.add(Direction.DOWN);
        while (!pending.isEmpty()) {
            Direction from = pending.poll();
            level.setBlockAndUpdate(pylon, level.getBlockState(pylon).setValue(HardpointBlock.FACING, from));
            for (Direction click : Direction.values()) {
                var state = level.getBlockState(pylon);
                ((HardpointBlock) state.getBlock()).onWrenched(state, context(wrencher, pylon, click));
                Direction now = level.getBlockState(pylon).getValue(HardpointBlock.FACING);
                if (seen.add(now)) {
                    pending.add(now);
                }
            }
        }
        helper.assertTrue(seen.size() == 6, "The wrench must reach every facing, saw " + seen);

        level.setBlockAndUpdate(pylon, level.getBlockState(pylon).setValue(HardpointBlock.FACING, Direction.DOWN));
        var hanging = level.getBlockState(pylon);
        ((HardpointBlock) hanging.getBlock()).onWrenched(hanging, context(wrencher, pylon, Direction.DOWN));
        helper.assertTrue(
                level.getBlockState(pylon).getValue(HardpointBlock.FACING) != Direction.DOWN,
                "Wrenching the face the pylon already points along must still turn it");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "hardpoint_release", timeoutTicks = 200)
    public static void releasesWithoutRadarHandOverNoTarget(GameTestHelper helper) {
        var level = helper.getLevel();
        var placer = player(helper, new ItemStack(ModItems.HARDPOINT.get()));
        BlockPos pylon = place(helper, placer);

        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.AIM9.get()));
        var mount = level.getBlockState(pylon)
                .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(pylon));
        helper.assertTrue(mount.consumesAction(), "The pylon takes an interceptor");

        BlockPos wire = pylon.east();
        level.setBlockAndUpdate(wire, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(3, () -> {
            var rounds = level.getEntitiesOfClass(Aim9Projectile.class, new AABB(pylon).inflate(30));
            helper.assertTrue(rounds.size() == 1, "The pulse releases the interceptor");
            helper.assertTrue(
                    rounds.getFirst().targetId() == null, "Without Create: Radars the relay hands over no contact");
            rounds.forEach(Entity::discard);

            level.removeBlock(wire, false);
            placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CRUISE_MISSILE.get()));
            var reload = level.getBlockState(pylon)
                    .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(pylon));
            helper.assertTrue(reload.consumesAction(), "The pylon takes a cruise missile");
            helper.runAfterDelay(2, () -> {
                level.setBlockAndUpdate(wire, Blocks.REDSTONE_BLOCK.defaultBlockState());
                helper.runAfterDelay(3, () -> {
                    var cruises = level.getEntitiesOfClass(CruiseMissileProjectile.class, new AABB(pylon).inflate(30));
                    helper.assertTrue(cruises.size() == 1, "The next rising edge releases the cruise missile");
                    helper.assertTrue(
                            cruises.getFirst().targetEntityId() == null,
                            "The cruise missile gets the interceptor's relay, not the old radar-network guidance");
                    cruises.forEach(Entity::discard);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "hardpoint_release", timeoutTicks = 200)
    public static void releasesFlyAlongTheMountNotStraightDown(GameTestHelper helper) {
        var level = helper.getLevel();
        var placer = player(helper, new ItemStack(ModItems.HARDPOINT.get()));
        BlockPos pylon = place(helper, placer);

        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMALL_BOMB.get()));
        var mount = level.getBlockState(pylon)
                .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(pylon));
        helper.assertTrue(mount.consumesAction(), "The pylon takes a bomb");

        BlockPos wire = pylon.east();
        level.setBlockAndUpdate(wire, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(3, () -> {
            var bombs = level.getEntitiesOfClass(DropBombProjectile.class, new AABB(pylon).inflate(8));
            helper.assertTrue(!bombs.isEmpty(), "The rising edge releases the bomb");
            Vec3 drop = bombs.getFirst().getDeltaMovement();
            helper.assertTrue(
                    drop.z < -0.1,
                    "A hanging pylon throws its bomb north along the flat rail, not straight down: " + drop);
            helper.assertTrue(Math.abs(drop.x) < 1.0E-6, "The release may not drift sideways: " + drop);
            bombs.forEach(Entity::discard);

            level.removeBlock(wire, false);
            placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.AIM9.get()));
            var reload = level.getBlockState(pylon)
                    .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(pylon));
            helper.assertTrue(reload.consumesAction(), "The pylon takes the interceptor");
            helper.runAfterDelay(2, () -> {
                level.setBlockAndUpdate(wire, Blocks.REDSTONE_BLOCK.defaultBlockState());
                helper.runAfterDelay(3, () -> {
                    var rounds = level.getEntitiesOfClass(Aim9Projectile.class, new AABB(pylon).inflate(8));
                    helper.assertTrue(rounds.size() == 1, "The next rising edge releases the interceptor");
                    Vec3 eject = rounds.getFirst().getDeltaMovement();
                    helper.assertTrue(
                            eject.z < -0.3, "The interceptor leaves along its flat mount — north, not down: " + eject);
                    helper.assertTrue(Math.abs(eject.x) < 1.0E-6, "The interceptor may not drift sideways: " + eject);
                    rounds.forEach(Entity::discard);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "hardpoint_fuel", timeoutTicks = 300)
    public static void unguidedReleasesFlyStraightForTheirFullFuel(GameTestHelper helper) {
        var level = helper.getLevel();
        level.resetEmptyTime();
        Vec3 origin = helper.absolutePos(new BlockPos(70, 170, 19000)).getCenter();
        var spawn = BlockPos.containing(origin);
        var center = new ChunkPos(spawn);
        level.getChunkSource()
                .addRegionTicket(
                        TicketType.create("hardpoint_fuel", Long::compareTo, 400), center, 2, spawn.asLong(), true);
        level.getChunkAt(spawn);

        var aim9 = ModEntityTypes.AIM9.get().create(level);
        aim9.setPos(origin);
        aim9.launch(null, Vec3.ZERO, new Vec3(0, 1, 0));
        helper.assertTrue(level.addFreshEntity(aim9), "Interceptor fixture spawns");
        for (int tick = 0; tick < 20; tick++) {
            advance(aim9);
        }
        Vec3 line = aim9.getDeltaMovement().normalize();
        for (int tick = 0; tick < 80; tick++) {
            advance(aim9);
        }
        helper.assertFalse(aim9.isRemoved(), "No contact is not instant death: the motor still has its seven seconds");
        helper.assertTrue(
                aim9.getDeltaMovement().normalize().dot(line) > 0.999, "No target, no steering: dead straight");
        for (int tick = 0; tick < 100 && !aim9.isRemoved(); tick++) {
            advance(aim9);
        }
        helper.assertTrue(aim9.isRemoved(), "The interceptor runs out of fuel and finishes");
        aim9.discard();

        var cruise = ModEntityTypes.CRUISE_MISSILE.get().create(level);
        cruise.setPos(origin);
        cruise.launch(new Vec3(0, 1, 0));
        helper.assertTrue(level.addFreshEntity(cruise), "Cruise fixture spawns");
        Vec3 cruiseLine = cruise.getDeltaMovement().normalize();
        for (int tick = 0; tick < 800; tick++) {
            advance(cruise);
            helper.assertFalse(cruise.isRemoved(), "Nothing but fuel ends this flight");
        }
        helper.assertTrue(cruise.isPowered(), "The cruise motor still burns long past the old cutoff");
        helper.assertTrue(
                cruise.getDeltaMovement().normalize().dot(cruiseLine) > 0.99, "No radar, no steering: dead straight");
        for (int tick = 0; tick < 600; tick++) {
            advance(cruise);
        }
        helper.assertFalse(cruise.isPowered(), "The cruise missile runs out of fuel after about a minute");
        cruise.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "hardpoint_sable", timeoutTicks = 120)
    public static void payloadSurvivesReleaseFromPhysicsBodyAttachedToABlock(GameTestHelper helper) {
        var level = helper.getLevel();
        List<ItemStack> payloads = List.of(
                new ItemStack(ModItems.SMALL_BOMB.get()),
                new ItemStack(ModItems.AIM9.get()),
                new ItemStack(ModItems.CRUISE_MISSILE.get()));
        var fixture = TicketType.create("hardpoint_body_fixture", Long::compareTo, 100);
        var ships = new ArrayList<ServerSubLevel>();
        var pylons = new ArrayList<BlockPos>();
        for (int i = 0; i < payloads.size(); i++) {
            BlockPos at = helper.absolutePos(new BlockPos(30 + i * 16, 70, 40));
            level.getChunkSource().addRegionTicket(fixture, new ChunkPos(at), 3, at.asLong());
            level.setBlock(at.west(), Blocks.STONE.defaultBlockState(), FLAGS);
            level.setBlock(
                    at,
                    ModBlocks.HARDPOINT.get().defaultBlockState().setValue(HardpointBlock.FACING, Direction.EAST),
                    FLAGS);
            level.setBlock(at.above(), Blocks.STONE.defaultBlockState(), FLAGS);
            var cells = List.of(at.west(), at, at.above());
            var ship =
                    SubLevelAssemblyHelper.assembleBlocks(level, at, cells, new BoundingBox3i(at.west(), at.above()));
            helper.assertTrue(ship != null && !ship.isRemoved(), "Physics body must assemble");
            for (BlockPos cell : cells) {
                helper.assertTrue(
                        level.getBlockState(cell).isAir(),
                        "Assembly must clear the original world cells, left " + level.getBlockState(cell));
            }
            var local = ship.getPlot().getCenterBlock();
            helper.assertTrue(
                    level.getBlockState(local).is(ModBlocks.HARDPOINT.get()),
                    "Plot centre is the pylon cell; found " + level.getBlockState(local));
            ((HardpointBlockEntity) level.getBlockEntity(local))
                    .setPayload(payloads.get(i).copy());
            ships.add(ship);
            pylons.add(local);
        }
        helper.runAfterDelay(4, () -> {
            for (BlockPos pylon : pylons) {
                level.setBlockAndUpdate(pylon.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            }
        });
        var expected = new ArrayList<Vec3>();
        for (int i = 0; i < payloads.size(); i++) {
            expected.add(helper.absolutePos(new BlockPos(30 + i * 16, 70, 40))
                    .getCenter()
                    .add(1, 0, 0));
        }
        var firstSeen = new ArrayList<Entity>();
        var firstPos = new ArrayList<Vec3>();
        var firstVel = new ArrayList<Vec3>();
        for (int i = 0; i < payloads.size(); i++) {
            firstSeen.add(null);
            firstPos.add(Vec3.ZERO);
            firstVel.add(Vec3.ZERO);
        }
        for (int tick = 5; tick <= 15; tick++) {
            helper.runAfterDelay(tick, () -> {
                for (int i = 0; i < pylons.size(); i++) {
                    if (firstSeen.get(i) != null) {
                        continue;
                    }
                    Entity round = findRound(level, expected.get(i), roundType(payloads.get(i)));
                    if (round != null) {
                        firstSeen.set(i, round);
                        firstPos.set(i, round.position());
                        firstVel.set(i, round.getDeltaMovement());
                    }
                }
            });
        }
        helper.runAfterDelay(16, () -> {
            var problems = new ArrayList<String>();
            var seen = new ArrayList<Entity>();
            for (int i = 0; i < pylons.size(); i++) {
                if (((HardpointBlockEntity) level.getBlockEntity(pylons.get(i))).hasPayload()) {
                    problems.add("body " + i + " payload not consumed");
                }
                if (firstSeen.get(i) == null) {
                    Entity late = findRound(level, expected.get(i), roundType(payloads.get(i)));
                    if (late != null) {
                        firstSeen.set(i, late);
                        firstPos.set(i, late.position());
                        firstVel.set(i, late.getDeltaMovement());
                    }
                }
                Entity round = firstSeen.get(i);
                if (round == null) {
                    problems.add("body " + i + " round missing/explosive; alive=" + roundCensus(level));
                    continue;
                }
                seen.add(round);
                Vec3 at = firstPos.get(i);
                Vec3 vel = firstVel.get(i);
                double dxz = Math.max(Math.abs(at.x - expected.get(i).x), Math.abs(at.z - expected.get(i).z));
                double dy = Math.abs(at.y - expected.get(i).y);
                if (dxz > 8.0 || dy > 6.0) {
                    problems.add("body " + i + " round drifted from " + expected.get(i) + " to " + at + " vel=" + vel);
                }
                if (vel.x <= 0.0) {
                    problems.add("body " + i + " must fly east away from its pylon, vel=" + vel);
                }
            }
            seen.forEach(Entity::discard);
            for (int i = 0; i < payloads.size(); i++) {
                level.getEntitiesOfClass(
                                roundType(payloads.get(i)),
                                new AABB(expected.get(i), expected.get(i)).inflate(9),
                                Entity::isAlive)
                        .forEach(Entity::discard);
                BlockPos local = pylons.get(i);
                for (BlockPos cell : List.of(local.west(), local, local.above())) {
                    level.removeBlock(cell, false);
                }
            }
            helper.assertTrue(problems.isEmpty(), String.join(" | ", problems));
            helper.succeed();
        });
    }

    private static String roundCensus(ServerLevel level) {
        var found = new ArrayList<String>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof DropBombProjectile
                    || entity instanceof Aim9Projectile
                    || entity instanceof CruiseMissileProjectile) {
                found.add(entity.position().toString());
            }
        }
        return found.toString();
    }

    private static Class<? extends Entity> roundType(ItemStack payload) {
        if (payload.is(ModItems.SMALL_BOMB.get())) {
            return DropBombProjectile.class;
        }
        return payload.is(ModItems.AIM9.get()) ? Aim9Projectile.class : CruiseMissileProjectile.class;
    }

    private static Entity findRound(ServerLevel level, Vec3 world, Class<? extends Entity> type) {
        for (Entity entity :
                level.getEntitiesOfClass(Entity.class, new AABB(world, world).inflate(8), type::isInstance)) {
            return entity;
        }
        return null;
    }

    private static void advance(Entity entity) {
        if (!entity.isRemoved()) {
            entity.tickCount++;
            entity.tick();
        }
    }
}
