package com.cbc_more_content.block;

import com.cbc_more_content.bomb.BombSize;
import com.cbc_more_content.item.DropBombItem;
import com.cbc_more_content.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class HardpointBlockEntity extends BlockEntity {
    private static final String PAYLOAD_KEY = "Payload";
    private static final String PAYLOADS_KEY = "Payloads";
    private static final String RELEASE_DELAY_KEY = "ReleaseDelay";
    private static final String FIRE_ALL_AT_ONCE_KEY = "FireAllAtOnce";

    /** One entry per rail, kept at {@link #slotCount()} so a mount always has an address. */
    private final List<ItemStack> payloads = new ArrayList<>();

    private int releaseDelayTicks;

    /** Bitmask of rails mid-rollout on the dial's interval; transient like the old flag. */
    private int rollingSlots;

    private boolean releaseAllAtOnce = true;

    @Nullable
    private BlockPos radarLink;

    public HardpointBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HARDPOINT.get(), pos, state);
        ensureSlotCount();
    }

    public int slotCount() {
        return slotCountFor(this.getBlockState());
    }

    private static int slotCountFor(BlockState state) {
        return state.getBlock() instanceof HardpointBlock hardpoint ? hardpoint.slotCount() : 1;
    }

    private void ensureSlotCount() {
        int slots = this.slotCount();
        while (this.payloads.size() < slots) {
            this.payloads.add(ItemStack.EMPTY);
        }
    }

    /** Live view, one entry per rail; empty stacks are unfilled slots. */
    public List<ItemStack> payloads() {
        return Collections.unmodifiableList(this.payloads);
    }

    public ItemStack payloadIn(int slot) {
        return slot >= 0 && slot < this.payloads.size() ? this.payloads.get(slot) : ItemStack.EMPTY;
    }

    /** The first mounted round, for single-rail callers and readouts. */
    public ItemStack payload() {
        int slot = this.firstOccupiedSlot();
        return slot < 0 ? ItemStack.EMPTY : this.payloads.get(slot);
    }

    public boolean hasPayload() {
        return this.firstOccupiedSlot() >= 0;
    }

    public int firstOccupiedSlot() {
        for (int slot = 0; slot < this.payloads.size(); slot++) {
            if (!this.payloads.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    public int firstEmptySlot() {
        for (int slot = 0; slot < this.payloads.size(); slot++) {
            if (this.payloads.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    public int payloadCount() {
        int filled = 0;
        for (ItemStack stack : this.payloads) {
            if (!stack.isEmpty()) {
                filled++;
            }
        }
        return filled;
    }

    /** Mounts onto the first free rail; false when every rail is loaded. */
    public boolean mount(ItemStack stack) {
        int slot = this.firstEmptySlot();
        if (slot < 0 || stack.isEmpty()) {
            return false;
        }
        this.payloads.set(slot, stack.copy());
        this.sync();
        return true;
    }

    /** Single-rail setter, kept for tests and fixtures. */
    public void setPayload(ItemStack stack) {
        this.ensureSlotCount();
        this.payloads.set(0, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        this.sync();
    }

    public void clearSlot(int slot) {
        if (slot < 0 || slot >= this.payloads.size() || this.payloads.get(slot).isEmpty()) {
            return;
        }
        this.payloads.set(slot, ItemStack.EMPTY);
        this.sync();
    }

    /** Unloads every rail and hands the rounds back to the caller. */
    public List<ItemStack> removePayloads() {
        List<ItemStack> removed = new ArrayList<>();
        for (int slot = 0; slot < this.payloads.size(); slot++) {
            ItemStack stack = this.payloads.get(slot);
            if (!stack.isEmpty()) {
                removed.add(stack);
                this.payloads.set(slot, ItemStack.EMPTY);
            }
        }
        if (!removed.isEmpty()) {
            this.sync();
        }
        return removed;
    }

    public void consumeOneBomb(int slot) {
        if (slot < 0 || slot >= this.payloads.size()) {
            return;
        }
        ItemStack stack = this.payloads.get(slot);
        if (!(stack.getItem() instanceof DropBombItem) || DropBombItem.getCassette(stack) <= 1) {
            this.payloads.set(slot, ItemStack.EMPTY);
            this.sync();
            return;
        }
        int remaining = DropBombItem.getCassette(stack) - 1;
        ItemStack rest = stack.copy();
        rest.setCount(1);
        DropBombItem.setCassette(rest, remaining);
        this.payloads.set(slot, rest);
        this.sync();
    }

    /** The first mounted small bomb pack, if any — the dial's reason to exist. */
    @Nullable
    public ItemStack firstSmallBombPack() {
        for (ItemStack stack : this.payloads) {
            if (stack.getItem() instanceof DropBombItem bombItem
                    && bombItem.getBlock() instanceof DropBombBlock bomb
                    && bomb.getBombSize() == BombSize.SMALL) {
                return stack;
            }
        }
        return null;
    }

    public int releaseDelayTicks() {
        return this.releaseDelayTicks;
    }

    public void setReleaseDelayTicks(int ticks) {
        int normalized = normalizeReleaseDelayTicks(ticks);
        if (normalized == this.releaseDelayTicks) {
            return;
        }
        this.releaseDelayTicks = normalized;
        this.sync();
    }

    private static int normalizeReleaseDelayTicks(int ticks) {
        return ticks < DropBombBlock.MIN_RELEASE_DELAY_TICKS ? 0 : DropBombBlock.normalizeReleaseDelayTicks(ticks);
    }

    /** One redstone pulse empties every rail at once, or only the first loaded one. */
    public boolean releaseAllAtOnce() {
        return this.releaseAllAtOnce;
    }

    public void setReleaseAllAtOnce(boolean releaseAllAtOnce) {
        if (releaseAllAtOnce == this.releaseAllAtOnce) {
            return;
        }
        this.releaseAllAtOnce = releaseAllAtOnce;
        this.sync();
    }

    public boolean isSlotRolling(int slot) {
        return (this.rollingSlots & (1 << slot)) != 0;
    }

    public void setSlotRolling(int slot, boolean rolling) {
        if (slot < 0 || slot >= 31) {
            return;
        }
        if (rolling) {
            this.rollingSlots |= 1 << slot;
        } else {
            this.rollingSlots &= ~(1 << slot);
        }
    }

    public void clearRolling() {
        this.rollingSlots = 0;
    }

    public boolean isReleasing() {
        return this.rollingSlots != 0;
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
        ListTag stored = new ListTag();
        for (int slot = 0; slot < this.payloads.size(); slot++) {
            ItemStack stack = this.payloads.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", slot);
            entry.put("Item", stack.save(registries));
            stored.add(entry);
        }
        if (!stored.isEmpty()) {
            tag.put(PAYLOADS_KEY, stored);
        }
        if (this.radarLink != null) {
            tag.putInt("RadarX", this.radarLink.getX());
            tag.putInt("RadarY", this.radarLink.getY());
            tag.putInt("RadarZ", this.radarLink.getZ());
        }
        if (this.releaseDelayTicks > 0) {
            tag.putInt(RELEASE_DELAY_KEY, this.releaseDelayTicks);
        }
        if (!this.releaseAllAtOnce) {
            tag.putBoolean(FIRE_ALL_AT_ONCE_KEY, false);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.payloads.clear();
        this.ensureSlotCount();
        if (tag.contains(PAYLOADS_KEY, Tag.TAG_LIST)) {
            ListTag stored = tag.getList(PAYLOADS_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < stored.size(); i++) {
                CompoundTag entry = stored.getCompound(i);
                int slot = entry.getInt("Slot");
                ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound("Item"));
                if (slot >= 0 && slot < this.payloads.size() && !stack.isEmpty()) {
                    this.payloads.set(slot, stack);
                }
            }
        } else if (tag.contains(PAYLOAD_KEY)) {
            // Pre-rails pylons carried a single payload.
            this.payloads.set(0, ItemStack.parseOptional(registries, tag.getCompound(PAYLOAD_KEY)));
        }
        this.radarLink = tag.contains("RadarX")
                ? new BlockPos(tag.getInt("RadarX"), tag.getInt("RadarY"), tag.getInt("RadarZ"))
                : null;
        this.releaseDelayTicks =
                tag.contains(RELEASE_DELAY_KEY) ? normalizeReleaseDelayTicks(tag.getInt(RELEASE_DELAY_KEY)) : 0;
        this.releaseAllAtOnce = !tag.contains(FIRE_ALL_AT_ONCE_KEY) || tag.getBoolean(FIRE_ALL_AT_ONCE_KEY);
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
