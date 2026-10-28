package net.larpbeta.profiles.mixin;

import net.larpbeta.profiles.ProfileRegistry;
import net.larpbeta.profiles.Profiles;
import net.minecraft.core.util.helper.UUIDHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Admin commands such as {@code /ban username Bob} turn the name into a UUID with this method, which
 * normally asks Mojang about a real account called Bob. On a BETA LARP server the names people use are
 * characters, so we answer from our own list first. That makes /ban, /unban, /op and the whitelist
 * commands work with character names (op and whitelist are then applied to the owning account by PlayerListMixin).
 */
@Mixin(UUIDHelper.class)
public class UuidLookupMixin {
	@Inject(method = "runConversionAction", at = @At("HEAD"), cancellable = true, remap = false)
	private static void profiles$characterNames(String name, UUIDHelper.UUIDFunction onUuid, UUIDHelper.StringFunction onFail, CallbackInfo ci) {
		ProfileRegistry registry = Profiles.registry();
		ProfileRegistry.Slot slot = registry == null ? null : registry.findAnySlotByName(name);
		if (slot != null && onUuid != null) {
			ci.cancel();
			onUuid.run(slot.id);
		}
	}
}
