package dev.shadowsoffire.fastsuite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.base.Stopwatch;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A cached list of recipes for a specific recipe type. This class is used to speed up recipe lookups by pre-sorting recipes into parallel and serial lists.
 * <p>
 * A recipe is parallelizable if the recipe class and all ingredients are known to be thread-safe. All vanilla recipes are considered thread-safe.
 */
@SuppressWarnings("deprecation")
class CachedRecipeList<C extends Container, T extends Recipe<C>> {

    static final Map<Class<?>, Boolean> parallelRecipeClassCache = Collections.synchronizedMap(new IdentityHashMap<>());
    static final Map<Class<?>, Boolean> ingredientClassCache = Collections.synchronizedMap(new IdentityHashMap<>());

    private final List<T> serialRecipes;
    private final List<T> parallelRecipes;
    private final RecipeType<T> type;

    public CachedRecipeList(RecipeType<T> type, Map<ResourceLocation, T> recipeMap) {
        this.type = type;
        this.serialRecipes = new ArrayList<>();
        this.parallelRecipes = new ArrayList<>();
        Stopwatch watch = Stopwatch.createStarted();
        for (Map.Entry<ResourceLocation, T> entry : recipeMap.entrySet()) {
            if (isParallelRecipe(entry.getValue()))
                this.parallelRecipes.add(entry.getValue());
            else
                this.serialRecipes.add(entry.getValue());
        }
        watch.stop();
        FastSuite.LOGGER.info("Constructed recipe list for {} in {}. {}/{} recipes are parallelized.",
            ForgeRegistries.RECIPE_TYPES.getKey(type), watch, this.parallelRecipes.size(), recipeMap.size());
    }

    /**
     * Attempts to match a recipe for the given container and level. This method first checks parallel recipes, then serial recipes.
     * 
     * @implNote This method will need to be updated if/when Forge implements recipe priority.
     */
    public Optional<T> getRecipeFor(C inv, Level level) {
        Optional<T> parRecipe = StreamUtils.executeUntil(() -> this.parallelRecipes.parallelStream()
            .filter(recipe -> recipe.matches(inv, level))
            .findFirst(),
            FastSuite.maxRecipeLookupTime, TimeUnit.SECONDS, Optional.empty(), () -> timeoutMsg(type));

        if (parRecipe.isPresent()) {
            return parRecipe;
        }

        // check serial recipes
        for (T recipe : this.serialRecipes) {
            if (recipe.matches(inv, level)) {
                return Optional.of(recipe);
            }
        }

        return Optional.empty();
    }

    /**
     * Matches all recipes for the given container and level.
     * <p>
     * In accordance with the contract of {@link RecipeType#getRecipesFor}, the returned list is sorted by the description ID of the result item.
     */
    public List<T> getRecipesFor(C inv, Level level) {
        Comparator<T> recipeSorter = Comparator.comparing((recipe) -> {
            return recipe.getResultItem(level.registryAccess()).getDescriptionId();
        });

        Predicate<T> recipeFilter = (recipe) -> {
            return recipe.matches(inv, level);
        };

        List<T> parallelList = StreamUtils.executeUntil(() -> this.parallelRecipes
            .parallelStream()
            .filter(recipeFilter)
            .collect(Collectors.toCollection(ArrayList::new)),
            FastSuite.maxRecipeLookupTime, TimeUnit.SECONDS, Collections.emptyList(), () -> timeoutMsg(type));

        parallelList.addAll(this.serialRecipes.stream().filter(recipeFilter).toList());
        Collections.sort(parallelList, recipeSorter);
        return parallelList;
    }

    /**
     * Checks if a recipe is parallelizable. A recipe is parallelizable if the recipe class and all ingredients are known to be thread-safe.
     * <p>
     * Mods can register known safe classes by using {@link FastSuite#registerSafeRecipeClass(Class)} and {@link FastSuite#registerSafeIngredientClass(Class)}.
     */
    private boolean isParallelRecipe(T recipe) {
        if (!isSafeRecipeClass(recipe.getClass())) {
            return false;
        }

        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!isSafeIngredient(ingredient))
                return false;
        }
        return true;
    }

    /**
     * Checks if a recipe class is parallelizable. All vanilla recipe classes are safe.
     */
    private static boolean isSafeRecipeClass(Class<?> clz) {
        return parallelRecipeClassCache.computeIfAbsent(clz, c -> c.getName().startsWith("net.minecraft.world.item.crafting."));
    }

    /**
     * Checks if an Ingredient is parallelizable. All vanilla and Forge ingredients are safe.
     */
    private static boolean isSafeIngredient(Ingredient ingredient) {
        if (ingredient.isVanilla())
            return true;
        return ingredientClassCache.computeIfAbsent(ingredient.getClass(), clz -> {
            return clz.getName().startsWith("net.minecraftforge.common.crafting.");
        });
    }

    static String timeoutMsg(RecipeType<?> type) {
        return String.format("Multithreaded recipe lookup took longer than %d seconds - aborting and returning nothing. Consider blacklisting this recipe type (%s) in the config.", FastSuite.maxRecipeLookupTime,
            BuiltInRegistries.RECIPE_TYPE.getKey(type));
    }
}
