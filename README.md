# FastSuite [![](http://cf.way2muchnoise.eu/fastsuite.svg)](https://www.curseforge.com/minecraft/mc-mods/fastsuite) [![](http://cf.way2muchnoise.eu/versions/fastsuite.svg)](https://www.curseforge.com/minecraft/mc-mods/fastsuite)

FastSuite improves the performance of Minecraft's recipe system by concurrently matching recipes whenever possible. When not possible, it will fall back to matching serially.

By default, all vanilla and NeoForge recipe types and ingredients are known-safe, and will be matched concurrently.

Mod-added types can be added to the known-safe lists by calling `FastSuite.registerSafeRecipeClass` and/or `FastSuite.registerSafeIngredientClass`.

An example of the perf increase on crafting recipes from the All The Mods 9 modpack is shown below:  
![](https://i.imgur.com/GHsHUVY.png)
