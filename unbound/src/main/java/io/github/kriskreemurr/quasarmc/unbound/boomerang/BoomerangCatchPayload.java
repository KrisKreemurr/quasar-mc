package io.github.kriskreemurr.quasarmc.unbound.boomerang;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: "catch my boomerang with this (empty) hand". Vanilla sends nothing for a right-click with an
 * empty hand in the air, so the catch needs its own message. The server checks everything (owner, catch window,
 * hand really empty).
 */
public record BoomerangCatchPayload(boolean offhand) implements CustomPacketPayload {
    public static final Type<BoomerangCatchPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "boomerang_catch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BoomerangCatchPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, BoomerangCatchPayload::offhand, BoomerangCatchPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
