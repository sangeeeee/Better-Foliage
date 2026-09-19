package com.eerussianguy.betterfoliage.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Fluff-only policy. Leaves Cull Leaves' core-face and whole-block culling untouched. */
public final class CullLeavesCompat
{
    private static final boolean LOADED = ModList.get().isLoaded("cullleaves");
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final ModelProperty<Boolean> HIDDEN = new ModelProperty<>();
    private static final ThreadLocal<BlockPos.MutableBlockPos> NEIGHBOR =
        ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
    private static volatile boolean incompatible;

    private CullLeavesCompat() {}

    public static boolean usesIndependentFluff()
    {
        return LOADED;
    }

    public static ModelData append(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data)
    {
        if (!LOADED)
        {
            return data;
        }
        final boolean hidden = isActive(state) && allNeighborsPresent(level, pos);
        if (Boolean.valueOf(hidden).equals(data.get(HIDDEN)))
        {
            return data;
        }
        // Explicit false clears stale suppression after a neighbor is removed or culling is disabled.
        return data.derive().with(HIDDEN, hidden).build();
    }

    public static boolean shouldSuppressFluff(ModelData data)
    {
        return Boolean.TRUE.equals(data.get(HIDDEN));
    }

    private static boolean allNeighborsPresent(BlockAndTintGetter level, BlockPos pos)
    {
        final BlockPos.MutableBlockPos neighbor = NEIGHBOR.get();
        for (Direction direction : DIRECTIONS)
        {
            neighbor.setWithOffset(pos, direction);
            if (level.getBlockState(neighbor).isAir())
            {
                return false;
            }
        }
        return true;
    }

    private static boolean isActive(BlockState state)
    {
        if (incompatible)
        {
            return false;
        }
        try
        {
            return Bridge.isActive(state);
        }
        catch (LinkageError error)
        {
            incompatible = true;
            LogUtils.getLogger().warn("Cull Leaves fluff suppression disabled: incompatible API", error);
            return false;
        }
    }

    /** Lazy linkage: the optional dependency is never resolved when Cull Leaves is absent. */
    private static final class Bridge
    {
        private static boolean isActive(BlockState state)
        {
            return eu.midnightdust.cullleaves.CullLeavesClient.isLeafSideInvisible(state)
                || eu.midnightdust.cullleaves.CullLeavesClient.forceHideInnerLeaves;
        }
    }
}
