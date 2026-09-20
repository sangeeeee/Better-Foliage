package com.eerussianguy.betterfoliage.model;

import net.minecraft.util.RandomSource;

import com.eerussianguy.betterfoliage.BFConfig;

public final class LeavesOrdinalData
{
    private static final long RANDOM_SEED_SALT = 0xD1B54A32D192ED03L;
    private static final long SPLITMIX_GAMMA = 0x9E3779B97F4A7C15L;
    private static final float UNIT_FLOAT = 0x1.0p-24F;

    private final int ordinal;
    private final float rotationOffset;
    private final int snowTexture;

    public static LeavesOrdinalData fromRenderRandom(RandomSource random)
    {
        // Vanilla and Sodium reset this source from BlockState.getSeed(pos) for every rendered face. Consuming one
        // long therefore gives us a stable coordinate seed even when a model wrapper discards NeoForge ModelData.
        return new LeavesOrdinalData(random.nextLong());
    }

    static LeavesOrdinalData fromSeed(long seed)
    {
        return new LeavesOrdinalData(seed);
    }

    private LeavesOrdinalData(long coordinateSeed)
    {
        final int cacheSize = BFConfig.CLIENT.leavesCacheSize.get();
        long randomState = coordinateSeed ^ RANDOM_SEED_SALT;

        // The render seed is derived from x/y/z. Feeding it through an avalanche mixer before every draw means
        // changing any one coordinate radically changes the complete offset and rotation sequence.
        final int offsetX = boundedIndex(mix64(randomState += SPLITMIX_GAMMA), cacheSize);
        final int offsetY = boundedIndex(mix64(randomState += SPLITMIX_GAMMA), cacheSize);
        final int offsetZ = boundedIndex(mix64(randomState += SPLITMIX_GAMMA), cacheSize);
        ordinal = (offsetX * cacheSize + offsetY) * cacheSize + offsetZ;

        // Keep 24 random bits (the full useful precision of a float) instead of quantising the angle to a small
        // model cache. The value is stable in [-1, 1) and is scaled to the configured model range by the renderer.
        final long rotationBits = mix64(randomState + SPLITMIX_GAMMA);
        rotationOffset = ((rotationBits >>> 40) * UNIT_FLOAT) * 2.0F - 1.0F;
        snowTexture = boundedIndex(mix64(randomState + 2L * SPLITMIX_GAMMA), SnowyLeavesOverlay.TEXTURE_COUNT);
    }

    public int get()
    {
        return ordinal;
    }

    public float rotationOffset()
    {
        return rotationOffset;
    }

    public int snowTexture()
    {
        return snowTexture;
    }

    private static int boundedIndex(long randomBits, int bound)
    {
        return (int) Long.remainderUnsigned(randomBits, bound);
    }

    private static long mix64(long value)
    {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

}
