package com.eerussianguy.betterfoliage.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/** Optional, reflection-only bridge to Sodium Leaf Culling. */
public final class SodiumLeafCullingCompat
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "sodiumleafculling";
    private static final String CONFIG_CLASS = "toni.sodiumleafculling.LeafCullingConfig";
    private static final String SODIUM_CONTEXT_CLASS = "net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext";
    private static final Direction[] ALL_DIRECTIONS = Direction.values();
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
        Direction.NORTH,
        Direction.SOUTH,
        Direction.WEST,
        Direction.EAST
    };
    private static final ThreadLocal<Boolean> SUPPRESS_FLUFF = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> PRESERVE_TOP_ALPHA = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<BlockPos.MutableBlockPos> NEIGHBOR_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
    private static final Api API = loadApi();
    private static volatile boolean broken;

    private SodiumLeafCullingCompat()
    {
    }

    public static boolean isAvailable()
    {
        return API != null && !broken;
    }

    /** Establishes the leaf currently being compiled before Sodium Leaf Culling processes any of its quads. */
    public static void beginLeaf(Object sodiumRenderer, BlockPos pos)
    {
        PRESERVE_TOP_ALPHA.set(Boolean.FALSE);
        if (!isAvailable())
        {
            return;
        }

        try
        {
            final Object quality = API.getQuality.invoke(null);
            final String qualityName = quality instanceof Enum<?> value ? value.name() : String.valueOf(quality);
            if ("NONE".equals(qualityName))
            {
                SUPPRESS_FLUFF.set(Boolean.FALSE);
                return;
            }

            final Object levelObject = API.level.get(sodiumRenderer);
            if (!(levelObject instanceof BlockAndTintGetter level))
            {
                SUPPRESS_FLUFF.set(Boolean.FALSE);
                return;
            }

            final boolean suppress = switch (qualityName)
            {
                case "HOLLOW" -> allTwentySixNeighborsPresent(level, pos);
                case "SOLID" -> allDirectionsPresent(level, pos, ALL_DIRECTIONS);
                case "SOLID_AGGRESSIVE" -> allDirectionsPresent(level, pos, HORIZONTAL_DIRECTIONS);
                default -> false;
            };
            final boolean fullTop = com.eerussianguy.betterfoliage.model.FluffVisibilityData.requiresFullTop(
                level.getBlockState(NEIGHBOR_POS.get().setWithOffset(pos, Direction.UP)))
                && !"SOLID_AGGRESSIVE".equals(qualityName);
            PRESERVE_TOP_ALPHA.set(fullTop);
            SUPPRESS_FLUFF.set(suppress && !fullTop);
        }
        catch (ReflectiveOperationException | LinkageError exception)
        {
            SUPPRESS_FLUFF.set(Boolean.FALSE);
            broken = true;
            LOGGER.warn("Disabling optional Sodium Leaf Culling compatibility after an API access failure", exception);
        }
    }

    public static void endLeaf()
    {
        PRESERVE_TOP_ALPHA.set(Boolean.FALSE);
        if (API != null)
        {
            SUPPRESS_FLUFF.set(Boolean.FALSE);
        }
    }

    /** Restrict the opaque-material exception to the leaf currently being compiled. */
    public static boolean preserveTopAlpha() { return PRESERVE_TOP_ALPHA.get(); }

    public static boolean shouldSuppressFluff()
    {
        return API != null && SUPPRESS_FLUFF.get();
    }

    private static boolean allDirectionsPresent(BlockAndTintGetter level, BlockPos pos, Direction[] directions)
    {
        final BlockPos.MutableBlockPos neighbor = NEIGHBOR_POS.get();
        for (Direction direction : directions)
        {
            neighbor.setWithOffset(pos, direction);
            if (level.getBlockState(neighbor).isAir())
            {
                return false;
            }
        }
        return true;
    }

    private static boolean allTwentySixNeighborsPresent(BlockAndTintGetter level, BlockPos pos)
    {
        final BlockPos.MutableBlockPos neighbor = NEIGHBOR_POS.get();
        for (int offsetX = -1; offsetX <= 1; offsetX++)
        {
            for (int offsetY = -1; offsetY <= 1; offsetY++)
            {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++)
                {
                    if (offsetX == 0 && offsetY == 0 && offsetZ == 0)
                    {
                        continue;
                    }
                    neighbor.set(pos.getX() + offsetX, pos.getY() + offsetY, pos.getZ() + offsetZ);
                    if (level.getBlockState(neighbor).isAir())
                    {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static Api loadApi()
    {
        if (!ModList.get().isLoaded(MOD_ID))
        {
            return null;
        }

        try
        {
            final Class<?> configType = Class.forName(CONFIG_CLASS);
            final Class<?> sodiumContextType = Class.forName(SODIUM_CONTEXT_CLASS);
            final Field level = sodiumContextType.getDeclaredField("level");
            level.setAccessible(true);
            return new Api(configType.getMethod("getQuality"), level);
        }
        catch (ReflectiveOperationException | LinkageError exception)
        {
            LOGGER.warn("Sodium Leaf Culling is installed, but Better Foliage could not initialize its optional compatibility", exception);
            return null;
        }
    }

    private record Api(Method getQuality, Field level)
    {
    }
}
