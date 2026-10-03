package com.cbc_more_content.bomb;

import com.cbc_more_content.config.WarnauticsConfig;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

public enum BombSize {
    SMALL(
            Block.box(4.0D, 0.0D, 4.0D, 12.0D, 10.0D, 12.0D),
            Block.box(4.0D, 3.0D, 0.0D, 12.0D, 13.0D, 10.0D),
            Block.box(0.0D, 3.0D, 4.0D, 10.0D, 13.0D, 12.0D),
            0.40D,
            0.12D,
            0.45f,
            3.625f,
            4.0f),
    SEA(
            Block.box(3.5D, 0.0D, 3.5D, 12.5D, 11.0D, 12.5D),
            Block.box(3.5D, 2.5D, 0.0D, 12.5D, 13.5D, 11.0D),
            Block.box(0.0D, 2.5D, 3.5D, 11.0D, 13.5D, 12.5D),
            0.38D,
            0.11D,
            0.55f,
            7.5f,
            12.0f),
    MEDIUM(
            Block.box(3.0D, 0.0D, 3.0D, 13.0D, 12.0D, 13.0D),
            Block.box(3.0D, 2.0D, 0.0D, 13.0D, 14.0D, 12.0D),
            Block.box(0.0D, 2.0D, 3.0D, 12.0D, 14.0D, 13.0D),
            0.36D,
            0.10D,
            0.65f,
            11.0f,
            14.0f),
    LARGE(
            Block.box(2.0D, 0.0D, 2.0D, 14.0D, 14.0D, 14.0D),
            Block.box(2.0D, 1.0D, 0.0D, 14.0D, 15.0D, 14.0D),
            Block.box(0.0D, 1.0D, 2.0D, 14.0D, 15.0D, 14.0D),
            0.30D,
            0.08D,
            0.90f,
            15.6f,
            21.45f),
    MOAB(
            Block.box(4.5D, -16.0D, -2.0D, 11.5D, 32.0D, 18.0D),
            Block.box(4.5D, -2.0D, -16.0D, 11.5D, 18.0D, 32.0D),
            Block.box(-16.0D, -2.0D, 4.5D, 32.0D, 18.0D, 11.5D),
            0.24D,
            0.06D,
            1.5f,
            66.0f,
            112.0f);

    public final VoxelShape shapeUd;
    public final VoxelShape shapeNs;
    public final VoxelShape shapeEw;
    public final double launchAlong;
    public final double launchDown;

    public final float entitySize;
    public final float blockBlastPower;
    public final float entityBlastPower;

    BombSize(
            VoxelShape shapeUd,
            VoxelShape shapeNs,
            VoxelShape shapeEw,
            double launchAlong,
            double launchDown,
            float entitySize,
            float blockBlastPower,
            float entityBlastPower) {
        this.shapeUd = shapeUd;
        this.shapeNs = shapeNs;
        this.shapeEw = shapeEw;
        this.launchAlong = launchAlong;
        this.launchDown = launchDown;
        this.entitySize = entitySize;
        this.blockBlastPower = blockBlastPower;
        this.entityBlastPower = entityBlastPower;
    }

    public VoxelShape shapeFor(Direction.Axis axis) {
        return switch (axis) {
            case X -> this.shapeEw;
            case Z -> this.shapeNs;
            case Y -> this.shapeUd;
        };
    }

    public BlastVolume blastVolume() {
        return this == MOAB ? new BlastVolume(3.4D, 1.0D) : BlastVolume.SPHERE;
    }

    public boolean isSeaBomb() {
        return this == SEA;
    }

    public int blockBudget() {
        if (this == MOAB) {
            return Math.min(WarnauticsConfig.maxBlocksPerDetonation() * 8, 60_000);
        }
        return WarnauticsConfig.maxBlocksPerDetonation();
    }

    public record BlastVolume(double h, double v) {
        public static final BlastVolume SPHERE = new BlastVolume(1.0D, 1.0D);

        public boolean isSphere() {
            return this.h == 1.0D && this.v == 1.0D;
        }

        public double horizontal(double radius) {
            return radius * this.h;
        }

        public double vertical(double radius) {
            return radius * this.v;
        }

        public boolean contains(double dx, double dy, double dz, double radius) {
            double nx = dx / (radius * this.h);
            double ny = dy / (radius * this.v);
            double nz = dz / (radius * this.h);
            return nx * nx + ny * ny + nz * nz <= 1.0D;
        }

        public float shellPowerForSameVolume(float power) {
            if (this.isSphere()) {
                return power;
            }
            return (float) (power / Math.cbrt(this.h * this.h * this.v));
        }
    }
}
