package com.eerussianguy.betterfoliage.model;

import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;

/** Headless regression: real native images, compressed disk IO and the full atlas-generation pipeline. */
final class SnowPaletteCacheTest
{
    private static int checks;
    static void run() throws Exception
    {
        SnowPalette palette = new SnowPalette(new int[]{0xffffff, 0x80a755, 0x619961}, 8);
        check(palette.colors[palette.select(0x619961)] == 0x619961, "fixed species tint stays exact");
        check(palette.colors[palette.select(0xffffff)] == 0xffffff, "white remains untinted");
        check(palette.select(0xff0000) == -1, "unknown mod tint falls back instead of turning green");
        Random random = new Random(1001);
        for (int i = 0; i < 10000; i++)
        {
            int rgb = random.nextInt(0x1000000), index = palette.select(rgb);
            check(index < 0 || SnowPalette.channelError(rgb, palette.colors[index]) <= 8, "selected color error is bounded");
            int abgr = random.nextInt();
            check(SnowPalette.tint(abgr, 0xffffff) == abgr, "white preserves every pixel bit");
            check((SnowPalette.tint(abgr, rgb) >>> 24) == (abgr >>> 24), "tint preserves alpha");
        }
        check(SnowPalette.tint(0xff80c0ff, 0x4080ff) == 0xff806040, "RGB tint times ABGR pixel channels");
        Map<ResourceLocation, Resource> resources = new HashMap<>();
        ResourceManager manager = manager(resources);
        try (NativeImage colormap = new NativeImage(4, 4, true))
        {
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) colormap.setPixelRGBA(x, y, 0xff537eab);
            resources.put(id("minecraft:textures/colormap/foliage.png"), resource(colormap.asByteArray()));
            SnowPalette sampled = SnowPalette.load(manager, 8, 1024, 8, "FF8800,invalid");
            check(sampled.select(0xab7e53) >= 0, "active pack colormap sampled in correct RGB order");
            check(sampled.colors[sampled.select(0xff8800)] == 0xff8800, "extra seasonal color exact");
            check(sampled.colors.length == 7, "deduplicate map colors plus fixed colors");
        }

        Path directory = Files.createTempDirectory(Path.of("."), "palette-regression-");
        List<SpriteContents> originals = new ArrayList<>();
        List<SpriteContents> first = List.of(), second = List.of(), changed = List.of(), budget = List.of();
        List<SpriteContents> corrupt = List.of(), recovered = List.of();
        try
        {
            NativeImage leaves = new NativeImage(32, 32, true);
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) leaves.setPixelRGBA(x, y, 0xffa0c0ff);
            originals.add(new SpriteContents(id("test:bushy_tinted"), new FrameSize(32, 32), leaves, ResourceMetadata.EMPTY));
            for (int i = 0; i < 3; i++)
            {
                NativeImage snow = new NativeImage(32, 32, true);
                snow.setPixelRGBA(i, 0, 0xfffafbfc);
                originals.add(new SpriteContents(id("betterfoliage:block/better_leaves_snowed_" + i), new FrameSize(32, 32), snow, ResourceMetadata.EMPTY));
            }
            // Stay True-style inherited model: tint index lives on parent faces, texture override on child.
            resources.put(id("test:models/block/parent.json"), resource(("{\"textures\":{\"fluff\":\"#leaves\"},"
                + "\"elements\":[{\"faces\":{\"north\":{\"texture\":\"#fluff\",\"tintindex\":0}}}]}").getBytes()));
            resources.put(id("test:models/block/leaf.json"), resource(("{\"parent\":\"test:block/parent\","
                + "\"textures\":{\"leaves\":\"test:bushy_tinted\"}}").getBytes()));
            first = generate(originals, manager, palette, directory, 1_000_000);
            check(first.size() == 13 && SnowCompositeSprites.hasPalette(), "three colors times three snow variants");
            Path file = SnowTextureCache.file(directory, originals.getFirst().name());
            try (var files = Files.list(directory)) { check(files.count() == 1, "all variants share one compressed file, no leftover temporary files"); }
            check(Files.size(file) < 32 * 32 * 4 * 9, "bundle is smaller than raw pixel data");
            System.out.println("Snow cache fixture: " + (32 * 32 * 4 * 9) + " raw bytes -> " + Files.size(file) + " compressed bytes");
            second = generate(originals, manager, palette, directory, 1_000_000);
            for (int i = 4; i < first.size(); i++)
            {
                check(first.get(i).name().equals(second.get(i).name()), "cache preserves sprite ordering and IDs");
                for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++)
                    check(first.get(i).getOriginalImage().getPixelRGBA(x, y) == second.get(i).getOriginalImage().getPixelRGBA(x, y), "disk pixels identical");
                check(first.get(i).getOriginalImage().getPixelRGBA((i - 4) % 3, 0) == 0xfffafbfc, "snow itself never tinted");
                check(first.get(i).getOriginalImage().getPixelRGBA(31, 31) == SnowPalette.tint(0xffa0c0ff, palette.colors[(i - 4) / 3]), "leaf receives palette tint exactly once");
            }
            Map<ResourceLocation, TextureAtlasSprite> atlas = new HashMap<>();
            for (SpriteContents sprite : second) atlas.put(sprite.name(), new TestSprite(sprite));
            var set = SnowCompositeSprites.findSet(originals.getFirst().name(), atlas::get);
            check(set.untinted.length == 3, "untinted variants remain available for mixed texture usage");
            check(set.select(0x619961, 2).contents().name().equals(SnowCompositeSprites.id(originals.getFirst().name(), 2, 0x619961)), "renderer chooses correct palette row and snow variant");
            check(set.select(0xff0000, 0) == null && set.select(null, 0) == null, "unsupported tint or absent model data stays layered");

            SpriteContents[] snow = originals.subList(1, 4).toArray(SpriteContents[]::new);
            String before = SnowTextureCache.fingerprint(originals.getFirst(), snow, palette.colors);
            leaves.setPixelRGBA(31, 31, 0xff112233);
            check(!before.equals(SnowTextureCache.fingerprint(originals.getFirst(), snow, palette.colors)), "source pixel change invalidates cache");
            check(!before.equals(SnowTextureCache.fingerprint(originals.getFirst(), snow, new int[]{0xffffff})), "palette change invalidates cache");
            changed = generate(originals, manager, palette, directory, 1_000_000);
            check(changed.get(4).getOriginalImage().getPixelRGBA(31, 31) == SnowPalette.tint(0xff112233, palette.colors[0]), "resource change regenerates on disk");
            check(first.get(4).getOriginalImage().getPixelRGBA(31, 31) != changed.get(4).getOriginalImage().getPixelRGBA(31, 31), "old atlas untouched by reload");

            byte[] bytes = Files.readAllBytes(file);
            Files.write(file, Arrays.copyOf(bytes, bytes.length / 2));
            corrupt = generate(originals, manager, palette, directory, 1_000_000);
            check(corrupt.size() == 13 && Files.size(file) == bytes.length, "truncated file automatically rebuilt");
            bytes = Files.readAllBytes(file); bytes[bytes.length - 8] ^= 1;
            Files.write(file, bytes);
            recovered = generate(originals, manager, palette, directory, 1_000_000);
            check(recovered.size() == 13 && !Arrays.equals(bytes, Files.readAllBytes(file)), "gzip checksum corruption rebuilt");

            budget = generate(originals, manager, palette, directory, 17000);
            check(budget.size() == 7 && !SnowCompositeSprites.hasPalette(), "palette budget fallback retains only white variants");
            check(SnowCompositeSprites.findSet(originals.getFirst().name(), name -> new TestSprite(budgetSprite(name, originals))).select(0x619961, 0) == null, "budget fallback never uses white for tinted leaves");
            check(generate(originals, manager, palette, directory, 1).size() == 4, "no budget means entirely layered fallback");
            Path sentinel = directory.resolve("unrelated.txt"); Files.writeString(sentinel, "keep");
            SnowTextureCache.trim(directory, 0);
            check(Files.exists(sentinel) && !Files.exists(file), "eviction deletes only own bundles");
        }
        finally
        {
            for (var list : List.of(first, second, changed, budget, corrupt, recovered))
                for (int i = originals.size(); i < list.size(); i++) list.get(i).close();
            originals.forEach(SpriteContents::close);
        }
        System.out.println("Snow palette and disk cache: " + checks + " checks passed");
    }

    private static SpriteContents budgetSprite(ResourceLocation name, List<SpriteContents> originals)
    {
        // An unavailable generated sprite resolves to the base, just as the real atlas resolves to missingno.
        return originals.getFirst();
    }
    private static List<SpriteContents> generate(List<SpriteContents> originals, ResourceManager resources, SnowPalette palette, Path cache, long budget)
    {
        return SnowCompositeSprites.generate(originals, resources, palette, cache, budget, 1_000_000, 4096);
    }
    private static Resource resource(byte[] bytes) { return new Resource(null, () -> new ByteArrayInputStream(bytes)); }
    @SuppressWarnings("unchecked")
    static ResourceManager manager(Map<ResourceLocation, Resource> resources)
    {
        return (ResourceManager) java.lang.reflect.Proxy.newProxyInstance(SnowPaletteCacheTest.class.getClassLoader(),
            new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                if (method.getName().equals("listResources"))
                {
                    Map<ResourceLocation, Resource> found = new HashMap<>();
                    resources.forEach((id, resource) -> {
                        if (id.getPath().startsWith(args[0] + "/") && ((Predicate<ResourceLocation>) args[1]).test(id)) found.put(id, resource);
                    });
                    return found;
                }
                throw new UnsupportedOperationException(method.getName());
            });
    }
    private static final class TestSprite extends TextureAtlasSprite
    {
        TestSprite(SpriteContents contents) { super(id("minecraft:textures/atlas/blocks.png"), contents, 256, 256, 0, 0); }
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static void check(boolean condition, String description)
    {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
}
