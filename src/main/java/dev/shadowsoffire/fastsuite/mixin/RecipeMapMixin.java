package dev.shadowsoffire.fastsuite.mixin;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.shadowsoffire.fastsuite.CachedRecipeList;
import dev.shadowsoffire.fastsuite.FastSuite;
import dev.shadowsoffire.fastsuite.TestableRecipeMap;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Replaces {@link RecipeMap#getRecipesFor(RecipeType, RecipeInput, Level)} for crafting with an indexed variant (see {@link CachedRecipeList}). All other
 * recipe types fall through to vanilla.
 */
@Mixin(value = RecipeMap.class, remap = false)
public abstract class RecipeMapMixin implements TestableRecipeMap {

    @Unique
    private Map<RecipeType<?>, CachedRecipeList<?, ?>> fastsuite$cache = Collections.synchronizedMap(new HashMap<>());

    @Inject(method = "getRecipesFor", at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void fastsuite$indexedGetRecipesFor(RecipeType<T> type, I container, Level level, CallbackInfoReturnable<Stream<RecipeHolder<T>>> cir) {
        if (type != RecipeType.CRAFTING || container.isEmpty() || FastSuite.singleThreadedLookups.contains(type)) {
            return; // only crafting is indexed; everything else (and empty inputs) falls through to vanilla
        }

        CachedRecipeList<I, T> cached = this.getCachedList(type);
        cir.setReturnValue(cached.getRecipesFor(container, level));
    }

    @Unique
    @SuppressWarnings("unchecked")
    private <I extends RecipeInput, T extends Recipe<I>> CachedRecipeList<I, T> getCachedList(RecipeType<T> type) {
        synchronized (this.fastsuite$cache) {
            CachedRecipeList<I, T> list = (CachedRecipeList<I, T>) this.fastsuite$cache.get(type);
            if (list == null) {
                list = new CachedRecipeList<>(type, this.byType(type));
                this.fastsuite$cache.put(type, list);
            }
            return list;
        }
    }

    @Shadow
    public abstract <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> byType(RecipeType<T> type);

}
