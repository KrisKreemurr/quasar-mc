package io.github.kriskreemurr.quasarmc.unbound.boomerang.client;

import io.github.kriskreemurr.quasarmc.unbound.boomerang.BoomerangEntity;
import io.github.kriskreemurr.quasarmc.unbound.boomerang.Boomerangs;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Loops the boomerang's flying sound at its position until it first hits something (or is gone). */
public class BoomerangFlyingSound extends AbstractTickableSoundInstance {
    private final BoomerangEntity boomerang;

    public BoomerangFlyingSound(BoomerangEntity boomerang) {
        super(Boomerangs.FLYING.get(), SoundSource.PLAYERS, RandomSource.create());
        this.boomerang = boomerang;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0f;
        this.x = boomerang.getX();
        this.y = boomerang.getY();
        this.z = boomerang.getZ();
    }

    @Override
    public void tick() {
        if (boomerang.isRemoved() || !boomerang.whooshing()) {
            stop();
            return;
        }
        x = boomerang.getX();
        y = boomerang.getY();
        z = boomerang.getZ();
    }
}
