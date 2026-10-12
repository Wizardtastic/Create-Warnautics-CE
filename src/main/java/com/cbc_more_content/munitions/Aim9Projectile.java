package com.cbc_more_content.munitions;

import com.cbc_more_content.bomb.BombSize;
import com.cbc_more_content.compat.SableDropCompat;
import com.cbc_more_content.registry.ModSounds;
import com.cbc_more_content.util.DropBombUtil;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class Aim9Projectile extends Entity {
    public static final int EJECT_TICKS = 14;
    public static final int MOTOR_TICKS = 140;
    public static final int DROP_TICKS = 4;

    private static final double DROP_SPEED = 0.6D;

    private static final double APPROACH_RANGE = 36;
    private static final double RECOVERY_TURN_RATE = .14;
    private static final double EARLY_TURN_RATE = .35;

    private static final double TURN_RATE = .55;
    private static final int TICKET_DISTANCE = 4;
    private static final EntityDataAccessor<Boolean> POWERED =
            SynchedEntityData.defineId(Aim9Projectile.class, EntityDataSerializers.BOOLEAN);
    private static final TicketType<UUID> TICKET = TicketType.create("aim9", UUID::compareTo, 20);

    @Nullable
    private UUID targetId;

    @Nullable
    private UUID targetShipId;

    @Nullable
    private Vec3 lastShipCentre;

    private int ejectTicks = EJECT_TICKS;
    private boolean dropped;

    private int motorTicks;
    private int lostTicks;
    private Vec3 carrierVelocity = Vec3.ZERO;
    private Vec3 blastDrift = Vec3.ZERO;
    private Vec3 lastTargetVelocity = Vec3.ZERO;
    private Vec3 approachOffset = Vec3.ZERO;
    private double closestApproach = Double.MAX_VALUE;
    private int stalledTicks;
    private int approachNumber;
    private int recoveryTicks;
    private boolean approachChecked;
    private boolean recovering;
    private boolean recoveryTurning;

    @Nullable
    private ChunkPos ticketChunk;

    private boolean finished;

    public Aim9Projectile(EntityType<? extends Aim9Projectile> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(POWERED, false);
    }

    public boolean isPowered() {
        return this.entityData.get(POWERED);
    }

    public boolean isEjecting() {
        return !isPowered() && !finished;
    }

    @Nullable
    public UUID targetId() {
        return targetId;
    }

    @Nullable
    public UUID targetShipId() {
        return targetShipId;
    }

    public void lockHull(UUID ship) {
        this.targetShipId = ship;
    }

    public void launch(@Nullable Entity target, Vec3 inheritedVelocity) {
        launch(target, inheritedVelocity, new Vec3(0, 1, 0));
    }

    public void launch(@Nullable Entity target, Vec3 inheritedVelocity, Vec3 ejectDirection) {
        targetId = target == null ? null : target.getUUID();
        lastTargetVelocity = target == null ? Vec3.ZERO : target.getDeltaMovement();
        carrierVelocity = inheritedVelocity;
        Vec3 eject = ejectDirection.lengthSqr() < 1.0E-6D ? new Vec3(0, 1, 0) : ejectDirection.normalize();
        setDeltaMovement(inheritedVelocity.add(eject.scale(.95)));
        face(eject);
        yRotO = getYRot();
        xRotO = getXRot();
    }

    public void drop(@Nullable Entity target, Vec3 inheritedVelocity, Vec3 forward) {
        targetId = target == null ? null : target.getUUID();
        lastTargetVelocity = target == null ? Vec3.ZERO : target.getDeltaMovement();
        carrierVelocity = inheritedVelocity;
        Vec3 nose = forward.lengthSqr() < 1.0E-6D ? new Vec3(0, 1, 0) : forward.normalize();
        dropped = true;
        ejectTicks = DROP_TICKS;
        setDeltaMovement(inheritedVelocity.add(0.0D, -DROP_SPEED, 0.0D));
        face(nose);
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || finished) {
            return;
        }
        var chunk = new ChunkPos(blockPosition());
        if (!chunk.equals(ticketChunk)) {
            releaseTicket(server);
            ticketChunk = chunk;
        }
        server.getChunkSource().addRegionTicket(TICKET, chunk, TICKET_DISTANCE, getUUID());
        Entity target = targetId == null ? null : server.getEntity(targetId);
        Vec3 hullCentre = targetShipId == null ? null : SableDropCompat.subLevelCentreByUuid(server, targetShipId);
        Vec3 motion = getDeltaMovement().subtract(blastDrift);
        if (ejectTicks > 0) {
            ejectTicks--;
            motion = motion.subtract(0, .022, 0);
            if (ejectTicks == 0) {
                entityData.set(POWERED, true);
                server.playSound(
                        null,
                        getX(),
                        getY(),
                        getZ(),
                        SoundEvents.FIREWORK_ROCKET_BLAST,
                        SoundSource.HOSTILE,
                        2.8f,
                        .75f);
                server.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 12, .2, .2, .2, .04);
            }
        } else {
            motorTicks++;
            if (targetId != null || targetShipId != null) {
                boolean tracked = (targetId != null && target != null && target.isAlive())
                        || (targetShipId != null && hullCentre != null);
                if (!tracked) {
                    if (++lostTicks > 12) {
                        finish(null, position(), false);
                        return;
                    }
                } else {
                    lostTicks = 0;
                }
            }
            if (motorTicks > MOTOR_TICKS || isInWater()) {
                finish(null, position(), false);
                return;
            }
            double progress = Mth.clamp(motorTicks / 20.0, 0, 1);
            double speed = .65 + 4.85 * progress * progress * (3 - 2 * progress);
            Vec3 heading = dropped
                    ? Vec3.directionFromRotation(getXRot(), getYRot())
                    : motion.subtract(carrierVelocity).normalize();
            if (dropped) {
                carrierVelocity = Vec3.ZERO;
            }
            if (target != null && target.isAlive()) {
                updateApproach(target, heading, motion, speed);
                if (recovering && recoveryTurning) {
                    speed *= .88;
                }
                Vec3 aim = recovering && !recoveryTurning
                        ? heading
                        : lead(
                                target.position().subtract(position()).add(approachOffset),
                                target.getDeltaMovement(),
                                speed);
                heading = MissileCollision.turn(
                        heading, aim, recovering ? RECOVERY_TURN_RATE : motorTicks < 10 ? EARLY_TURN_RATE : TURN_RATE);
            } else if (hullCentre != null) {
                Vec3 shipVelocity = lastShipCentre == null ? Vec3.ZERO : hullCentre.subtract(lastShipCentre);
                lastShipCentre = hullCentre;
                Vec3 aim = lead(hullCentre.subtract(position()), shipVelocity, speed);
                heading = MissileCollision.turn(heading, aim, motorTicks < 10 ? EARLY_TURN_RATE : TURN_RATE);
            }
            motion = heading.scale(speed).add(carrierVelocity);
            carrierVelocity = carrierVelocity.scale(.9);
            if (tickCount % 8 == 0) {
                server.playSound(
                        null,
                        getX(),
                        getY(),
                        getZ(),
                        ModSounds.CRUISE_MISSILE_ENGINE.get(),
                        SoundSource.HOSTILE,
                        1.8f,
                        1.4f);
            }
        }
        motion = motion.add(blastDrift);
        blastDrift = blastDrift.scale(.82);
        setDeltaMovement(motion);
        Vec3 from = position(), to = from.add(motion);
        var block = server.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 stop = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : null;
        Vec3 hull = SableDropCompat.clipSubLevels(server, from, to);
        if (hull != null && (stop == null || from.distanceToSqr(hull) < from.distanceToSqr(stop))) {
            stop = hull;
        }
        CruiseMissileProjectile struck = null;
        if (isPowered() && target instanceof CruiseMissileProjectile cruise && target.isAlive()) {
            Vec3 relative = motion.subtract(target.getDeltaMovement());
            Vec3 offset = target.position().subtract(from);
            double t = relative.lengthSqr() < 1.0E-8 ? 0 : Mth.clamp(offset.dot(relative) / relative.lengthSqr(), 0, 1);
            if (offset.subtract(relative.scale(t)).lengthSqr() <= 1.3 * 1.3) {
                Vec3 proximity = from.lerp(to, t);
                if (stop == null || from.distanceToSqr(proximity) < from.distanceToSqr(stop)) {
                    stop = proximity;
                    struck = cruise;
                }
            }
        }
        for (Entity other : server.getEntities(
                this, new AABB(from, to).inflate(3), e -> e.isAlive() && e.isPickable() && !e.isSpectator())) {
            Vec3 contact = MissileCollision.contact(this, other, from, to);
            if (contact != null && (stop == null || from.distanceToSqr(contact) < from.distanceToSqr(stop))) {
                stop = contact;
                struck = other instanceof CruiseMissileProjectile cruise ? cruise : null;
            }
        }
        if (stop != null) {
            finish(struck, stop, true);
            return;
        }
        setPos(to);
        if (!dropped || motorTicks > 0) {
            face(motion);
        }
    }

    private void updateApproach(Entity target, Vec3 heading, Vec3 motion, double speed) {
        Vec3 offset = target.position().subtract(position());
        Vec3 velocity = target.getDeltaMovement();
        Vec3 acceleration = velocity.subtract(lastTargetVelocity);
        lastTargetVelocity = velocity;
        double distance = offset.length();
        boolean closing = offset.dot(motion.subtract(velocity)) > 0;
        if (recovering) {
            recoveryTicks++;
            if (!recoveryTurning) {
                recoveryTurning = recoveryTicks >= 6 && distance >= 44;
                return;
            }
            if (recoveryTicks < 8 || !closing || distance < 10 || heading.dot(lead(offset, velocity, speed)) < .94) {
                return;
            }
            recovering = false;
            recoveryTurning = false;
            approachChecked = false;
            closestApproach = Double.MAX_VALUE;
            stalledTicks = 0;
        }
        if (!approachChecked && closing && distance <= APPROACH_RANGE) {
            approachChecked = true;
            closestApproach = distance;
            double chance = MissileGuidanceError.interceptMissChance(offset, motion, velocity, acceleration);
            if (MissileGuidanceError.missesIntercept(getUUID(), approachNumber, chance)) {
                approachOffset =
                        MissileGuidanceError.interceptOffset(getUUID(), approachNumber, lead(offset, velocity, speed));
            }
            approachNumber++;
        }
        if (approachChecked) {
            if (distance < closestApproach - 1.0E-3D) {
                stalledTicks = 0;
            } else if (distance > closestApproach + 2) {
                stalledTicks++;
            }
            closestApproach = Math.min(closestApproach, distance);
            boolean stalled = stalledTicks > 20;
            if ((!closing || stalled) && (distance > closestApproach + 2 || heading.dot(offset.normalize()) < -.1)) {
                recovering = true;
                recoveryTurning = false;
                recoveryTicks = 0;
                approachOffset = Vec3.ZERO;
            }
        }
    }

    private static Vec3 lead(Vec3 offset, Vec3 velocity, double speed) {
        double a = velocity.lengthSqr() - speed * speed;
        double b = 2 * offset.dot(velocity), c = offset.lengthSqr();
        double time = Math.sqrt(c) / Math.max(.5, speed);
        double discriminant = b * b - 4 * a * c;
        if (Math.abs(a) > 1.0E-6 && discriminant >= 0) {
            double first = (-b - Math.sqrt(discriminant)) / (2 * a);
            double second = (-b + Math.sqrt(discriminant)) / (2 * a);
            if (first > 0 || second > 0) {
                time = first > 0 && second > 0 ? Math.min(first, second) : Math.max(first, second);
            }
        }
        return offset.add(velocity.scale(Mth.clamp(time, 0, 45))).normalize();
    }

    private void finish(@Nullable CruiseMissileProjectile target, Vec3 at, boolean burst) {
        if (finished || !(level() instanceof ServerLevel server)) {
            return;
        }
        finished = true;
        entityData.set(POWERED, false);
        if (burst) {
            if (target != null && target.isAlive()) {
                target.hurt(server.damageSources().explosion(this, this), 40);
            }
            DropBombUtil.detonateAsReleasedProjectile(BombSize.MEDIUM, server, at);
            server.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 22, .8, .8, .8, .1);
        } else {
            server.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 6, .2, .2, .2, .02);
        }
        discard();
    }

    private void face(Vec3 motion) {
        if (motion.lengthSqr() < 1.0E-8) {
            return;
        }
        Vec3 dir = motion.normalize();
        yRotO = getYRot();
        xRotO = getXRot();
        setYRot((float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90);
        setXRot((float) -Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1, 1))));
    }

    public void applyBlastImpulse(Vec3 impulse) {
        blastDrift = blastDrift.add(impulse);
        setDeltaMovement(getDeltaMovement().add(impulse));
        hasImpulse = true;
        hurtMarked = true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (amount <= 0 || finished || isInvulnerableTo(source)) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION) && amount < 22) {
            return false;
        }
        if (!level().isClientSide) {
            finish(null, position(), true);
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return !finished;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 512 * 512;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        setPos(x, y, z);
        setRot(yaw, pitch);
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        setDeltaMovement(x, y, z);
    }

    private void releaseTicket(ServerLevel server) {
        if (ticketChunk != null) {
            server.getChunkSource().removeRegionTicket(TICKET, ticketChunk, TICKET_DISTANCE, getUUID());
            ticketChunk = null;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel server) {
            releaseTicket(server);
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        targetId = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        targetShipId = tag.hasUUID("TargetShip") ? tag.getUUID("TargetShip") : null;
        ejectTicks = Mth.clamp(tag.getInt("EjectTicks"), 0, EJECT_TICKS);
        dropped = tag.getBoolean("Dropped");
        motorTicks = Mth.clamp(tag.getInt("MotorTicks"), 0, MOTOR_TICKS + 1);
        lostTicks = tag.getInt("LostTicks");
        carrierVelocity = new Vec3(tag.getDouble("CarrierX"), tag.getDouble("CarrierY"), tag.getDouble("CarrierZ"));
        blastDrift = new Vec3(tag.getDouble("BlastDriftX"), tag.getDouble("BlastDriftY"), tag.getDouble("BlastDriftZ"));
        lastTargetVelocity = new Vec3(
                tag.getDouble("TargetVelocityX"), tag.getDouble("TargetVelocityY"), tag.getDouble("TargetVelocityZ"));
        approachOffset = new Vec3(
                tag.getDouble("ApproachOffsetX"), tag.getDouble("ApproachOffsetY"), tag.getDouble("ApproachOffsetZ"));
        approachChecked = tag.getBoolean("ApproachChecked");
        stalledTicks = Math.max(0, tag.getInt("StalledTicks"));
        recovering = tag.getBoolean("Recovering");
        recoveryTurning = tag.getBoolean("RecoveryTurning");
        approachNumber = Math.max(0, tag.getInt("ApproachNumber"));
        recoveryTicks = Math.max(0, tag.getInt("RecoveryTicks"));
        closestApproach =
                tag.contains("ClosestApproach") ? Math.max(0, tag.getDouble("ClosestApproach")) : Double.MAX_VALUE;
        entityData.set(POWERED, ejectTicks == 0 && motorTicks <= MOTOR_TICKS);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (targetId != null) {
            tag.putUUID("Target", targetId);
        }
        if (targetShipId != null) {
            tag.putUUID("TargetShip", targetShipId);
        }
        tag.putInt("EjectTicks", ejectTicks);
        tag.putBoolean("Dropped", dropped);
        tag.putInt("MotorTicks", motorTicks);
        tag.putInt("LostTicks", lostTicks);
        tag.putDouble("CarrierX", carrierVelocity.x);
        tag.putDouble("CarrierY", carrierVelocity.y);
        tag.putDouble("CarrierZ", carrierVelocity.z);
        tag.putDouble("BlastDriftX", blastDrift.x);
        tag.putDouble("BlastDriftY", blastDrift.y);
        tag.putDouble("BlastDriftZ", blastDrift.z);
        tag.putDouble("TargetVelocityX", lastTargetVelocity.x);
        tag.putDouble("TargetVelocityY", lastTargetVelocity.y);
        tag.putDouble("TargetVelocityZ", lastTargetVelocity.z);
        tag.putDouble("ApproachOffsetX", approachOffset.x);
        tag.putDouble("ApproachOffsetY", approachOffset.y);
        tag.putDouble("ApproachOffsetZ", approachOffset.z);
        tag.putBoolean("ApproachChecked", approachChecked);
        tag.putInt("StalledTicks", stalledTicks);
        tag.putBoolean("Recovering", recovering);
        tag.putBoolean("RecoveryTurning", recoveryTurning);
        tag.putInt("ApproachNumber", approachNumber);
        tag.putInt("RecoveryTicks", recoveryTicks);
        tag.putDouble("ClosestApproach", closestApproach);
    }
}
