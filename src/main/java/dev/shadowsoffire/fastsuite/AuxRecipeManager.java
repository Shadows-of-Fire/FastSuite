package dev.shadowsoffire.fastsuite;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.jetbrains.annotations.VisibleForTesting;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class AuxRecipeManager extends RecipeManager {

    private final Map<RecipeType<?>, CachedRecipeList<?, ?>> cachedRecipeListMap = new HashMap<>();

    public AuxRecipeManager(HolderLookup.Provider registries) {
        super(registries);
    }

    @VisibleForTesting
    public <C extends RecipeInput, T extends Recipe<C>> Optional<RecipeHolder<T>> super_getRecipeFor(RecipeType<T> type, C inv, Level level) {
        return super.getRecipeFor(type, inv, level);
    }

    /**
     * Returns the first recipe for the given recipe type, recipe input, and level.
     * <p>
     * This method will concurrently match recipes if the number of recipes is above the threshold set in {@link FastSuite#MIN_SIZE_REQUIRED_FOR_THREADING} and the
     * type is not blacklisted.
     * <p>
     * If {@link FastSuite#unsafeMode}, all recipes will be matched in parallel; otherwise, only the known-safe recipes will be matched in parallel.
     *
     * @apiNote The output of this method is not necessarily stable if there are recipe conflicts. Recipe conflicts are undefined behavior.
     * @implNote We need to specifically override this method (the one with the RecipeHolder param) since it is the "general" case. The other methods delegate to
     *           this one.
     */
    @Override
    public <C extends RecipeInput, T extends Recipe<C>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> type, C inv, Level level, @Nullable RecipeHolder<T> lastRecipe) {
        if (this.numRecipesOf(type) < FastSuite.MIN_SIZE_REQUIRED_FOR_THREADING || FastSuite.singleThreadedLookups.contains(type)) {
            return super.getRecipeFor(type, inv, level, lastRecipe);
        }

        if (lastRecipe != null && lastRecipe.value().matches(inv, level)) {
            return Optional.of(lastRecipe);
        }

        this.lockAllStacks(inv, true);
        try {
            if (FastSuite.unsafeMode) {
                return StreamUtils.executeUntil(() -> this.byType(type).parallelStream().filter(recipe -> {
                    return recipe.value().matches(inv, level);
                }).findFirst(), FastSuite.maxRecipeLookupTime, TimeUnit.SECONDS, Optional.empty(), () -> CachedRecipeList.timeoutMsg(type));
            }
            else {
                var cachedRecipeList = getCachedRecipeList(type);
                try {
                	var out = cachedRecipeList.getRecipeFor(inv, level);
                	if (FastSuite.DEBUG_MATCHING) {
                    	FastSuite.LOGGER.info("Matched recipe: " + out + " for input " + inv);
                	}
                	return out;
                } catch(Exception ex){
                	FastSuite.LOGGER.error("Invalid recipeFor for input " + inv);
                	return null;
                }
            }
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        finally {
            this.lockAllStacks(inv, false);
        }
    }

    /**
     * Returns all recipes for the given recipe type, recipe input, and level.
     * <p>
     * This method will concurrently match recipes if the number of recipes is above the threshold
     * set by {@link FastSuite#MIN_SIZE_REQUIRED_FOR_THREADING} and the type is not blacklisted.
     * <p>
     * If {@link FastSuite#unsafeMode}, all recipes will be matched in parallel; otherwise, only the known-safe recipes will be matched in parallel.
     */
    @Override
    public <C extends RecipeInput, T extends Recipe<C>> List<RecipeHolder<T>> getRecipesFor(RecipeType<T> type, C inv, Level level) {
        if (this.numRecipesOf(type) < FastSuite.MIN_SIZE_REQUIRED_FOR_THREADING || FastSuite.singleThreadedLookups.contains(type)) {
            return super.getRecipesFor(type, inv, level);
        }

        this.lockAllStacks(inv, true);
        try {
            if (FastSuite.unsafeMode) {
                return StreamUtils.executeUntil(() -> this.byType(type).parallelStream().filter((recipe) -> {
                    return recipe.value().matches(inv, level);
                }).sorted(Comparator.comparing((recipe) -> {
                    return recipe.value().getResultItem(level.registryAccess()).getDescriptionId();
                })).collect(Collectors.toList()), FastSuite.maxRecipeLookupTime, TimeUnit.SECONDS, Collections.emptyList(), () -> CachedRecipeList.timeoutMsg(type));
            }
            else {
                var cachedRecipeList = getCachedRecipeList(type);
                return cachedRecipeList.getRecipesFor(inv, level);
            }
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        finally {
            this.lockAllStacks(inv, false);
        }
    }

    /**
     * Returns the cached recipe list for the given recipe type, creating it if it doesn't exist.
     */
    private <C extends RecipeInput, T extends Recipe<C>> CachedRecipeList<C, T> getCachedRecipeList(RecipeType<T> type) {
        synchronized (cachedRecipeListMap) {
            CachedRecipeList<C, T> list = (CachedRecipeList<C, T>) cachedRecipeListMap.get(type);
            if (list == null) {
                list = new CachedRecipeList<>(type, this.byType(type));
                cachedRecipeListMap.put(type, list);
            }
            return list;
        }
    }

    /**
     * If {@link FastSuite#lockInputStacks} is enabled, modifies the locked state of all stacks in the given recipe input.
     * 
     * @param inv    The recipe input to modify the stacks of.
     * @param locked Whether to lock or unlock the stacks.
     */
    private <C extends RecipeInput> void lockAllStacks(C inv, boolean locked) {
        if (FastSuite.lockInputStacks) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty()) {
                    ((ILockableItemStack) (Object) s).setLocked(locked);
                }
            }
        }
    }

    private int numRecipesOf(RecipeType type) {
        return this.byType(type).size();
    }

}
