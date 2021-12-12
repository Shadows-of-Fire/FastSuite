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