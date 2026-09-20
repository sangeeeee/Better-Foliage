package com.eerussianguy.betterfoliage.model;

import com.eerussianguy.betterfoliage.BFConfig;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Two-bit diagonal selection computed once during mesh construction, independent of render RNG/camera. */
public final class FluffVisibilityData
{
    static final int NE_SW = 1, NW_SE = 2, FULL = NE_SW | NW_SE;
    static final int NORTH = 1, EAST = 2, SOUTH = 4, WEST = 8;
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final ModelProperty<Integer> PLANES = new ModelProperty<>();
    private static final ModelProperty<Boolean> FULL_TOP = new ModelProperty<>();
    private static final ThreadLocal<BlockPos.MutableBlockPos> NEIGHBOR = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
    private FluffVisibilityData() {}

    static ModelData append(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data)
    {
        var neighbor = NEIGHBOR.get();
        BlockState above = level.getBlockState(neighbor.setWithOffset(pos, Direction.UP));
        boolean fullTop = requiresFullTop(above);
        var config = BFConfig.CLIENT;
        int mask = FULL;
        if (config.fluffVisibilityEnabled.get() && !fullTop)
        {
            int horizontal = 0;
            for (int i = 0; i < HORIZONTAL.length; i++)
                if (level.getBlockState(neighbor.setWithOffset(pos, HORIZONTAL[i])).isAir()) horizontal |= 1 << i;
            // Only a fully enclosed horizontal ring needs the underside test.
            boolean bottomAir = horizontal == 0 && level.getBlockState(neighbor.setWithOffset(pos, Direction.DOWN)).isAir();
            mask = select(false, horizontal, bottomAir, pos.getX(), pos.getY(), pos.getZ(),
                config.fluffCornerSecondChance.get(), config.fluffSideChance.get(), config.fluffBottomChance.get());
        }
        ModelData selected = withMask(data, mask);
        selected = withFullTop(selected, fullTop);
        // Reuse the already sampled top state, and avoid snow/tint work entirely for invisible fluff.
        return mask == 0 ? selected : SnowyLeavesData.append(level, pos, state, selected, above);
    }

    public static boolean requiresFullTop(BlockState above)
    {
        return above.isAir() || above.is(BlockTags.SNOW)
            || above.is(net.minecraft.world.level.block.Blocks.SNOW)
            || above.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK);
    }

    static boolean fullTop(ModelData data) { return Boolean.TRUE.equals(data.get(FULL_TOP)); }

    static ModelData withFullTop(ModelData data, boolean fullTop)
    {
        return fullTop == fullTop(data) ? data : data.derive().with(FULL_TOP, fullTop).build();
    }

    static boolean allowsFluff(ModelData data, boolean sodiumHidden, boolean cullLeavesHidden)
    {
        // The Sodium bridge already exempts air/snow tops except in SOLID_AGGRESSIVE.
        // Never override its remaining suppression here.
        return !sodiumHidden && (fullTop(data) || !cullLeavesHidden);
    }

    static int mask(ModelData data)
    {
        Integer value = data.get(PLANES);
        return value == null ? FULL : value;
    }

    static ModelData withMask(ModelData data, int mask)
    {
        return mask(data) == mask ? data : data.derive().with(PLANES, mask).build();
    }

    static int select(boolean topOpenOrSnow, int horizontal, boolean bottomAir, int x, int y, int z,
        double cornerSecondChance, double sideChance, double bottomChance)
    {
        int count = Integer.bitCount(horizontal);
        if (topOpenOrSnow || count >= 3) return FULL;
        if (count == 0 && !bottomAir) return 0;
        // Separate coordinate hash stream: existing placement/rotation/snow-variant random draws never change.
        long seed = mix64((long) x * 0xD1B54A32D192ED03L ^ Long.rotateLeft((long) y * 0x94D049BB133111EBL, 21)
            ^ Long.rotateLeft((long) z * 0x9E3779B97F4A7C15L, 42) ^ 0xC6BC279692B5CC83L);
        double first = unit(mix64(seed ^ 0xA24BAED4963EE407L));
        double second = unit(mix64(seed ^ 0x9FB21C651E98DF25L));
        if (count == 2 && horizontal != (NORTH | SOUTH) && horizontal != (EAST | WEST))
        {
            int guaranteed = horizontal == (NORTH | EAST) || horizontal == (SOUTH | WEST) ? NE_SW : NW_SE;
            double optional = guaranteed == NE_SW ? second : first;
            return guaranteed | (optional < cornerSecondChance ? FULL ^ guaranteed : 0);
        }
        double chance = count == 0 ? bottomChance : sideChance;
        return (first < chance ? NE_SW : 0) | (second < chance ? NW_SE : 0);
    }

    /** Opposite rectangle vertices span the horizontal tangent on both front and back faces. */
    static boolean keep(BakedQuad quad, int mask)
    {
        if (mask == FULL) return true;
        if (mask == 0) return false;
        int[] vertices = quad.getVertices();
        int a = IQuadTransformer.POSITION, b = 2 * IQuadTransformer.STRIDE + a;
        float dx = Float.intBitsToFloat(vertices[b]) - Float.intBitsToFloat(vertices[a]);
        float dz = Float.intBitsToFloat(vertices[b + 2]) - Float.intBitsToFloat(vertices[a + 2]);
        int plane = dx * dz < 0 ? NE_SW : NW_SE;
        return (mask & plane) != 0;
    }

    private static double unit(long value) { return (value >>> 40) * 0x1.0p-24; }
    private static long mix64(long value)
    {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
