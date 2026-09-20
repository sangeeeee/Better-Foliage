package com.eerussianguy.betterfoliage.model;

import java.io.Reader;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.client.Minecraft;
import com.eerussianguy.betterfoliage.BFConfig;

/** Reload-scoped atlas synthesis; no GPU upload, image processing or image cache on the rendering path. */
public final class SnowCompositeSprites
{
    private static volatile Map<ResourceLocation, SnowPalette> generatedPalettes = Map.of();
    private static volatile Set<ResourceLocation> generated = Set.of();
    private static final TextureAtlasSprite[] NONE = new TextureAtlasSprite[0];

    private SnowCompositeSprites() {}

    static ResourceLocation id(ResourceLocation base, int variant)
    {
        return ResourceLocation.fromNamespaceAndPath("betterfoliage",
            "generated/snow/" + variant + "/" + base.getNamespace() + "/" + base.getPath());
    }

    static ResourceLocation id(ResourceLocation base, int variant, int rgb)
    {
        return rgb == 0xffffff ? id(base, variant) : ResourceLocation.fromNamespaceAndPath("betterfoliage",
            "generated/snow/tinted/" + Integer.toHexString(rgb) + "/" + variant + "/" + base.getNamespace() + "/" + base.getPath());
    }

    static boolean hasPalette() { return !generatedPalettes.isEmpty(); }

    static final class SpriteSet
    {
        final TextureAtlasSprite[] untinted;
        private final SnowPalette palette;
        private final TextureAtlasSprite[][] tinted;
        SpriteSet(TextureAtlasSprite[] untinted, SnowPalette palette, TextureAtlasSprite[][] tinted)
        {
            this.untinted = untinted; this.palette = palette; this.tinted = tinted;
        }
        TextureAtlasSprite select(Integer color, int variant)
        {
            if (palette == null || color == null) return null;
            int index = palette.select(color);
            return index < 0 ? null : tinted[index][variant];
        }
    }

    static SpriteSet findSet(ResourceLocation base, Function<ResourceLocation, TextureAtlasSprite> getter)
    {
        TextureAtlasSprite[] white = find(base, getter);
        SnowPalette palette = generatedPalettes.get(base);
        if (palette == null) return new SpriteSet(white, null, null);
        TextureAtlasSprite[][] tinted = new TextureAtlasSprite[palette.colors.length][3];
        for (int c = 0; c < tinted.length; c++) for (int v = 0; v < 3; v++)
        {
            tinted[c][v] = getter.apply(id(base, v, palette.colors[c]));
            if (!tinted[c][v].contents().name().equals(id(base, v, palette.colors[c])))
                return new SpriteSet(white, null, null);
        }
        return new SpriteSet(white, palette, tinted);
    }

    static TextureAtlasSprite[] find(ResourceLocation base, Function<ResourceLocation, TextureAtlasSprite> getter)
    {
        if (!generated.contains(base)) return NONE;
        TextureAtlasSprite[] result = new TextureAtlasSprite[3];
        for (int i = 0; i < 3; i++)
        {
            result[i] = getter.apply(id(base, i));
            if (!result[i].contents().name().equals(id(base, i))) return NONE;
        }
        return result;
    }

    public static List<SpriteContents> generate(List<SpriteContents> original, ResourceManager resources)
    {
        return generate(original, resources, 16384);
    }

    public static List<SpriteContents> generate(List<SpriteContents> original, ResourceManager resources, int maxTextureSize)
    {
        final var mc = Minecraft.getInstance();
        final var config = BFConfig.CLIENT;
        final Path cacheDirectory = mc == null ? null : mc.gameDirectory.toPath().resolve(".cache").resolve("better-foliage");
        final boolean paletteEnabled = resources != null && mc != null && config.snowPaletteEnabled.get();
        final SnowPalette palette = paletteEnabled ? SnowPalette.load(resources, config.snowPaletteStep.get(),
            config.snowPaletteMaxColors.get(), config.snowExtraColors.get()) : null;
        return generate(original, resources, palette, cacheDirectory,
            (mc == null ? 128L : config.snowAtlasBudget.get()) * 1024 * 1024,
            (mc == null ? 512L : config.snowDiskBudget.get()) * 1024 * 1024, maxTextureSize);
    }

    static List<SpriteContents> generate(List<SpriteContents> original, ResourceManager resources,
        SnowPalette palette, Path cacheDirectory, long atlasBudget, long diskBudget, int maxTextureSize)
    {
        // Replaced, never accumulated, on each block-atlas reload. Native images belong to the atlas.
        generated = Set.of();
        generatedPalettes = Map.of();
        if (resources == null) return original;
        Map<ResourceLocation, SpriteContents> sprites = new HashMap<>();
        for (SpriteContents sprite : original) sprites.put(sprite.name(), sprite);
        SpriteContents[] snow = new SpriteContents[3];
        for (int i = 0; i < 3; i++)
        {
            snow[i] = sprites.get(ResourceLocation.fromNamespaceAndPath("betterfoliage", "block/better_leaves_snowed_" + i));
            // An independently animated snow layer cannot in general be merged with the base animation.
            if (snow[i] == null || snow[i].getOriginalImage().getWidth() != snow[i].width()
                || snow[i].getOriginalImage().getHeight() != snow[i].height()) return original;
        }
        long remaining = atlasBudget;
        // Leave substantial atlas packing headroom; compressed disk size is NOT a VRAM budget.
        long originalArea = original.stream().mapToLong(s -> (long) s.width() * s.height()).sum();
        remaining = Math.min(remaining, Math.max(0L, ((long) maxTextureSize * maxTextureSize * 3 / 5 - originalArea) * 16 / 3));
        Map<ResourceLocation, JsonObject> models = readModels(resources);
        Set<ResourceLocation> plainCandidates = candidates(models);
        Set<ResourceLocation> tintedCandidates = candidates(models, true);
        // Keep existing cheap untinted composites ahead of palette expansion when the budget is tight.
        Set<ResourceLocation> all = new TreeSet<>(Comparator.<ResourceLocation, Boolean>comparing(tintedCandidates::contains)
            // Prioritize active vanilla/Stay True replacements over bundled mod compatibility textures.
            .thenComparing(candidate -> !candidate.getNamespace().equals("minecraft"))
            .thenComparing(ResourceLocation::toString));
        all.addAll(plainCandidates); all.addAll(tintedCandidates);
        List<SpriteContents> result = new ArrayList<>(original);
        Set<ResourceLocation> successful = new HashSet<>();
        Map<ResourceLocation, SnowPalette> colored = new HashMap<>();
        int budgetFallbacks = 0, cacheHits = 0;
        for (ResourceLocation candidate : all)
        {
            SpriteContents base = sprites.get(candidate);
            if (base == null) continue;
            List<SpriteContents> variants = new ArrayList<>();
            try
            {
                boolean usePalette = palette != null && tintedCandidates.contains(candidate);
                long oneSetBytes = 0;
                for (SpriteContents overlay : snow)
                {
                    SnowTextureCache.ImageSpec size = spec(base, overlay, id(candidate, 0));
                    oneSetBytes += (long) size.width() * size.height() * 16 / 3;
                }
                if (usePalette && oneSetBytes * palette.colors.length > remaining) { usePalette = false; budgetFallbacks++; }
                if (oneSetBytes > remaining) { budgetFallbacks++; continue; }
                int[] colors = usePalette ? palette.colors : new int[]{0xffffff};
                List<SnowTextureCache.ImageSpec> specs = new ArrayList<>(colors.length * 3);
                for (int color : colors) for (int i = 0; i < 3; i++)
                {
                    ResourceLocation name = id(candidate, i, color);
                    if (sprites.containsKey(name)) throw new IllegalArgumentException("generated sprite ID collision");
                    specs.add(spec(base, snow[i], name));
                }
                String fingerprint = cacheDirectory == null ? "" : SnowTextureCache.fingerprint(base, snow, colors);
                Path file = cacheDirectory == null ? null : SnowTextureCache.file(cacheDirectory, candidate);
                List<SpriteContents> cached = file == null ? null : SnowTextureCache.read(file, fingerprint, specs, base.metadata());
                if (cached != null) { variants = cached; cacheHits++; }
                else
                {
                    for (int color : colors) for (int i = 0; i < 3; i++) variants.add(compose(base, snow[i], id(candidate, i, color), color));
                    if (file != null) SnowTextureCache.write(file, fingerprint, variants);
                }
                remaining -= oneSetBytes * colors.length;
                result.addAll(variants);
                successful.add(candidate);
                if (usePalette) colored.put(candidate, palette);
            }
            catch (RuntimeException exception)
            {
                variants.forEach(SpriteContents::close);
                LogUtils.getLogger().warn("Keeping layered snowy fluff for {}: {}", candidate, exception.toString());
            }
        }
        generated = Set.copyOf(successful);
        generatedPalettes = Map.copyOf(colored);
        if (cacheDirectory != null) SnowTextureCache.trim(cacheDirectory, diskBudget);
        LogUtils.getLogger().info("Snow composites: {} textures, {} palette textures, {} colors, {} disk cache hits, {} budget fallbacks",
            successful.size(), colored.size(), palette == null ? 0 : palette.colors.length, cacheHits, budgetFallbacks);
        return result;
    }

    private static SnowTextureCache.ImageSpec spec(SpriteContents base, SpriteContents snow, ResourceLocation name)
    {
        int width = Math.max(base.width(), snow.width()), height = Math.max(base.height(), snow.height());
        int columns = base.getOriginalImage().getWidth() / base.width(), rows = base.getOriginalImage().getHeight() / base.height();
        if ((long) width * height * columns * rows > 4_194_304L) throw new IllegalArgumentException("composite exceeds safe image budget");
        return new SnowTextureCache.ImageSpec(name, width * columns, height * rows, width, height);
    }

    static SpriteContents compose(SpriteContents base, SpriteContents snow, ResourceLocation name)
    {
        return compose(base, snow, name, 0xffffff);
    }

    static SpriteContents compose(SpriteContents base, SpriteContents snow, ResourceLocation name, int tint)
    {
        NativeImage leaves = base.getOriginalImage(), cover = snow.getOriginalImage();
        int width = Math.max(base.width(), snow.width()), height = Math.max(base.height(), snow.height());
        int columns = leaves.getWidth() / base.width(), rows = leaves.getHeight() / base.height();
        if ((long) width * height * columns * rows > 4_194_304L)
            throw new IllegalArgumentException("composite exceeds safe image budget");
        NativeImage output = new NativeImage(width * columns, height * rows, false);
        try
        {
            // Compose each physical frame; the original frame order, timing and interpolation metadata survive.
            for (int fy = 0; fy < rows; fy++) for (int fx = 0; fx < columns; fx++)
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                {
                    int leaf = leaves.getPixelRGBA(fx * base.width() + x * base.width() / width,
                        fy * base.height() + y * base.height() / height);
                    int overlay = cover.getPixelRGBA(x * snow.width() / width, y * snow.height() / height);
                    output.setPixelRGBA(fx * width + x, fy * height + y, compositePixel(SnowPalette.tint(leaf, tint), overlay));
                }
            return new SpriteContents(name, new FrameSize(width, height), output, base.metadata());
        }
        catch (RuntimeException | Error exception)
        {
            output.close();
            throw exception;
        }
    }

    /** Snow's non-transparent pixels replace leaf pixels; transparent snow preserves leaves exactly. */
    static int compositePixel(int leaf, int snow)
    {
        return (snow >>> 24) == 0 ? leaf : snow;
    }

    static boolean untinted(JsonObject face)
    {
        return !face.has("tintindex") || face.get("tintindex").getAsInt() == -1;
    }

    private static Map<ResourceLocation, JsonObject> readModels(ResourceManager resources)
    {
        Map<ResourceLocation, JsonObject> models = new HashMap<>();
        resources.listResources("models", path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            try (Reader reader = resource.openAsReader())
            {
                String modelPath = path.getPath();
                models.put(ResourceLocation.fromNamespaceAndPath(path.getNamespace(),
                    modelPath.substring(7, modelPath.length() - 5)), JsonParser.parseReader(reader).getAsJsonObject());
            }
            catch (Exception ignored) { /* Foreign/custom formats simply keep the layered fallback. */ }
        });
        return models;
    }

    static Set<ResourceLocation> candidates(Map<ResourceLocation, JsonObject> models)
    {
        return candidates(models, false);
    }

    static Set<ResourceLocation> candidates(Map<ResourceLocation, JsonObject> models, boolean tinted)
    {
        Set<ResourceLocation> result = new HashSet<>();
        for (JsonObject model : models.values())
        {
            try
            {
                if (model.has("loader") && model.get("loader").getAsString().equals("betterfoliage:leaves"))
                {
                    // Matches LeavesLoader's default of true. tintOverlay only affects the cube, not fluff.
                    if ((!model.has("tintLeaves") || model.get("tintLeaves").getAsBoolean()) == tinted)
                        result.add(ResourceLocation.parse(model.get("fluff").getAsString()));
                    continue;
                }
                // Resolve vanilla resource-pack inheritance/texture aliases. Runtime quad tint is checked again.
                Map<String, String> textures = new HashMap<>();
                JsonArray elements = null;
                JsonObject cursor = model;
                Set<JsonObject> visited = Collections.newSetFromMap(new IdentityHashMap<>());
                while (cursor != null && visited.add(cursor))
                {
                    if (elements == null && cursor.has("elements")) elements = cursor.getAsJsonArray("elements");
                    if (cursor.has("textures")) cursor.getAsJsonObject("textures").entrySet()
                        .forEach(entry -> textures.putIfAbsent(entry.getKey(), entry.getValue().getAsString()));
                    cursor = cursor.has("parent") ? models.get(ResourceLocation.parse(cursor.get("parent").getAsString())) : null;
                }
                if (elements == null) continue;
                for (JsonElement element : elements)
                {
                    JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
                    if (faces == null) continue;
                    for (JsonElement value : faces.asMap().values())
                    {
                        JsonObject face = value.getAsJsonObject();
                        if (untinted(face) == tinted) continue;
                        String texture = face.get("texture").getAsString();
                        Set<String> aliases = new HashSet<>();
                        while (texture != null && texture.startsWith("#") && aliases.add(texture)) texture = textures.get(texture.substring(1));
                        if (texture != null && !texture.startsWith("#"))
                        {
                            ResourceLocation resolved = ResourceLocation.parse(texture);
                            if (resolved.getPath().contains("bushy")) result.add(resolved);
                        }
                    }
                }
            }
            catch (RuntimeException ignored) { /* Missing or unsupported models are not eligible. */ }
        }
        return result;
    }
}
