package com.eerussianguy.betterfoliage.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import com.google.common.collect.Maps;
import com.eerussianguy.betterfoliage.BFConfig;
import com.eerussianguy.betterfoliage.Helpers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.NamedRenderTypeManager;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** Three shared transparent snow-mask layers aligned to Better Foliage's complete position cache. */
final class SnowyLeavesOverlay
{
    static final int TEXTURE_COUNT = 3;
    private static final ResourceLocation[] TEXTURES = {
        Helpers.identifier("block/better_leaves_snowed_0"),
        Helpers.identifier("block/better_leaves_snowed_1"),
        Helpers.identifier("block/better_leaves_snowed_2")
    };
    private static final ConcurrentMap<CacheKey, SnowyLeavesOverlay> CACHE = new ConcurrentHashMap<>();

    private final BakedModel[][] models;

    private SnowyLeavesOverlay(List<TextureAtlasSprite> sprites)
    {
        final int cacheSize = BFConfig.CLIENT.leavesCacheSize.get();
        final int modelCount = cacheSize * cacheSize * cacheSize;
        this.models = new BakedModel[TEXTURE_COUNT][modelCount];
        final float variationDistance = BFConfig.CLIENT.leavesVariationDistance.get().floatValue();
        final float[] intervals = Helpers.intervals(cacheSize, -variationDistance, variationDistance);
        final BlockModel blockModel = new BlockModel(
            null,
            new ArrayList<>(),
            Map.of(),
            false,
            BlockModel.GuiLight.FRONT,
            ItemTransforms.NO_TRANSFORMS,
            new ArrayList<>()
        );

        int ordinal = 0;
        for (float x : intervals)
        {
            for (float y : intervals)
            {
                for (float z : intervals)
                {
                    for (int texture = 0; texture < TEXTURE_COUNT; texture++)
                    {
                        models[texture][ordinal] = buildCross(blockModel, sprites.get(texture), x, y, z);
                    }
                    ordinal++;
                }
            }
        }
    }

    static SnowyLeavesOverlay get(Function<ResourceLocation, TextureAtlasSprite> spriteGetter)
    {
        final List<TextureAtlasSprite> sprites = List.of(
            spriteGetter.apply(TEXTURES[0]),
            spriteGetter.apply(TEXTURES[1]),
            spriteGetter.apply(TEXTURES[2])
        );
        return CACHE.computeIfAbsent(
            new CacheKey(sprites),
            key -> new SnowyLeavesOverlay(key.sprites())
        );
    }

    static void clearCache()
    {
        CACHE.clear();
    }

    List<BakedQuad> getQuads(
        LeavesOrdinalData variation,
        @Nullable BlockState state,
        @Nullable Direction side,
        RandomSource random,
        ModelData data,
        @Nullable RenderType renderType
    )
    {
        return models[variation.snowTexture()][variation.get()].getQuads(state, side, random, data, renderType);
    }

    private BakedModel buildCross(
        BlockModel blockModel,
        TextureAtlasSprite sprite,
        float x,
        float y,
        float z
    )
    {
        final Map<Direction, BlockElementFace> faces = Maps.newEnumMap(Direction.class);
        faces.put(Direction.NORTH, Helpers.makeFace(Helpers.UV_DEFAULT));
        faces.put(Direction.SOUTH, Helpers.makeFace(Helpers.UV_DEFAULT));

        final Vector3f from = new Vector3f(-8.0F, -8.0F, 8.0F);
        final Vector3f to = new Vector3f(24.0F, 24.0F, 8.0F);
        final Vector3f offset = new Vector3f(x / 2.0F, y / 1.2F, z / 2.0F);
        from.add(offset);
        to.add(offset);

        final BlockElement first = new BlockElement(from, to, faces, makeRotation(45.0F), false);
        final BlockElement second = new BlockElement(from, to, faces, makeRotation(-45.0F), false);
        final SimpleBakedModel.Builder builder = new SimpleBakedModel.Builder(blockModel, ItemOverrides.EMPTY, false)
            .particle(sprite);
        LeavesBakedModel.assembleFluffFaces(builder, first, sprite);
        LeavesBakedModel.assembleFluffFaces(builder, second, sprite);
        return builder.build(NamedRenderTypeManager.get(ResourceLocation.parse("cutout_mipped")));
    }

    private static BlockElementRotation makeRotation(float degrees)
    {
        return new BlockElementRotation(
            new Vector3f(8.0F * 0.0625F, 0.0F, 8.0F * 0.0625F),
            Direction.Axis.Y,
            degrees,
            false
        );
    }

    private record CacheKey(List<TextureAtlasSprite> sprites)
    {
    }
}
