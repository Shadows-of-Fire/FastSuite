## 6.0.7
* Fixed a severe performance issue that can occur with FastSuite+ModernFix+AE2JEIIntegration.

## 6.0.6
* Fixed an issue where Ingredient#stackingIds could be the casualty of a data race and crash the game.

## 6.0.5
* Updated to Placebo 9.9.0.

## 6.0.4
* Fixed a Stack Overflow caused by calling the incorrect super method in `getRecipeFor`.

## 6.0.3
* Fixed a race condition that occurs when multiple shapeless recipes match concurrently.

## 6.0.2
* Fixed the debug test being enabled, which would cause world start times to increase substantially.

## 6.0.1
* Fixed a conflict with KubeJS that caused the game to crash on startup.

## 6.0.0
* Updated to 1.21.1. This version also contains the thread-safe recipe matching improvements from 5.1.0.

## 5.1.0
* FastSuite will now only parallelize recipes that are known to be thread-safe.
  * By default, this includes all vanilla recipe classes that use all vanilla or forge ingredients.
  * Additional modded recipe classes and ingredients can be added by calling the static methods on `FastSuite`.
  * In large modpacks, more than 90% of crafting recipes are covered by the default thread-safe classifiers.

## 5.0.1
* Removed forge dependency line from the mods.toml and marked as Forge and NeoForge for CF.
  * The dependency will be added back and the Forge marker will be removed once CF supports Neo correctly.

## 5.0.0
* Updated to 1.20.1

## 4.1.1
* Added a config to lock the input stacks during the parallel matching process.

## 4.1.0
* Switched from the Linked List Cache model to a Concurrent Recipe Matching model.
* This increases matching performance substantially as the number of recipes increases, without becoming useless when a mod like Polymorph is installed.
  * This also means that with FS 4.1.0+, Polymorph will no longer incur a performance hit during recipe matching.
* Concurrent Recipe Matching is automatically enabled for all Recipe Types which have more than 100 recipes.
* Individual recipe types can be blacklisted in the config file if they exhibit problems with Concurrent Recipe Matching.
  * If certain mods are having problems, you can report an issue to FastSuite and I will investigate if that mod can be made compatible, or add their recipe types to the default blacklist.
* There is a configurable max time that a Concurrent Match operation may take, to prevent deadlocks (in the case that another mod somehow triggers a blocking operation from a recipe match worker thread).

## 4.0.0
* Updated to 1.19.2

## 3.0.2
* Updated to support new ICondition$IContext feature.

## 3.0.1
* Rebuilt for 1.18.2

## 3.0.0
* Ported to 1.18.1

## 2.0.0
* Ported to 1.17.1

## 1.1.1
* Made calls to LinkedRecipeList#findFirstMatch synchronized, to avoid deadlocks when multiple threads trigger a list modification.

## 1.1.0
* Moved the mixin target to after the DataPackRegistries object is fully completed, which should resolve possible race conditions.
* Removed the canFit check due to various conflicts with a wide range of mods.  Turns out canFit is not implemented very well across the board.

## 1.0.2
* Fixed a missing reference to a mixin in fastsuite.mixins.json

## 1.0.1
* Added a preliminary check to canFit before attempting true matching.  Should reduce matching time in 2x2 grids.
* Removed the override for RecipeManager#getRecipes due to memory usage issues.

## 1.0.0
* Initial release.