package com.eerussianguy.betterfoliage.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.eerussianguy.betterfoliage.Helpers;
import com.google.common.collect.Maps;
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
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.NamedRenderTypeManager;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class ReedBakedModelSet
{
    public static final int TEXTURE_COUNT = 4;
    public static final int OFFSET_STEPS = 5;
    public static final int MODELS_PER_TEXTURE = OFFSET_STEPS * OFFSET_STEPS;
    private static final float MAX_OFFSET = 2.0F;

    private final BakedModel[] models = new BakedModel[TEXTURE_COUNT * MODELS_PER_TEXTURE];
    private final BlockModel blockModel = new BlockModel(null, new ArrayList<>(), new HashMap<>(), false, BlockModel.GuiLight.FRONT, ItemTransforms.NO_TRANSFORMS, new ArrayList<>());

    public ReedBakedModelSet(Function<ResourceLocation, TextureAtlasSprite> spriteGetter)
    {
        float[] offsets = Helpers.intervals(OFFSET_STEPS, -MAX_OFFSET, MAX_OFFSET);
        int ordinal = 0;
        for (int texture = 0; texture < TEXTURE_COUNT; texture++)
        {
            TextureAtlasSprite sprite = spriteGetter.apply(Helpers.identifier("block/better_reed_" + texture));
            for (float x : offsets)
            {
                for (float z : offsets)
                {
                    models[ordinal++] = build(sprite, x, z);
                }
            }
        }
    }

    public List<BakedQuad> getQuads(int model, int packedLight, @Nullable BlockState state, @Nullable Direction side, RandomSource random, ModelData data, @Nullable RenderType renderType)
    {
        List<BakedQuad> quads = models[model].getQuads(state, side, random, data, renderType);
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

    private BakedModel build(TextureAtlasSprite sprite, float xOffset, float zOffset)
    {
        BlockElement positive = buildPlane(xOffset, zOffset, 45.0F);
        BlockElement negative = buildPlane(xOffset, zOffset, -45.0F);
        SimpleBakedModel.Builder builder = new SimpleBakedModel.Builder(blockModel, ItemOverrides.EMPTY, false).particle(sprite);
        addUnculledFaces(builder, positive, sprite);
        addUnculledFaces(builder, negative, sprite);
        return builder.build(NamedRenderTypeManager.get(ResourceLocation.parse("cutout_mipped")));
    }

    private static BlockElement buildPlane(float xOffset, float zOffset, float angle)
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
}
