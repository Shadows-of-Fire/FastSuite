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