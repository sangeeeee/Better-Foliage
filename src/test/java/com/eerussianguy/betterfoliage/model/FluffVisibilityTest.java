package com.eerussianguy.betterfoliage.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

import static com.eerussianguy.betterfoliage.model.FluffVisibilityData.*;

/** Headless regression for deterministic thinning and the shared BF / pack-proxy geometry path. */
final class FluffVisibilityTest
{
    private static int checks;

    static void run()
    {
        rules();
        distribution();
        geometry();
        check(mask(ModelData.EMPTY) == FULL, "missing model data preserves the full cross");
        ModelData hidden = withMask(ModelData.EMPTY, 0);
        check(mask(hidden) == 0 && withMask(hidden, 0) == hidden, "hidden data reused without allocation");
        check(mask(withMask(hidden, FULL)) == FULL, "reused hidden data becomes visible again");
        System.out.println("Fluff visibility: " + checks + " checks passed");
    }

    private static void rules()
    {
        for (int horizontal = 0; horizontal < 16; horizontal++)
        {
            int count = Integer.bitCount(horizontal);
            boolean corner = count == 2 && horizontal != (NORTH | SOUTH) && horizontal != (EAST | WEST);
            for (int sample = -100; sample < 100; sample++)
            {
                check(select(true, horizontal, false, sample, sample * 3, -sample, 0, 0, 0) == FULL,
                    "top air/snow overrides thinning");
                int zero = select(false, horizontal, false, sample, sample * 3, -sample, 0, 0, 0);
                int one = select(false, horizontal, false, sample, sample * 3, -sample, 1, 1, 1);
                int expected = count >= 3 ? FULL : !corner ? 0
                    : horizontal == (NORTH | EAST) || horizontal == (SOUTH | WEST) ? NE_SW : NW_SE;
                check(zero == expected, "zero probability keeps only the correct guaranteed corner");
                check(one == (count == 0 ? 0 : FULL), "one probability restores all exposed planes");
                check(select(false, 0, true, sample, 5, -sample, 0, 0, 1) == FULL, "underside has its own probability");
                check(select(false, 0, true, sample, 5, -sample, 1, 1, 0) == 0, "underside zero probability");
            }
        }
    }

    private static void distribution()
    {
        int samples = 100_000;
        int[] counts = new int[3];
        int secondCorner = 0, undersidePlanes = 0;
        int[] changed = new int[3];
        for (int i = 0; i < samples; i++)
        {
            int x = i - samples / 2, y = i % 385 - 64, z = 193 - i * 17;
            int side = select(false, NORTH, false, x, y, z, .5, .65, .65);
            counts[Integer.bitCount(side)]++;
            check(side == select(false, NORTH | SOUTH, false, x, y, z, .5, .65, .65),
                "same coordinate and probability use stable independent plane draws");
            int corner = select(false, NORTH | EAST, false, x, y, z, .5, .65, .65);
            check((corner & NE_SW) != 0, "corner never loses its silhouette plane");
            if (corner == FULL) secondCorner++;
            undersidePlanes += Integer.bitCount(select(false, 0, true, x, y, z, .5, .65, .65));
            if (side != select(false, NORTH, false, x + 1, y, z, .5, .65, .65)) changed[0]++;
            if (side != select(false, NORTH, false, x, y + 1, z, .5, .65, .65)) changed[1]++;
            if (side != select(false, NORTH, false, x, y, z + 1, .5, .65, .65)) changed[2]++;
        }
        near(counts[0] / (double) samples, .1225, "no plane frequency");
        near(counts[1] / (double) samples, .455, "one plane frequency");
        near(counts[2] / (double) samples, .4225, "two planes frequency");
        near(secondCorner / (double) samples, .5, "optional corner frequency");
        near(undersidePlanes / (2.0 * samples), .65, "underside per-plane frequency");
        for (int axis = 0; axis < 3; axis++) near(changed[axis] / (double) samples,
            1 - Math.pow(.65 * .65 + .35 * .35, 2), "adjacent coordinate decorrelation, axis " + axis);
    }

    private static void geometry()
    {
        try (SpriteContents contents = new SpriteContents(ResourceLocation.parse("test:bushy"), new FrameSize(16, 16),
            new NativeImage(16, 16, true), ResourceMetadata.EMPTY))
        {
            TextureAtlasSprite base = new TestSprite(contents, 16), composite = new TestSprite(contents, 128);
            neighborCulling(base);
            FaceBakery bakery = new FaceBakery();
            for (float offset : new float[] {-5, 0, 5})
            {
                List<BakedQuad> cross = new ArrayList<>();
                for (float angle : new float[] {45, -45}) for (Direction side : new Direction[] {Direction.NORTH, Direction.SOUTH})
                {
                    BakedQuad quad = bakery.bakeQuad(new Vector3f(-8 + offset / 2, -8 + offset / 1.2F, 8 + offset / 2),
                        new Vector3f(24 + offset / 2, 24 + offset / 1.2F, 8 + offset / 2),
                        new BlockElementFace(null, 0, "", new BlockFaceUV(new float[] {0, 0, 16, 16}, 0)), base, side,
                        BlockModelRotation.X0_Y0, new BlockElementRotation(new Vector3f(.5F, 0, .5F), Direction.Axis.Y, angle, false), false);
                    check(keep(quad, angle == 45 ? NE_SW : NW_SE), "baked rotation maps to correct world diagonal, both faces");
                    cross.add(quad);
                }
                List<int[]> originals = cross.stream().map(q -> q.getVertices().clone()).toList();
                for (int planes = 0; planes <= FULL; planes++) for (float jitter : new float[] {-3, 0, 2.7F})
                {
                    int expected = 2 * Integer.bitCount(planes);
                    List<BakedQuad> plain = new ArrayList<>(), layered = new ArrayList<>(), composed = new ArrayList<>();
                    // The pre-existing full path is the reference, including offset/rotation and source ordering.
                    List<BakedQuad> reference = new ArrayList<>();
                    LeavesBakedModel.appendPositionRotation(reference, cross, List.of(), jitter);
                    LeavesBakedModel.appendPositionRotation(plain, cross, List.of(), jitter, null, planes);
                    LeavesBakedModel.appendPositionRotation(layered, cross, cross, jitter, null, planes);
                    LeavesBakedModel.appendPositionRotation(composed, cross, List.of(), jitter, composite, planes);
                    check(plain.size() == expected && composed.size() == expected && layered.size() == expected * 2,
                        "plain, composite, and fallback overlay have the same plane mask");
                    int selected = 0;
                    for (int i = 0; i < cross.size(); i++)
                    {
                        check(Arrays.equals(originals.get(i), cross.get(i).getVertices()), "shared cached geometry remains immutable");
                        if (!keep(cross.get(i), planes)) continue;
                        BakedQuad actual = plain.get(selected);
                        check(Arrays.equals(reference.get(i).getVertices(), actual.getVertices()), "kept quad geometry bit-identical to old path");
                        check(Arrays.equals(actual.getVertices(), layered.get(selected + expected).getVertices()), "fallback snow follows selected base geometry");
                        check(composed.get(selected).getSprite() == composite && !composed.get(selected).isTinted(), "composite remains untinted");
                        selected++;
                    }
                    // Filtering also remains correct on arbitrary front/back subsets.
                    for (int face = 0; face < 2; face++)
                    {
                        List<BakedQuad> bucket = new ArrayList<>();
                        LeavesBakedModel.appendPositionRotation(bucket, List.of(cross.get(face), cross.get(face + 2)), List.of(), jitter, null, planes);
                        check(bucket.size() == Integer.bitCount(planes), "directional front/back bucket agrees with unculled cross");
                    }
                }
            }
        }
    }

    private static void neighborCulling(TextureAtlasSprite sprite)
    {
        var block = new BlockModel(null, new ArrayList<>(), java.util.Map.of(), false,
            BlockModel.GuiLight.FRONT, ItemTransforms.NO_TRANSFORMS, new ArrayList<>());
        var builder = new net.minecraft.client.resources.model.SimpleBakedModel.Builder(block, ItemOverrides.EMPTY, false).particle(sprite);
        var faces = java.util.Map.of(
            Direction.NORTH, new BlockElementFace(null, 0, "", new BlockFaceUV(new float[] {0, 0, 16, 16}, 0)),
            Direction.SOUTH, new BlockElementFace(null, 0, "", new BlockFaceUV(new float[] {0, 0, 16, 16}, 0)));
        for (float angle : new float[] {45, -45})
            LeavesBakedModel.assembleFluffFaces(builder, new BlockElement(new Vector3f(-8, -8, 8),
                new Vector3f(24, 24, 8), faces,
                new BlockElementRotation(new Vector3f(.5F, 0, .5F), Direction.Axis.Y, angle, false), false), sprite);
        var model = builder.build();
        var random = net.minecraft.util.RandomSource.create(17);
        // Simulate every combination of six blocked cube faces, including north/south/both.
        for (int blocked = 0; blocked < 64; blocked++)
        {
            List<BakedQuad> emitted = new ArrayList<>(model.getQuads(null, null, random));
            for (Direction side : Direction.values())
            {
                check(model.getQuads(null, side, random).isEmpty(), "fluff is never tied to a cube face bucket");
                if ((blocked & (1 << side.ordinal())) == 0) emitted.addAll(model.getQuads(null, side, random));
            }
            check(emitted.size() == 4, "neighbor culling cannot remove one half of the cross");
            for (int planes = 0; planes <= FULL; planes++)
            {
                List<BakedQuad> filtered = new ArrayList<>();
                LeavesBakedModel.appendPositionRotation(filtered, emitted, List.of(), 2, null, planes);
                check(filtered.size() == Integer.bitCount(planes) * 2, "explicit thinning keeps paired faces regardless of neighbors");
            }
        }
    }

    private static final class TestSprite extends TextureAtlasSprite
    {
        TestSprite(SpriteContents contents, int x)
        {
            super(ResourceLocation.parse("test:atlas"), contents, 256, 256, x, 32);
        }
    }

    private static void near(double actual, double expected, String message)
    {
        check(Math.abs(actual - expected) < .01, message + ": " + actual);
    }

    private static void check(boolean value, String message)
    {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
