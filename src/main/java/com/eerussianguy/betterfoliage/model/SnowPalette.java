package com.eerussianguy.betterfoliage.model;

import java.util.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.server.packs.resources.ResourceManager;

/** Immutable, reload-scoped palette. All expensive matching happens once during resource loading. */
final class SnowPalette
{
    final int[] colors;
    private final short[] nearest = new short[32 * 32 * 32];

    SnowPalette(int[] colors)
    {
        this.colors = colors.clone();
        Arrays.sort(this.colors);
        colors = this.colors;
        if (colors.length == 0 || colors.length > 4096) throw new IllegalArgumentException("palette size");
        for (int cell = 0; cell < nearest.length; cell++)
        {
            int rgb = Math.min(255, ((cell >> 10) & 31) * 8 + 4) << 16
                | Math.min(255, ((cell >> 5) & 31) * 8 + 4) << 8 | Math.min(255, (cell & 31) * 8 + 4);
            int best = 0, distance = Integer.MAX_VALUE;
            for (int i = 0; i < colors.length; i++)
            {
                int dr = (rgb >> 16) - (colors[i] >> 16);
                int dg = ((rgb >> 8) & 255) - ((colors[i] >> 8) & 255);
                int db = (rgb & 255) - (colors[i] & 255);
                int error = dr * dr + dg * dg + db * db;
                if (error < distance) { best = i; distance = error; }
            }
            nearest[cell] = (short) best;
        }
    }

    int select(int rgb)
    {
        rgb &= 0xffffff;
        // Exact palette samples (especially white/fixed species colors) should never be approximated.
        int exact = Arrays.binarySearch(colors, rgb);
        if (exact >= 0) return exact;
        int cell = ((rgb >> 19) & 31) << 10 | ((rgb >> 11) & 31) << 5 | ((rgb >> 3) & 31);
        // Always approximate with the nearest precomputed cell color, even for out-of-palette tints.
        return nearest[cell] & 0xffff;
    }

    static int channelError(int a, int b)
    {
        return Math.max(Math.abs((a >> 16 & 255) - (b >> 16 & 255)),
            Math.max(Math.abs((a >> 8 & 255) - (b >> 8 & 255)), Math.abs((a & 255) - (b & 255))));
    }

    static SnowPalette load(ResourceManager resources, int step, int limit, String extras)
    {
        TreeSet<Integer> sampled = new TreeSet<>();
        resources.listResources("textures/colormap", id -> id.getPath().endsWith(".png")
            && (id.getPath().contains("foliage") || id.getPath().contains("leaves"))).forEach((id, resource) -> {
            try (var input = resource.open(); NativeImage image = NativeImage.read(input))
            {
                // Bounded sampling for HD colormaps; the standard 256x256 image is read in full.
                int stride = Math.max(1, Math.max(image.getWidth(), image.getHeight()) / 256);
                for (int y = 0; y < image.getHeight(); y += stride) for (int x = 0; x < image.getWidth(); x += stride)
                {
                    int pixel = image.getPixelRGBA(x, y);
                    sampled.add(quantize(pixel & 255, step) << 16 | quantize(pixel >> 8 & 255, step) << 8
                        | quantize(pixel >> 16 & 255, step));
                }
            }
            catch (Exception e) { LogUtils.getLogger().warn("Cannot sample foliage colormap {}", id); }
        });
        TreeSet<Integer> fixed = new TreeSet<>(List.of(0xffffff, 0x619961, 0x80a755, 0x48b518, 0x6a7039));
        for (String value : extras.split(","))
        {
            String hex = value.trim().replace("#", "");
            if (hex.matches("[0-9a-fA-F]{6}") && fixed.size() < limit) fixed.add(Integer.parseInt(hex, 16));
        }
        sampled.removeAll(fixed);
        Integer[] choices = sampled.toArray(Integer[]::new);
        int count = Math.min(limit - fixed.size(), choices.length);
        for (int i = 0; i < count; i++) fixed.add(choices[(int) ((long) i * choices.length / count)]);
        return new SnowPalette(fixed.stream().mapToInt(Integer::intValue).toArray());
    }

    private static int quantize(int value, int step) { return Math.min(255, (value + step / 2) / step * step); }

    static int tint(int abgr, int rgb)
    {
        return abgr & 0xff000000 | ((abgr & 255) * (rgb >> 16 & 255) / 255)
            | ((abgr >> 8 & 255) * (rgb >> 8 & 255) / 255) << 8
            | ((abgr >> 16 & 255) * (rgb & 255) / 255) << 16;
    }
}
