package com.cbc_more_content.block;

import com.cbc_more_content.compat.RadarCompat;
import com.cbc_more_content.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MissileLaunchpadBlockEntity extends BlockEntity {
    private static final int AUTO_SCAN_INTERVAL = 10;

    private static final String MISSILE_KEY = "Missile";

    private ItemStack missile = ItemStack.EMPTY;

    @Nullable
    private BlockPos target;

    private Mode mode = Mode.COORDINATES;

    private boolean autoFire;

    @Nullable
    private BlockPos radarLink;

    public MissileLaunchpadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MISSILE_LAUNCHPAD.get(), pos, state);
    }

    public ItemStack missile() {
        return this.missile;
    }

    public boolean hasMissile() {
        return !this.missile.isEmpty();
    }

    public void setMissile(ItemStack stack) {
        this.missile = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        this.sync();
    }

    @Nullable
    public BlockPos target() {
        return this.target;
    }

    public void setTarget(@Nullable BlockPos target) {
        this.target = target == null ? null : target.immutable();
        this.sync();
    }

    public Mode mode() {
        return this.mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        this.sync();
    }

    public boolean autoFire() {
        return this.autoFire;
    }

    public void setAutoFire(boolean autoFire) {
        this.autoFire = autoFire;
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, MissileLaunchpadBlockEntity be) {
        if (!(level instanceof ServerLevel server)
                || !be.autoFire
                || be.mode != Mode.RADAR
                || !be.hasMissile()
                || be.radarLink == null
                || !RadarCompat.loaded()
                || Math.floorMod(level.getGameTime() + pos.asLong(), AUTO_SCAN_INTERVAL) != 0) {
            return;
        }
        if (!MissileLaunchpadBlock.hasSelectedTarget(server, be.radarLink)) {
            return;
        }
        MissileLaunchpadBlock.launch(server, pos, state);
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
        if (!this.missile.isEmpty()) {
            tag.put(MISSILE_KEY, this.missile.save(registries));
        }
        if (this.target != null) {
            tag.putInt("TargetX", this.target.getX());
            tag.putInt("TargetY", this.target.getY());
            tag.putInt("TargetZ", this.target.getZ());
        }
        tag.putInt("Mode", this.mode.ordinal());
        tag.putBoolean("AutoFire", this.autoFire);
        if (this.radarLink != null) {
            tag.putInt("RadarX", this.radarLink.getX());
            tag.putInt("RadarY", this.radarLink.getY());
            tag.putInt("RadarZ", this.radarLink.getZ());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.missile = tag.contains(MISSILE_KEY)
                ? ItemStack.parseOptional(registries, tag.getCompound(MISSILE_KEY))
                : ItemStack.EMPTY;
        this.target = tag.contains("TargetX")
                ? new BlockPos(tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ"))
                : null;
        this.mode = Mode.byId(tag.getInt("Mode"));
        this.autoFire = tag.getBoolean("AutoFire");
        this.radarLink = tag.contains("RadarX")
                ? new BlockPos(tag.getInt("RadarX"), tag.getInt("RadarY"), tag.getInt("RadarZ"))
                : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public enum Mode {
        COORDINATES,
        RADAR;

        public static Mode byId(int id) {
            Mode[] values = values();
            return id >= 0 && id < values.length ? values[id] : COORDINATES;
        }
    }
}
