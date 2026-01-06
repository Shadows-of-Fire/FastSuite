package dev.shadowsoffire.fastsuite.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

@Mixin(Ingredient.class)
public abstract class IngredientMixin {

    @Shadow
    @Nullable
    private IntList stackingIds;

    /**
     * Overwrite of {@link Ingredient#getStackingIds()} to synchronize on the stackingIds.
     * Without this, it is possible for multiple threads to enter the method simultaneously and return null from this method.
     */
    @Overwrite
    public IntList getStackingIds() {
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

    @Shadow
    public abstract boolean checkInvalidation();

    @Shadow
    protected abstract void markValid();

    @Shadow
    public abstract ItemStack[] getItems();
}
