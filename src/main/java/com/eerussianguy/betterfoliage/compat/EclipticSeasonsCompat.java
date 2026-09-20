package com.eerussianguy.betterfoliage.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/** Optional client integration. Only the lazily loaded bridge links to the compile-only dependency. */
public final class EclipticSeasonsCompat
{
    private static final boolean LOADED = ModList.get().isLoaded("eclipticseasons");
    private static volatile boolean incompatible;

    private EclipticSeasonsCompat()
    {
    }

    public static boolean isAvailable() { return LOADED && !incompatible; }

    public static boolean isSnowy(BlockAndTintGetter view, BlockPos pos, BlockState state)
    {
        if (!LOADED || incompatible)
        {
            return false;
        }
        final Level clientLevel = Minecraft.getInstance().level;
        if (clientLevel == null || (view instanceof Level level && level != clientLevel))
        {
            return false;
        }
        try
        {
            return Bridge.isSnowy(view, pos, state);
        }
        catch (LinkageError error)
        {
            // A future incompatible ES API must not disable BF's ordinary snow-layer detection.
            if (!incompatible)
            {
                incompatible = true;
                LogUtils.getLogger().warn("Ecliptic Seasons snow-fluff compatibility disabled: incompatible client API", error);
            }
            return false;
        }
    }

    private static final class Bridge
    {
        private static final ThreadLocal<BlockPos.MutableBlockPos> CHECK_POS =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

        private static boolean isSnowy(BlockAndTintGetter view, BlockPos pos, BlockState state)
        {
            // Use ES' client rendering query, not a season/biome approximation or the shared BlockState's
            // cached snow model. It consumes the supplied vanilla/Sodium region snapshot (IMapSlice), includes
            // snow removal, shelter, lighting and snowy-tree settings, and leaves the caller's position intact.
            // This client helper is version-specific; the compile-only artifact above pins its signature.
            return com.teamtea.eclipticseasons.client.core.ExtraModelManager.canSnowy(
                view, pos, state, state.getSeed(pos), CHECK_POS.get()
            );
        }
    }
}
