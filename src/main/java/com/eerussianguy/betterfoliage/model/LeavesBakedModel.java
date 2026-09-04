package com.eerussianguy.betterfoliage.model;

import java.util.*;
import java.util.function.Function;

import com.google.common.collect.Maps;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import com.eerussianguy.betterfoliage.BFConfig;
import com.eerussianguy.betterfoliage.Helpers;
import com.eerussianguy.betterfoliage.compat.SodiumLeafCullingCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.neoforge.client.NamedRenderTypeManager;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class LeavesBakedModel extends BFBakedModel
{
    static final float MAX_ROTATION_VARIATION = 3.0F;

    private final boolean isOverlay;
    private final boolean tintLeaves;

    private final TextureAtlasSprite leavesTex;
    private final TextureAtlasSprite fluffTex;

    private final BlockModel blockModel;
    private final BakedModel[] crosses;
    private final SnowyLeavesOverlay snowOverlay;

    private final BakedModel core;
    @Nullable private final BakedModel outerCore;

    public LeavesBakedModel(ResourceLocation leaves, ResourceLocation fluff, ResourceLocation overlay, boolean tintLeaves, boolean tintOverlay, Function<ResourceLocation, TextureAtlasSprite> spriteGetter)
    {
        this.blockModel = new BlockModel(null, new ArrayList<>(), new HashMap<>(), false, BlockModel.GuiLight.FRONT, ItemTransforms.NO_TRANSFORMS, new ArrayList<>());

        this.isOverlay = !overlay.equals(Helpers.EMPTY);
        this.tintLeaves = tintLeaves;
        this.leavesTex = spriteGetter.apply(leaves);
        this.fluffTex = spriteGetter.apply(fluff);
        this.crosses = new BakedModel[(int) Math.pow(BFConfig.CLIENT.leavesCacheSize.get(), 3)];
        this.snowOverlay = SnowyLeavesOverlay.get(spriteGetter, false);
        this.core = buildBlock(leavesTex, tintLeaves);
        this.outerCore = isOverlay ? buildBlock(spriteGetter.apply(overlay), tintOverlay) : null;
        buildCrosses();
    }

    /**
     * Construct a cache of baked models to overlay onto the base leaves block.
     */
    private void buildCrosses()
    {
        int ordinal = 0;
        float leavesVariationDistance = BFConfig.CLIENT.leavesVariationDistance.get().floatValue();
        float[] intervals = Helpers.intervals(BFConfig.CLIENT.leavesCacheSize.get(), -leavesVariationDistance, leavesVariationDistance);
        for (float x : intervals)
        {
            for (float y : intervals)
            {
                for (float z : intervals)
                {
                    buildCross(ordinal, x, y, z);
                    ordinal++;
                }
            }
        }
    }

    private void buildCross(int ordinal, float x, float y, float z)
    {
        Map<Direction, BlockElementFace> mapFacesIn = Maps.newEnumMap(Direction.class);
        mapFacesIn.put(Direction.NORTH, tintLeaves ? Helpers.makeTintedFace(Helpers.UV_DEFAULT) : Helpers.makeFace(Helpers.UV_DEFAULT));
        mapFacesIn.put(Direction.SOUTH, tintLeaves ? Helpers.makeTintedFace(Helpers.UV_DEFAULT) : Helpers.makeFace(Helpers.UV_DEFAULT));

        Vector3f from = new Vector3f(-8f, -8f, 8f);
        Vector3f to = new Vector3f(24f, 24f, 8f);
        Vector3f moveVec = new Vector3f(x / 2, y / 1.2f, z / 2);
        from.add(moveVec);
        to.add(moveVec);

        BlockElement part = new BlockElement(from, to, mapFacesIn, makeRotation(45f), false);
        BlockElement partR = new BlockElement(from, to, mapFacesIn, makeRotation(-45f), false);

        SimpleBakedModel.Builder builder = new SimpleBakedModel.Builder(blockModel, ItemOverrides.EMPTY, false).particle(leavesTex);
        Helpers.assembleFaces(builder, part, fluffTex);
        Helpers.assembleFaces(builder, partR, fluffTex);

        crosses[ordinal] = builder.build(NamedRenderTypeManager.get(ResourceLocation.parse("cutout_mipped")));
    }

    private BlockElementRotation makeRotation(float degrees)
    {
        return new BlockElementRotation(new Vector3f(8f * 0.0625f, 0f, 8f * 0.0625f), Direction.Axis.Y, degrees, false);
    }

    private BakedModel buildBlock(TextureAtlasSprite tex, boolean tint)
    {
        Map<Direction, BlockElementFace> mapFacesIn = Maps.newEnumMap(Direction.class);
        for (Direction d : Helpers.DIRECTIONS)
        {
            mapFacesIn.put(d, tint ? Helpers.makeTintedFace(Helpers.UV_DEFAULT, true) : Helpers.makeFace(Helpers.UV_DEFAULT, true));
        }
        BlockElement part = new BlockElement(new Vector3f(0f, 0f, 0f), new Vector3f(16f, 16f, 16f), mapFacesIn, null, true);
        SimpleBakedModel.Builder builder = new SimpleBakedModel.Builder(blockModel, ItemOverrides.EMPTY, false).particle(tex);

        for (Map.Entry<Direction, BlockElementFace> e : part.faces.entrySet())
        {
            Direction d = e.getKey();
            builder.addCulledFace(d, Helpers.makeBakedQuad(part, e.getValue(), tex, d, BlockModelRotation.X0_Y0));
        }

        return builder.build(NamedRenderTypeManager.get(ResourceLocation.parse("cutout_mipped")));
    }

    @Override
    @NotNull
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType)
    {
        // RandomSource is the one position-dependent input guaranteed by both the legacy and ModelData-aware baked
        // model paths. Resolve our variation before delegating to child models so wrappers cannot collapse it to ZERO.
        final LeavesOrdinalData data = state == null ? null : LeavesOrdinalData.fromRenderRandom(rand);
        List<BakedQuad> coreQuads = core.getQuads(state, side, rand, extraData, renderType);
        if (data != null)
        {
            List<BakedQuad> quads = new ArrayList<>(coreQuads);
            if (!SodiumLeafCullingCompat.shouldSuppressFluff())
            {
                List<BakedQuad> crossQuads = crosses[data.get()].getQuads(state, side, rand, extraData, renderType);
                final float rotation = data.rotationOffset() * MAX_ROTATION_VARIATION;
                quads.addAll(applyPositionRotation(crossQuads, rotation));
                if (SnowyLeavesData.isSnowy(extraData))
                {
                    quads.addAll(applyPositionRotation(
                        snowOverlay.getQuads(data, state, side, rand, extraData, renderType),
                        rotation
                    ));
                }
            }
            if (isOverlay)
            {
                List<BakedQuad> outQuads = Objects.requireNonNull(outerCore).getQuads(state, side, rand, extraData, renderType);
                quads.addAll(outQuads);
            }
            return quads;
        }
        return coreQuads;
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
        return SnowyLeavesData.append(level, pos, data);
    }

    public static void clearSnowOverlayCache()
    {
        SnowyLeavesOverlay.clearCache();
    }

    /**
     * Applies the coordinate PRNG's high-entropy, position-stable rotation without multiplying the baked-model
     * cache. Rotating around each quad's own centre preserves the fluff-centre offset exactly.
     */
    static List<BakedQuad> applyPositionRotation(List<BakedQuad> source, float rotationDegrees)
    {
        if (source.isEmpty())
        {
            return source;
        }

        final double radians = Math.toRadians(rotationDegrees);
        final float sin = (float) Math.sin(radians);
        final float cos = (float) Math.cos(radians);
        final List<BakedQuad> result = new ArrayList<>(source.size());

        for (BakedQuad quad : source)
        {
            final int[] vertices = Arrays.copyOf(quad.getVertices(), quad.getVertices().length);
            float centreX = 0.0F;
            float centreZ = 0.0F;
            Direction direction = quad.getDirection();

            for (int vertex = 0; vertex < 4; vertex++)
            {
                final int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
                centreX += Float.intBitsToFloat(vertices[offset]);
                centreZ += Float.intBitsToFloat(vertices[offset + 2]);
            }
            centreX *= 0.25F;
            centreZ *= 0.25F;

            for (int vertex = 0; vertex < 4; vertex++)
            {
                final int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
                final float x = Float.intBitsToFloat(vertices[offset]) - centreX;
                final float z = Float.intBitsToFloat(vertices[offset + 2]) - centreZ;
                vertices[offset] = Float.floatToRawIntBits(centreX + cos * x + sin * z);
                vertices[offset + 2] = Float.floatToRawIntBits(centreZ - sin * x + cos * z);

                final int normalOffset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.NORMAL;
                final int packedNormal = vertices[normalOffset];
                if ((packedNormal & 0x00FFFFFF) != 0)
                {
                    final float normalX = (byte) packedNormal / 127.0F;
                    final float normalY = (byte) (packedNormal >>> 8) / 127.0F;
                    final float normalZ = (byte) (packedNormal >>> 16) / 127.0F;
                    final float rotatedNormalX = cos * normalX + sin * normalZ;
                    final float rotatedNormalZ = -sin * normalX + cos * normalZ;
                    vertices[normalOffset] = packNormal(rotatedNormalX, normalY, rotatedNormalZ, packedNormal);
                    if (vertex == 0)
                    {
                        direction = Direction.getNearest(rotatedNormalX, normalY, rotatedNormalZ);
                    }
                }
            }

            result.add(new BakedQuad(
                vertices,
                quad.getTintIndex(),
                direction,
                quad.getSprite(),
                quad.isShade(),
                quad.hasAmbientOcclusion()
            ));
        }
        return result;
    }

    private static int packNormal(float x, float y, float z, int original)
    {
        final int packedX = Math.round(Math.max(-1.0F, Math.min(1.0F, x)) * 127.0F) & 0xFF;
        final int packedY = Math.round(Math.max(-1.0F, Math.min(1.0F, y)) * 127.0F) & 0xFF;
        final int packedZ = Math.round(Math.max(-1.0F, Math.min(1.0F, z)) * 127.0F) & 0xFF;
        return packedX | (packedY << 8) | (packedZ << 16) | (original & 0xFF000000);
    }

    @Override
    public TextureAtlasSprite getParticleIcon()
    {
        return leavesTex;
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext transformType, PoseStack poseStack, boolean applyLeftHandTransform)
    {
        Helpers.applyTransform(transformType, poseStack, applyLeftHandTransform);
        return this;
    }
}
