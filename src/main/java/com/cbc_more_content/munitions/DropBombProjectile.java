package com.cbc_more_content.munitions;

import com.cbc_more_content.block.DropBombBlock;
import com.cbc_more_content.bomb.BombSize;
import com.cbc_more_content.damage.BombDamageSource;
import com.cbc_more_content.effects.BombExplosionHandler;
import com.cbc_more_content.effects.BombSympatheticDetonation;
import com.cbc_more_content.registry.ModBlocks;
import com.cbc_more_content.registry.ModEntityTypes;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.index.CBCMunitionPropertiesHandlers;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.config.BigCannonCommonShellProperties;
import rbasamoyai.createbigcannons.munitions.big_cannon.config.BigCannonFuzePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.big_cannon.config.BigCannonProjectilePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.EntityDamagePropertiesComponent;

public class DropBombProjectile extends FuzedBigCannonProjectile {

    public static final float DUD_CHANCE = 0.02f;

    private static final Block[] BURIAL_SOIL = {
        Blocks.GRASS_BLOCK,
        Blocks.DIRT,
        Blocks.COARSE_DIRT,
        Blocks.ROOTED_DIRT,
        Blocks.GRAVEL,
        Blocks.SAND,
        Blocks.RED_SAND
    };

    private static final double BURY_DEPTH = 2.0D;
    private static final double BURY_STEP = 1.0D / 16.0D;
    static final int BURY_STEPS = (int) (BURY_DEPTH / BURY_STEP);
    static final int BURY_TICKS_PER_STEP = (3 * 24000) / BURY_STEPS;

    private static final int IMPACT_ANSWER_TICKS = 60;

    private static final EntityDataAccessor<Boolean> FAILED =
            SynchedEntityData.defineId(DropBombProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LODGED =
            SynchedEntityData.defineId(DropBombProjectile.class, EntityDataSerializers.BOOLEAN);

    private boolean detonated;
    private boolean clientImpact;
    private int impactAnswerTicks;
    private int buryTicks;
    private int burySteps;

    public DropBombProjectile(EntityType<? extends DropBombProjectile> type, Level level) {
        super(type, level);
        this.setLocalSoundCooldown(-1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FAILED, false);
        builder.define(LODGED, false);
    }

    public boolean failedToDetonate() {
        return this.entityData.get(FAILED);
    }

    public boolean isLodged() {
        return this.entityData.get(LODGED);
    }

    protected boolean shouldFailToDetonate() {
        return this.random.nextFloat() < DUD_CHANCE;
    }

    public final void detonateFromPhysicalImpact(Vec3 position) {
        this.forceDetonate(position);
    }

    @Override
    protected boolean onClip(ProjectileContext ctx, Vec3 start, Vec3 end) {
        if (this.failedToDetonate() || this.isLodged()) {
            return false;
        }
        return super.onClip(ctx, start, end);
    }

    @Override
    protected boolean onImpact(HitResult hitResult, ImpactResult impactResult, ProjectileContext projectileContext) {
        if (this.level().isClientSide) {
            this.clientImpact = true;
            this.impactAnswerTicks = IMPACT_ANSWER_TICKS;
            this.setDeltaMovement(Vec3.ZERO);
            return false;
        }
        if (this.failedToDetonate() || this.isLodged()) {
            return false;
        }
        if (this.detonated || this.isRemoved()) {
            return true;
        }
        this.forceDetonate(this.impactDetonationPosition(hitResult));
        return true;
    }

    @Override
    protected ImpactResult calculateBlockPenetration(
            ProjectileContext projectileContext, BlockState state, BlockHitResult blockHitResult) {
        if (!this.level().isClientSide
                && !this.failedToDetonate()
                && !this.isLodged()
                && !this.detonated
                && !this.isRemoved()
                && this.shouldFailToDetonate()) {
            this.entityData.set(FAILED, true);
            this.setDeltaMovement(Vec3.ZERO);
            this.setProjectileMass(0.0f);
            this.impactPos = blockHitResult.getBlockPos().immutable();
            state.onProjectileHit(this.level(), state, blockHitResult, this);
            return new ImpactResult(ImpactResult.KinematicOutcome.STOP, false);
        }
        if (this.failedToDetonate() || this.isLodged()) {
            state.onProjectileHit(this.level(), state, blockHitResult, this);
            return new ImpactResult(ImpactResult.KinematicOutcome.STOP, false);
        }
        return super.calculateBlockPenetration(projectileContext, state, blockHitResult);
    }

    private Vec3 impactDetonationPosition(HitResult hitResult) {
        Vec3 hit = hitResult.getLocation();
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return hit;
        }
        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1.0E-5D) {
            return hit;
        }

        double baseDepth =
                switch (this.bombSize()) {
                    case SMALL -> 0.18D;
                    case SEA -> 0.24D;
                    case MEDIUM -> 0.42D;
                    case LARGE -> 0.62D;
                    case MOAB -> -0.45D;
                };
        double speedScale = Math.max(0.35D, Math.min(1.0D, speed / 1.25D));
        return hit.add(velocity.scale((baseDepth * speedScale) / speed));
    }

    @Override
    public void tick() {
        if (this.tickFailureState()) {
            return;
        }
        super.tick();
        if (this.level().isClientSide || this.isRemoved() || this.detonated) {
            return;
        }
        if (this.failedToDetonate()) {
            this.removeNextTick = false;
            if (this.isInGround()) {
                this.beginLodge();
            }
        } else if (this.isInGround()) {
            this.forceDetonate(this.position());
        }
    }

    protected boolean tickFailureState() {
        if (this.level().isClientSide) {
            if (this.clientImpact || this.isLodged()) {
                this.tickHeldCopy();
                return true;
            }
            return false;
        }
        if (this.isLodged()) {
            this.tickLodged();
            return true;
        }
        return false;
    }

    private void tickHeldCopy() {
        this.baseTick();
        this.setDeltaMovement(Vec3.ZERO);
        if (this.isLodged() || this.failedToDetonate()) {
            return;
        }
        if (this.impactAnswerTicks > 0 && --this.impactAnswerTicks == 0) {
            this.discard();
        }
    }

    private void tickLodged() {
        if (this.removeNextTick) {
            this.discard();
            return;
        }
        this.baseTick();
        this.tryBuryStep();
    }

    void tryBuryStep() {
        if (this.burySteps >= BURY_STEPS) {
            return;
        }
        if (++this.buryTicks < BURY_TICKS_PER_STEP) {
            return;
        }
        this.buryTicks = 0;
        double nextY = this.getY() - BURY_STEP;
        if (!this.burialSoilBelow(nextY)) {
            this.burySteps = BURY_STEPS;
            return;
        }
        this.setPos(this.getX(), nextY, this.getZ());
        this.burySteps++;
    }

    private boolean burialSoilBelow(double y) {
        BlockPos pos = BlockPos.containing(this.getX(), y - 0.05D, this.getZ());
        BlockState state = this.level().getBlockState(pos);
        for (Block soil : BURIAL_SOIL) {
            if (state.is(soil)) {
                return true;
            }
        }
        return false;
    }

    protected void beginLodge() {
        if (this.isLodged()) {
            return;
        }
        this.entityData.set(LODGED, true);
        this.setDeltaMovement(Vec3.ZERO);
        this.nextVelocity = null;
        this.impactPos = null;
        this.buryTicks = 0;
        this.burySteps = this.burialSoilBelow(this.getY()) ? 0 : BURY_STEPS;
    }

    @Override
    protected void detonate(Position position) {
        this.forceDetonate(new Vec3(position.x(), position.y(), position.z()));
    }

    private void forceDetonate(Vec3 position) {
        this.forceDetonate(position, 1.0f);
    }

    protected void forceDetonate(Vec3 position, float powerScale) {
        this.forceDetonate(position, powerScale, powerScale);
    }

    protected void forceDetonate(Vec3 position, float blockPowerScale, float entityPowerScale) {
        if (this.detonated || this.level().isClientSide) {
            return;
        }
        if (this.failedToDetonate() || this.isLodged()) {
            return;
        }
        this.detonated = true;
        this.removeNextTick = true;

        BigCannonCommonShellProperties properties = this.getAllProperties();
        float blockPower = properties.explosion().blockDamagePower() * blockPowerScale;
        float entityPower = properties.explosion().entityDamagePower() * entityPowerScale;

        if (this.level() instanceof ServerLevel serverLevel) {
            BombExplosionHandler.detonate(
                    serverLevel,
                    this,
                    BombDamageSource.create(serverLevel),
                    position,
                    blockPower,
                    entityPower,
                    this.bombSize());
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.failedToDetonate() || this.isLodged()) {
            return false;
        }
        if (!BombSympatheticDetonation.allowsCookoffFrom(null)) {
            return false;
        }
        if (this.detonated || this.level().isClientSide || this.isRemoved()) {
            return false;
        }
        this.forceDetonate(this.position());
        return true;
    }

    @Override
    public boolean canHitEntity(Entity entity) {
        return !(entity instanceof DropBombProjectile) && super.canHitEntity(entity);
    }

    public void sympatheticDetonate() {
        if (this.detonated
                || this.level().isClientSide
                || this.isRemoved()
                || this.failedToDetonate()
                || this.isLodged()) {
            return;
        }
        this.forceDetonate(this.position());
    }

    @Override
    protected DamageSource getEntityDamage(Entity entity) {
        return BombDamageSource.create(this.level());
    }

    @Override
    public DamageSource indirectArtilleryFire(boolean bypassArmor) {
        return BombDamageSource.create(this.level());
    }

    @Override
    public boolean canLingerInGround() {
        return false;
    }

    @Override
    public BlockState getRenderedBlockState() {
        Block block = this.visualBlock();
        BlockState state = block.defaultBlockState().setValue(DropBombBlock.FACING, Direction.NORTH);
        if (this.getType() == ModEntityTypes.SEA_BOMB.get()) {
            state = state.setValue(DropBombBlock.POWERED, true).setValue(DropBombBlock.CASSETTE, 4);
        }
        return state;
    }

    private Block visualBlock() {
        EntityType<?> type = this.getType();
        if (type == ModEntityTypes.SEA_BOMB.get()) {
            return ModBlocks.SEA_BOMB.get();
        }
        if (type == ModEntityTypes.MEDIUM_BOMB.get()) {
            return ModBlocks.MEDIUM_BOMB.get();
        }
        if (type == ModEntityTypes.LARGE_BOMB.get()) {
            return ModBlocks.LARGE_BOMB.get();
        }
        if (type == ModEntityTypes.MOAB.get()) {
            return ModBlocks.MOAB.get();
        }
        return ModBlocks.SMALL_BOMB.get();
    }

    public BombSize bombSize() {
        EntityType<?> type = this.getType();
        if (type == ModEntityTypes.SEA_BOMB.get()) {
            return BombSize.SEA;
        }
        if (type == ModEntityTypes.MEDIUM_BOMB.get()) {
            return BombSize.MEDIUM;
        }
        if (type == ModEntityTypes.LARGE_BOMB.get()) {
            return BombSize.LARGE;
        }
        if (type == ModEntityTypes.MOAB.get()) {
            return BombSize.MOAB;
        }
        return BombSize.SMALL;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("FailedToDetonate", this.failedToDetonate());
        tag.putBoolean("Lodged", this.isLodged());
        tag.putInt("BuryTicks", this.buryTicks);
        tag.putInt("BurySteps", this.burySteps);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(FAILED, tag.getBoolean("FailedToDetonate"));
        this.entityData.set(LODGED, tag.getBoolean("Lodged"));
        this.buryTicks = Mth.clamp(tag.getInt("BuryTicks"), 0, BURY_TICKS_PER_STEP);
        this.burySteps = Mth.clamp(tag.getInt("BurySteps"), 0, BURY_STEPS);
    }

    @Nonnull
    @Override
    protected BigCannonFuzePropertiesComponent getFuzeProperties() {
        return this.getAllProperties().fuze();
    }

    @Nonnull
    @Override
    protected BigCannonProjectilePropertiesComponent getBigCannonProjectileProperties() {
        return this.getAllProperties().bigCannonProperties();
    }

    @Nonnull
    @Override
    public EntityDamagePropertiesComponent getDamageProperties() {
        return this.getAllProperties().damage();
    }

    @Nonnull
    @Override
    protected BallisticPropertiesComponent getBallisticProperties() {
        return this.getAllProperties().ballistics();
    }

    protected BigCannonCommonShellProperties getAllProperties() {
        return CBCMunitionPropertiesHandlers.COMMON_SHELL_BIG_CANNON_PROJECTILE.getPropertiesOf(this);
    }
}
