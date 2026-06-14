package dev.shadowsoffire.fastsuite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.spongepowered.asm.mixin.Unique;

import com.google.common.base.Stopwatch;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * A cached list of recipes for a specific recipe type. This class is used to speed up recipe lookups by pre-sorting recipes into parallel and serial lists.
 * <p>
 * A recipe is parallelizable if the recipe class and all ingredients are known to be thread-safe. All vanilla recipes are considered thread-safe.
 */
@SuppressWarnings("deprecation")
public class CachedRecipeList<C extends RecipeInput, T extends Recipe<C>> {

    static final Map<Class<?>, Boolean> parallelRecipeClassCache = Collections.synchronizedMap(new IdentityHashMap<>());
    static final Map<Class<?>, Boolean> ingredientClassCache = Collections.synchronizedMap(new IdentityHashMap<>());

    // Neo implements recipe priorities at the RecipeManager level, but we don't have that context here.
    // The effective priorities are the iteration order, so we need to compute that and preserve it to re-order after matching.
    private final Object2IntMap<RecipeHolder<T>> effectivePriorities = new Object2IntOpenHashMap<>();
    private final List<RecipeHolder<T>> serialRecipes;
    private final List<RecipeHolder<T>> parallelRecipes;
    private final RecipeType<T> type;

    public CachedRecipeList(RecipeType<T> type, Collection<RecipeHolder<T>> recipes) {
        this.type = type;
        this.serialRecipes = new ArrayList<>();
        this.parallelRecipes = new ArrayList<>();
        Stopwatch watch = Stopwatch.createStarted();
        for (RecipeHolder<T> holder : recipes) {
            if (isParallelRecipe(holder.value())) {
                this.parallelRecipes.add(holder);
            }
            else {
                this.serialRecipes.add(holder);
            }

            this.effectivePriorities.put(holder, recipes.size() - this.effectivePriorities.size());
        }
        watch.stop();
        FastSuite.LOGGER.info("Constructed recipe list for {} in {}. {}/{} recipes are parallelized.",
            BuiltInRegistries.RECIPE_TYPE.getKey(type), watch, this.parallelRecipes.size(), recipes.size());
    }

    /**
     * Matches all recipes for the given input and level. Recipes deemed "safe" are matched in parallel, and then recipes deemed "unsafe" are matched serially
     * afterwards.
     */
    public List<RecipeHolder<T>> getRecipesFor(C inv, Level level) {
        Predicate<RecipeHolder<T>> recipeFilter = (recipe) -> {
            return recipe.value().matches(inv, level);
        };

        try {
            this.lockAllStacks(inv, true);
            List<RecipeHolder<T>> matches = StreamUtils.<List<RecipeHolder<T>>>executeUntil(() -> this.parallelRecipes
                .parallelStream()
                .filter(recipeFilter)
                .collect(Collectors.toCollection(ArrayList::new)),
                FastSuite.maxRecipeLookupTime, TimeUnit.SECONDS, Collections.emptyList(), () -> timeoutMsg(type));

            matches.addAll(this.serialRecipes.stream().filter(recipeFilter).toList());

            matches.sort(Comparator.comparingInt(this.effectivePriorities));

            return matches;
        }
        catch (Exception ex) {
            throw ex;
        }
        finally {
            this.lockAllStacks(inv, false);
        }
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

        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
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
        if (!ingredient.isCustom())
            return true;
        return ingredientClassCache.computeIfAbsent(ingredient.getCustomIngredient().getClass(), clz -> {
            return clz.getName().startsWith("net.neoforged.neoforge.common.crafting.");
        });
    }

    static String timeoutMsg(RecipeType<?> type) {
        return String.format("Multithreaded recipe lookup took longer than %d seconds - aborting and returning nothing. Consider blacklisting this recipe type (%s) in the config.", FastSuite.maxRecipeLookupTime,
            BuiltInRegistries.RECIPE_TYPE.getKey(type));
    }

    /**
     * If {@link FastSuite#lockInputStacks} is enabled, modifies the locked state of all stacks in the given recipe input.
     *
     * @param inv    The recipe input to modify the stacks of.
     * @param locked Whether to lock or unlock the stacks.
     */
    @Unique
    private void lockAllStacks(C inv, boolean locked) {
        if (FastSuite.lockInputStacks) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty()) {
                    ((ILockableItemStack) (Object) s).setLocked(locked);
                }
            }
        }
    }
}
