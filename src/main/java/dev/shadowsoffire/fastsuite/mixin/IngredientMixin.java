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

@Mixin(value = Ingredient.class, priority = 500)
public abstract class IngredientMixin {

    @Shadow
    @Nullable
    private IntList stackingIds;

    /**
     * Overwrite of {@link Ingredient#getStackingIds()} to synchronize on the stackingIds.
     * Without this, it is possible for multiple threads to enter the method simultaneously and return null from this method.
     */
    @Overwrite
    @SuppressWarnings("unused")
    public IntList getStackingIds() {
        Object ignored = this.getItems(); // Need to call this to make ModernFix's mixin happy otherwise the world explodes
        return this.__getStackingIds();
    }

    @Shadow(remap = false)
    public abstract boolean checkInvalidation();

    @Shadow(remap = false)
    protected abstract void markValid();

    @Shadow
    public abstract ItemStack[] getItems();

    @Unique
    private IntList __getStackingIds() {
        synchronized (this) {
            if (this.stackingIds == null || checkInvalidation()) {
                this.markValid();
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
