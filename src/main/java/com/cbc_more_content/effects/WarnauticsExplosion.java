package com.cbc_more_content.effects;

import com.cbc_more_content.bomb.BombSize;
import com.cbc_more_content.config.WarnauticsConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.multiloader.NetworkPlatform;
import rbasamoyai.createbigcannons.network.ClientboundCBCExplodePacket;
import rbasamoyai.createbigcannons.remix.CustomExplosion;

public final class WarnauticsExplosion extends CustomExplosion.Impl {
    private final float basePower;
    private final BombSize.BlastVolume volume;
    private final long seed;
    private final int blockBudget;
    private final List<Entity> affectedEntities = new ArrayList<>();
    private Map<BlockPos, BlastScorch.Change> surfaceChanges = Map.of();

    public WarnauticsExplosion(
            ServerLevel level,
            @Nullable Entity source,
            DamageSource damageSource,
            Vec3 center,
            float blockPower,
            float entityPower,
            BombSize.BlastVolume volume) {
        this(
                level,
                source,
                damageSource,
                center,
                blockPower,
                entityPower,
                volume,
                WarnauticsConfig.maxBlocksPerDetonation());
    }

    public WarnauticsExplosion(
            ServerLevel level,
            @Nullable Entity source,
            DamageSource damageSource,
            Vec3 center,
            float blockPower,
            float entityPower,
            BombSize.BlastVolume volume,
            int blockBudget) {
        super(
                level,
                source,
                damageSource,
                FluidTransparentBlastCalculator.INSTANCE,
                center.x,
                center.y,
                center.z,
                volume.shellPowerForSameVolume(blockPower),
                entityPower,
                false,
                CBCConfigs.server().munitions.damageRestriction.get().explosiveInteraction());
        this.basePower = blockPower;
        this.volume = volume;
        this.seed = level.random.nextLong();
        this.blockBudget = blockBudget;
    }

    @Override
    public void explode() {
        ServerLevel server = (ServerLevel) this.level;
        BlastImpulse impulses = new BlastImpulse();
        server.gameEvent(getDirectSourceEntity(), GameEvent.EXPLODE, center());
        if (canDamageTerrain() && getBlockInteraction() != BlockInteraction.KEEP) {
            getToBlow().addAll(BlastPropagation.gather(server, this, basePower, volume, seed, blockBudget, impulses));
            getToBlow()
                    .addAll(BlastGlassShatter.gather(
                            server,
                            center(),
                            volume.horizontal(radius()),
                            getToBlow(),
                            blockBudget - getToBlow().size()));
        }
        if (canDamageTerrain()
                && getBlockInteraction() != BlockInteraction.KEEP
                && CBCConfigs.server().munitions.projectilesChangeSurroundings.get()) {
            int cap = blockBudget;
            int scarBudget = BlastScorch.changeBudget();
            double scarRadius = Math.max(5, volume.horizontal(radius()) * (volume.isSphere() ? 2.2 : 0.85));
            surfaceChanges = BlastScorch.gather(
                    server, center(), scarRadius, BlastScorch.SCAR_STRENGTH, getToBlow(), scarBudget, seed);
            int available = cap - surfaceChanges.size();
            if (getToBlow().size() > available) {
                getToBlow().subList(available, getToBlow().size()).clear();
                surfaceChanges = BlastScorch.gather(
                        server,
                        center(),
                        scarRadius,
                        BlastScorch.SCAR_STRENGTH,
                        getToBlow(),
                        Math.min(scarBudget, cap - getToBlow().size()),
                        seed);
            }
            getToBlow().addAll(surfaceChanges.keySet());
        }
        double reach = getEntityRadius() * 2.0D;
        affectedEntities.addAll(
                server.getEntities((Entity) null, new AABB(center(), center()).inflate(reach), Entity::isAlive));
        var protectedBlocks = new HashSet<>(getToBlow());
        NeoForge.EVENT_BUS.post(new ExplosionEvent.Detonate(server, this, affectedEntities));
        var survivingBlocks = new HashSet<>(getToBlow());
        protectedBlocks.removeAll(survivingBlocks);
        impulses.apply(protectedBlocks);
    }

    public List<BlockPos> destroyedBlocks() {
        return getToBlow().stream()
                .filter(pos -> !surfaceChanges.containsKey(pos))
                .toList();
    }

    @Override
    public void finalizeExplosion(boolean particles) {
        var allowedScars = new ArrayList<BlockPos>();
        getToBlow().removeIf(pos -> {
            if (!surfaceChanges.containsKey(pos)) {
                return false;
            }
            allowedScars.add(pos);
            return true;
        });
        super.finalizeExplosion(particles);
        if (canDamageTerrain() && getBlockInteraction() != BlockInteraction.KEEP) {
            for (var pos : allowedScars) {
                surfaceChanges.get(pos).apply((ServerLevel) level, pos);
            }
        }
    }

    public List<Entity> affectedEntities() {
        return affectedEntities;
    }

    @Override
    public void sendExplosionToClient(ServerPlayer player) {
        Vec3 knock = getHitPlayers().getOrDefault(player, Vec3.ZERO);
        NetworkPlatform.sendToClientPlayer(
                new ClientboundCBCExplodePacket(
                        x,
                        y,
                        z,
                        blockSize,
                        entitySize,
                        List.of(),
                        (float) knock.x,
                        (float) knock.y,
                        (float) knock.z,
                        ClientboundCBCExplodePacket.ExplosionType.SHELL_NO_EFFECTS),
                player);
    }
}
