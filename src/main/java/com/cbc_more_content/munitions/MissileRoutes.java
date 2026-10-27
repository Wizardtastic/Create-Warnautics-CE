package com.cbc_more_content.munitions;

import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class MissileRoutes {
    private static final long SIDE_SALT = 0x524F555445534944L;
    private static final long PHASE_SALT = 0x524F555445504841L;
    private static final long LIFT_SALT = 0x524F555445564552L;

    private MissileRoutes() {}

    public static double uniform(UUID id, long salt) {
        long value = id.getLeastSignificantBits() ^ Long.rotateLeft(id.getMostSignificantBits(), 23) ^ salt;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    public static Vec3 drift(UUID id, Vec3 toward, int ticks, double range, double fade, double amplitude) {
        double envelope = Mth.clamp((range - fade) / fade, 0.0D, 1.0D);
        if (envelope <= 0.0D || amplitude <= 0.0D) {
            return Vec3.ZERO;
        }
        Vec3 side = side(id, toward);
        double phase = uniform(id, PHASE_SALT) * Mth.TWO_PI;
        double liftPhase = uniform(id, LIFT_SALT) * Mth.TWO_PI;
        double weave = Math.sin(ticks * .045D + phase) * .62D + Math.sin(ticks * .017D + phase * 1.9D) * .38D;
        double lift = Math.sin(ticks * .029D + liftPhase) * .35D;
        return side.scale(weave * amplitude * envelope).add(0.0D, lift * amplitude * envelope, 0.0D);
    }

    private static Vec3 side(UUID id, Vec3 toward) {
        Vec3 flat = new Vec3(toward.x, 0.0D, toward.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            return uniform(id, SIDE_SALT) < .5D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 0.0D, 1.0D);
        }
        return new Vec3(-flat.z, 0.0D, flat.x).normalize();
    }
}
