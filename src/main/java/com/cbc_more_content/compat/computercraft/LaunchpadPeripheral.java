package com.cbc_more_content.compat.computercraft;

import com.cbc_more_content.block.MissileLaunchpadBlock;
import com.cbc_more_content.block.MissileLaunchpadBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class LaunchpadPeripheral implements IPeripheral {
    private final MissileLaunchpadBlockEntity pad;

    public LaunchpadPeripheral(MissileLaunchpadBlockEntity pad) {
        this.pad = pad;
    }

    @Override
    public String getType() {
        return "missile_launchpad";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof LaunchpadPeripheral peripheral && peripheral.pad == this.pad;
    }

    @Override
    public Object getTarget() {
        return this.pad;
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> status() {
        Map<String, Object> status = new HashMap<>();
        status.put("loaded", this.pad.hasMissile());
        status.put("mode", this.pad.mode().name().toLowerCase());
        BlockPos target = this.pad.target();
        if (target != null) {
            status.put("x", target.getX());
            status.put("y", target.getY());
            status.put("z", target.getZ());
        }
        status.put("autoFire", this.pad.autoFire());
        BlockPos radar = this.pad.radarLink();
        if (radar != null) {
            Map<String, Object> link = new HashMap<>();
            link.put("x", radar.getX());
            link.put("y", radar.getY());
            link.put("z", radar.getZ());
            status.put("radar", link);
        }
        return status;
    }

    @LuaFunction(mainThread = true)
    public final boolean isLoaded() {
        return this.pad.hasMissile();
    }

    @LuaFunction(mainThread = true)
    public final String getMode() {
        return this.pad.mode().name().toLowerCase();
    }

    @LuaFunction(mainThread = true)
    public final boolean setMode(String mode) {
        switch (mode.toLowerCase()) {
            case "coordinates" -> this.pad.setMode(MissileLaunchpadBlockEntity.Mode.COORDINATES);
            case "radar" -> this.pad.setMode(MissileLaunchpadBlockEntity.Mode.RADAR);
            default -> {
                return false;
            }
        }
        return true;
    }

    @LuaFunction(mainThread = true)
    public final void setTarget(int x, int y, int z) {
        this.pad.setTarget(new BlockPos(x, y, z));
        this.pad.setMode(MissileLaunchpadBlockEntity.Mode.COORDINATES);
    }

    @LuaFunction(mainThread = true)
    public final boolean setAutoFire(boolean autoFire) {
        this.pad.setAutoFire(autoFire);
        return this.pad.autoFire();
    }

    @LuaFunction(mainThread = true)
    public final boolean launch() {
        if (!(this.pad.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        return MissileLaunchpadBlock.launch(level, this.pad.getBlockPos(), this.pad.getBlockState())
                == MissileLaunchpadBlock.Result.LAUNCHED;
    }
}
