package com.eerussianguy.betterfoliage.model;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockAndTintGetter;
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

    public static ModelData append(BlockAndTintGetter level, BlockPos pos, ModelData data)
    {
        final BlockPos.MutableBlockPos above = ABOVE_POS.get().setWithOffset(pos, Direction.UP);
        if (!level.getBlockState(above).is(BlockTags.SNOW))
        {
            return data;
        }
        if (Boolean.TRUE.equals(data.get(PROPERTY)))
        {
            return data;
        }
        return data.derive().with(PROPERTY, Boolean.TRUE).build();
    }

    public static boolean isSnowy(ModelData data)
    {
        return Boolean.TRUE.equals(data.get(PROPERTY));
    }
}
