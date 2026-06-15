# FastSuite [![](http://cf.way2muchnoise.eu/fastsuite.svg)](https://www.curseforge.com/minecraft/mc-mods/fastsuite) [![](http://cf.way2muchnoise.eu/versions/fastsuite.svg)](https://www.curseforge.com/minecraft/mc-mods/fastsuite)

FastSuite improves the performance of Minecraft's recipe system by building an index of items to recipes that use those items, skipping the need to check recipes that will never match anyway.

By default, only crafting recipes receive this behavior, though other recipe types can be opted-in via config file.

Mod-added types can be added to the known-safe lists by calling `FastSuite.registerSafeRecipeClass` and/or `FastSuite.registerSafeIngredientClass`.

Measured on **All the Mods 11** — 16,352 crafting recipes — averaged over 10,000 lookups:

**Matching every recipe** — what Polymorph and similar mods request on each crafting change:

| Crafting input    | FastSuite | Vanilla  | Speedup |
| ----------------- | --------: | -------: | ------: |
| Acacia planks     |    110 µs |   417 µs |    3.8× |
| Sticks            |    199 µs |   628 µs |    3.2× |
| Crafting table    |     96 µs |   316 µs |    3.3× |
| Black shulker box |    199 µs |   501 µs |    2.5× |
| No match          |    238 µs | 1,144 µs |    4.8× |

**Finding the first match** — vanilla's single-recipe lookup:

| Crafting input    | FastSuite | Vanilla | Speedup |
| ----------------- | --------: | ------: | ------: |
| Acacia planks     |    5.1 µs |  2.5 µs |    0.5× |
| Sticks            |    203 µs |  458 µs |    2.3× |
| Crafting table    |     34 µs |   87 µs |    2.5× |
| Black shulker box |     16 µs |   23 µs |    1.4× |
| No match          |    175 µs |  405 µs |    2.3× |
