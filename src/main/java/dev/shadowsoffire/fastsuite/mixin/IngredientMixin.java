package dev.shadowsoffire.fastsuite.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

@Mixin(value = Ingredient.class, priority = 500, remap = false)
public abstract class IngredientMixin {

    @Shadow
    @Nullable
    private IntList stackingIds;

    /**
     * @reason Ensures that {@link Ingredient#getStackingIds()} is synchronized.
     *         Without this, it is possible for multiple threads to enter the method simultaneously and return null from this method.
     * @author Shadows_of_Fire
     */
    @Overwrite
    @SuppressWarnings("unused")
    public IntList getStackingIds() {
        Object ignored = this.getItems(); // Need to call this to make ModernFix's mixin happy otherwise the world explodes
        return this.__getStackingIds();
    }

    @Shadow
    public abstract ItemStack[] getItems();

    /**
     * This has to be in its own method due to a mixin issue where the synchronized block is not injected properly into the overwritten method.
     */
    @Unique
    private IntList __getStackingIds() {
        synchronized (this) {
            if (this.stackingIds == null) {
                ItemStack[] aitemstack = this.getItems();
                this.stackingIds = new IntArrayList(aitemstack.length);

                for (ItemStack itemstack : aitemstack) {
                    this.stackingIds.add(StackedContents.getStackingIndex(itemstack));
                }

                this.stackingIds.sort(IntComparators.NATURAL_COMPARATOR);
            }

            return this.stackingIds;
        }
    }
}
