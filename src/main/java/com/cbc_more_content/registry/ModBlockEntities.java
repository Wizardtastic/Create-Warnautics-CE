package com.cbc_more_content.registry;

import com.cbc_more_content.CBCMoreContent;
import com.cbc_more_content.block.Aim9BlockEntity;
import com.cbc_more_content.block.C4BlockEntity;
import com.cbc_more_content.block.CruiseMissileBlockEntity;
import com.cbc_more_content.block.HardpointBlockEntity;
import com.cbc_more_content.block.SeaMineBlockEntity;
import com.cbc_more_content.block.SirenBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CBCMoreContent.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Aim9BlockEntity>> AIM9 =
            BLOCK_ENTITIES.register("aim9", () -> BlockEntityType.Builder.of(Aim9BlockEntity::new, ModBlocks.AIM9.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RopeConnectorBlockEntity>> CHAIN_CONNECTOR =
            BLOCK_ENTITIES.register("chain_connector", () -> BlockEntityType.Builder.of(
                            (pos, state) ->
                                    new RopeConnectorBlockEntity(ModBlockEntities.CHAIN_CONNECTOR.get(), pos, state),
                            ModBlocks.CHAIN_CONNECTOR.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<C4BlockEntity>> C4 =
            BLOCK_ENTITIES.register("c4", () -> BlockEntityType.Builder.of(C4BlockEntity::new, ModBlocks.C4.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CruiseMissileBlockEntity>> CRUISE_MISSILE =
            BLOCK_ENTITIES.register("cruise_missile", () -> BlockEntityType.Builder.of(
                            CruiseMissileBlockEntity::new, ModBlocks.CRUISE_MISSILE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HardpointBlockEntity>> HARDPOINT =
            BLOCK_ENTITIES.register(
                    "hardpoint", () -> BlockEntityType.Builder.of(HardpointBlockEntity::new, ModBlocks.HARDPOINT.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SeaMineBlockEntity>> SEA_MINE =
            BLOCK_ENTITIES.register(
                    "sea_mine", () -> BlockEntityType.Builder.of(SeaMineBlockEntity::new, ModBlocks.SEA_MINE.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SirenBlockEntity>> SIREN =
            BLOCK_ENTITIES.register("siren", () -> BlockEntityType.Builder.of(
                            (pos, state) -> new SirenBlockEntity(ModBlockEntities.SIREN.get(), pos, state),
                            ModBlocks.SIREN.get())
                    .build(null));

    private ModBlockEntities() {}
}
