package com.eerussianguy.betterfoliage;

import java.util.function.Function;

import net.neoforged.neoforge.common.ModConfigSpec;

import static com.eerussianguy.betterfoliage.BetterFoliage.MOD_ID;

public class ClientConfig
{
    public final ModConfigSpec.IntValue particleAttempts;
    public final ModConfigSpec.IntValue particleDistance;

    public final ModConfigSpec.BooleanValue souls;
    public final ModConfigSpec.BooleanValue leaves;
    public final ModConfigSpec.BooleanValue snowballs;

    public final ModConfigSpec.IntValue leavesCacheSize;
    public final ModConfigSpec.DoubleValue leavesVariationDistance;
    public final ModConfigSpec.IntValue extraGrassRarity;
    public final ModConfigSpec.DoubleValue reedPopulation;
    public final ModConfigSpec.DoubleValue waterPetalPopulation;

    public final ModConfigSpec.BooleanValue forceForgeLighting;
    public final ModConfigSpec.BooleanValue snowPaletteEnabled;
    public final ModConfigSpec.IntValue snowPaletteStep;
    public final ModConfigSpec.IntValue snowPaletteMaxColors;
    public final ModConfigSpec.IntValue snowAtlasBudget;
    public final ModConfigSpec.IntValue snowDiskBudget;
    public final ModConfigSpec.BooleanValue eclipticSnowLeaves;
    public final ModConfigSpec.IntValue eclipticSnowAtlasBudget;
    public final ModConfigSpec.ConfigValue<String> snowExtraColors;
    public final ModConfigSpec.BooleanValue fluffVisibilityEnabled;
    public final ModConfigSpec.DoubleValue fluffCornerSecondChance;
    public final ModConfigSpec.DoubleValue fluffSideChance;
    public final ModConfigSpec.DoubleValue fluffBottomChance;

    ClientConfig(ModConfigSpec.Builder innerBuilder)
    {
        Function<String, ModConfigSpec.Builder> builder = name -> innerBuilder.translation(MOD_ID + ".config.server." + name);

        innerBuilder.push("general");

        particleAttempts = builder.apply("particleAttempts").comment("Attempts per tick to spawn a particle").defineInRange("particleAttempts", 2, 0, Integer.MAX_VALUE);
        particleDistance = builder.apply("particleDistance").comment("Horizontal and Vertical distance particles will spawn from").defineInRange("particleDistance", 15, 0, Integer.MAX_VALUE);
        souls = builder.apply("souls").comment("Enable Soul Particles?").define("souls", true);
        leaves = builder.apply("leaves").comment("Enable Leaf Particles?").define("leaves", true);
        snowballs = builder.apply("snowballs").comment("Enable Snowballs?").define("snowballs", true);
        leavesCacheSize = builder.apply("leavesCacheSize").comment("Determines the size of the leaves cache. Number of models cached per leaf block will be the number you input to the third power. Bigger cache = more RAM, but more variation and less z-fighting as a result").worldRestart().defineInRange("leavesCacheSize", 7, 5, 20);
        leavesVariationDistance = builder.apply("leavesVariationDistance").comment("Determines the max distance leaves block fluff can deviate from the actual block. 0.0 means no distance variation (all fluff is in the middle of the block)").worldRestart().defineInRange("leavesVariationDistance", 2.75f, 0f, 7f);
        forceForgeLighting = builder.apply("forceForgeLighting").comment("Force Forge Lighting Pipeline? (should be true when not using Optifine)").define("forceForgeLighting", true);
        extraGrassRarity = builder.apply("extraGrassRarity").comment("Inverse of the rarity of the extra grass. Increase the value to make it less common.").defineInRange("extraGrassRarity", 2, 1, Integer.MAX_VALUE);

        innerBuilder.pop();

        innerBuilder.push("fluffVisibility");
        fluffVisibilityEnabled = innerBuilder.comment("Thin fluff using immediate neighbors and stable world coordinates. Existing leaf-culling rules still take priority. Reload chunks/resources after changes.")
            .define("enabled", true);
        fluffCornerSecondChance = innerBuilder.comment("Chance of retaining the second diagonal at an exposed horizontal corner; the corner diagonal is always retained.")
            .defineInRange("cornerSecondChance", 0.5D, 0.0D, 1.0D);
        fluffSideChance = innerBuilder.comment("Independent chance for each diagonal when one side, or two opposite sides, is exposed to air.")
            .defineInRange("sideChance", 0.65D, 0.0D, 1.0D);
        fluffBottomChance = innerBuilder.comment("Independent chance for each diagonal when horizontal sides and top are covered but the bottom is exposed to air.")
            .defineInRange("bottomChance", 0.65D, 0.0D, 1.0D);
        innerBuilder.pop();

        innerBuilder.push("snowPalette");
        snowPaletteEnabled = innerBuilder.comment("Single-layer tinted snowy fluff using a finite palette. Reload resources after changing these settings.")
            .define("enabled", true);
        snowPaletteStep = innerBuilder.comment("RGB quantization step for active foliage colormaps. Smaller is finer but uses more atlas space; 8 has at most 4/255 rounding error per sampled channel.")
            .defineInRange("colorStep", 8, 2, 32);
        snowPaletteMaxColors = innerBuilder.comment("Maximum palette entries including white and vanilla fixed leaf colors.")
            .defineInRange("maxColors", 1024, 16, 4096);
        snowAtlasBudget = innerBuilder.comment("Budget in MiB for added snow sprites, including mipmaps (atlas packing overhead is additional). Over-budget textures keep layered snow.")
            .defineInRange("atlasBudgetMiB", 128, 8, 1024);
        snowDiskBudget = innerBuilder.comment("Maximum total size of BF's compressed .bfs cache files; oldest files are evicted after reload.")
            .defineInRange("diskBudgetMiB", 512, 16, 4096);
        eclipticSnowLeaves = innerBuilder.comment("Merge Ecliptic Seasons' standard leaf-cube snow overlays into single-layer faces. Reload resources after changing.")
            .define("eclipticLeaves", true);
        eclipticSnowAtlasBudget = innerBuilder.comment("Separate added-image budget in MiB for Ecliptic Seasons leaf-cube composites, including estimated mipmaps.")
            .defineInRange("eclipticAtlasBudgetMiB", 64, 8, 1024);
        snowExtraColors = innerBuilder.comment("Optional exact seasonal/mod RGB colors, comma-separated hex, e.g. FF8800,AA3377. Unrepresented colors use a nearby palette color, without a color-error fallback.")
            .define("extraColors", "");
        innerBuilder.pop();

        innerBuilder.push("reed");
        reedPopulation = builder.apply("reed.population")
            .comment("Chance for better reeds to render on eligible dirt blocks. 0 disables reeds and 1 renders them everywhere eligible.")
            .defineInRange("population", 0.5D, 0.0D, 1.0D);
        innerBuilder.pop();

        innerBuilder.push("waterPetals");
        waterPetalPopulation = builder.apply("waterPetals.population")
            .comment("Chance for water-surface cherry petals to render below vanilla cherry leaves. 0 disables them and 1 renders them on every eligible water block.")
            .worldRestart()
            .defineInRange("population", 0.625D, 0.0D, 1.0D);
        innerBuilder.pop();
    }
}
