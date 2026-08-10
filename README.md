# Better Foliage
A slim Better Foliage port for 1.16 and beyond.

Most elements of this mod can be disabled via Resource Packs.

### Better reeds

Better reeds are rendered as client-side crossed quads on recognized dirt blocks when the block above is water, the next block is air, and the biome is neither a beach nor an ocean. The four `better_reed_*` textures and their horizontal offsets are selected deterministically from the dirt block position. Their density is controlled by the client option `reed.population` (`0.5` by default). Vanilla dirt is wrapped after model baking so its active resource-pack appearance is preserved. Resource-pack compatibility models using the `betterfoliage:grass` loader can opt custom dirt blocks in with `"renderReed": true`.

### Integrated compatibility

The mod JAR directly includes the additional bushy-leaves compatibility assets for 26 Oh The Biomes We've Gone leaves and five newer Biomes O' Plenty leaves (`orange_maple`, `red_maple`, `yellow_maple`, `cypress`, and `snowblossom`). No separate Better Foliage Addons resource pack is required. BWG and supplemental BOP models use the isolated `betterfoliage_bwg` and `betterfoliage_bop` asset namespaces so they do not overwrite the mod's existing Atmospheric or Biomes O' Plenty compatibility models.

Some assets contained in this repository are based on those from other mods for the purpose of providing compatibility. These assets follow the licenses of their respective mod authors. If you are an author who does not want derivatives of their assets hosted here, make an issue.

Created by EERussianguy.
Co-authored by Paint_Ninja.

This mod is under the MIT License.
