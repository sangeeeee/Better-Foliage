package com.eerussianguy.betterfoliage.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.eerussianguy.betterfoliage.BFConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Adds Better Foliage reeds without replacing the active resource-pack dirt model. */
public class DirtReedBakedModel extends BFBakedModel
{
    private static final RenderType REED_RENDER_TYPE = RenderType.cutout();
    private static final ChunkRenderTypeSet REED_RENDER_TYPES = ChunkRenderTypeSet.of(REED_RENDER_TYPE);

    private final BakedModel delegate;
    private final ReedBakedModelSet reeds;

    public DirtReedBakedModel(BakedModel delegate, Function<ResourceLocation, TextureAtlasSprite> spriteGetter)
    {
        this.delegate = delegate;
        this.reeds = new ReedBakedModelSet(spriteGetter);
    }

    @Override
    @NotNull
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random, ModelData data, @Nullable RenderType renderType)
    {
        boolean delegatePass = renderType == null || state == null || delegate.getRenderTypes(state, random, data).contains(renderType);
        List<BakedQuad> quads = delegatePass
            ? new ArrayList<>(delegate.getQuads(state, side, random, data, renderType))
            : new ArrayList<>();

        ReedData reedData = data.get(ReedData.PROPERTY);
        boolean reedPass = renderType == null || renderType == REED_RENDER_TYPE;
        if (reedPass && reedData != null && reedData.shouldRender(BFConfig.CLIENT.reedPopulation.get()))
        {
            quads.addAll(reeds.getQuads(reedData.model(), reedData.light(), state, side, random, data, renderType));
        }
        return quads;
    }

    @Override
    @NotNull
    public ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data)
    {
        return delegate.getModelData(level, pos, state, data).derive()
            .with(ReedData.PROPERTY, ReedData.create(level, pos))
            .build();
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data)
    {
        return ChunkRenderTypeSet.union(delegate.getRenderTypes(state, random, data), REED_RENDER_TYPES);
    }

    @Override
    public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType)
    {
        return delegate.useAmbientOcclusion(state, data, renderType);
    }

    @Override
    public boolean useAmbientOcclusion()
    {
        return delegate.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d()
    {
        return delegate.isGui3d();
    }

    @Override
    public boolean usesBlockLight()
    {
        return delegate.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer()
    {
        return delegate.isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon()
    {
        return delegate.getParticleIcon();
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data)
    {
        return delegate.getParticleIcon(data);
    }

    @Override
    public ItemTransforms getTransforms()
    {
        return delegate.getTransforms();
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext transformType, PoseStack poseStack, boolean applyLeftHandTransform)
    {
        delegate.applyTransform(transformType, poseStack, applyLeftHandTransform);
        return this;
    }

    @Override
    public ItemOverrides getOverrides()
    {
        return delegate.getOverrides();
    }
}
