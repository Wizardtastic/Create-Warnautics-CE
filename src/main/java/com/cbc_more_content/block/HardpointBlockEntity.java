package com.cbc_more_content.block;

import com.cbc_more_content.item.DropBombItem;
import com.cbc_more_content.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class HardpointBlockEntity extends BlockEntity {
    private static final String PAYLOAD_KEY = "Payload";

    private ItemStack payload = ItemStack.EMPTY;

    @Nullable
    private BlockPos radarLink;

    public HardpointBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HARDPOINT.get(), pos, state);
    }

    public ItemStack payload() {
        return this.payload;
    }

    public boolean hasPayload() {
        return !this.payload.isEmpty();
    }

    public void setPayload(ItemStack stack) {
        this.payload = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        this.sync();
    }

    public void consumeOneBomb() {
        if (!(this.payload.getItem() instanceof DropBombItem) || DropBombItem.getCassette(this.payload) <= 1) {
            this.payload = ItemStack.EMPTY;
            this.sync();
            return;
        }
        int remaining = DropBombItem.getCassette(this.payload) - 1;
        ItemStack rest = this.payload.copy();
        rest.setCount(1);
        DropBombItem.setCassette(rest, remaining);
        this.payload = rest;
        this.sync();
    }

    @Nullable
    public BlockPos radarLink() {
        return this.radarLink;
    }

    public void setRadarLink(@Nullable BlockPos link) {
        this.radarLink = link == null ? null : link.immutable();
        this.sync();
    }

    private void sync() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(
                    this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.payload.isEmpty()) {
            tag.put(PAYLOAD_KEY, this.payload.save(registries));
        }
        if (this.radarLink != null) {
            tag.putInt("RadarX", this.radarLink.getX());
            tag.putInt("RadarY", this.radarLink.getY());
            tag.putInt("RadarZ", this.radarLink.getZ());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.payload = tag.contains(PAYLOAD_KEY)
                ? ItemStack.parseOptional(registries, tag.getCompound(PAYLOAD_KEY))
                : ItemStack.EMPTY;
        this.radarLink = tag.contains("RadarX")
                ? new BlockPos(tag.getInt("RadarX"), tag.getInt("RadarY"), tag.getInt("RadarZ"))
                : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = this.saveWithoutMetadata(registries);
        tag.putBoolean("HardpointSync", true);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
