package com.cbc_more_content.registry;

import com.cbc_more_content.CBCMoreContent;
import com.cbc_more_content.effects.BlastSmokeData;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, CBCMoreContent.MOD_ID);

    /** Hot casing sliver thrown by an antipersonnel mine. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MINE_FRAGMENT =
            PARTICLE_TYPES.register("mine_fragment", () -> new SimpleParticleType(false));

    /** Rocket efflux behind a cruise missile under power. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MISSILE_EXHAUST =
            PARTICLE_TYPES.register("missile_exhaust", () -> new SimpleParticleType(false));

    /** Cold ejection gas, before the motor lights. No heat, no glow — just pressure. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MISSILE_GAS =
            PARTICLE_TYPES.register("missile_gas", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MISSILE_SMOKE =
            PARTICLE_TYPES.register("missile_smoke", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, ParticleType<BlastSmokeData>> BLAST_SMOKE =
            PARTICLE_TYPES.register("blast_smoke", () -> new ParticleType<>(true) {
                @Override
                public MapCodec<BlastSmokeData> codec() {
                    return BlastSmokeData.CODEC;
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, BlastSmokeData> streamCodec() {
                    return BlastSmokeData.STREAM_CODEC;
                }
            });

    private ModParticles() {}
}
