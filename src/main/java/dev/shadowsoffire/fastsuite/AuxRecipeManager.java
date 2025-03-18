package dev.shadowsoffire.fastsuite;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    @Override
    public <C extends RecipeInput, T extends Recipe<C>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> type, C inv, Level level) {
        if (this.numRecipesOf(type) < FastSuite.MIN_SIZE_REQUIRED_FOR_THREADING || FastSuite.singleThreadedLookups.contains(type)) return super.getRecipeFor(type, inv, level);
        this.lockAllStacks(inv, true);
        try {
            if (FastSuite.unsafeMode) {
                return StreamUtils.executeUntil(() -> this.byType(type).parallelStream().filter(recipe -> {
                    return recipe.value().matches(inv, level);
                }).findFirst(), FastSuite.maxRecipeLookupTime, TimeUnit.SECONDS, Optional.empty(), () -> CachedRecipeList.timeoutMsg(type));
            }
            else {
                var cachedRecipeList = getCachedRecipeList(type);
                return cachedRecipeList.getRecipeFor(inv, level);
            }
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        finally {
            this.lockAllStacks(inv, false);
        }
    }

    @Override
    public <C extends RecipeInput, T extends Recipe<C>> List<RecipeHolder<T>> getRecipesFor(RecipeType<T> type, C inv, Level level) {
        if (this.numRecipesOf(type) < FastSuite.MIN_SIZE_REQUIRED_FOR_THREADING || FastSuite.singleThreadedLookups.contains(type)) return super.getRecipesFor(type, inv, level);
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

    private <C extends RecipeInput> void lockAllStacks(C inv, boolean locked) {
        if (!FastSuite.lockInputStacks) return;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                ((ILockableItemStack) (Object) s).setLocked(locked);
            }
        }
    }

    private int numRecipesOf(RecipeType type) {
        return this.byType(type).size();
    }

}
