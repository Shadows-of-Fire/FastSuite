package dev.shadowsoffire.fastsuite.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shadowsoffire.fastsuite.ILockableItemStack;
import net.minecraft.world.item.ItemStack;

@Mixin(ItemStack.class)
public class ItemStackMixin implements ILockableItemStack {

	private boolean locked;

	@Override
	public void setLocked(boolean locked) {
		this.locked = locked;
	}

	@Inject(method = "setCount", at = @At("HEAD"), cancellable = true, require = 1)
	private void fs_injectCountLock(int count, CallbackInfo ci) {
		if (locked) throw new RuntimeException("A mod has modified a stack during recipe matching!");
	}

}
