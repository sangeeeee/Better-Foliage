package com.eerussianguy.betterfoliage.model;

import java.util.*;
import java.util.function.Function;

import com.google.common.collect.Maps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.eerussianguy.betterfoliage.BFConfig;
import com.eerussianguy.betterfoliage.Helpers;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.NamedRenderTypeManager;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class GrassBakedModel extends BFBakedModel
{
    public static final ChunkRenderTypeSet RENDER_TYPES = ChunkRenderTypeSet.of(RenderType.cutout());

    private final BlockModel blockModel;

    private final boolean hasOverlay;
    private final ModelResourceLocation grass;
    private final boolean tint;
    private final boolean renderReed;

    private final TextureAtlasSprite dirtTex;
    private final TextureAtlasSprite topTex;
    @Nullable private final TextureAtlasSprite overlayTex;

    private final BakedModel[] models = new BakedModel[16];
    @Nullable private final ReedBakedModelSet reedModels;

    public GrassBakedModel(ResourceLocation dirt, ResourceLocation top, ResourceLocation overlay, boolean tint, ResourceLocation grass, boolean renderReed, Function<ResourceLocation, TextureAtlasSprite> spriteGetter)
    {
        this.blockModel = new BlockModel(null, new ArrayList<>(), new HashMap<>(), false, BlockModel.GuiLight.FRONT, ItemTransforms.NO_TRANSFORMS, new ArrayList<>());

        this.hasOverlay = !overlay.equals(Helpers.EMPTY);
        this.tint = tint;
        this.grass = ModelResourceLocation.standalone(grass);
        this.renderReed = renderReed;
        this.dirtTex = spriteGetter.apply(dirt);
        this.topTex = spriteGetter.apply(top);
        this.overlayTex = hasOverlay ? spriteGetter.apply(overlay) : null;
        generateModels();
        this.reedModels = renderReed ? new ReedBakedModelSet(spriteGetter) : null;
    }

    private BlockElement buildCore()
    {
        Map<Direction, BlockElementFace> mapFaces = Maps.newEnumMap(Direction.class);
        for (Direction d : Helpers.DIRECTIONS)
        {
            BlockFaceUV faceUV = new BlockFaceUV(new float[] {0f, 0f, 16f, 16f}, 0);
            mapFaces.put(d, (d == Direction.UP && tint) ? Helpers.makeTintedFace(faceUV) : Helpers.makeFace(faceUV));
        }
        return new BlockElement(new Vector3f(0f, 0f, 0f), new Vector3f(16f, 16f, 16f), mapFaces, null, true);
    }

    public void generateModels()
    {
        BlockElement core = buildCore();
        for (int meta = 0; meta < 16; meta++)
        {
            Map<Direction, BlockElementFace> mapFacesIn = Maps.newEnumMap(Direction.class);
            for (Direction d : Helpers.DIRECTIONS)
            {
                BlockFaceUV faceUV = new BlockFaceUV(new float[] {0f, 0f, 16f, 16f}, 0);
                mapFacesIn.put(d, (d != Direction.DOWN && tint) ? Helpers.makeTintedFace(faceUV) : Helpers.makeFace(faceUV));
            }
            BlockElement part = new BlockElement(new Vector3f(0f, 0f, 0f), new Vector3f(16f, 16f, 16f), mapFacesIn, null, true);
            SimpleBakedModel.Builder builder = new SimpleBakedModel.Builder(blockModel, ItemOverrides.EMPTY, false).particle(topTex);

            final int fMeta = meta;
            Helpers.assembleFacesConditional(builder, core, direction -> direction == Direction.UP ? topTex : dirtTex);
            if (hasOverlay)
            {
                Helpers.assembleFacesConditional(builder, part, direction -> resolveTexture(direction, stateFromMeta(fMeta)));
            }
            models[meta] = builder.build(NamedRenderTypeManager.get(ResourceLocation.parse("cutout_mipped")));
        }
    }

    private TextureAtlasSprite resolveTexture(Direction d, boolean[] booleans)
    {
        assert dirtTex != null && topTex != null && overlayTex != null;
        return switch (d)
            {
                case UP -> topTex;
                case NORTH -> booleans[0] ? topTex : overlayTex;
                case EAST -> booleans[1] ? topTex : overlayTex;
                case SOUTH -> booleans[2] ? topTex : overlayTex;
                case WEST -> booleans[3] ? topTex : overlayTex;
                default -> dirtTex;
            };
    }

    private static boolean[] stateFromMeta(int meta)
    {
        boolean[] state = {false, false, false, false}; // N E S W
        state[0] = (meta & 1) > 0;
        state[1] = (meta & 2) > 0;
        state[2] = (meta & 4) > 0;
        state[3] = (meta & 8) > 0;
        return state;
    }

    @Override
    @NotNull
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType)
    {
        if (extraData.has(GrassConnectionData.PROPERTY))
        {
            GrassConnectionData grassData = extraData.get(GrassConnectionData.PROPERTY);
            if (grassData != null)
            {
                final int meta = grassData.get();
                List<BakedQuad> quads = new ArrayList<>(models[meta].getQuads(state, side, rand, extraData, renderType));
                if (grassData.hasUp() && !grass.id().equals(Helpers.EMPTY) && rand.nextInt(BFConfig.CLIENT.extraGrassRarity.get()) == 0)
                {
                    final BakedModel grassModel = Minecraft.getInstance().getModelManager().getModel(grass);
                    quads.addAll(grassModel.getQuads(state, side, rand, extraData, renderType));
                }
                ReedData reedData = extraData.get(ReedData.PROPERTY);
                if (reedData != null && reedData.shouldRender(BFConfig.CLIENT.reedPopulation.get()))
                {
                    quads.addAll(Objects.requireNonNull(reedModels).getQuads(reedData.model(), reedData.light(), state, side, rand, extraData, renderType));
                }
                return quads;
            }
        }
        return models[0].getQuads(state, side, rand, extraData, renderType);
    }

    @Override
    @NotNull
    public ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData extraData)
    {
        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        final BlockPos down = pos.below();
        final boolean north = level.getBlockState(mutable.setWithOffset(down, Direction.NORTH)).hasProperty(BlockStateProperties.SNOWY);
        final boolean east = level.getBlockState(mutable.setWithOffset(down, Direction.EAST)).hasProperty(BlockStateProperties.SNOWY);
        final boolean south = level.getBlockState(mutable.setWithOffset(down, Direction.SOUTH)).hasProperty(BlockStateProperties.SNOWY);
        final boolean west = level.getBlockState(mutable.setWithOffset(down, Direction.WEST)).hasProperty(BlockStateProperties.SNOWY);
        final BlockState upState = level.getBlockState(mutable.setWithOffset(pos, Direction.UP));
        final boolean up = upState.isAir() || upState.is(Blocks.SNOW);
        GrassConnectionData connectionData = new GrassConnectionData(north, east, south, west, up);
        ModelData.Builder builder = extraData.derive().with(GrassConnectionData.PROPERTY, connectionData);
        if (renderReed)
        {
            builder.with(ReedData.PROPERTY, ReedData.create(level, pos));
        }
        return builder.build();
    }

    @Override
    public TextureAtlasSprite getParticleIcon()
    {
        return dirtTex;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data)
    {
        return RENDER_TYPES;
    }
}
