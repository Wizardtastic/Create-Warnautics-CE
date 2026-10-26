package com.cbc_more_content.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Client-only entry point for {@link HardpointModeScreen}. */
@OnlyIn(Dist.CLIENT)
public final class HardpointModeClient {
    private HardpointModeClient() {}

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new HardpointModeScreen(pos));
    }
}
