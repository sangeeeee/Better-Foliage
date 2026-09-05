package com.eerussianguy.betterfoliage.model;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.Map;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.level.block.Blocks;

/** Optional, reflection-only bridge to Iris. Better Foliage does not require Iris at runtime. */
public final class IrisShaderCompat
{
    private static final String BUFFER_CLASS = "net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder";
    private static final String SETTINGS_CLASS = "net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings";
    private static final String SODIUM_CONSUMER_CLASS = "net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkVertexConsumer";
    private static final String SODIUM_VERTEX_CLASS = "net.irisshaders.iris.vertices.sodium.terrain.ChunkVertexExtension";
    private static final Api API = loadApi();

    private IrisShaderCompat()
    {
    }

    /**
     * Resolves a rooted-vegetation ID from the active shader pack. Crop and block-tag mappings are preferred, then
     * ordinary ground foliage, with leaves retained as a last-resort category for packs that expose only leaf waving.
     */
    static int groundVegetationId()
    {
        if (API == null)
        {
            return -1;
        }

        try
        {
            final Object ids = API.getBlockStateIds.invoke(API.settings);
            if (ids instanceof Map<?, ?> map)
            {
                final int crops = mappedId(map, Blocks.WHEAT.defaultBlockState());
                if (crops >= 0)
                {
                    return crops;
                }
                final int groundFoliage = mappedId(map, Blocks.SHORT_GRASS.defaultBlockState());
                if (groundFoliage >= 0)
                {
                    return groundFoliage;
                }
                final int reeds = mappedId(map, Blocks.SUGAR_CANE.defaultBlockState());
                if (reeds >= 0)
                {
                    return reeds;
                }
                return mappedId(map, Blocks.OAK_LEAVES.defaultBlockState());
            }
        }
        catch (ReflectiveOperationException ignored)
        {
        }
        return -1;
    }

    /** Starts an Iris terrain-block context at the logical origin of synthetic vegetation geometry. */
    static boolean beginGroundVegetation(VertexConsumer consumer, int vegetationId, int localX, int localY, int localZ)
    {
        if (API == null || vegetationId < 0)
        {
            return false;
        }

        if (consumer instanceof ReedShaderContext context)
        {
            context.betterfoliage$beginReed(vegetationId, localX, localY, localZ);
            return true;
        }
        if (!API.bufferType.isInstance(consumer))
        {
            return false;
        }

        try
        {
            API.beginBlock.invoke(consumer, vegetationId, (byte) -1, (byte) 0, localX, localY, localZ);
            return true;
        }
        catch (ReflectiveOperationException ignored)
        {
            return false;
        }
    }

    static void endGroundVegetation(VertexConsumer consumer)
    {
        if (consumer instanceof ReedShaderContext context)
        {
            context.betterfoliage$endReed();
            return;
        }
        if (API == null || !API.bufferType.isInstance(consumer))
        {
            return;
        }

        try
        {
            API.endBlock.invoke(consumer);
        }
        catch (ReflectiveOperationException ignored)
        {
        }
    }

    /** Called by the optional Sodium mixin immediately before a completed quad enters the chunk mesh. */
    public static void writeSodiumVertexData(Object consumer, int blockId, int localX, int localY, int localZ)
    {
        if (API == null || API.sodiumConsumerType == null || API.sodiumVertices == null || API.setSodiumVertexData == null
            || !API.sodiumConsumerType.isInstance(consumer))
        {
            return;
        }

        try
        {
            final Object[] vertices = (Object[]) API.sodiumVertices.get(consumer);
            for (Object vertex : vertices)
            {
                API.setSodiumVertexData.invoke(vertex, (byte) 0, (byte) 0, blockId, localX, localY, localZ);
            }
        }
        catch (ReflectiveOperationException ignored)
        {
        }
    }

    private static int mappedId(Map<?, ?> map, Object state)
    {
        final Object id = map.get(state);
        return id instanceof Integer value ? value : -1;
    }

    private static Api loadApi()
    {
        try
        {
            final Class<?> bufferType = Class.forName(BUFFER_CLASS);
            final Class<?> settingsType = Class.forName(SETTINGS_CLASS);
            final Object settings = settingsType.getField("INSTANCE").get(null);
            Class<?> sodiumConsumerType = null;
            Field sodiumVertices = null;
            Method setSodiumVertexData = null;
            try
            {
                sodiumConsumerType = Class.forName(SODIUM_CONSUMER_CLASS);
                final Class<?> sodiumVertexType = Class.forName(SODIUM_VERTEX_CLASS);
                sodiumVertices = sodiumConsumerType.getDeclaredField("vertices");
                sodiumVertices.setAccessible(true);
                setSodiumVertexData = sodiumVertexType.getMethod("iris$setData", byte.class, byte.class, int.class, int.class, int.class, int.class);
            }
            catch (ReflectiveOperationException | LinkageError ignored)
            {
                // The vanilla Iris buffer path can still work without Sodium's fallback consumer bridge.
            }
            return new Api(
                bufferType,
                bufferType.getMethod("beginBlock", int.class, byte.class, byte.class, int.class, int.class, int.class),
                bufferType.getMethod("endBlock"),
                settingsType.getMethod("getBlockStateIds"),
                settings,
                sodiumConsumerType,
                sodiumVertices,
                setSodiumVertexData
            );
        }
        catch (ReflectiveOperationException | LinkageError ignored)
        {
            return null;
        }
    }

    private record Api(
        Class<?> bufferType,
        Method beginBlock,
        Method endBlock,
        Method getBlockStateIds,
        Object settings,
        Class<?> sodiumConsumerType,
        Field sodiumVertices,
        Method setSodiumVertexData
    )
    {
    }
}
