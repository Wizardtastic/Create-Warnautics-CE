package com.cbc_more_content.network;

import com.cbc_more_content.CBCMoreContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Chosen on the multi-rail hardpoint's choice screen: fire everything at once, or one per pulse. */
public record HardpointModePayload(BlockPos pos, boolean fireAllAtOnce) implements CustomPacketPayload {

    public static final Type<HardpointModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CBCMoreContent.MOD_ID, "hardpoint_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HardpointModePayload> STREAM_CODEC =
            StreamCodec.of(HardpointModePayload::write, HardpointModePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, HardpointModePayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeBoolean(payload.fireAllAtOnce);
    }

    private static HardpointModePayload read(RegistryFriendlyByteBuf buf) {
        return new HardpointModePayload(buf.readBlockPos(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
