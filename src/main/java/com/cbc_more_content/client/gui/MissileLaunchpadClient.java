package com.cbc_more_content.client.gui;

import com.cbc_more_content.block.MissileLaunchpadBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class MissileLaunchpadClient {
    private MissileLaunchpadClient() {}

    public static void open(BlockPos pos) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof MissileLaunchpadBlockEntity be) {
            BlockPos target = be.target() == null ? pos : be.target();
            mc.setScreen(
                    new MissileLaunchpadScreen(pos, be.mode(), target, be.autoFire(), be.hasMissile(), be.radarLink()));
        }
    }
}
