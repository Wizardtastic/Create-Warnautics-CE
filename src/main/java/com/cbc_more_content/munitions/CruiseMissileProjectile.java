package com.cbc_more_content.munitions;

import com.cbc_more_content.CBCMoreContent;
import com.cbc_more_content.block.CruiseMissileBlockEntity.Guidance;
import com.cbc_more_content.bomb.BombSize;
import com.cbc_more_content.compat.RadarCompat;
import com.cbc_more_content.compat.SableDropCompat;
import com.cbc_more_content.config.WarnauticsConfig;
import com.cbc_more_content.damage.BombDamageSource;
import com.cbc_more_content.effects.BombExplosionHandler;
import com.cbc_more_content.radar.InterceptSettings;
import com.cbc_more_content.radar.InterceptSettingsStore;
import com.cbc_more_content.registry.ModSounds;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

public class CruiseMissileProjectile extends Entity {
    public static final int FUEL_TICKS = 1200;

    private static final double CRUISE_SPEED = 1.4D;
    private static final double GRAVITY = 0.085D;
    private static final double DRAG = 0.95D;
    private static final int ARMING_TICKS = 4;

    private static final TicketType<Long> MISSILE_TICKET = TicketType.create("cruise_missile", Long::compareTo, 20);
    private static final int CHUNK_TICKET_RADIUS = 2;

    private static final EntityDataAccessor<Boolean> POWERED =
            SynchedEntityData.defineId(CruiseMissileProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> EJECTING =
            SynchedEntityData.defineId(CruiseMissileProjectile.class, EntityDataSerializers.BOOLEAN);

    public static final int EJECT_TICKS = 14;
    public static final double EJECT_SPEED = 0.62D;
    private static final double EJECT_GRAVITY = 0.03D;
    public static final int DROP_TICKS = 4;

    public static final int IGNITION_TICKS = 5;

    private static final double LIFT_SPEED = 1.75D;

    private static final double LIFT_ACCEL = 0.36D;

    private static final double LIFT_CLEARANCE = 10.0D;

    private static final int LIFT_LIMIT_TICKS = 40;

    private static final double DROP_SPEED = 0.6D;

    private static final double TURN_RATE = 0.095D;

    private static final double LOOKAHEAD = 36.0D;
    private static final double TERMINAL_RANGE = 44.0D;
    private static final double TERMINAL_TURN_RATE = 0.22D;
    private static final double FUSE_RANGE = 2.4D;
    private static final double JINK_DOT = 0.55D;
    private static final float JINK_CHANCE = 0.4f;
    private static final int SHAKEN_TICKS = 26;

    private static final float BLOCK_POWER = 28.05f;

    private static final float ENTITY_POWER = 47.6f;

    private int fuel = FUEL_TICKS;
    private double fuelFraction;
    private boolean dropped;

    private int poweredTicks;
    private MissileFlightProfile flightProfile = MissileFlightProfile.DIRECT;
    private boolean detonated;
    private boolean waterEntered;
    private boolean trackingHull;
    private int shaken;

    @Nullable
    private Vec3 lastAim;

    @Nullable
    private Vec3 lastAimDrift;

    private int ejecting;
    private int igniting;
    private boolean padLaunch;
    private int liftTicks;
    private Vec3 padUp = new Vec3(0.0D, 1.0D, 0.0D);
    private Vec3 padOrigin = Vec3.ZERO;
    private Vec3 carrierVelocity = Vec3.ZERO;
    private Vec3 blastDrift = Vec3.ZERO;

    @Nullable
    private java.util.UUID salvoId;

    @Nullable
    private java.util.UUID targetEntityId;

    private int salvoIndex;
    private int salvoCount = 1;
    private Vec3 salvoOrigin = Vec3.ZERO;
    private final java.util.Set<ChunkPos> ticketChunks = new java.util.HashSet<>();

    @Nullable
    private ChunkPos ticketCenter;

    private final MissileTargetingState targeting = new MissileTargetingState();

    private boolean contactIsHull;

    public CruiseMissileProjectile(EntityType<? extends CruiseMissileProjectile> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(POWERED, true);
        builder.define(EJECTING, false);
    }

    public boolean isPowered() {
        return this.entityData.get(POWERED);
    }

    public void setController(@Nullable BlockPos controller) {
        this.targeting.setController(controller);
    }

    public void setContact(@Nullable String contact) {
        this.targeting.setContact(contact);
    }

    public void setTargetEntity(@Nullable Entity target) {
        this.targetEntityId = target == null ? null : target.getUUID();
    }

    @Nullable
    public java.util.UUID targetEntityId() {
        return this.targetEntityId;
    }

    public void setGuidance(Guidance guidance, @Nullable BlockPos target, int lockedSubLevel) {
        this.targeting.setGuidance(guidance, target, lockedSubLevel);
    }

    public void setFlightProfile(MissileFlightProfile profile) {
        this.flightProfile = profile;
    }

    public MissileFlightProfile flightProfile() {
        return this.flightProfile;
    }

    public void setSalvo(java.util.UUID id, int index, int count) {
        this.setSalvo(id, index, count, this.position());
    }

    public void setSalvo(java.util.UUID id, int index, int count, Vec3 origin) {
        this.salvoId = id;
        this.salvoIndex = Math.clamp(index, 0, 15);
        this.salvoCount = Math.clamp(count, 1, 16);
        this.salvoOrigin = origin;
    }

    public boolean isEjecting() {
        return this.entityData.get(EJECTING);
    }

    public void ejectUpward() {
        this.eject(new Vec3(0, 1, 0), Vec3.ZERO);
    }

    public void igniteOnPad(Vec3 up, Vec3 carrierVelocity) {
        Vec3 dir = up.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : up.normalize();
        this.carrierVelocity = carrierVelocity;
        this.padLaunch = true;
        this.padUp = dir;
        this.padOrigin = this.position();
        this.liftTicks = 0;
        this.igniting = IGNITION_TICKS;
        this.entityData.set(POWERED, false);
        this.entityData.set(EJECTING, false);
        this.setDeltaMovement(Vec3.ZERO);
        this.faceMotion(dir);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.level()
                .playSound(
                        null,
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        ModSounds.CRUISE_MISSILE_ENGINE.get(),
                        SoundSource.HOSTILE,
                        1.8f,
                        0.55f);
    }

    public void eject(Vec3 heading, Vec3 carrierVelocity) {
        this.carrierVelocity = carrierVelocity;
        this.ejecting = EJECT_TICKS;
        this.entityData.set(EJECTING, true);
        this.entityData.set(POWERED, false);
        this.setDeltaMovement(heading.normalize().scale(EJECT_SPEED).add(carrierVelocity));
        this.faceMotion(heading);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public void drop(Vec3 forward, Vec3 carrierVelocity) {
        this.carrierVelocity = carrierVelocity;
        this.dropped = true;
        this.ejecting = DROP_TICKS;
        this.entityData.set(EJECTING, true);
        this.entityData.set(POWERED, false);
        this.setDeltaMovement(carrierVelocity.add(0.0D, -DROP_SPEED, 0.0D));
        Vec3 nose = forward.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : forward.normalize();
        this.faceMotion(nose);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public void launch(Vec3 heading) {
        this.launch(heading, Vec3.ZERO);
    }

    public void launch(Vec3 heading, Vec3 carrierVelocity) {
        this.carrierVelocity = carrierVelocity;
        Vec3 dir = heading.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : heading.normalize();
        this.setDeltaMovement(dir.scale(CRUISE_SPEED).add(carrierVelocity));
        this.setYRot((float) (Math.atan2(dir.z, dir.x) * 180.0D / Math.PI) - 90.0f);
        this.setXRot((float) (-Math.asin(dir.y) * 180.0D / Math.PI));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.detonated) {
            return;
        }

        this.refreshChunkTickets();
        if (this.igniting > 0) {
            this.spoolUp();
            return;
        }
        if (this.padLaunch) {
            this.liftOffRail();
            return;
        }
        if (this.ejecting > 0) {
            this.coastOutOfRack();
            return;
        }

        if (!this.waterEntered && this.isInWater()) {
            this.waterEntered = true;
            this.entityData.set(POWERED, false);
            this.level()
                    .playSound(
                            null,
                            this.getX(),
                            this.getY(),
                            this.getZ(),
                            SoundEvents.FIRE_EXTINGUISH,
                            SoundSource.HOSTILE,
                            2.0f,
                            0.55f);
        }

        Vec3 aim = this.aimPoint();
        if (this.fuel > 0 && !this.waterEntered) {
            this.poweredTicks++;
            double range =
                    aim == null ? Double.POSITIVE_INFINITY : this.position().distanceTo(aim);
            double plannedSpeed = this.flightProfile.speed(
                    this.poweredTicks, range, this.getUUID().getLeastSignificantBits());
            this.fuelFraction += this.flightProfile.fuelPerTick(plannedSpeed);
            int spent = (int) this.fuelFraction;
            this.fuelFraction -= spent;
            this.fuel = Math.max(0, this.fuel - spent);
            if (this.fuel == 0) {
                this.entityData.set(POWERED, false);
                this.level()
                        .playSound(
                                null,
                                this.getX(),
                                this.getY(),
                                this.getZ(),
                                SoundEvents.FIRE_EXTINGUISH,
                                SoundSource.HOSTILE,
                                2.2f,
                                0.6f);
            }
        }

        this.watchForJink(aim);

        Vec3 motion = this.getDeltaMovement().subtract(this.blastDrift);
        if (this.isPowered() && !this.waterEntered) {
            motion = this.steer(motion.subtract(this.carrierVelocity).normalize(), aim)
                    .scale(this.speedFor(aim))
                    .add(this.carrierVelocity);
            this.carrierVelocity = this.carrierVelocity.scale(0.92D);
            if (this.tickCount % 4 == 0) {
                this.level()
                        .playSound(
                                null,
                                this.getX(),
                                this.getY(),
                                this.getZ(),
                                ModSounds.CRUISE_MISSILE_ENGINE.get(),
                                SoundSource.HOSTILE,
                                3.0f,
                                0.9f + this.random.nextFloat() * 0.1f);
            }
        } else {
            motion = motion.scale(DRAG).subtract(0.0D, GRAVITY, 0.0D);
        }
        motion = motion.add(this.blastDrift);
        this.blastDrift = this.blastDrift.scale(.82);
        this.setDeltaMovement(motion);

        Vec3 from = this.position();
        Vec3 to = from.add(motion);
        if (this.tickCount > ARMING_TICKS && this.checkImpact(from, to)) {
            return;
        }

        this.setPos(to);
        this.faceMotion(motion);
        this.fuseOnTarget(from, aim);
    }

    private void spoolUp() {
        this.igniting--;
        // The deck carries the round while it waits, exactly as the hull carries the pad.
        this.setDeltaMovement(this.carrierVelocity);
        if (this.carrierVelocity.lengthSqr() > 1.0E-6D) {
            this.setPos(this.position().add(this.carrierVelocity));
        }
        this.exhaustSmoke(1.0D + (IGNITION_TICKS - this.igniting) * 0.6D);
        if (this.igniting > 0) {
            return;
        }
        this.level()
                .playSound(
                        null,
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        ModSounds.CRUISE_MISSILE_LAUNCH.get(),
                        SoundSource.HOSTILE,
                        4.0f,
                        0.95f);
        // No coasting: the first boost step goes down on the tick the rail lets go.
        Vec3 motion = this.padUp.scale(LIFT_ACCEL).add(this.carrierVelocity);
        this.setDeltaMovement(motion);
        this.setPos(this.position().add(motion));
    }

    private void liftOffRail() {
        this.liftTicks++;
        Vec3 rise = this.getDeltaMovement().subtract(this.carrierVelocity).add(this.padUp.scale(LIFT_ACCEL));
        if (rise.length() > LIFT_SPEED) {
            rise = rise.normalize().scale(LIFT_SPEED);
        }
        Vec3 motion = rise.add(this.carrierVelocity);
        this.setDeltaMovement(motion);

        Vec3 from = this.position();
        Vec3 to = from.add(motion);
        if (this.tickCount > ARMING_TICKS && this.checkImpact(from, to)) {
            return;
        }
        this.setPos(to);
        this.faceMotion(this.padUp);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        if (this.tickCount % 2 == 0) {
            this.exhaustSmoke(2.4D);
        }

        if (to.subtract(this.padOrigin).dot(this.padUp) < LIFT_CLEARANCE && this.liftTicks < LIFT_LIMIT_TICKS) {
            return;
        }
        this.padLaunch = false;
        this.entityData.set(POWERED, true);
        this.setDeltaMovement(this.padUp.scale(this.speedFor(null)).add(this.carrierVelocity));
    }

    private void exhaustSmoke(double strength) {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 tail = this.position().subtract(this.padUp.scale(1.35D));
        double spread = 0.1D + strength * 0.06D;
        server.sendParticles(
                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                tail.x,
                tail.y,
                tail.z,
                (int) Math.ceil(strength * 2.0D),
                spread,
                0.08D,
                spread,
                0.02D);
        server.sendParticles(
                ParticleTypes.FLAME,
                tail.x,
                tail.y,
                tail.z,
                (int) Math.ceil(strength * 3.0D),
                spread * 0.6D,
                0.06D,
                spread * 0.6D,
                0.01D);
    }

    private void coastOutOfRack() {
        this.ejecting--;
        Vec3 motion = this.getDeltaMovement().subtract(0.0D, EJECT_GRAVITY, 0.0D);
        this.setDeltaMovement(motion);
        this.setPos(this.position().add(motion));
        if (!this.dropped) {
            this.faceMotion(motion);
        }

        if (this.ejecting > 0) {
            return;
        }

        this.entityData.set(EJECTING, false);
        this.entityData.set(POWERED, true);
        Vec3 heading;
        if (this.dropped) {
            heading = Vec3.directionFromRotation(this.getXRot(), this.getYRot());
            this.carrierVelocity = Vec3.ZERO;
        } else {
            Vec3 relative = motion.subtract(this.carrierVelocity);
            heading = relative.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 1.0D, 0.0D) : relative.normalize();
        }
        this.setDeltaMovement(heading.scale(CRUISE_SPEED).add(this.carrierVelocity));
        this.level()
                .playSound(
                        null,
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        ModSounds.CRUISE_MISSILE_LAUNCH.get(),
                        SoundSource.HOSTILE,
                        5.0f,
                        0.72f);
    }

    private double speedFor(@Nullable Vec3 aim) {
        double range = aim == null ? Double.POSITIVE_INFINITY : this.position().distanceTo(aim);
        return this.flightProfile.speed(this.poweredTicks, range, this.getUUID().getLeastSignificantBits());
    }

    private void watchForJink(@Nullable Vec3 aim) {
        if (this.shaken > 0) {
            --this.shaken;
        }
        if (aim == null) {
            this.lastAim = null;
            this.lastAimDrift = null;
            return;
        }
        if (this.lastAim != null) {
            Vec3 drift = aim.subtract(this.lastAim);
            if (this.lastAimDrift != null
                    && drift.lengthSqr() > 0.0025D
                    && this.lastAimDrift.lengthSqr() > 0.0025D
                    && this.shaken == 0
                    && drift.normalize().dot(this.lastAimDrift.normalize()) < JINK_DOT
                    && this.position().distanceToSqr(aim) <= TERMINAL_RANGE * TERMINAL_RANGE
                    && this.random.nextFloat() < JINK_CHANCE) {
                this.shaken = SHAKEN_TICKS;
            }
            this.lastAimDrift = drift;
        }
        this.lastAim = aim;
    }

    private Vec3 steer(Vec3 heading, @Nullable Vec3 aim) {
        if (aim == null) {
            return turnToward(heading, this.separate(heading), .12);
        }
        Vec3 toTarget = aim.subtract(this.position());
        double distance = toTarget.length();
        if (distance < 1.0E-4D) {
            return heading;
        }
        Vec3 shaped = this.flightProfile.shapedAim(
                this.position(), aim, this.poweredTicks, this.getUUID().getLeastSignificantBits());
        if (this.salvoId != null && this.salvoCount > 1) {
            Vec3 forward = aim.subtract(this.salvoOrigin).normalize();
            Vec3 side = forward.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < .01) {
                side = new Vec3(1, 0, 0);
            }
            side = side.normalize();
            Vec3 up = side.cross(forward).normalize();
            double angle = this.salvoIndex * (Math.PI * 2 / this.salvoCount);
            double radius = Math.max(3.5, this.salvoCount * .8) * Mth.clamp((distance - 5) / 24, 0, 1);
            if (this.flightProfile != MissileFlightProfile.EVASIVE && distance > TERMINAL_RANGE) {
                double along = Mth.clamp(
                        this.position().subtract(this.salvoOrigin).dot(forward) + 30,
                        0,
                        aim.distanceTo(this.salvoOrigin));
                Vec3 profileOffset = shaped.subtract(aim).scale(Math.min(1, 30 / distance));
                shaped = this.salvoOrigin.add(forward.scale(along)).add(profileOffset);
            }
            shaped = shaped.add(side.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
        }
        Vec3 wanted = shaped.subtract(this.position()).normalize();
        if (distance > TERMINAL_RANGE) {
            Vec3 separated = this.separate(this.avoid(heading, wanted));
            return turnToward(heading, separated, separated.dot(wanted) < .995 ? .12 : TURN_RATE);
        }
        return turnToward(
                heading,
                distance > 5 ? this.separate(wanted) : wanted,
                this.shaken > 0 ? TURN_RATE : TERMINAL_TURN_RATE);
    }

    private Vec3 separate(Vec3 wanted) {
        Vec3 force = Vec3.ZERO;
        int count = 0;
        for (var other : this.level()
                .getEntitiesOfClass(
                        CruiseMissileProjectile.class,
                        this.getBoundingBox().inflate(8),
                        e -> e != this && e.isAlive())) {
            Vec3 offset = this.position().subtract(other.position());
            Vec3 relative = this.getDeltaMovement().subtract(other.getDeltaMovement());
            double time =
                    relative.lengthSqr() < .001 ? 0 : Mth.clamp(-offset.dot(relative) / relative.lengthSqr(), 0, 4);
            Vec3 future = offset.add(relative.scale(time));
            double distance = Math.min(offset.length(), future.length());
            if (distance >= 6.5) {
                continue;
            }
            Vec3 away = future.lengthSqr() > .04 ? future : offset;
            if (away.lengthSqr() < .001) {
                away = new Vec3(this.getUUID().compareTo(other.getUUID()) < 0 ? 1 : -1, 0, 0);
            }
            force = force.add(away.normalize().scale((6.5 - distance) / 6.5));
            if (++count == 16) {
                break;
            }
        }
        return wanted.add(force.scale(1.5)).normalize();
    }

    private void fuseOnTarget(Vec3 from, @Nullable Vec3 aim) {
        if (this.detonated || aim == null || this.tickCount <= ARMING_TICKS || this.trackingHull || this.shaken > 0) {
            return;
        }
        Vec3 span = this.position().subtract(from);
        double lengthSqr = span.lengthSqr();
        double part = lengthSqr < 1.0E-8D ? 0 : Mth.clamp(aim.subtract(from).dot(span) / lengthSqr, 0, 1);
        Vec3 nearest = from.add(span.scale(part));
        if (nearest.distanceToSqr(aim) <= FUSE_RANGE * FUSE_RANGE) {
            this.detonate(nearest);
        }
    }

    @Nullable
    private Vec3 aimPoint() {
        this.trackingHull = false;
        if (this.targetEntityId != null) {
            Entity hunted = this.level() instanceof ServerLevel server ? server.getEntity(this.targetEntityId) : null;
            if (hunted != null && hunted.isAlive()) {
                return this.dispersedAim(hunted.position(), 1.8);
            }
            // The quarry is gone; a launch point still on file is better than flying on blind.
            this.targetEntityId = null;
        }
        if (this.targeting.guidance() == Guidance.INTERCEPT) {
            Vec3 tracked = this.radarAim();
            if (tracked != null) {
                this.trackingHull = this.contactIsHull;
                return this.dispersedAim(tracked, this.contactIsHull ? .6 : 1.8);
            }
        }
        if (this.targeting.guidance() == Guidance.LOCK
                && this.targeting.lockedSubLevel() >= 0
                && ModList.get().isLoaded("sable")
                && this.level() instanceof ServerLevel server) {
            Vec3 tracked = SableDropCompat.subLevelCentre(server, this.targeting.lockedSubLevel());
            if (tracked != null) {
                this.trackingHull = true;
                return this.dispersedAim(tracked, .6);
            }
        }
        return this.targeting.target() == null || this.targeting.guidance() == Guidance.NONE
                ? null
                : this.dispersedAim(Vec3.atCenterOf(this.targeting.target()), 1.8);
    }

    private Vec3 dispersedAim(Vec3 target, double radius) {
        return target.add(MissileGuidanceError.cruiseOffset(this.getUUID(), radius));
    }

    public void applyBlastImpulse(Vec3 impulse) {
        this.blastDrift = this.blastDrift.add(impulse);
        this.setDeltaMovement(this.getDeltaMovement().add(impulse));
        this.hasImpulse = true;
        this.hurtMarked = true;
    }

    @Nullable
    private Vec3 radarAim() {
        if (this.targeting.controller() == null || !RadarCompat.loaded()) {
            return null;
        }
        if (this.targeting.contact() != null) {
            var held = RadarCompat.contactById(this.level(), this.targeting.controller(), this.targeting.contact());
            if (held != null) {
                this.contactIsHull = held.hull();
                return held.position();
            }
            if (this.targeting.target() != null) {
                return null;
            }
            this.targeting.setContact(null);
        }
        var settings = this.level() instanceof ServerLevel server
                ? InterceptSettingsStore.get(server).forController(this.targeting.controller())
                : InterceptSettings.DEFAULT;
        var fresh = RadarCompat.bestContact(this.level(), this.targeting.controller(), this.position(), settings);
        if (fresh == null) {
            return null;
        }
        this.contactIsHull = fresh.hull();
        this.targeting.setContact(fresh.id());
        return fresh.position();
    }

    private Vec3 avoid(Vec3 heading, Vec3 wanted) {
        Vec3 from = this.position();
        BlockHitResult hit = this.level()
                .clip(new ClipContext(
                        from,
                        from.add(heading.scale(LOOKAHEAD)),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        this));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return wanted;
        }

        double closeness = 1.0D - Math.sqrt(hit.getLocation().distanceToSqr(from)) / LOOKAHEAD;
        Vec3 lift = new Vec3(
                hit.getDirection().getStepX(),
                Math.max(0.35D, hit.getDirection().getStepY()),
                hit.getDirection().getStepZ());
        return wanted.add(lift.scale(Mth.clamp(closeness, 0.0D, 1.0D) * 1.4D)).normalize();
    }

    private static Vec3 turnToward(Vec3 from, Vec3 to, double maxRadians) {
        return MissileCollision.turn(from, to, maxRadians);
    }

    private boolean checkImpact(Vec3 from, Vec3 to) {
        BlockHitResult block =
                this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 stop = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : null;

        if (ModList.get().isLoaded("sable") && this.level() instanceof ServerLevel server) {
            Vec3 hull = SableDropCompat.clipSubLevels(server, from, to);
            if (hull != null && (stop == null || from.distanceToSqr(hull) < from.distanceToSqr(stop))) {
                stop = hull;
            }
        }

        AABB sweep = new AABB(from, to).inflate(3);
        for (Entity entity : this.level().getEntities(this, sweep, this::canHit)) {
            Vec3 at = MissileCollision.contact(this, entity, from, to);
            if (at != null && (stop == null || from.distanceToSqr(at) < from.distanceToSqr(stop))) {
                stop = at;
            }
        }

        if (stop == null) {
            return false;
        }
        this.detonate(stop);
        return true;
    }

    private boolean canHit(Entity entity) {
        return entity.isAlive() && entity.isPickable() && !entity.isSpectator();
    }

    private void refreshChunkTickets() {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        ChunkPos center = new ChunkPos(this.blockPosition());
        if (center.equals(this.ticketCenter) && this.tickCount % 10 != 0) {
            return;
        }
        var previous = this.ticketChunks.iterator();
        while (previous.hasNext()) {
            ChunkPos old = previous.next();
            if (Math.abs(old.x - center.x) > CHUNK_TICKET_RADIUS || Math.abs(old.z - center.z) > CHUNK_TICKET_RADIUS) {
                server.getChunkSource()
                        .removeRegionTicket(
                                MISSILE_TICKET, old, 2, this.getUUID().getLeastSignificantBits());
                previous.remove();
            }
        }
        this.ticketCenter = center;
        for (int x = -CHUNK_TICKET_RADIUS; x <= CHUNK_TICKET_RADIUS; x++) {
            for (int z = -CHUNK_TICKET_RADIUS; z <= CHUNK_TICKET_RADIUS; z++) {
                ChunkPos active = new ChunkPos(center.x + x, center.z + z);
                this.ticketChunks.add(active);
                server.getChunkSource()
                        .addRegionTicket(
                                MISSILE_TICKET, active, 2, this.getUUID().getLeastSignificantBits());
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (this.level() instanceof ServerLevel server) {
            for (ChunkPos chunk : this.ticketChunks) {
                server.getChunkSource()
                        .removeRegionTicket(
                                MISSILE_TICKET, chunk, 2, this.getUUID().getLeastSignificantBits());
            }
            this.ticketChunks.clear();
            this.ticketCenter = null;
        }
        super.remove(reason);
    }

    private void detonate(Vec3 at) {
        if (this.detonated || !(this.level() instanceof ServerLevel server)) {
            return;
        }
        this.detonated = true;
        try {
            detonateWarhead(server, this, at);
        } catch (Throwable t) {
            CBCMoreContent.LOGGER.error("Cruise missile detonation failed at {}", at, t);
        } finally {
            this.discard();
        }
    }

    public static void detonateWarhead(ServerLevel level, @Nullable Entity source, Vec3 at) {
        BombExplosionHandler.detonate(
                level,
                source,
                BombDamageSource.create(level),
                at,
                BLOCK_POWER,
                ENTITY_POWER,
                BombSize.MOAB,
                WarnauticsConfig.maxBlocksPerDetonation());
    }

    private void faceMotion(Vec3 motion) {
        if (motion.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 dir = motion.normalize();
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.setYRot((float) (Math.atan2(dir.z, dir.x) * 180.0D / Math.PI) - 90.0f);
        this.setXRot((float) (-Math.asin(dir.y) * 180.0D / Math.PI));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (amount <= 0 || this.detonated || this.isInvulnerableTo(source)) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION) && amount < 28) {
            return false;
        }
        if (!this.level().isClientSide && !this.detonated) {
            this.detonate(this.position());
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return !this.detonated;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 512.0D * 512.0D;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        this.setPos(x, y, z);
        this.setRot(yaw, pitch);
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        this.setDeltaMovement(x, y, z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.fuel = tag.getInt("Fuel");
        this.fuelFraction = tag.getDouble("FuelFraction");
        this.poweredTicks = tag.getInt("PoweredTicks");
        this.flightProfile = MissileFlightProfile.byId(tag.getInt("FlightProfile"));
        this.entityData.set(POWERED, tag.getBoolean("Powered"));
        this.waterEntered = tag.getBoolean("WaterEntered");
        this.ejecting = Math.clamp(tag.getInt("Ejecting"), 0, EJECT_TICKS);
        this.entityData.set(EJECTING, this.ejecting > 0);
        this.dropped = tag.getBoolean("Dropped");
        this.igniting = Math.clamp(tag.getInt("Igniting"), 0, IGNITION_TICKS);
        this.padLaunch = tag.getBoolean("PadLaunch");
        this.liftTicks = tag.getInt("LiftTicks");
        this.padUp = new Vec3(tag.getDouble("PadUpX"), tag.getDouble("PadUpY"), tag.getDouble("PadUpZ"));
        if (this.padUp.lengthSqr() < 1.0E-6D) {
            this.padUp = new Vec3(0.0D, 1.0D, 0.0D);
        }
        this.padOrigin =
                new Vec3(tag.getDouble("PadOriginX"), tag.getDouble("PadOriginY"), tag.getDouble("PadOriginZ"));
        this.carrierVelocity =
                new Vec3(tag.getDouble("CarrierX"), tag.getDouble("CarrierY"), tag.getDouble("CarrierZ"));
        this.blastDrift =
                new Vec3(tag.getDouble("BlastDriftX"), tag.getDouble("BlastDriftY"), tag.getDouble("BlastDriftZ"));
        this.targeting.readFrom(tag);
        this.targetEntityId = tag.hasUUID("TargetEntity") ? tag.getUUID("TargetEntity") : null;
        this.salvoId = tag.hasUUID("Salvo") ? tag.getUUID("Salvo") : null;
        this.salvoIndex = Math.clamp(tag.getInt("SalvoIndex"), 0, 15);
        this.salvoCount = Math.clamp(tag.getInt("SalvoCount"), 1, 16);
        this.salvoOrigin = new Vec3(tag.getDouble("SalvoX"), tag.getDouble("SalvoY"), tag.getDouble("SalvoZ"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Fuel", this.fuel);
        tag.putDouble("FuelFraction", this.fuelFraction);
        tag.putInt("PoweredTicks", this.poweredTicks);
        tag.putInt("FlightProfile", this.flightProfile.id());
        tag.putBoolean("Powered", this.isPowered());
        tag.putBoolean("WaterEntered", this.waterEntered);
        tag.putInt("Ejecting", this.ejecting);
        tag.putBoolean("Dropped", this.dropped);
        tag.putInt("Igniting", this.igniting);
        tag.putBoolean("PadLaunch", this.padLaunch);
        tag.putInt("LiftTicks", this.liftTicks);
        tag.putDouble("PadUpX", this.padUp.x);
        tag.putDouble("PadUpY", this.padUp.y);
        tag.putDouble("PadUpZ", this.padUp.z);
        tag.putDouble("PadOriginX", this.padOrigin.x);
        tag.putDouble("PadOriginY", this.padOrigin.y);
        tag.putDouble("PadOriginZ", this.padOrigin.z);
        tag.putDouble("CarrierX", this.carrierVelocity.x);
        tag.putDouble("CarrierY", this.carrierVelocity.y);
        tag.putDouble("CarrierZ", this.carrierVelocity.z);
        tag.putDouble("BlastDriftX", this.blastDrift.x);
        tag.putDouble("BlastDriftY", this.blastDrift.y);
        tag.putDouble("BlastDriftZ", this.blastDrift.z);
        this.targeting.writeTo(tag);
        if (this.targetEntityId != null) {
            tag.putUUID("TargetEntity", this.targetEntityId);
        }
        if (this.salvoId != null) {
            tag.putUUID("Salvo", this.salvoId);
        }
        tag.putInt("SalvoIndex", this.salvoIndex);
        tag.putInt("SalvoCount", this.salvoCount);
        tag.putDouble("SalvoX", this.salvoOrigin.x);
        tag.putDouble("SalvoY", this.salvoOrigin.y);
        tag.putDouble("SalvoZ", this.salvoOrigin.z);
    }
}
