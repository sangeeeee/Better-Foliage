package com.eerussianguy.betterfoliage.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import com.google.common.collect.Maps;
import com.eerussianguy.betterfoliage.BFConfig;
import com.eerussianguy.betterfoliage.Helpers;
import com.eerussianguy.betterfoliage.compat.SodiumLeafCullingCompat;
import com.eerussianguy.betterfoliage.compat.CullLeavesCompat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.NamedRenderTypeManager;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Proxies resource-pack leaf models that already contain bushy quads. The pack keeps control of the leaf cube and
 * its textures; only its fixed bushy planes are replaced by Better Foliage's position-randomized geometry.
 */
public final class ResourcePackLeavesBakedModel extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel
{
    private static final String BUSHY_TEXTURE_MARKER = "bushy";

    /** A model may choose several bushy textures through a weighted blockstate, so build each texture lazily. */
    private final ConcurrentMap<FluffKey, BakedModel[]> fluffModels = new ConcurrentHashMap<>();
    private final SnowyLeavesOverlay snowOverlay;
    private final Function<ResourceLocation, TextureAtlasSprite> compositeGetter;
    private final ConcurrentMap<ResourceLocation, SnowCompositeSprites.SpriteSet> compositeSprites = new ConcurrentHashMap<>();

    public ResourcePackLeavesBakedModel(
        BakedModel originalModel,
        Function<Material, TextureAtlasSprite> spriteGetter
    )
    {
        super(originalModel);
        this.compositeGetter = texture -> spriteGetter.apply(new Material(TextureAtlas.LOCATION_BLOCKS, texture));
        this.snowOverlay = SnowyLeavesOverlay.get(
            texture -> spriteGetter.apply(new Material(TextureAtlas.LOCATION_BLOCKS, texture))
        );
    }

    @Override
    @NotNull
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random)
    {
        return getQuads(state, side, random, ModelData.EMPTY, null);
    }

    @Override
    @NotNull
    public List<BakedQuad> getQuads(
        @Nullable BlockState state,
        @Nullable Direction side,
        RandomSource random,
        ModelData data,
        @Nullable RenderType renderType
    )
    {
        final List<BakedQuad> originalQuads = originalModel.getQuads(state, side, random, data, renderType);
        // Stay True defines its bushy planes as unculled quads, so they occur only in the null-side query. Leaving all
        // directional calls untouched also preserves the resource pack's original weighted core-model selection.
        if (state == null || side != null || originalQuads.isEmpty())
        {
            return originalQuads;
        }

        ArrayList<BakedQuad> result = null;
        TextureAtlasSprite fluffSprite = null;
        int fluffTintIndex = -1;
        boolean anyTintedFluff = false;
        boolean consistentTint = true;

        for (int index = 0; index < originalQuads.size(); index++)
        {
            final BakedQuad quad = originalQuads.get(index);
            if (isBushyQuad(quad))
            {
                anyTintedFluff |= quad.isTinted();
                if (result == null)
                {
                    result = new ArrayList<>(originalQuads.size());
                    for (int before = 0; before < index; before++) result.add(originalQuads.get(before));
                }
                if (fluffSprite == null)
                {
                    fluffSprite = quad.getSprite();
                    fluffTintIndex = quad.getTintIndex();
                }
                else if (fluffTintIndex != quad.getTintIndex()) consistentTint = false;
            }
            else if (result != null)
            {
                result.add(quad);
            }
        }

        // This leaf model was replaced by a resource pack, but it does not contain a recognizable bushy texture.
        if (result == null)
        {
            return originalQuads;
        }

        // Original bushy quads have already been removed, so a suppressed leaf never reaches Sodium Leaf Culling as
        // transparent geometry that it could force into the solid render pass.
        if (!SodiumLeafCullingCompat.shouldSuppressFluff() && !CullLeavesCompat.shouldSuppressFluff(data))
        {
            // Weighted pack models have already consumed their selection value. The next value remains a stable,
            // coordinate-derived source with the same uniform offset/rotation distribution used by BF models.
            final long seed = random.nextLong();
            final int planes = FluffVisibilityData.mask(data);
            // Still consume the same random value, and never put the pack's original bushy quads back.
            if (planes == 0) return result;
            final LeavesOrdinalData variation = LeavesOrdinalData.fromSeed(seed);
            final FluffKey key = new FluffKey(Objects.requireNonNull(fluffSprite), fluffTintIndex);
            final BakedModel[] crosses = fluffModels.computeIfAbsent(key, this::buildCrosses);
            final List<BakedQuad> crossQuads = crosses[variation.get()].getQuads(state, side, random, data, renderType);
            final float rotation = variation.rotationOffset() * LeavesBakedModel.MAX_ROTATION_VARIATION;
            final boolean snowy = SnowyLeavesData.isSnowy(data);
            TextureAtlasSprite composite = null;
            if (snowy && consistentTint)
            {
                final SnowCompositeSprites.SpriteSet sprites = compositeSprites.computeIfAbsent(fluffSprite.contents().name(),
                    name -> SnowCompositeSprites.findSet(name, compositeGetter));
                if (!anyTintedFluff && sprites.untinted.length == 3) composite = sprites.untinted[variation.snowTexture()];
                else if (fluffTintIndex == 0) composite = sprites.select(SnowTintData.color(data), variation.snowTexture());
            }
            final List<BakedQuad> snowQuads = snowy && composite == null
                ? snowOverlay.getQuads(variation, state, side, random, data, renderType) : List.of();
            result.ensureCapacity(result.size() + (crossQuads.size() + snowQuads.size()) * Integer.bitCount(planes) / 2);
            LeavesBakedModel.appendPositionRotation(result, crossQuads, snowQuads, rotation, composite, planes);
        }
        return result;
    }

    @Override
    @NotNull
    public ModelData getModelData(
        @NotNull BlockAndTintGetter level,
        @NotNull BlockPos pos,
        @NotNull BlockState state,
        @NotNull ModelData data
    )
    {
        final ModelData culled = CullLeavesCompat.append(level, pos, state, originalModel.getModelData(level, pos, state, data));
        if (CullLeavesCompat.shouldSuppressFluff(culled)) return culled;
        final ModelData selected = FluffVisibilityData.append(level, pos, state, culled);
        return FluffVisibilityData.mask(selected) == 0 ? selected : SnowTintData.append(level, pos, state, selected);
    }

    private static boolean isBushyQuad(BakedQuad quad)
    {
        return quad.getSprite().contents().name().getPath().contains(BUSHY_TEXTURE_MARKER);
    }

    private BakedModel[] buildCrosses(FluffKey key)
    {
        final int cacheSize = BFConfig.CLIENT.leavesCacheSize.get();
        final BakedModel[] crosses = new BakedModel[cacheSize * cacheSize * cacheSize];
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
                    crosses[ordinal++] = buildCross(blockModel, key, x, y, z);
                }
            }
        }
        return crosses;
    }

    private BakedModel buildCross(BlockModel blockModel, FluffKey key, float x, float y, float z)
    {
        final Map<Direction, BlockElementFace> faces = Maps.newEnumMap(Direction.class);
        faces.put(Direction.NORTH, new BlockElementFace(null, key.tintIndex(), "", Helpers.UV_DEFAULT));
        faces.put(Direction.SOUTH, new BlockElementFace(null, key.tintIndex(), "", Helpers.UV_DEFAULT));

        final Vector3f from = new Vector3f(-8.0F, -8.0F, 8.0F);
        final Vector3f to = new Vector3f(24.0F, 24.0F, 8.0F);
        final Vector3f offset = new Vector3f(x / 2.0F, y / 1.2F, z / 2.0F);
        from.add(offset);
        to.add(offset);

        final BlockElement first = new BlockElement(from, to, faces, makeRotation(45.0F), false);
        final BlockElement second = new BlockElement(from, to, faces, makeRotation(-45.0F), false);
        final SimpleBakedModel.Builder builder = new SimpleBakedModel.Builder(blockModel, ItemOverrides.EMPTY, false)
            .particle(key.sprite());

        // Stay True's bushy planes are unculled. Keeping the replacement in the same bucket lets us remove and add
        // them in one model query while preserving every directional core quad supplied by the resource pack.
        LeavesBakedModel.assembleFluffFaces(builder, first, key.sprite());
        LeavesBakedModel.assembleFluffFaces(builder, second, key.sprite());
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

    private record FluffKey(TextureAtlasSprite sprite, int tintIndex)
    {
    }
}
