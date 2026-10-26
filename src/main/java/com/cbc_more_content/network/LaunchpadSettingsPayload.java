package com.cbc_more_content.network;

import com.cbc_more_content.CBCMoreContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LaunchpadSettingsPayload(
        BlockPos pos, int mode, int targetX, int targetY, int targetZ, boolean autoFire, boolean fire)
        implements CustomPacketPayload {
    public static final Type<LaunchpadSettingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CBCMoreContent.MOD_ID, "launchpad_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LaunchpadSettingsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos());
                buf.writeVarInt(packet.mode());
                buf.writeInt(packet.targetX());
                buf.writeInt(packet.targetY());
                buf.writeInt(packet.targetZ());
                buf.writeBoolean(packet.autoFire());
                buf.writeBoolean(packet.fire());
            },
            buf -> new LaunchpadSettingsPayload(
                    buf.readBlockPos(),
                    buf.readVarInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
