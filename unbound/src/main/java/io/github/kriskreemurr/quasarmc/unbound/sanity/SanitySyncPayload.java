package io.github.kriskreemurr.quasarmc.unbound.sanity;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server → client: the player's own sanity (0..100) and its current rate of change (per minute). */
public record SanitySyncPayload(float value, float ratePerMinute) implements CustomPacketPayload {
    public static final Type<SanitySyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "sanity"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SanitySyncPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.FLOAT, SanitySyncPayload::value,
                    ByteBufCodecs.FLOAT, SanitySyncPayload::ratePerMinute, SanitySyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
