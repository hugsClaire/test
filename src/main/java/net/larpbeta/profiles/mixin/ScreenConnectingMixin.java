package net.larpbeta.profiles.mixin;

import net.larpbeta.profiles.client.ClientState;
import net.minecraft.client.gui.ScreenConnecting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every new connection starts with this screen. Forget what we knew about the last server, so
 * a non-BETA LARP server later on is treated like normal.
 */
@Mixin(ScreenConnecting.class)
public class ScreenConnectingMixin {
	@Inject(method = "<init>", at = @At("TAIL"), remap = false)
	private void profiles$newConnection(CallbackInfo ci) {
		ClientState.reset();
	}
}
