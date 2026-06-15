package dev.shadowsoffire.fastsuite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.common.base.Stopwatch;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * A per-type recipe index that narrows a lookup to the recipes that could plausibly match the input, rather than scanning every recipe of the type.
 * <p>
 * This significantly reduces the amount of work done during matching, especially when matching the entire recipe list (i.e. when Polymorph is in use).
 */
@SuppressWarnings("deprecation")
public class CachedRecipeList<C extends RecipeInput, T extends Recipe<C>> {

    static final Map<Class<?>, Boolean> parallelRecipeClassCache = Collections.synchronizedMap(new IdentityHashMap<>());
    static final Map<Class<?>, Boolean> ingredientClassCache = Collections.synchronizedMap(new IdentityHashMap<>());

    // Neo implements recipe priorities at the RecipeManager level, but we don't have that context here.
    // The effective priorities are the iteration order, so we preserve it to re-order results after matching.
    private final Object2IntMap<RecipeHolder<T>> effectivePriorities = new Object2IntOpenHashMap<>();

    /** Indexable recipes, filed under each item their pivot (most-selective) ingredient accepts. */
    private final Reference2ObjectMap<Item, List<RecipeHolder<T>>> byPivotItem = new Reference2ObjectOpenHashMap<>();

    /** Recipes that can't be statically indexed (special recipes, or unsafe class/ingredients); matched on every lookup. */
    private final List<RecipeHolder<T>> alwaysCheck = new ArrayList<>();

    public CachedRecipeList(RecipeType<T> type, Collection<RecipeHolder<T>> recipes) {
        Stopwatch watch = Stopwatch.createStarted();
        for (RecipeHolder<T> holder : recipes) {
            // File each recipe under its iteration index so the ascending sort below restores the original (vanilla byType) order.
            this.effectivePriorities.put(holder, this.effectivePriorities.size());

            Ingredient pivot = selectPivot(holder.value());
            if (pivot == null) {
                this.alwaysCheck.add(holder);
            }
            else {
                pivot.items().forEach(item -> this.byPivotItem.computeIfAbsent(item.value(), k -> new ArrayList<>()).add(holder));
            }
        }
        watch.stop();
        FastSuite.LOGGER.info("Indexed recipes for {} in {}. {}/{} recipes are indexed, {} always-checked.",
            BuiltInRegistries.RECIPE_TYPE.getKey(type), watch, recipes.size() - this.alwaysCheck.size(), recipes.size(), this.alwaysCheck.size());
    }

    /**
     * Returns a (sorted) stream of all recipes that should be checked to match the given input.
     * <p>
     * This is the join of any recipes whose pivot is one of the items in the input, plus {@link #alwaysCheck}.
     */
    public Stream<RecipeHolder<T>> getRecipesFor(C inv, Level level) {
        List<RecipeHolder<T>> candidates = new ArrayList<>(this.gatherCandidates(inv));
        candidates.sort(Comparator.comparingInt(this.effectivePriorities));

        return this.mergeByPriority(candidates, this.alwaysCheck)
            .filter(rh -> rh.value().matches(inv, level));
    }

    /**
     * Lazily merges two priority-sorted, disjoint recipe lists into one priority-ordered stream, so {@code findFirst} can short-circuit without sorting (or even
     * matching) the rest of the always-check bucket.
     */
    private Stream<RecipeHolder<T>> mergeByPriority(List<RecipeHolder<T>> a, List<RecipeHolder<T>> b) {
        Iterator<RecipeHolder<T>> merged = new Iterator<>(){
            private int i = 0;
            private int j = 0;

            @Override
            public boolean hasNext() {
                return this.i < a.size() || this.j < b.size();
            }

            @Override
            public RecipeHolder<T> next() {
                if (this.j == b.size() || (this.i < a.size() && effectivePriorities.getInt(a.get(this.i)) <= effectivePriorities.getInt(b.get(this.j)))) {
                    return a.get(this.i++);
                }
                return b.get(this.j++);
            }
        };

        long size = (long) a.size() + b.size();
        return StreamSupport.stream(Spliterators.spliterator(merged, size, Spliterator.ORDERED), false);
    }

    /**
     * Collects the (deduplicated) indexed recipes that could match the given input - those filed under any item present in the input.
     */
    private ObjectOpenHashSet<RecipeHolder<T>> gatherCandidates(C inv) {
        ObjectOpenHashSet<RecipeHolder<T>> candidates = new ObjectOpenHashSet<>();
        int size = inv.size();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            List<RecipeHolder<T>> bucket = this.byPivotItem.get(stack.getItem());
            if (bucket != null) {
                candidates.addAll(bucket);
            }
        }
        return candidates;
    }

    /**
     * Selects a pivot for a given recipe. The pivot is only selectable if the recipe is "safe" (vanilla class, vanilla ingredients, not special).
     */
    private static Ingredient selectPivot(Recipe<?> recipe) {
        if (!isSafeRecipeClass(recipe.getClass()) || recipe.isSpecial()) {
            return null;
        }

        Ingredient pivot = null;
        long fewest = Long.MAX_VALUE;
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (!isSafeIngredient(ingredient)) {
                return null;
            }
            long count = ingredient.items().count();
            if (count == 0) {
                return null; // can't file an ingredient that accepts nothing
            }
            if (count < fewest) {
                fewest = count;
                pivot = ingredient;
            }
        }
        return pivot; // null when the recipe has no placeable ingredients (special recipe)
    }

    /**
     * Checks if a recipe class is safe to index. All vanilla recipe classes are safe.
     */
    private static boolean isSafeRecipeClass(Class<?> clz) {
        return parallelRecipeClassCache.computeIfAbsent(clz, c -> c.getName().startsWith("net.minecraft.world.item.crafting."));
    }

    /**
     * Checks if an Ingredient is safe to index. All vanilla and NeoForge ingredients are safe.
     */
    private static boolean isSafeIngredient(Ingredient ingredient) {
        if (!ingredient.isCustom())
            return true;
        return ingredientClassCache.computeIfAbsent(ingredient.getCustomIngredient().getClass(), clz -> {
            return clz.getName().startsWith("net.neoforged.neoforge.common.crafting.");
        });
    }
}
