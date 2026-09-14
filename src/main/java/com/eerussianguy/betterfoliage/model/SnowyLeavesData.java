package com.eerussianguy.betterfoliage.model;

import com.eerussianguy.betterfoliage.compat.EclipticSeasonsCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Per-block marker computed during chunk rebuilding; it is never polled once per rendered frame. */
public final class SnowyLeavesData
{
    public static final ModelProperty<Boolean> PROPERTY = new ModelProperty<>();
    private static final ThreadLocal<BlockPos.MutableBlockPos> ABOVE_POS =
        ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    private SnowyLeavesData()
    {
    }

    public static ModelData append(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data)
    {
        final BlockPos.MutableBlockPos above = ABOVE_POS.get().setWithOffset(pos, Direction.UP);
        final boolean snowy = level.getBlockState(above).is(BlockTags.SNOW)
            || EclipticSeasonsCompat.isSnowy(level, pos, state);
        if (snowy == Boolean.TRUE.equals(data.get(PROPERTY)))
        {
            return data;
        }
        // Reused ModelData must also lose its snow marker after seasonal snow melts or the layer is removed.
        return data.derive().with(PROPERTY, snowy).build();
    }

    public static boolean isSnowy(ModelData data)
    {
        return Boolean.TRUE.equals(data.get(PROPERTY));
    }
}
