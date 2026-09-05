package com.eerussianguy.betterfoliage.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.eerussianguy.betterfoliage.BFConfig;
import com.eerussianguy.betterfoliage.Helpers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;

/** Renders purely client-side petal quads on water below supported cherry leaves. */
public final class WaterPetalRenderer
{
    private static final int SEARCH_DEPTH = 20;
    private static final int MIN_FALL_DISTANCE = 2;
    private static final float SURFACE_EPSILON = 0.002F;
    private static final float MAX_OFFSET = 0.03125F;
    private static final long RANDOM_SALT = 0xBB67AE8584CAA73BL;
    private static final ResourceLocation VANILLA_PETAL_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "block/pink_petals");
    private static final ResourceLocation BWG_YELLOW_LEAVES = ResourceLocation.fromNamespaceAndPath("biomeswevegone", "yellow_sakura_leaves");
    private static final ResourceLocation BWG_WHITE_LEAVES = ResourceLocation.fromNamespaceAndPath("biomeswevegone", "white_sakura_leaves");
    private static final ResourceLocation BWG_YELLOW_PETAL_TEXTURE = ResourceLocation.fromNamespaceAndPath("biomeswevegone", "block/yellow_sakura_petals");
    private static final ResourceLocation BWG_WHITE_PETAL_TEXTURE = ResourceLocation.fromNamespaceAndPath("biomeswevegone", "block/white_sakura_petals");
    private static final RenderType RENDER_TYPE = RenderType.cutoutMipped();
    private static volatile Map<Block, ResourceLocation> supportedLeaves;

    private WaterPetalRenderer()
    {
    }

    /** Called on the main client thread. All world reads happen here, before the worker-thread renderer is created. */
    public static void addSectionGeometry(AddSectionGeometryEvent event)
    {
        final double population = BFConfig.CLIENT.waterPetalPopulation.get();
        if (population <= 0.0D)
        {
            return;
        }

        final BlockPos origin = event.getSectionOrigin();
        final Map<Block, ResourceLocation> leafTextures = supportedLeaves();
        final List<PetalPatch> patches = collectPatches(event.getLevel(), origin, population, leafTextures);
        if (patches.isEmpty())
        {
            return;
        }

        // Resolve active atlas sprites only for leaf types that actually produced patches. Resource-pack replacements are honored.
        final Map<ResourceLocation, TextureAtlasSprite> sprites = new HashMap<>();
        for (PetalPatch patch : patches)
        {
            sprites.computeIfAbsent(patch.texture(), Helpers::getTexture);
        }
        final List<PetalPatch> immutablePatches = List.copyOf(patches);
        final Map<ResourceLocation, TextureAtlasSprite> immutableSprites = Map.copyOf(sprites);
        final int vegetationId = IrisShaderCompat.groundVegetationId();
        event.addRenderer(context -> render(immutablePatches, immutableSprites, vegetationId, context));
    }

    /**
     * Scans each of the section's 256 vertical columns once. The nearest non-air block is tracked while moving upward,
     * so the cost is bounded to at most 26 state reads per column instead of checking ten blocks above every water block.
     */
    private static List<PetalPatch> collectPatches(Level level, BlockPos origin, double population, Map<Block, ResourceLocation> leafTextures)
    {
        final int waterMinY = Math.max(origin.getY(), level.getMinBuildHeight());
        final int waterMaxY = Math.min(origin.getY() + 15, level.getMaxBuildHeight() - 1);
        if (waterMinY > waterMaxY)
        {
            return List.of();
        }

        final int scanMaxY = Math.min(waterMaxY + SEARCH_DEPTH, level.getMaxBuildHeight() - 1);
        if (!mayContainSupportedLeaves(level, origin, waterMinY, scanMaxY, leafTextures))
        {
            return List.of();
        }

        final List<PetalPatch> patches = new ArrayList<>();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++)
        {
            final int worldX = origin.getX() + localX;
            for (int localZ = 0; localZ < 16; localZ++)
            {
                final int worldZ = origin.getZ() + localZ;
                int candidateWaterY = Integer.MIN_VALUE;
                float candidateSurfaceHeight = 0.0F;

                for (int y = waterMinY; y <= scanMaxY; y++)
                {
                    cursor.set(worldX, y, worldZ);
                    final BlockState state = level.getBlockState(cursor);
                    if (state.isAir())
                    {
                        continue;
                    }

                    final ResourceLocation petalTexture = leafTextures.get(state.getBlock());
                    if (petalTexture != null)
                    {
                        if (candidateWaterY != Integer.MIN_VALUE)
                        {
                            final int fallDistance = y - candidateWaterY;
                            if (fallDistance >= MIN_FALL_DISTANCE && fallDistance <= SEARCH_DEPTH)
                            {
                                final long randomBits = mix64(BlockPos.asLong(worldX, candidateWaterY, worldZ) ^ RANDOM_SALT);
                                if (unitFloat(randomBits) < population)
                                {
                                    cursor.set(worldX, candidateWaterY + 1, worldZ);
                                    final int light = LevelRenderer.getLightColor(level, cursor);
                                    patches.add(createPatch(localX, candidateWaterY - origin.getY(), localZ, candidateSurfaceHeight, light, randomBits, petalTexture));
                                }
                            }
                        }

                        // This leaf is now the nearest non-air block and blocks every leaf above it from reaching the water.
                        candidateWaterY = Integer.MIN_VALUE;
                    }
                    else if (y <= waterMaxY && isStillWater(state))
                    {
                        final FluidState fluid = state.getFluidState();
                        candidateWaterY = y;
                        candidateSurfaceHeight = fluid.getHeight(level, cursor);
                    }
                    else
                    {
                        // Any non-air, non-eligible block breaks the falling path immediately.
                        candidateWaterY = Integer.MIN_VALUE;
                    }
                }
            }
        }
        return patches;
    }

    private static boolean mayContainSupportedLeaves(Level level, BlockPos origin, int minY, int maxY, Map<Block, ResourceLocation> leafTextures)
    {
        final LevelChunk chunk = level.getChunkAt(origin);
        final int minIndex = chunk.getSectionIndex(minY);
        final int maxIndex = chunk.getSectionIndex(maxY);
        final Predicate<BlockState> isSupportedLeaves = state -> leafTextures.containsKey(state.getBlock());
        for (int index = minIndex; index <= maxIndex; index++)
        {
            if (chunk.getSection(index).maybeHas(isSupportedLeaves))
            {
                return true;
            }
        }
        return false;
    }

    private static PetalPatch createPatch(int localX, int waterLocalY, int localZ, float surfaceHeight, int light, long populationBits, ResourceLocation texture)
    {
        final long variantBits = mix64(populationBits);
        final int quadrantMask = 1 + (int) Long.remainderUnsigned(variantBits, 15L);
        final int rotation = (int) ((variantBits >>> 8) & 3L);
        final float xOffset = byteOffset(variantBits >>> 16);
        final float zOffset = byteOffset(variantBits >>> 24);
        return new PetalPatch(localX, waterLocalY, waterLocalY + surfaceHeight + SURFACE_EPSILON, localZ, quadrantMask, rotation, xOffset, zOffset, light, texture);
    }

    private static void render(
        List<PetalPatch> patches,
        Map<ResourceLocation, TextureAtlasSprite> sprites,
        int vegetationId,
        AddSectionGeometryEvent.SectionRenderingContext context
    )
    {
        final VertexConsumer consumer = context.getOrCreateChunkBuffer(RENDER_TYPE);
        final PoseStack.Pose pose = context.getPoseStack().last();
        for (PetalPatch patch : patches)
        {
            // Associate only this synthetic patch with Iris' ordinary vegetation material. The water block beneath
            // the patch is its logical origin, leaving the surface vertices free to move instead of anchoring them
            // as the base of a crop. This is the same optional bridge used by Better Foliage reeds.
            final boolean irisContext = IrisShaderCompat.beginGroundVegetation(
                consumer,
                vegetationId,
                (int) patch.localX(),
                patch.waterLocalY(),
                (int) patch.localZ()
            );
            try
            {
                final TextureAtlasSprite sprite = sprites.get(patch.texture());
                for (int quadrant = 0; quadrant < 4; quadrant++)
                {
                    if ((patch.quadrantMask() & (1 << quadrant)) != 0)
                    {
                        emitQuadrant(consumer, pose, sprite, patch, quadrant);
                    }
                }
            }
            finally
            {
                if (irisContext)
                {
                    IrisShaderCompat.endGroundVegetation(consumer);
                }
            }
        }
    }

    private static void emitQuadrant(VertexConsumer consumer, PoseStack.Pose pose, TextureAtlasSprite sprite, PetalPatch patch, int quadrant)
    {
        final float x0 = quadrant >= 2 ? 0.5F : 0.0F;
        final float z0 = quadrant == 1 || quadrant == 2 ? 0.5F : 0.0F;
        final float x1 = x0 + 0.5F;
        final float z1 = z0 + 0.5F;
        // TextureAtlasSprite uses normalized 0..1 coordinates. Passing vanilla model-space 0..16 values here
        // escapes this sprite and samples unrelated entries from the full block atlas.
        final float u0 = sprite.getU(x0);
        final float v0 = sprite.getV(z0);
        final float u1 = sprite.getU(x1);
        final float v1 = sprite.getV(z1);

        // Chunk cutout rendering culls back faces, so a single upward-facing quad disappears when viewed underwater.
        // Emit both windings at the same height: only the face aimed at the camera survives culling, without an offset.
        vertex(consumer, pose, patch, x0, z0, u0, v0, 1.0F);
        vertex(consumer, pose, patch, x0, z1, u0, v1, 1.0F);
        vertex(consumer, pose, patch, x1, z1, u1, v1, 1.0F);
        vertex(consumer, pose, patch, x1, z0, u1, v0, 1.0F);

        vertex(consumer, pose, patch, x1, z0, u1, v0, -1.0F);
        vertex(consumer, pose, patch, x1, z1, u1, v1, -1.0F);
        vertex(consumer, pose, patch, x0, z1, u0, v1, -1.0F);
        vertex(consumer, pose, patch, x0, z0, u0, v0, -1.0F);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, PetalPatch patch, float x, float z, float u, float v, float normalY)
    {
        final float rotatedX;
        final float rotatedZ;
        switch (patch.rotation())
        {
            case 1 -> {
                rotatedX = 1.0F - z;
                rotatedZ = x;
            }
            case 2 -> {
                rotatedX = 1.0F - x;
                rotatedZ = 1.0F - z;
            }
            case 3 -> {
                rotatedX = z;
                rotatedZ = 1.0F - x;
            }
            default -> {
                rotatedX = x;
                rotatedZ = z;
            }
        }

        consumer.addVertex(pose, patch.localX() + rotatedX + patch.xOffset(), patch.localY(), patch.localZ() + rotatedZ + patch.zOffset())
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(patch.light())
            .setNormal(pose, 0.0F, normalY, 0.0F);
    }

    /** Marks a lower water section dirty when a leaf or an intervening obstruction changes across a section boundary. */
    public static void onBlockChanged(LevelRenderer renderer, BlockGetter level, BlockPos pos, BlockState oldState, BlockState newState)
    {
        if (BFConfig.CLIENT.waterPetalPopulation.get() <= 0.0D)
        {
            return;
        }

        final boolean oldSupportedLeaves = isSupportedLeaves(oldState);
        final boolean newSupportedLeaves = isSupportedLeaves(newState);
        if (oldSupportedLeaves != newSupportedLeaves)
        {
            markFirstWaterBelow(renderer, level, pos, SEARCH_DEPTH);
            return;
        }

        // A state-property update cannot alter the path. Only a transition between air and a block can expose/block it.
        if (oldState.isAir() == newState.isAir())
        {
            return;
        }

        final BlockPos.MutableBlockPos cursor = pos.mutable();
        int leafDistance = -1;
        for (int distance = 1; distance < SEARCH_DEPTH; distance++)
        {
            cursor.setWithOffset(pos, 0, distance, 0);
            final BlockState state = level.getBlockState(cursor);
            if (state.isAir())
            {
                continue;
            }
            if (isSupportedLeaves(state))
            {
                leafDistance = distance;
            }
            break;
        }

        if (leafDistance > 0)
        {
            markFirstWaterBelow(renderer, level, pos, SEARCH_DEPTH - leafDistance);
        }
    }

    private static void markFirstWaterBelow(LevelRenderer renderer, BlockGetter level, BlockPos pos, int maxDistance)
    {
        final BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int distance = 1; distance <= maxDistance; distance++)
        {
            cursor.setWithOffset(pos, 0, -distance, 0);
            final BlockState state = level.getBlockState(cursor);
            if (state.isAir())
            {
                continue;
            }
            if (isStillWater(state))
            {
                final int changedSectionY = SectionPos.blockToSectionCoord(pos.getY());
                final int waterSectionY = SectionPos.blockToSectionCoord(cursor.getY());
                if (changedSectionY != waterSectionY)
                {
                    renderer.setSectionDirty(
                        SectionPos.blockToSectionCoord(cursor.getX()),
                        waterSectionY,
                        SectionPos.blockToSectionCoord(cursor.getZ())
                    );
                }
            }
            break;
        }
    }

    private static boolean isStillWater(BlockState state)
    {
        return state.is(Blocks.WATER) && state.getFluidState().isSource();
    }

    private static boolean isSupportedLeaves(BlockState state)
    {
        return supportedLeaves().containsKey(state.getBlock());
    }

    private static Map<Block, ResourceLocation> supportedLeaves()
    {
        Map<Block, ResourceLocation> result = supportedLeaves;
        if (result == null)
        {
            synchronized (WaterPetalRenderer.class)
            {
                result = supportedLeaves;
                if (result == null)
                {
                    final Map<Block, ResourceLocation> discovered = new HashMap<>();
                    discovered.put(Blocks.CHERRY_LEAVES, VANILLA_PETAL_TEXTURE);
                    BuiltInRegistries.BLOCK.getOptional(BWG_YELLOW_LEAVES)
                        .ifPresent(block -> discovered.put(block, BWG_YELLOW_PETAL_TEXTURE));
                    BuiltInRegistries.BLOCK.getOptional(BWG_WHITE_LEAVES)
                        .ifPresent(block -> discovered.put(block, BWG_WHITE_PETAL_TEXTURE));
                    result = Map.copyOf(discovered);
                    supportedLeaves = result;
                }
            }
        }
        return result;
    }

    private static float unitFloat(long value)
    {
        return (float) (value >>> 40) * 0x1.0p-24F;
    }

    private static float byteOffset(long value)
    {
        return (((float) (value & 255L) / 255.0F) * 2.0F - 1.0F) * MAX_OFFSET;
    }

    private static long mix64(long value)
    {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private record PetalPatch(float localX, int waterLocalY, float localY, float localZ, int quadrantMask, int rotation, float xOffset, float zOffset, int light, ResourceLocation texture)
    {
    }
}
