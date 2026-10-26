package com.cbc_more_content;

import com.cbc_more_content.block.HardpointBlock;
import com.cbc_more_content.block.HardpointBlockEntity;
import com.cbc_more_content.item.DropBombItem;
import com.cbc_more_content.item.HardpointItem;
import com.cbc_more_content.munitions.Aim9Projectile;
import com.cbc_more_content.munitions.CruiseMissileProjectile;
import com.cbc_more_content.munitions.DropBombProjectile;
import com.cbc_more_content.registry.ModBlocks;
import com.cbc_more_content.registry.ModEntityTypes;
import com.cbc_more_content.registry.ModItems;
import com.cbc_more_content.util.DropGraceVisibility;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.phys.shapes.CollisionContext;
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
        return place(helper, player, 180.0F);
    }

    private static BlockPos place(GameTestHelper helper, FakePlayer player, float lookYaw) {
        player.setYRot(lookYaw);
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

    private static BlockPos placeRail(
            GameTestHelper helper, FakePlayer player, net.minecraft.world.item.Item item, Block expected) {
        player.setYRot(180.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        var level = helper.getLevel();
        BlockPos ceiling = helper.absolutePos(SUPPORT).above();
        level.setBlockAndUpdate(ceiling, Blocks.STONE.defaultBlockState());
        InteractionResult result = player.getMainHandItem().getItem().useOn(context(player, ceiling, Direction.DOWN));
        helper.assertTrue(result.consumesAction(), "The pylon must place against the ceiling");
        BlockPos placed = ceiling.below();
        helper.assertTrue(level.getBlockState(placed).is(expected), "Placement lands in the clicked cell");
        return placed;
    }

    @GameTest(template = "empty", batch = "hardpoint_placement", timeoutTicks = 100)
    public static void ceilingPlacementTakesThePlacersLookDirection(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos ceiling = helper.absolutePos(SUPPORT).above();
        level.setBlockAndUpdate(ceiling, Blocks.STONE.defaultBlockState());
        var placer = player(helper, new ItemStack(ModItems.HARDPOINT.get()));
        placer.setYRot(-90.0F); // looking east along the wing

        InteractionResult result = placer.getMainHandItem().getItem().useOn(context(placer, ceiling, Direction.DOWN));
        helper.assertTrue(result.consumesAction(), "The hardpoint must place against the ceiling");
        BlockPos hung = ceiling.below();
        var down = level.getBlockState(hung);
        helper.assertTrue(down.is(ModBlocks.HARDPOINT.get()), "Placement lands in the clicked cell");
        helper.assertTrue(
                down.getValue(HardpointBlock.FACING) == Direction.DOWN, "Clicking the ceiling points the pylon down");
        helper.assertTrue(
                down.getValue(HardpointBlock.NOSE) == Direction.EAST,
                "A wing pylon takes the placer's look direction for its rounds: " + down.getValue(HardpointBlock.NOSE));
        level.removeBlock(hung, false);

        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HARDPOINT.get()));
        placer.setYRot(0.0F); // looking south
        result = placer.getMainHandItem().getItem().useOn(context(placer, ceiling, Direction.EAST));
        helper.assertTrue(result.consumesAction(), "The hardpoint must place against the side face");
        var wall = level.getBlockState(ceiling.relative(Direction.EAST));
        helper.assertTrue(wall.is(ModBlocks.HARDPOINT.get()), "The wall placement lands beside the support");
        helper.assertTrue(
                wall.getValue(HardpointBlock.FACING) == Direction.EAST,
                "A wall pylon points along the clicked face: " + wall.getValue(HardpointBlock.FACING));
        helper.assertTrue(
                wall.getValue(HardpointBlock.NOSE) == Direction.EAST,
                "A wall pylon's rounds follow its own face: " + wall.getValue(HardpointBlock.NOSE));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "hardpoint_placement", timeoutTicks = 100)
    public static void wrenchSwingsAWingPylonsRoundsAroundItsAxis(GameTestHelper helper) {
        var level = helper.getLevel();
        var wrencher = player(helper, new ItemStack(ModItems.HARDPOINT.get()));
        BlockPos pylon = place(helper, wrencher);
        Direction first = level.getBlockState(pylon).getValue(HardpointBlock.NOSE);
        helper.assertTrue(first == Direction.NORTH, "The fixture starts facing north: " + first);
        Direction swung = first;
        for (int i = 0; i < 4; i++) {
            var before = level.getBlockState(pylon);
            ((HardpointBlock) before.getBlock()).onWrenched(before, context(wrencher, pylon, Direction.DOWN));
            var after = level.getBlockState(pylon);
            helper.assertTrue(
                    after.getValue(HardpointBlock.FACING) == Direction.DOWN,
                    "Swinging along its own axis must not tip the pylon off the wing");
            swung = after.getValue(HardpointBlock.NOSE);
            helper.assertTrue(
                    swung != before.getValue(HardpointBlock.NOSE),
                    "Each swing advances the rack: " + before.getValue(HardpointBlock.NOSE) + " -> " + swung);
        }
        helper.assertTrue(swung == first, "Four swings return the rack to where it started: " + swung);
        helper.succeed();
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

    /**
     * The clamp model ships as client assets the game test server never loads, so this locks
     * the contract by hand: every facing/nose combination must match exactly one blockstate
     * variant (a miss renders the missing-model cube, a double match is nondeterministic),
     * and each vertical variant must carry the same x/y turn the mounted rounds get — x:180
     * for a ceiling flip, y following the nose, mirroring BlockModelRotation's rotateYXZ(-y,
     * -x, 0) where y:90 turns north to east just like the payload's YP(-90).
     */
    @GameTest(template = "empty", batch = "hardpoint_placement", timeoutTicks = 100)
    public static void hardpointBlockstateCoversEveryFacingAndNose(GameTestHelper helper) {
        for (String blockstate : List.of("hardpoint", "double_hardpoint", "triple_hardpoint")) {
            assertBlockstateCoversEveryFacingAndNose(helper, blockstate);
        }
        helper.succeed();
    }

    private static void assertBlockstateCoversEveryFacingAndNose(GameTestHelper helper, String blockstate) {
        var stream = HardpointGameTests.class.getResourceAsStream(
                "/assets/cbc_more_content/blockstates/" + blockstate + ".json");
        helper.assertTrue(stream != null, "The " + blockstate + " blockstate ships with the mod");
        JsonObject variants;
        try (stream) {
            variants = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonObject("variants");
        } catch (IOException e) {
            throw new UncheckedIOException("The " + blockstate + " blockstate must be readable", e);
        }
        helper.assertTrue(variants != null && !variants.isEmpty(), "The blockstate lists variants");
        List<String> keys = new ArrayList<>(variants.keySet());
        for (Direction facing : Direction.values()) {
            for (Direction nose : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
                Map<String, String> state = Map.of("facing", facing.getName(), "nose", nose.getName());
                List<String> hits =
                        keys.stream().filter(key -> variantMatches(key, state)).toList();
                helper.assertTrue(
                        hits.size() == 1,
                        blockstate + ": facing=" + facing + ",nose=" + nose
                                + " must match exactly one variant, matched " + hits + " of " + keys);
                JsonObject variant = variants.getAsJsonObject(hits.getFirst());
                int wantX = facing == Direction.DOWN ? 180 : facing.getAxis() == Direction.Axis.Y ? 0 : 90;
                int wantY = facing.getAxis() == Direction.Axis.Y
                        ? switch (nose) {
                            case EAST -> 90;
                            case SOUTH -> 180;
                            case WEST -> 270;
                            default -> 0;
                        }
                        : switch (facing) {
                            case EAST -> 90;
                            case SOUTH -> 180;
                            case WEST -> 270;
                            default -> 0;
                        };
                int x = variant.has("x") ? variant.get("x").getAsInt() : 0;
                int y = variant.has("y") ? variant.get("y").getAsInt() : 0;
                helper.assertTrue(
                        x == wantX && y == wantY,
                        blockstate + " " + hits.getFirst() + " must turn x=" + wantX + ",y=" + wantY
                                + " so the clamp follows the rounds, found x=" + x + ",y=" + y);
            }
        }
    }

    private static boolean variantMatches(String key, Map<String, String> state) {
        for (String entry : key.split(",")) {
            String[] parts = entry.split("=", 2);
            if (!state.containsKey(parts[0]) || !state.get(parts[0]).equals(parts[1])) {
                return false;
            }
        }
        return true;
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
        var turned = level.getBlockState(pylon);
        helper.assertTrue(
                turned.getValue(HardpointBlock.FACING) != Direction.DOWN
                        || turned.getValue(HardpointBlock.NOSE) != hanging.getValue(HardpointBlock.NOSE),
                "Wrenching the face the pylon already points along must still turn it");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "hardpoint_release", timeoutTicks = 200)
    public static void roundsReleaseAlongTheDirectionThePylonWasPlaced(GameTestHelper helper) {
        var level = helper.getLevel();
        var placer = player(helper, new ItemStack(ModItems.HARDPOINT.get()));
        BlockPos pylon = place(helper, placer, -90.0F); // look east — the rounds must follow
        helper.assertTrue(
                level.getBlockState(pylon).getValue(HardpointBlock.NOSE) == Direction.EAST,
                "The ceiling pylon keeps the placer's east look: "
                        + level.getBlockState(pylon).getValue(HardpointBlock.NOSE));

        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.AIM9.get()));
        var mount = level.getBlockState(pylon)
                .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(pylon));
        helper.assertTrue(mount.consumesAction(), "The pylon takes an interceptor");

        BlockPos wire = pylon.east();
        level.setBlockAndUpdate(wire, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(3, () -> {
            var rounds = level.getEntitiesOfClass(Aim9Projectile.class, new AABB(pylon).inflate(8));
            helper.assertTrue(rounds.size() == 1, "The rising edge releases the interceptor");
            var round = rounds.getFirst();
            helper.assertTrue(
                    round.getDeltaMovement().y < -0.05,
                    "The interceptor cold-drops away from the pylon: " + round.getDeltaMovement());
            assertRackForward(helper, round, "interceptor", Direction.EAST);
            rounds.forEach(Entity::discard);
            helper.succeed();
        });
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
            assertRackForward(helper, rounds.getFirst(), "interceptor", Direction.NORTH);
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
                    assertRackForward(helper, cruises.getFirst(), "cruise missile", Direction.NORTH);
                    cruises.forEach(Entity::discard);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "hardpoint_release", timeoutTicks = 200)
    public static void releasesDropStraightDownInsteadOfAlongTheMount(GameTestHelper helper) {
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
                    drop.y < -0.05, "A hanging pylon drops its bomb straight down instead of throwing it: " + drop);
            helper.assertTrue(Math.abs(drop.x) < 1.0E-6, "The drop may not drift sideways: " + drop);
            helper.assertTrue(Math.abs(drop.z) < 1.0E-6, "The drop may not sail along the mount: " + drop);
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
                            eject.y < -0.5,
                            "The interceptor drops away from its mount — down, not out along it: " + eject);
                    helper.assertTrue(Math.abs(eject.x) < 1.0E-6, "The drop may not drift sideways: " + eject);
                    helper.assertTrue(Math.abs(eject.z) < 1.0E-6, "The drop may not sail along the mount: " + eject);
                    assertRackForward(helper, rounds.getFirst(), "interceptor", Direction.NORTH);
                    rounds.forEach(Entity::discard);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "hardpoint_release", timeoutTicks = 120)
    public static void settingsKeyOpensBundleDialWithoutStrippingPayload(GameTestHelper helper) {
        var level = helper.getLevel();
        var placer = player(helper, new ItemStack(ModItems.HARDPOINT.get()));
        BlockPos pylon = place(helper, placer);

        placer.setItemInHand(InteractionHand.MAIN_HAND, DropBombItem.withCassette(ModItems.SMALL_BOMB.get(), 4));
        var mount = level.getBlockState(pylon)
                .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(pylon));
        helper.assertTrue(mount.consumesAction(), "The pylon takes a bomb bundle");
        var fresh = (HardpointBlockEntity) level.getBlockEntity(pylon);
        helper.assertTrue(fresh.releaseDelayTicks() == 0, "A fresh pylon is set to drop its whole bundle at once");

        var keyHolder = playerAt(helper, pylon, new ItemStack(ModItems.SETTINGS_KEY.get()));
        var dial = level.getBlockState(pylon)
                .useItemOn(keyHolder.getMainHandItem(), level, keyHolder, InteractionHand.MAIN_HAND, hit(pylon));
        helper.assertTrue(dial.consumesAction(), "The settings key must open the bundle dial");
        var loaded = (HardpointBlockEntity) level.getBlockEntity(pylon);
        helper.assertTrue(loaded.hasPayload(), "Opening the dial must not strip the payload");
        helper.assertTrue(
                DropBombItem.getCassette(loaded.payload()) == 4,
                "The mounted bundle is still whole, found cassette=" + DropBombItem.getCassette(loaded.payload()));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "hardpoint_release", timeoutTicks = 120)
    public static void bundleDropsAllAtOnceOnOneSignal(GameTestHelper helper) {
        var level = helper.getLevel();
        var placer = player(helper, new ItemStack(ModItems.HARDPOINT.get()));
        BlockPos pylon = place(helper, placer);

        placer.setItemInHand(InteractionHand.MAIN_HAND, DropBombItem.withCassette(ModItems.SMALL_BOMB.get(), 4));
        var mount = level.getBlockState(pylon)
                .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(pylon));
        helper.assertTrue(mount.consumesAction(), "The pylon takes a bomb bundle");

        BlockPos wire = pylon.east();
        level.setBlockAndUpdate(wire, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(3, () -> {
            var be = (HardpointBlockEntity) level.getBlockEntity(pylon);
            var bombs = level.getEntitiesOfClass(DropBombProjectile.class, new AABB(pylon).inflate(8));
            int dropped = bombs.size();
            bombs.forEach(Entity::discard);
            helper.assertFalse(be.hasPayload(), "One signal empties the pylon in all-at-once mode");
            helper.assertFalse(be.isReleasing(), "An all-at-once release leaves no sequence running");
            helper.assertTrue(dropped == 4, "Every bomb of the bundle drops together, saw " + dropped);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "hardpoint_release", timeoutTicks = 200)
    public static void staggeredBundleDripsOneBombPerInterval(GameTestHelper helper) {
        var level = helper.getLevel();
        var placer = player(helper, new ItemStack(ModItems.HARDPOINT.get()));
        BlockPos pylon = place(helper, placer);

        var be = (HardpointBlockEntity) level.getBlockEntity(pylon);
        be.setReleaseDelayTicks(10);
        helper.assertTrue(be.releaseDelayTicks() == 10, "The pylon stores the dial's interval");

        placer.setItemInHand(InteractionHand.MAIN_HAND, DropBombItem.withCassette(ModItems.SMALL_BOMB.get(), 4));
        var mount = level.getBlockState(pylon)
                .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(pylon));
        helper.assertTrue(mount.consumesAction(), "The pylon takes a bomb bundle");

        BlockPos wire = pylon.east();
        level.setBlockAndUpdate(wire, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(3, () -> {
            var current = (HardpointBlockEntity) level.getBlockEntity(pylon);
            helper.assertTrue(current.isReleasing(), "A staggered release keeps running between bombs");
            helper.assertTrue(
                    current.hasPayload() && DropBombItem.getCassette(current.payload()) == 3,
                    "Only the first bomb leaves on the signal, cassette left="
                            + (current.hasPayload() ? DropBombItem.getCassette(current.payload()) : 0));
            var bombs = level.getEntitiesOfClass(DropBombProjectile.class, new AABB(pylon).inflate(8));
            helper.assertTrue(bombs.size() == 1, "Exactly one bomb in the air so far, saw " + bombs.size());
            bombs.forEach(Entity::discard);
        });
        helper.runAfterDelay(40, () -> {
            var current = (HardpointBlockEntity) level.getBlockEntity(pylon);
            helper.assertFalse(current.hasPayload(), "The interval runs the whole bundle out");
            helper.assertFalse(current.isReleasing(), "The sequence ends with the bundle");
            level.getEntitiesOfClass(DropBombProjectile.class, new AABB(pylon).inflate(96))
                    .forEach(Entity::discard);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "hardpoint_rails", timeoutTicks = 120)
    public static void doubleRailFiresEveryLoadedRoundOnOneSignal(GameTestHelper helper) {
        var level = helper.getLevel();
        var placer = player(helper, new ItemStack(ModItems.DOUBLE_HARDPOINT.get()));
        BlockPos pylon = placeRail(helper, placer, ModItems.DOUBLE_HARDPOINT.get(), ModBlocks.DOUBLE_HARDPOINT.get());
        var fresh = (HardpointBlockEntity) level.getBlockEntity(pylon);
        helper.assertTrue(fresh.slotCount() == 2, "The double rail has two slots, found " + fresh.slotCount());
        helper.assertTrue(fresh.releaseAllAtOnce(), "A fresh rail fires everything at once");

        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMALL_BOMB.get(), 2));
        for (int round = 1; round <= 2; round++) {
            var mount = level.getBlockState(pylon)
                    .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(pylon));
            helper.assertTrue(mount.consumesAction(), "Round " + round + " must mount on the double rail");
        }
        var loaded = (HardpointBlockEntity) level.getBlockEntity(pylon);
        helper.assertTrue(loaded.payloadCount() == 2, "Both rails are loaded, found " + loaded.payloadCount());

        BlockPos wire = pylon.east();
        level.setBlockAndUpdate(wire, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(3, () -> {
            var after = (HardpointBlockEntity) level.getBlockEntity(pylon);
            helper.assertFalse(after.hasPayload(), "One signal empties both rails at once");
            var bombs = level.getEntitiesOfClass(DropBombProjectile.class, new AABB(pylon).inflate(8));
            helper.assertTrue(bombs.size() == 2, "Both rounds are in the air, saw " + bombs.size());
            if (bombs.size() == 2) {
                Vec3 first = bombs.get(0).position();
                Vec3 second = bombs.get(1).position();
                double separation = Math.max(Math.abs(first.x - second.x), Math.abs(first.z - second.z));
                helper.assertTrue(
                        Math.abs(separation - HardpointBlock.RAIL_SPACING) < 0.06D,
                        "The two rails release a rail's width apart, saw " + separation);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "hardpoint_rails", timeoutTicks = 160)
    public static void tripleRailOnePerPulseSpendsOneRoundEach(GameTestHelper helper) {
        var level = helper.getLevel();
        var placer = player(helper, new ItemStack(ModItems.TRIPLE_HARDPOINT.get()));
        BlockPos pylon = placeRail(helper, placer, ModItems.TRIPLE_HARDPOINT.get(), ModBlocks.TRIPLE_HARDPOINT.get());
        ((HardpointBlockEntity) level.getBlockEntity(pylon)).setReleaseAllAtOnce(false);

        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMALL_BOMB.get(), 3));
        for (int round = 1; round <= 3; round++) {
            var mount = level.getBlockState(pylon)
                    .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(pylon));
            helper.assertTrue(mount.consumesAction(), "Round " + round + " must mount on the triple rail");
        }
        helper.assertTrue(
                ((HardpointBlockEntity) level.getBlockEntity(pylon)).payloadCount() == 3, "All three rails are loaded");

        BlockPos wire = pylon.east();
        level.setBlockAndUpdate(wire, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(3, () -> {
            var after = (HardpointBlockEntity) level.getBlockEntity(pylon);
            helper.assertTrue(after.payloadCount() == 2, "One pulse spends one round, left " + after.payloadCount());
            int dropped = level.getEntitiesOfClass(DropBombProjectile.class, new AABB(pylon).inflate(8))
                    .size();
            helper.assertTrue(dropped == 1, "Exactly one round leaves on the pulse, saw " + dropped);
            level.getEntitiesOfClass(DropBombProjectile.class, new AABB(pylon).inflate(8))
                    .forEach(Entity::discard);

            level.removeBlock(wire, false);
            helper.runAfterDelay(2, () -> {
                level.setBlockAndUpdate(wire, Blocks.REDSTONE_BLOCK.defaultBlockState());
                helper.runAfterDelay(3, () -> {
                    var last = (HardpointBlockEntity) level.getBlockEntity(pylon);
                    helper.assertTrue(
                            last.payloadCount() == 1,
                            "The next pulse spends the next round, left " + last.payloadCount());
                    level.getEntitiesOfClass(DropBombProjectile.class, new AABB(pylon).inflate(8))
                            .forEach(Entity::discard);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "hardpoint_rails", timeoutTicks = 100)
    public static void railsRefuseTheOrdnanceThatNeedsASingleRail(GameTestHelper helper) {
        var level = helper.getLevel();
        var placer = player(helper, new ItemStack(ModItems.DOUBLE_HARDPOINT.get()));
        BlockPos doubleRail =
                placeRail(helper, placer, ModItems.DOUBLE_HARDPOINT.get(), ModBlocks.DOUBLE_HARDPOINT.get());

        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CRUISE_MISSILE.get()));
        var refusedMissile = level.getBlockState(doubleRail)
                .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(doubleRail));
        helper.assertFalse(refusedMissile.consumesAction(), "The double rail cannot take a cruise missile");

        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LARGE_BOMB.get()));
        var doubleLarge = level.getBlockState(doubleRail)
                .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(doubleRail));
        helper.assertFalse(doubleLarge.consumesAction(), "The double rail cannot take a large bomb");

        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOAB.get()));
        var doubleMoab = level.getBlockState(doubleRail)
                .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(doubleRail));
        helper.assertFalse(doubleMoab.consumesAction(), "The double rail cannot take a MOAB");
        ((HardpointBlockEntity) level.getBlockEntity(doubleRail)).removePayloads();
        level.removeBlock(doubleRail, false);

        BlockPos tripleRail =
                placeRail(helper, placer, ModItems.TRIPLE_HARDPOINT.get(), ModBlocks.TRIPLE_HARDPOINT.get());
        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LARGE_BOMB.get()));
        var refusedLarge = level.getBlockState(tripleRail)
                .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(tripleRail));
        helper.assertFalse(refusedLarge.consumesAction(), "The triple rail cannot take a large bomb");

        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOAB.get()));
        var refusedMoab = level.getBlockState(tripleRail)
                .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(tripleRail));
        helper.assertFalse(refusedMoab.consumesAction(), "The triple rail cannot take a MOAB");

        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CRUISE_MISSILE.get()));
        var refusedTripleMissile = level.getBlockState(tripleRail)
                .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(tripleRail));
        helper.assertFalse(refusedTripleMissile.consumesAction(), "The triple rail cannot take a cruise missile");

        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.AIM9.get()));
        var interceptor = level.getBlockState(tripleRail)
                .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(tripleRail));
        helper.assertTrue(interceptor.consumesAction(), "The triple rail still takes an interceptor");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "hardpoint_rails", timeoutTicks = 100)
    public static void railsStopThreePixelsShortOfTheSinglePylon(GameTestHelper helper) {
        var level = helper.getLevel();
        double third = 3.0 / 16.0;
        var single = ModBlocks.HARDPOINT.get().defaultBlockState();
        var rail = ModBlocks.DOUBLE_HARDPOINT.get().defaultBlockState();
        var triple = ModBlocks.TRIPLE_HARDPOINT.get().defaultBlockState();

        BlockPos hung = helper.absolutePos(new BlockPos(4, 6, 4));
        BlockPos floor = hung.south();
        BlockPos wall = floor.south();
        level.setBlock(hung, single.setValue(HardpointBlock.FACING, Direction.DOWN), FLAGS);
        level.setBlock(hung.east(), rail.setValue(HardpointBlock.FACING, Direction.DOWN), FLAGS);
        level.setBlock(floor, triple.setValue(HardpointBlock.FACING, Direction.UP), FLAGS);
        level.setBlock(floor.east(), single.setValue(HardpointBlock.FACING, Direction.UP), FLAGS);
        level.setBlock(wall, rail.setValue(HardpointBlock.FACING, Direction.EAST), FLAGS);
        level.setBlock(wall.east(), single.setValue(HardpointBlock.FACING, Direction.EAST), FLAGS);

        double singleHang = level.getBlockState(hung)
                .getShape(level, hung, CollisionContext.empty())
                .bounds()
                .minY;
        double railHang = level.getBlockState(hung.east())
                .getShape(level, hung.east(), CollisionContext.empty())
                .bounds()
                .minY;
        helper.assertTrue(
                Math.abs(railHang - (singleHang + third)) < 1.0e-9,
                "A hanging rail stops three pixels short, single=" + singleHang + " rail=" + railHang);

        double singleRise = level.getBlockState(floor.east())
                .getShape(level, floor.east(), CollisionContext.empty())
                .bounds()
                .maxY;
        double tripleRise = level.getBlockState(floor)
                .getShape(level, floor, CollisionContext.empty())
                .bounds()
                .maxY;
        helper.assertTrue(
                Math.abs(tripleRise - (singleRise - third)) < 1.0e-9,
                "A floor rail stops three pixels short, single=" + singleRise + " rail=" + tripleRise);

        double singleReach = level.getBlockState(wall.east())
                .getShape(level, wall.east(), CollisionContext.empty())
                .bounds()
                .maxX;
        double railReach = level.getBlockState(wall)
                .getShape(level, wall, CollisionContext.empty())
                .bounds()
                .maxX;
        helper.assertTrue(
                Math.abs(railReach - (singleReach - third)) < 1.0e-9,
                "A wall rail stops three pixels short, single=" + singleReach + " rail=" + railReach);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "hardpoint_rails", timeoutTicks = 120)
    public static void everyHardpointDropIsInvisibleForItsFirstSecond(GameTestHelper helper) {
        var level = helper.getLevel();
        var placer = player(helper, new ItemStack(ModItems.DOUBLE_HARDPOINT.get()));
        BlockPos pylon = placeRail(helper, placer, ModItems.DOUBLE_HARDPOINT.get(), ModBlocks.DOUBLE_HARDPOINT.get());

        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMALL_BOMB.get(), 2));
        for (int round = 1; round <= 2; round++) {
            var mount = level.getBlockState(pylon)
                    .useItemOn(placer.getMainHandItem(), level, placer, InteractionHand.MAIN_HAND, hit(pylon));
            helper.assertTrue(mount.consumesAction(), "Round " + round + " must mount on the double rail");
        }

        Entity[] dropped = new Entity[2];
        BlockPos wire = pylon.east();
        level.setBlockAndUpdate(wire, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(3, () -> {
            var bombs = level.getEntitiesOfClass(DropBombProjectile.class, new AABB(pylon).inflate(8));
            helper.assertTrue(bombs.size() == 2, "Both rounds are in the air, saw " + bombs.size());
            for (int i = 0; i < bombs.size(); i++) {
                Entity bomb = bombs.get(i);
                helper.assertTrue(bomb.isInvisible(), "A freshly dropped round starts invisible");
                // Park the rounds high and physics-free so the grace can be watched in open air.
                bomb.noPhysics = true;
                bomb.setDeltaMovement(Vec3.ZERO);
                bomb.teleportTo(bomb.getX(), bomb.getY() + 30.0D, bomb.getZ());
                dropped[i] = bomb;
            }
        });
        helper.runAfterDelay(12, () -> {
            for (Entity bomb : dropped) {
                helper.assertTrue(bomb != null && bomb.isInvisible(), "The grace spans most of the first second");
                bomb.setDeltaMovement(Vec3.ZERO);
            }
        });
        helper.runAfterDelay(30, () -> {
            for (Entity bomb : dropped) {
                helper.assertTrue(bomb != null && !bomb.isRemoved(), "The dropped round survives its grace");
                helper.assertFalse(bomb.isInvisible(), "The round is visible again after its second");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "hardpoint_rails", timeoutTicks = 120)
    public static void roundsInTheirGraceNeverCollideWithEachOther(GameTestHelper helper) {
        var level = helper.getLevel();
        Vec3 origin = helper.absolutePos(new BlockPos(8, 6, 8)).getCenter();

        var first = ModEntityTypes.AIM9.get().create(level);
        var second = ModEntityTypes.AIM9.get().create(level);
        first.setPos(origin);
        second.setPos(origin.add(0.1, 0.0, 0.0));
        first.launch(null, Vec3.ZERO, new Vec3(0.0, 1.0, 0.0));
        second.launch(null, Vec3.ZERO, new Vec3(0.0, 1.0, 0.0));
        helper.assertTrue(level.addFreshEntity(first), "The first interceptor spawns");
        helper.assertTrue(level.addFreshEntity(second), "The second interceptor spawns");
        // What a double rail hands its release path: two rounds sharing one spot.
        DropGraceVisibility.grant(first);
        DropGraceVisibility.grant(second);

        // Parked with no motion: no block clip can fire, so only an entity contact
        // could end this early — exactly the salvo collision under test.
        for (int tick = 0; tick < 10 && !first.isRemoved() && !second.isRemoved(); tick++) {
            first.noPhysics = true;
            second.noPhysics = true;
            first.setDeltaMovement(Vec3.ZERO);
            second.setDeltaMovement(Vec3.ZERO);
            advance(first);
            advance(second);
        }
        boolean firstSurvived = !first.isRemoved();
        boolean secondSurvived = !second.isRemoved();
        first.discard();
        second.discard();
        helper.assertTrue(firstSurvived, "The first round must not detonate on its salvo-mate");
        helper.assertTrue(secondSurvived, "The second round must not detonate on its salvo-mate");
        helper.succeed();
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

    @GameTest(template = "empty", batch = "hardpoint_sable", timeoutTicks = 240)
    public static void payloadSurvivesReleaseFromPhysicsBodyAttachedToABlock(GameTestHelper helper) {
        var level = helper.getLevel();
        List<ItemStack> payloads = List.of(
                new ItemStack(ModItems.SMALL_BOMB.get()),
                new ItemStack(ModItems.AIM9.get()),
                new ItemStack(ModItems.CRUISE_MISSILE.get()));
        var fixture = TicketType.create("hardpoint_body_fixture", Long::compareTo, 300);
        var ships = new ArrayList<ServerSubLevel>();
        var pylons = new ArrayList<BlockPos>();
        var origins = new ArrayList<BlockPos>();
        for (int i = 0; i < payloads.size(); i++) {
            // 32 blocks apart, not 16: the interceptor's eastward test lane must be
            // discarded before it can reach the neighbouring fixture's hull.
            BlockPos at = helper.absolutePos(new BlockPos(30 + i * 32, 70, 40));
            origins.add(at);
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
        for (BlockPos origin : origins) {
            level.getChunkAt(origin);
        }
        var expected = new ArrayList<Vec3>();
        for (BlockPos origin : origins) {
            expected.add(origin.getCenter());
        }
        Runnable arm = () -> {
            for (BlockPos pylon : pylons) {
                level.setBlockAndUpdate(pylon.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            }
            var sightPos = new ArrayList<Vec3>();
            var sightVel = new ArrayList<Vec3>();
            for (int i = 0; i < payloads.size(); i++) {
                sightPos.add(null);
                sightVel.add(null);
            }
            var types = new ArrayList<Class<? extends Entity>>();
            for (ItemStack payload : payloads) {
                types.add(roundType(payload));
            }
            pollFirstSight(helper, level, expected, types, sightPos, sightVel, 1, () -> {
                var problems = new ArrayList<String>();
                var trail = new ArrayList<String>();
                for (int i = 0; i < pylons.size(); i++) {
                    var state = level.getBlockState(pylons.get(i));
                    if (!(level.getBlockEntity(pylons.get(i)) instanceof HardpointBlockEntity pylon)) {
                        problems.add("body " + i + " pylon block entity is gone; state=" + state + " shipRemoved="
                                + ships.get(i).isRemoved());
                    } else if (pylon.hasPayload()) {
                        problems.add("body " + i + " payload not consumed");
                    }
                    Vec3 column = expected.get(i);
                    Vec3 at = sightPos.get(i);
                    Vec3 vel = sightVel.get(i);
                    String label = "body " + i + " state=" + state + " shipRemoved="
                            + ships.get(i).isRemoved();
                    if (at == null) {
                        problems.add(label + " round never released; remaining=" + roundCensus(level));
                        trail.add(label + " released=false");
                        continue;
                    }
                    trail.add(label + " drop=" + at + " " + vel);
                    double dxz = Math.max(Math.abs(at.x - column.x), Math.abs(at.z - column.z));
                    if (dxz > 2.5) {
                        problems.add("body " + i + " round dropped outside its pylon column -> " + at + " vel=" + vel);
                    }
                    if (at.y > column.y + 0.5) {
                        problems.add("body " + i + " round must spawn below its pylon, not above it, pos=" + at);
                    }
                    if (vel.y >= -0.05) {
                        problems.add("body " + i + " round must fall away from its pylon at release, vel=" + vel);
                    }
                    if (Math.abs(vel.x) > 0.01 || Math.abs(vel.z) > 0.01) {
                        problems.add("body " + i + " round must not be thrusting sideways at release, vel=" + vel);
                    }
                    boolean missile = roundType(payloads.get(i)) != DropBombProjectile.class;
                    Entity round = findInBox(
                            level, missile ? flightLane(column) : dropColumn(column), roundType(payloads.get(i)));
                    if (round == null) {
                        problems.add(label + " round lost after release; remaining=" + roundCensus(level));
                        continue;
                    }
                    Vec3 now = round.getDeltaMovement();
                    if (missile) {
                        boolean powered = round instanceof Aim9Projectile aim9
                                ? aim9.isPowered()
                                : round instanceof CruiseMissileProjectile cruise && cruise.isPowered();
                        if (!powered) {
                            problems.add("body " + i + " motor must light half a second after the drop, vel=" + now);
                        }
                        Vec3 forward = state.is(ModBlocks.HARDPOINT.get())
                                ? Vec3.atLowerCornerOf(
                                        HardpointBlock.releaseNose(state).getNormal())
                                : new Vec3(1.0D, 0.0D, 0.0D);
                        if (now.dot(forward) <= 0.1) {
                            problems.add("body " + i + " must fly forward along its firing facing, vel=" + now
                                    + " forward=" + forward);
                        }
                        double yawWant = Math.toDegrees(Math.atan2(forward.z, forward.x)) - 90.0;
                        double pitchWant = -Math.toDegrees(Math.asin(Math.max(-1.0D, Math.min(1.0D, forward.y))));
                        double yawDelta = Math.abs(round.getYRot() - yawWant);
                        if (yawDelta > 180.0) {
                            yawDelta = 360.0 - yawDelta;
                        }
                        double pitchDelta = Math.abs(round.getXRot() - pitchWant);
                        if (yawDelta > 10.0 || pitchDelta > 10.0) {
                            problems.add("body " + i
                                    + " must keep the facing it fired with: yaw=" + round.getYRot()
                                    + " pitch=" + round.getXRot() + " expected yaw=" + yawWant + " pitch="
                                    + pitchWant);
                        }
                    } else if (now.y >= -0.05) {
                        problems.add("body " + i + " bomb must still be falling, vel=" + now);
                    }
                }
                String remaining = roundCensus(level);
                for (int i = 0; i < payloads.size(); i++) {
                    level.getEntitiesOfClass(roundType(payloads.get(i)), flightLane(expected.get(i)), Entity::isAlive)
                            .forEach(Entity::discard);
                    BlockPos local = pylons.get(i);
                    for (BlockPos cell : List.of(local.west(), local, local.above())) {
                        level.removeBlock(cell, false);
                    }
                }
                helper.assertTrue(
                        problems.isEmpty(),
                        String.join(" | ", problems)
                                + " || "
                                + String.join(" || ", trail)
                                + " || remaining="
                                + remaining);
                helper.succeed();
            });
        };
        pollUntilTicking(helper, level, origins, 1, arm, 3);
    }

    private static void assertRackForward(GameTestHelper helper, Entity round, String what, Direction nose) {
        helper.assertTrue(
                Math.abs(round.getXRot()) < 10.0F,
                "The " + what + " must hang nose-level through the drop, not nose-down: pitch=" + round.getXRot());
        double yawWant = Math.toDegrees(Math.atan2(nose.getStepZ(), nose.getStepX())) - 90.0;
        double yawDelta = Math.abs(round.getYRot() - yawWant);
        if (yawDelta > 180.0) {
            yawDelta = 360.0 - yawDelta;
        }
        helper.assertTrue(
                yawDelta < 10.0,
                "A ceiling pylon points its " + what + " along " + nose + ", not down: yaw=" + round.getYRot()
                        + " expected " + yawWant);
    }

    private static AABB dropColumn(Vec3 column) {
        return new AABB(column.x - 4, column.y - 80, column.z - 4, column.x + 4, column.y + 1, column.z + 4);
    }

    private static AABB flightLane(Vec3 column) {
        return new AABB(column.x - 4, column.y - 80, column.z - 8, column.x + 80, column.y + 1, column.z + 8);
    }

    private static Entity findInBox(ServerLevel level, AABB box, Class<? extends Entity> type) {
        return level.getEntitiesOfClass(Entity.class, box, type::isInstance).stream()
                .findFirst()
                .orElse(null);
    }

    private static void pollFirstSight(
            GameTestHelper helper,
            ServerLevel level,
            List<Vec3> expected,
            List<Class<? extends Entity>> types,
            List<Vec3> sightPos,
            List<Vec3> sightVel,
            int tick,
            Runnable finish) {
        for (int i = 0; i < types.size(); i++) {
            if (sightPos.get(i) != null) {
                continue;
            }
            Entity round = findInBox(level, dropColumn(expected.get(i)), types.get(i));
            if (round != null) {
                sightPos.set(i, round.position());
                sightVel.set(i, round.getDeltaMovement());
            }
        }
        boolean allSeen = true;
        for (Vec3 seen : sightPos) {
            if (seen == null) {
                allSeen = false;
                break;
            }
        }
        if (allSeen || tick >= 34) {
            helper.runAfterDelay(allSeen ? 14 : 6, finish);
            return;
        }
        helper.runAfterDelay(1, new Runnable() {
            @Override
            public void run() {
                pollFirstSight(helper, level, expected, types, sightPos, sightVel, tick + 1, finish);
            }
        });
    }

    private static void pollUntilTicking(
            GameTestHelper helper,
            ServerLevel level,
            List<BlockPos> origins,
            int attempt,
            Runnable onReady,
            int delay) {
        helper.runAfterDelay(delay, new Runnable() {
            @Override
            public void run() {
                if (origins.stream().allMatch(level::isPositionEntityTicking)) {
                    onReady.run();
                    return;
                }
                helper.assertTrue(attempt <= 100, "Fixture chunks never became entity-ticking, origins=" + origins);
                pollUntilTicking(helper, level, origins, attempt + 1, onReady, 1);
            }
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

    private static void advance(Entity entity) {
        if (!entity.isRemoved()) {
            entity.tickCount++;
            entity.tick();
        }
    }
}
