package com.eerussianguy.betterfoliage.model;

import java.util.*;

import com.google.common.collect.Maps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.eerussianguy.betterfoliage.BFConfig;
import com.eerussianguy.betterfoliage.Helpers;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.NamedRenderTypeManager;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class GrassBakedModel extends BFBakedModel
{
    public static List<GrassBakedModel> INSTANCES = new ArrayList<>();
    public static final ChunkRenderTypeSet RENDER_TYPES = ChunkRenderTypeSet.of(RenderType.cutout());
    private static final int REED_TEXTURE_COUNT = 4;
    private static final int REED_OFFSET_STEPS = 5;
    private static final int REED_MODELS_PER_TEXTURE = REED_OFFSET_STEPS * REED_OFFSET_STEPS;
    private static final float REED_MAX_OFFSET = 2.0F;
    private static final long REED_RANDOM_SALT = 0x6A09E667F3BCC909L;

    private final BlockModel blockModel;

    private final ResourceLocation dirt;
    private final ResourceLocation top;
    private final ResourceLocation overlay;
    private final boolean hasOverlay;
    private final ModelResourceLocation grass;
    private final boolean tint;
    private final boolean renderReed;

    @Nullable private TextureAtlasSprite dirtTex;
    @Nullable private TextureAtlasSprite topTex;
    @Nullable private TextureAtlasSprite overlayTex;

    private final BakedModel[] models = new BakedModel[16];
    private final BakedModel[] reedModels = new BakedModel[REED_TEXTURE_COUNT * REED_MODELS_PER_TEXTURE];

    public GrassBakedModel(ResourceLocation dirt, ResourceLocation top, ResourceLocation overlay, boolean tint, ResourceLocation grass, boolean renderReed)
    {
        this.blockModel = new BlockModel(null, new ArrayList<>(), new HashMap<>(), false, BlockModel.GuiLight.FRONT, ItemTransforms.NO_TRANSFORMS, new ArrayList<>());

        this.dirt = dirt;
        this.top = top;
        this.overlay = overlay;
        this.hasOverlay = !overlay.equals(Helpers.EMPTY);
        this.tint = tint;
        this.grass = ModelResourceLocation.standalone(grass);
        this.renderReed = renderReed;

        INSTANCES.add(this);
    }

    public void init()
    {
        dirtTex = Helpers.getTexture(dirt);
        topTex = Helpers.getTexture(top);
        overlayTex = hasOverlay ? Helpers.getTexture(overlay) : null;

        generateModels();
        if (renderReed)
        {
            generateReedModels();
        }
    }

    private void generateReedModels()
    {
        float[] offsets = Helpers.intervals(REED_OFFSET_STEPS, -REED_MAX_OFFSET, REED_MAX_OFFSET);
        int ordinal = 0;
        for (int texture = 0; texture < REED_TEXTURE_COUNT; texture++)
        {
            TextureAtlasSprite sprite = Helpers.getTexture(Helpers.identifier("block/better_reed_" + texture));
            for (float x : offsets)
            {
                for (float z : offsets)
                {
                    reedModels[ordinal++] = buildReed(sprite, x, z);
                }
            }
        }
    }

    private BakedModel buildReed(TextureAtlasSprite sprite, float xOffset, float zOffset)
    {
        BlockElement positive = buildReedPlane(xOffset, zOffset, 45.0F);
        BlockElement negative = buildReedPlane(xOffset, zOffset, -45.0F);

        SimpleBakedModel.Builder builder = new SimpleBakedModel.Builder(blockModel, ItemOverrides.EMPTY, false).particle(sprite);
        addUnculledFaces(builder, positive, sprite);
        addUnculledFaces(builder, negative, sprite);
        return builder.build(NamedRenderTypeManager.get(ResourceLocation.parse("cutout_mipped")));
    }

    private static BlockElement buildReedPlane(float xOffset, float zOffset, float angle)
    {
        Map<Direction, BlockElementFace> faces = Maps.newEnumMap(Direction.class);
        faces.put(Direction.NORTH, Helpers.makeFace(Helpers.UV_DEFAULT, false));
        faces.put(Direction.SOUTH, Helpers.makeFace(Helpers.UV_DEFAULT, false));

        Vector3f from = new Vector3f(xOffset, 16.0F, 8.0F + zOffset);
        Vector3f to = new Vector3f(16.0F + xOffset, 48.0F, 8.0F + zOffset);
        Vector3f origin = new Vector3f((8.0F + xOffset) * 0.0625F, 1.0F, (8.0F + zOffset) * 0.0625F);
        return new BlockElement(from, to, faces, new BlockElementRotation(origin, Direction.Axis.Y, angle, true), false);
    }

    private static void addUnculledFaces(SimpleBakedModel.Builder builder, BlockElement element, TextureAtlasSprite sprite)
    {
        for (Map.Entry<Direction, BlockElementFace> entry : element.faces.entrySet())
        {
            builder.addUnculledFace(Helpers.makeBakedQuad(element, entry.getValue(), sprite, entry.getKey(), BlockModelRotation.X0_Y0));
        }
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
            assert topTex != null;
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
                if (grassData.hasReed(BFConfig.CLIENT.reedPopulation.get()))
                {
                    List<BakedQuad> reedQuads = reedModels[grassData.getReedModel()].getQuads(state, side, rand, extraData, renderType);
                    quads.addAll(withLight(reedQuads, grassData.getReedLight()));
                }
                return quads;
            }
        }
        return models[0].getQuads(state, side, rand, extraData, renderType);
    }

    private static List<BakedQuad> withLight(List<BakedQuad> quads, int packedLight)
    {
        if (quads.isEmpty())
        {
            return quads;
        }

        List<BakedQuad> litQuads = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads)
        {
            int[] vertices = quad.getVertices().clone();
            for (int vertex = 0; vertex < 4; vertex++)
            {
                vertices[vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV2] = packedLight;
            }
            litQuads.add(new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion()));
        }
        return litQuads;
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
        final BlockState twoUpState = level.getBlockState(pos.above(2));
        final boolean up = upState.isAir() || upState.is(Blocks.SNOW);
        boolean reedEligible = renderReed && upState.is(Blocks.WATER) && twoUpState.isAir();
        if (reedEligible)
        {
            var clientLevel = Minecraft.getInstance().level;
            if (clientLevel == null)
            {
                reedEligible = false;
            }
            else
            {
                var biome = clientLevel.getBiome(pos);
                reedEligible = !biome.is(BiomeTags.IS_BEACH) && !biome.is(BiomeTags.IS_OCEAN);
            }
        }

        RandomSource reedRandom = RandomSource.create(pos.asLong() ^ REED_RANDOM_SALT);
        float reedPopulationRoll = reedRandom.nextFloat();
        int reedTexture = reedRandom.nextInt(REED_TEXTURE_COUNT);
        int reedXOffset = reedRandom.nextInt(REED_OFFSET_STEPS);
        int reedZOffset = reedRandom.nextInt(REED_OFFSET_STEPS);
        int reedModel = reedTexture * REED_MODELS_PER_TEXTURE + reedXOffset * REED_OFFSET_STEPS + reedZOffset;
        int reedLight = 0;
        if (reedEligible)
        {
            int waterLight = LevelRenderer.getLightColor(level, upState, pos.above());
            int airLight = LevelRenderer.getLightColor(level, twoUpState, pos.above(2));
            reedLight = LightTexture.pack(
                Math.max(LightTexture.block(waterLight), LightTexture.block(airLight)),
                Math.max(LightTexture.sky(waterLight), LightTexture.sky(airLight))
            );
        }

        GrassConnectionData connectionData = new GrassConnectionData(north, east, south, west, up, reedEligible, reedPopulationRoll, reedModel, reedLight);
        return extraData.derive().with(GrassConnectionData.PROPERTY, connectionData).build();
    }

    @Override
    public TextureAtlasSprite getParticleIcon()
    {
        return Objects.requireNonNull(dirtTex);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data)
    {
        return RENDER_TYPES;
    }
}
