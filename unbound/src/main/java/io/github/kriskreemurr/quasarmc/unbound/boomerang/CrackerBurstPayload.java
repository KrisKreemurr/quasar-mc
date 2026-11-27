package io.github.kriskreemurr.quasarmc.unbound.boomerang;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.Level;

/**
 * Server to client: a Cracker-Rang went off here. The client draws a vanilla firework burst (with its blast sound) in
 * colours and a shape picked from the seed, so every explosion looks different.
 */
public record CrackerBurstPayload(double x, double y, double z, long seed) implements CustomPacketPayload {
    public static final Type<CrackerBurstPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(QuasarUnbound.MODID, "cracker_burst"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CrackerBurstPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, CrackerBurstPayload::x, ByteBufCodecs.DOUBLE, CrackerBurstPayload::y,
            ByteBufCodecs.DOUBLE, CrackerBurstPayload::z, ByteBufCodecs.VAR_LONG, CrackerBurstPayload::seed,
            CrackerBurstPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** runs on the client (Level.createFireworks does nothing on a server, so this is safe to reference anywhere) */
    void show(Level level) {
        RandomSource r = RandomSource.create(seed);
        DyeColor[] dyes = DyeColor.values();
        FireworkExplosion.Shape[] shapes = FireworkExplosion.Shape.values();
        IntArrayList colors = new IntArrayList(), fades = new IntArrayList();
        int n = 2 + r.nextInt(2);
        for (int i = 0; i < n; i++) colors.add(dyes[r.nextInt(dyes.length)].getFireworkColor());
        if (r.nextBoolean()) fades.add(dyes[r.nextInt(dyes.length)].getFireworkColor());
        FireworkExplosion burst = new FireworkExplosion(shapes[r.nextInt(shapes.length)], colors, fades, r.nextInt(3) == 0,
            r.nextInt(3) == 0);
        level.createFireworks(x, y, z, 0, 0, 0, java.util.List.of(burst), true);
    }
}
