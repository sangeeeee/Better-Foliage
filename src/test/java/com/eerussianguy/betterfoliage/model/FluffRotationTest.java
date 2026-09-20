package com.eerussianguy.betterfoliage.model;

import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.IQuadTransformer;

/** Bit-for-bit comparison with the pre-optimization rotation implementation. */
public final class FluffRotationTest
{
    public static void main(String[] args)
    {
        Random random = new Random(19860423L);
        int checks = 0;
        for (int sample = 0; sample < 1000; sample++)
        {
            int[] data = new int[4 * IQuadTransformer.STRIDE];
            for (int i = 0; i < data.length; i++) data[i] = random.nextInt();
            int commonNormal = random.nextInt();
            for (int vertex = 0; vertex < 4; vertex++)
            {
                int offset = vertex * IQuadTransformer.STRIDE;
                for (int axis = 0; axis < 3; axis++)
                    data[offset + IQuadTransformer.POSITION + axis] = Float.floatToRawIntBits(random.nextFloat() * 3 - 1);
                data[offset + IQuadTransformer.NORMAL] = sample % 3 == 0 ? 0
                    : sample % 3 == 1 ? commonNormal : random.nextInt();
            }
            int[] before = data.clone();
            BakedQuad quad = new BakedQuad(data, sample % 2 - 1, Direction.NORTH, null, sample % 2 == 0, true);
            List<BakedQuad> normal = sample % 5 == 0 ? List.of() : List.of(quad, quad);
            List<BakedQuad> snow = sample % 7 == 0 ? List.of() : List.of(quad);
            float angle = sample == 0 ? 0 : (random.nextFloat() * 2 - 1) * 3;
            List<BakedQuad> expected = new ArrayList<>(referenceRotation(normal, angle));
            expected.addAll(referenceRotation(snow, angle));
            List<BakedQuad> actual = new ArrayList<>();
            actual.add(quad); // Appending must preserve geometry already collected.
            LeavesBakedModel.appendPositionRotation(actual, normal, snow, angle);
            if (actual.getFirst() != quad || actual.size() != expected.size() + 1) throw new AssertionError("append");
            for (int i = 0; i < expected.size(); i++)
            {
                BakedQuad a = actual.get(i + 1), b = expected.get(i);
                if (!Arrays.equals(a.getVertices(), b.getVertices())
                    || a.getDirection() != b.getDirection() || a.getTintIndex() != b.getTintIndex()
                    || a.isShade() != b.isShade() || a.hasAmbientOcclusion() != b.hasAmbientOcclusion())
                    throw new AssertionError("rotation mismatch at " + sample);
                checks++;
            }
            if (!Arrays.equals(before, data)) throw new AssertionError("mutated cached source");
        }
        System.out.println("Fluff rotation: " + checks + " exact quad comparisons passed; 1000 source immutability checks passed");
    }

    static List<BakedQuad> referenceRotation(List<BakedQuad> source, float rotationDegrees)
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


}

