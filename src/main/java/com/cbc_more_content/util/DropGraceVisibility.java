package com.cbc_more_content.util;

import com.cbc_more_content.CBCMoreContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = CBCMoreContent.MOD_ID)
public final class DropGraceVisibility {
    private static final String REMAINING_KEY = "WarnauticsDropGraceTicks";

    public static final int GRACE_TICKS = 20;

    private static final Map<ServerLevel, List<UUID>> ACTIVE = new HashMap<>();

    private DropGraceVisibility() {}

    public static void grant(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        entity.getPersistentData().putInt(REMAINING_KEY, GRACE_TICKS);
        entity.setInvisible(true);
        List<UUID> pending = ACTIVE.computeIfAbsent(level, ignored -> new ArrayList<>());
        if (!pending.contains(entity.getUUID())) {
            pending.add(entity.getUUID());
        }
    }

    public static boolean isInGrace(Entity entity) {
        return entity.getPersistentData().getInt(REMAINING_KEY) > 0;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<UUID> pending = ACTIVE.get(level);
        if (pending == null) {
            return;
        }
        pending.removeIf(uuid -> {
            Entity entity = level.getEntity(uuid);
            if (entity == null || entity.isRemoved()) {
                return true;
            }
            CompoundTag data = entity.getPersistentData();
            int remaining = data.getInt(REMAINING_KEY) - 1;
            if (remaining > 0) {
                data.putInt(REMAINING_KEY, remaining);
                return false;
            }
            data.remove(REMAINING_KEY);
            entity.setInvisible(false);
            return true;
        });
        if (pending.isEmpty()) {
            ACTIVE.remove(level);
        }
    }

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getEntity().getPersistentData().getInt(REMAINING_KEY) > 0) {
            List<UUID> pending = ACTIVE.computeIfAbsent(level, ignored -> new ArrayList<>());
            if (!pending.contains(event.getEntity().getUUID())) {
                pending.add(event.getEntity().getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ACTIVE.remove(level);
        }
    }
}
