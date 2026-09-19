# Better Foliage Reborn

![Better Foliage Reborn](src/main/resources/logo.png)

Version **1.0.0** — a client-side visual enhancement mod for Minecraft 1.21 / 1.21.1 on NeoForge.

This project is a fork of [Better Foliage Renewed](https://www.curseforge.com/minecraft/mc-mods/better-foliage-renewed). It retains features from Renewed while adding new effects, expanded compatibility, and selected features from the original [Better Foliage](https://www.curseforge.com/minecraft/mc-mods/better-foliage) that were not ported in the Renewed version this fork is based on.

Most elements of this mod can be disabled via Resource Packs.

## Restored and new features

- **Reeds in shallow water:** restores a classic Better Foliage effect. Reeds use crossed textures with coordinate-based variation in texture and horizontal position. They appear above recognized dirt under a full, still water block with air above, outside beach and ocean biomes. Density is configurable through `reed.population` (default: `0.5`). These are decorative geometry, not new blocks.
- **Snow-covered leaf fluff:** restores snowy tops for bushy leaves when snow lies above them. Snow overlays are composed with the active fluff textures and regenerated on resource reload, avoiding a separate hand-made snowy texture for every leaf type.
- **Floating cherry petals:** adds decorative petals on water beneath vanilla cherry leaves, with coordinate-based placement and randomized four-quadrant layouts. Petals use flower textures without stems, are visible from above and below, and do not add actual blocks. The search is limited to ten blocks below the leaves; intervening non-air, non-water blocks stop it, and water immediately below the leaves is excluded.
- **Shader animation:** adds Iris integration for reeds and floating petals. Reeds can use the shader pack's vegetation waving; floating petals have motion support without being classified as water. The final appearance depends on the shader pack.
- **Stable fluff variation:** leaf positions and rotations vary deterministically by block coordinates, including height, reducing coincident surfaces and flickering while retaining a natural, irregular appearance.

## Rendering and resource-pack compatibility

- **Ecliptic Seasons:** renders snowy fluff when the mod considers the leaf block snow-covered, as well as when ordinary snow lies above the leaves. The integration uses a compile-only dependency; Ecliptic Seasons is not bundled or required at runtime.
- **Sodium Leaf Culling:** respects its None, Hollow, Solid, and Solid Aggressive modes. Hidden fluff is omitted according to the selected mode, avoiding opaque fluff squares caused by leaf-culling optimizations.
- **Cull Leaves (4.1.1):** keeps its original leaf-cube face culling, but renders BF fluff as a complete, independently culled cross. While Cull Leaves culling is enabled, fluff is hidden only when all six adjacent positions contain non-air blocks (including water and non-solid blocks). Snowy fluff and supported resource-pack bushy models follow the same rule. No Cull Leaves runtime dependency is required. If Sodium Leaf Culling is also enabled, its existing suppression rules still apply independently.
- **Sodium / Iris:** includes optional rendering bridges for the additional vegetation geometry and shader material handling. Shader waving still requires a shader pack that supports the relevant vegetation category.
- **Stay True and supported bushy-leaf resource-pack models:** reuses the pack's bushy textures while letting Better Foliage control fluff placement, rotation, and leaf-culling compatibility. This is not a guarantee of support for every custom model format.
- **Resource reloads:** generated snowy fluff follows the currently loaded textures; reloading resources rebuilds the generated variants.
- **Oh The Biomes We've Gone:** white and yellow sakura leaves also support floating petals in their corresponding colors.

These integrations are optional. The mod can run without the corresponding mods or resource packs installed.

## Expanded built-in leaf compatibility

Compared with the Renewed base used by this fork, additional fluff models and textures are bundled for the following mods:

| Mod | Added coverage |
| --- | --- |
| Oh The Biomes We've Gone (BWG) | A broad selection of leaves, including flowering and fruit-bearing variants, colored leaves, and white/yellow sakura |
| Biomes O' Plenty (BOP) | Supplements existing compatibility with orange, red, and yellow maple, cypress, and snowblossom leaves |
| Upgrade Aquatic | River leaves, with biome tinting |
| BetterEnd | Dragon tree, lacugrove, pythadendron, and cave-bush variants |
| Cluttered | Crabapple, fluorescent maple, poplar, sycamore, willow, and flowering variants |
| Cook's Collection | Lemon leaves |
| Cultural Delights | Avocado and fruiting avocado variants |
| Eternal Starlight | Banyin, cradlewood, lunar variants, northland, scarlet, and torreya leaves |
| Paster Dream (`pasterdream`) | Cold domain, dyedream, dyedream worldtree, and windmoor variants |

Existing compatibility resources are also retained for Atmospheric, Bayou Blues, Biomes O' Plenty, Enhanced Farming, Quark, and TerraFirmaCraft. Bundled resources cover specific supported blocks; they do not imply complete coverage of every version of each mod.

All these compatibility assets are included directly in the mod JAR. No separate Better Foliage Addons resource pack is needed. Supplemental models use isolated `betterfoliage_*` namespaces to preserve the existing compatibility resources.

## Configuration

Many inherited visual features can still be customized through resource packs. Client configuration controls additional effects such as reed density. Compatibility models using the `betterfoliage:grass` loader can opt custom dirt blocks into reed rendering with `"renderReed": true`.

Install this fork in place of Better Foliage Renewed, not alongside it: both use the `betterfoliage` mod ID.

## Credits and license

Some assets contained in this repository are based on those from other mods for the purpose of providing compatibility. These assets follow the licenses of their respective mod authors. If you are an author who does not want derivatives of their assets hosted here, make an issue.

Original Better Foliage by **Octarine Noise**. Better Foliage Renewed by **EERussianguy**, co-authored by **Paint_Ninja**. This fork builds on their work.

This mod is under the MIT License.
