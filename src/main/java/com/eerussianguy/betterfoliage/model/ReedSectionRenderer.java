package com.eerussianguy.betterfoliage.model;

import java.util.ArrayList;
import java.util.List;

import com.eerussianguy.betterfoliage.BFConfig;
import com.eerussianguy.betterfoliage.Helpers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Renders client-only reeds as independent section geometry, allowing only their vertices to carry a shader block ID. */
public final class ReedSectionRenderer
{
    private static final RenderType RENDER_TYPE = RenderType.cutout();
    private static volatile ReedBakedModelSet models;

    private ReedSectionRenderer()
    {
    }

    /** Collects world data on the main thread before handing immutable data to the section-building worker. */
    public static void addSectionGeometry(AddSectionGeometryEvent event)
    {
        final double population = BFConfig.CLIENT.reedPopulation.get();
        if (population <= 0.0D)
        {
            return;
        }

        final Level level = event.getLevel();
        final BlockPos origin = event.getSectionOrigin();
        final LevelChunkSection section = level.getChunkAt(origin).getSection(level.getSectionIndex(origin.getY()));
        if (!section.maybeHas(state -> state.is(Blocks.DIRT)))
        {
            return;
        }

        final List<ReedPatch> patches = collectPatches(level, section, origin, population);
        if (patches.isEmpty())
        {
            return;
        }

        final ReedBakedModelSet reedModels = models();
        final int vegetationId = IrisShaderCompat.groundVegetationId();
        final List<ReedPatch> immutablePatches = List.copyOf(patches);
        event.addRenderer(context -> render(immutablePatches, reedModels, vegetationId, context));
    }

    public static void clearCache()
    {
        models = null;
    }

    private static List<ReedPatch> collectPatches(Level level, LevelChunkSection section, BlockPos origin, double population)
    {
        final List<ReedPatch> patches = new ArrayList<>();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int localX = 0; localX < 16; localX++)
        {
            for (int localY = 0; localY < 16; localY++)
            {
                for (int localZ = 0; localZ < 16; localZ++)
                {
                    // Read the section palette directly; only candidate dirt positions require world/biome lookups.
                    if (!section.getBlockState(localX, localY, localZ).is(Blocks.DIRT))
                    {
                        continue;
                    }

                    cursor.set(origin.getX() + localX, origin.getY() + localY, origin.getZ() + localZ);
                    final ReedData data = ReedData.create(level, cursor);
                    if (data.shouldRender(population))
                    {
                        patches.add(new ReedPatch(localX, localY, localZ, data.model(), data.light()));
                    }
                }
            }
        }
        return patches;
    }

    private static ReedBakedModelSet models()
    {
        ReedBakedModelSet result = models;
        if (result == null)
        {
            synchronized (ReedSectionRenderer.class)
            {
                result = models;
                if (result == null)
                {
                    result = new ReedBakedModelSet(Helpers::getTexture);
                    models = result;
                }
            }
        }
        return result;
    }

    private static void render(List<ReedPatch> patches, ReedBakedModelSet reedModels, int vegetationId, AddSectionGeometryEvent.SectionRenderingContext context)
    {
        final VertexConsumer consumer = context.getOrCreateChunkBuffer(RENDER_TYPE);
        final PoseStack poseStack = context.getPoseStack();
        final RandomSource random = RandomSource.create(0L);
        final BlockState dirt = Blocks.DIRT.defaultBlockState();

        for (ReedPatch patch : patches)
        {
            // The reed begins in the water block above the dirt. Using that as Iris' mid-block origin keeps the base
            // anchored while shader packs displace the upper vertices like ordinary ground vegetation.
            final boolean irisContext = IrisShaderCompat.beginGroundVegetation(
                consumer,
                vegetationId,
                patch.localX(),
                patch.localY() + 1,
                patch.localZ()
            );
            poseStack.pushPose();
            poseStack.translate(patch.localX(), patch.localY(), patch.localZ());
            try
            {
                for (BakedQuad quad : reedModels.getQuads(patch.model(), dirt, random, ModelData.EMPTY, RENDER_TYPE))
                {
                    consumer.putBulkData(poseStack.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, patch.light(), 0);
                }
            }
            finally
            {
                poseStack.popPose();
                if (irisContext)
                {
                    IrisShaderCompat.endGroundVegetation(consumer);
                }
            }
        }
    }

    private record ReedPatch(int localX, int localY, int localZ, int model, int light)
    {
    }
}
