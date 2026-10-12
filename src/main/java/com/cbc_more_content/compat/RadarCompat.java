package com.cbc_more_content.compat;

import com.cbc_more_content.CBCMoreContent;
import com.cbc_more_content.radar.InterceptSettings;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

public final class RadarCompat {
    public static final String MOD_ID = "create_radar";
    private static final String SOURCE_CLASS = "com.happysg.radar.api.tracking.RadarSource";

    private static boolean resolved;

    @Nullable
    private static Class<?> sourceClass;

    @Nullable
    private static Method getContacts;

    @Nullable
    private static Method isRunning;

    @Nullable
    private static Method contactId;

    @Nullable
    private static Method contactPosition;

    @Nullable
    private static Method contactVelocity;

    private RadarCompat() {}

    public static boolean loaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public record Contact(String id, Vec3 position, Vec3 velocity, boolean hull) {}

    public static boolean isController(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || !resolve()) {
            return false;
        }
        return sourceClass != null && sourceClass.isInstance(blockEntity);
    }

    public static boolean isRadarModBlock(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || !loaded()) {
            return false;
        }
        var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        return key != null && MOD_ID.equals(key.getNamespace());
    }

    private static final String MONITOR_CLASS = "com.happysg.radar.block.monitor.MonitorBlockEntity";
    private static final String TARGETING_CONFIG_CLASS =
            "com.happysg.radar.block.behavior.networks.config.TargetingConfig";

    private static boolean monitorResolved;

    @Nullable
    private static Class<?> monitorClass;

    @Nullable
    private static Method monitorGetController;

    @Nullable
    private static Method monitorGetSelectedEntity;

    @Nullable
    private static Field monitorSelectedEntity;

    @Nullable
    private static Method monitorGetTargetPos;

    @Nullable
    private static Method monitorGetRadarSnapshots;

    @Nullable
    private static Method monitorGetTracks;

    @Nullable
    private static Object targetingDefault;

    @Nullable
    private static Class<?> snapshotClass;

    @Nullable
    private static Method snapshotPosAccessor;

    public static boolean isMonitor(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || !resolveMonitor()) {
            return false;
        }
        return monitorClass != null && monitorClass.isInstance(blockEntity);
    }

    @Nullable
    public static BlockPos monitorController(Level level, BlockPos clicked) {
        BlockEntity controller = monitorControllerBe(level, clicked);
        return controller == null ? null : controller.getBlockPos();
    }

    @Nullable
    public static String selectedContact(Level level, BlockPos monitor) {
        BlockEntity controller = monitorControllerBe(level, monitor);
        if (controller == null || level.isClientSide) {
            return null;
        }
        try {
            Object id;
            if (monitorGetSelectedEntity != null) {
                id = monitorGetSelectedEntity.invoke(controller);
            } else if (monitorSelectedEntity != null) {
                id = monitorSelectedEntity.get(controller);
            } else {
                return null;
            }
            return id instanceof String name && !name.isBlank() ? name : null;
        } catch (Throwable t) {
            CBCMoreContent.LOGGER.debug("Radar screen selection read failed: {}", t.toString());
            return null;
        }
    }

    @Nullable
    public static Vec3 selectedTargetPos(Level level, BlockPos monitor) {
        BlockEntity controller = monitorControllerBe(level, monitor);
        if (controller == null || level.isClientSide) {
            return null;
        }
        try {
            Object pos = monitorGetTargetPos.invoke(controller, targetingDefault);
            return pos instanceof Vec3 at ? at : null;
        } catch (Throwable t) {
            CBCMoreContent.LOGGER.debug("Radar screen target read failed: {}", t.toString());
            return null;
        }
    }

    @Nullable
    private static BlockEntity monitorControllerBe(Level level, BlockPos monitor) {
        BlockEntity bound = level.getBlockEntity(monitor);
        if (!isMonitor(bound)) {
            return null;
        }
        try {
            Object controller = monitorGetController.invoke(bound);
            return controller instanceof BlockEntity be ? be : bound;
        } catch (Throwable t) {
            CBCMoreContent.LOGGER.debug("Radar screen controller resolve failed: {}", t.toString());
            return bound;
        }
    }

    @Nullable
    private static BlockEntity controllerOf(BlockEntity bound) {
        try {
            Object controller = monitorGetController.invoke(bound);
            return controller instanceof BlockEntity be ? be : bound;
        } catch (Throwable t) {
            return bound;
        }
    }

    private static boolean resolveMonitor() {
        if (monitorResolved) {
            return monitorClass != null;
        }
        monitorResolved = true;
        if (!loaded()) {
            return false;
        }
        try {
            Class<?> config = Class.forName(TARGETING_CONFIG_CLASS);
            monitorClass = Class.forName(MONITOR_CLASS);
            monitorGetController = monitorClass.getMethod("getController");
            monitorGetTargetPos = monitorClass.getMethod("getTargetPos", config);
            targetingDefault = config.getField("DEFAULT").get(null);
        } catch (Throwable t) {
            CBCMoreContent.LOGGER.info(
                    "Create Radar is present but its screen API did not resolve; "
                            + "hardpoint target relay stays inert. {}",
                    t.toString());
            monitorClass = null;
            return false;
        }
        try {
            monitorGetSelectedEntity = monitorClass.getMethod("getSelectedEntity");
        } catch (Throwable ignored) {
            monitorGetSelectedEntity = null;
        }
        if (monitorGetSelectedEntity == null) {
            try {
                monitorSelectedEntity = monitorClass.getField("selectedEntity");
            } catch (Throwable ignored) {
                monitorSelectedEntity = null;
            }
        }
        try {
            monitorGetRadarSnapshots = monitorClass.getMethod("getRadarSnapshots");
        } catch (Throwable ignored) {
            monitorGetRadarSnapshots = null;
        }
        try {
            monitorGetTracks = monitorClass.getMethod("getTracks");
        } catch (Throwable ignored) {
            monitorGetTracks = null;
        }
        if (monitorGetSelectedEntity == null && monitorSelectedEntity == null) {
            CBCMoreContent.LOGGER.info("Create Radar exposes neither getSelectedEntity() nor selectedEntity; "
                    + "the hardpoint relay cannot read the screen's selection.");
        }
        return true;
    }

    @Nullable
    public static Contact bestContact(Level level, BlockPos controller, Vec3 from, InterceptSettings settings) {
        Contact best = null;
        double bestScore = -Double.MAX_VALUE;
        double maxRange = settings.maxRange();
        double rangeSqr = maxRange * maxRange;
        for (Contact contact : contacts(level, controller)) {
            double distanceSqr = contact.position().distanceToSqr(from);
            if (distanceSqr > rangeSqr) {
                continue;
            }
            if (contact.velocity().length() < settings.minSpeed()) {
                continue;
            }
            if (settings.hullsOnly()
                    && !contact.hull()
                    && !SableDropCompat.isInsideSubLevel(level, contact.position())) {
                continue;
            }
            double score = contact.velocity().length() * 40.0D - Math.sqrt(distanceSqr) / maxRange;
            if (score > bestScore) {
                bestScore = score;
                best = contact;
            }
        }
        return best;
    }

    @Nullable
    public static Contact contactById(Level level, BlockPos controller, String id) {
        for (Contact contact : contacts(level, controller)) {
            if (contact.id().equals(id)) {
                return contact;
            }
        }
        return null;
    }

    private static final int SOURCE_SCAN_RADIUS = 48;

    private static final int SOURCE_CACHE_TICKS = 10;

    @Nullable
    private static Level cachedSourceLevel;

    @Nullable
    private static BlockPos cachedSourceController;

    @Nullable
    private static List<BlockEntity> cachedSources;

    private static long cachedSourceGameTime;

    private static Iterable<Contact> contacts(Level level, BlockPos controller) {
        if (!loaded() || !resolve() || !level.isLoaded(controller)) {
            return List.of();
        }
        List<Contact> out = new ArrayList<>();
        BlockEntity bound = level.getBlockEntity(controller);
        if (isController(bound)) {
            readContacts(level, bound, out);
            return out;
        }
        Set<BlockPos> read = new HashSet<>();
        for (BlockEntity source : monitorNetworkSources(level, bound)) {
            if (read.add(source.getBlockPos())) {
                readContacts(level, source, out);
            }
        }
        if (out.isEmpty()) {
            for (BlockEntity source : sourcesNear(level, controller)) {
                if (read.add(source.getBlockPos())) {
                    readContacts(level, source, out);
                }
            }
        }
        if (out.isEmpty()) {
            readMonitorTracks(level, bound, out);
        }
        return out;
    }

    private static List<BlockEntity> monitorNetworkSources(Level level, @Nullable BlockEntity bound) {
        if (bound == null || !isMonitor(bound) || monitorGetRadarSnapshots == null) {
            return List.of();
        }
        BlockEntity monitor = controllerOf(bound);
        try {
            Object snapshots = monitorGetRadarSnapshots.invoke(monitor);
            if (!(snapshots instanceof Collection<?> list)) {
                return List.of();
            }
            List<BlockEntity> found = new ArrayList<>();
            for (Object snapshot : list) {
                if (snapshot == null) {
                    continue;
                }
                BlockPos radarPos = snapshotPos(snapshot);
                if (radarPos == null) {
                    continue;
                }
                BlockEntity source = level.getBlockEntity(radarPos);
                if (isController(source)) {
                    found.add(source);
                }
            }
            return found;
        } catch (Throwable t) {
            CBCMoreContent.LOGGER.debug("Radar network snapshot read failed: {}", t.toString());
            return List.of();
        }
    }

    @Nullable
    private static BlockPos snapshotPos(Object snapshot) {
        try {
            Class<?> type = snapshot.getClass();
            if (snapshotPosAccessor == null || snapshotClass != type) {
                snapshotClass = type;
                snapshotPosAccessor = type.getMethod("radarPos");
            }
            return snapshotPosAccessor.invoke(snapshot) instanceof BlockPos pos ? pos : null;
        } catch (Throwable t) {
            snapshotPosAccessor = null;
            return null;
        }
    }

    private static void readMonitorTracks(Level level, @Nullable BlockEntity bound, List<Contact> out) {
        if (bound == null || !resolveMonitor() || monitorGetTracks == null || !isMonitor(bound)) {
            return;
        }
        try {
            Object tracks = monitorGetTracks.invoke(controllerOf(bound));
            if (tracks instanceof Collection<?> list) {
                for (Object track : list) {
                    if (track != null) {
                        readTrack(level, track, out);
                    }
                }
            }
        } catch (Throwable t) {
            CBCMoreContent.LOGGER.debug("Radar screen track read failed: {}", t.toString());
        }
    }

    private static boolean isShipId(Level level, String id) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        try {
            return SableDropCompat.subLevelByUuid(server, UUID.fromString(id)) != null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static List<BlockEntity> sourcesNear(Level level, BlockPos controller) {
        BlockEntity bound = level.getBlockEntity(controller);
        if (isController(bound)) {
            return List.of(bound);
        }
        if (!isRadarModBlock(bound)) {
            return List.of();
        }

        List<BlockEntity> cached = cacheIfFresh(level, controller);
        if (cached != null) {
            return cached;
        }

        List<BlockEntity> found = new ArrayList<>();
        int minChunkX = (controller.getX() - SOURCE_SCAN_RADIUS) >> 4;
        int maxChunkX = (controller.getX() + SOURCE_SCAN_RADIUS) >> 4;
        int minChunkZ = (controller.getZ() - SOURCE_SCAN_RADIUS) >> 4;
        int maxChunkZ = (controller.getZ() + SOURCE_SCAN_RADIUS) >> 4;
        int reachSqr = SOURCE_SCAN_RADIUS * SOURCE_SCAN_RADIUS;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (BlockEntity candidate :
                        level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (isController(candidate) && candidate.getBlockPos().distSqr(controller) <= reachSqr) {
                        found.add(candidate);
                    }
                }
            }
        }

        cachedSourceLevel = level;
        cachedSourceController = controller.immutable();
        cachedSourceGameTime = level.getGameTime();
        cachedSources = found;
        return found;
    }

    @Nullable
    private static List<BlockEntity> cacheIfFresh(Level level, BlockPos controller) {
        if (cachedSources == null
                || cachedSourceLevel != level
                || cachedSourceController == null
                || !cachedSourceController.equals(controller)
                || level.getGameTime() - cachedSourceGameTime >= SOURCE_CACHE_TICKS) {
            return null;
        }
        for (BlockEntity source : cachedSources) {
            if (source.isRemoved()) {
                cachedSources = null;
                return null;
            }
        }
        return cachedSources;
    }

    private static void readContacts(Level level, BlockEntity source, List<Contact> out) {
        try {
            if (!(Boolean) isRunning.invoke(source)) {
                return;
            }
            Collection<?> raw = (Collection<?>) getContacts.invoke(source);
            if (raw == null || raw.isEmpty()) {
                return;
            }
            for (Object track : raw) {
                if (track != null) {
                    readTrack(level, track, out);
                }
            }
        } catch (Throwable t) {
            CBCMoreContent.LOGGER.debug("Radar contact read failed: {}", t.toString());
        }
    }

    private static void readTrack(Level level, Object track, List<Contact> out) {
        try {
            Object id = contactId.invoke(track);
            Object position = contactPosition.invoke(track);
            Object velocity = contactVelocity.invoke(track);
            if (id instanceof String name && position instanceof Vec3 at) {
                out.add(new Contact(
                        name, at, velocity instanceof Vec3 moving ? moving : Vec3.ZERO, isShipId(level, name)));
            }
        } catch (Throwable t) {
            CBCMoreContent.LOGGER.debug("Radar contact read failed: {}", t.toString());
        }
    }

    private static boolean resolve() {
        if (resolved) {
            return sourceClass != null;
        }
        resolved = true;
        if (!loaded()) {
            return false;
        }
        try {
            Class<?> source = Class.forName(SOURCE_CLASS);
            Class<?> contact = Class.forName("com.happysg.radar.api.tracking.RadarContact");
            getContacts = source.getMethod("getContacts");
            isRunning = source.getMethod("isRunning");
            contactId = contact.getMethod("getId");
            contactPosition = contact.getMethod("getPosition");
            contactVelocity = contact.getMethod("getVelocity");
            sourceClass = source;
            return true;
        } catch (Throwable t) {
            CBCMoreContent.LOGGER.info(
                    "Create Radar is present but its tracking API did not resolve; "
                            + "intercept guidance stays inert. {}",
                    t.toString());
            sourceClass = null;
            return false;
        }
    }
}
