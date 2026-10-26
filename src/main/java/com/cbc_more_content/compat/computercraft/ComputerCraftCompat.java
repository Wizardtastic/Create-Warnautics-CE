package com.cbc_more_content.compat.computercraft;

import com.cbc_more_content.CBCMoreContent;
import com.cbc_more_content.registry.ModBlockEntities;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class ComputerCraftCompat {
    private ComputerCraftCompat() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                PeripheralCapability.get(),
                ModBlockEntities.MISSILE_LAUNCHPAD.get(),
                (pad, side) -> new LaunchpadPeripheral(pad));
        CBCMoreContent.LOGGER.info("Missile launchpads exposed to ComputerCraft as missile_launchpad");
    }
}
