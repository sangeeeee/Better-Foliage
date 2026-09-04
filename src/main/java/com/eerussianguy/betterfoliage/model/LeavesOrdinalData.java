package com.eerussianguy.betterfoliage.model;

import java.util.Random;

import net.minecraft.core.BlockPos;

import com.eerussianguy.betterfoliage.BFConfig;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public final class LeavesOrdinalData
{
    public static final ModelProperty<LeavesOrdinalData> PROPERTY = new ModelProperty<>();
    private static final long POSITION_SEED_MULTIPLIER = 524287L;
    private static final ThreadLocal<Random> RANDOM = ThreadLocal.withInitial(Random::new);

    private final int ordinal;

    public LeavesOrdinalData(BlockPos pos)
    {
        final int cacheSize = BFConfig.CLIENT.leavesCacheSize.get();
        final int variantCount = cacheSize * cacheSize * cacheSize;
        final Random random = RANDOM.get();

        // Preserve the original coordinate-seeded distribution without sharing mutable Random state
        // between chunk rendering threads. This reproduces the pre-fix algorithm's intended result.
        random.setSeed(pos.asLong() * POSITION_SEED_MULTIPLIER);
        ordinal = random.nextInt(variantCount);
    }

    public int get()
    {
        return ordinal;
    }

}
