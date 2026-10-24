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

/**
 * Freshly released ordnance spends its first second invisible.
 * <p>
 * A salvo off a double or triple rail puts several rounds in the air a fraction of a
 * block apart; without this grace they can catch each other in their own blast before
 * they have cleared the pylon. Invisibility is synced with the flag itself, so clients
 * see the same fade the server counts.
 * <p>
 * The remaining time lives in the entity's persistent data, not only in memory: a round
 * that saves, unloads or relogs mid-grace comes back invisible and the counter resumes
 * when it rejoins, so it can never be stranded out of sight.
 */
@EventBusSubscriber(modid = CBCMoreContent.MOD_ID)
public final class DropGraceVisibility {
    private static final String REMAINING_KEY = "WarnauticsDropGraceTicks";

    /** One second — long enough for a salvo to clear the rails, short enough to watch the fall. */
    public static final int GRACE_TICKS = 20;

    private static final Map<ServerLevel, List<UUID>> ACTIVE = new HashMap<>();

    private DropGraceVisibility() {}

    /** Starts the grace on a just-released round. Safe to call on either side of a launch. */
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

    /**
     * True while a round is still inside its post-release grace. Contact sweeps check
     * this so freshly dropped ordnance cannot detonate on its own salvo, which is
     * invisible or not.
     */
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
                // Unloaded: its persistent counter picks the grace up again on rejoin.
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

    /** A round that saved mid-grace comes back invisible; pick its counter up again. */
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
