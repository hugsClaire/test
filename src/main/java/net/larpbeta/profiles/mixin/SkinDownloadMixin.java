package net.larpbeta.profiles.mixin;

import net.larpbeta.profiles.client.ClientState;
import net.minecraft.client.net.thread.ThreadDownloadImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.image.BufferedImage;
import java.util.UUID;

/**
 * The game "downloads" a skin from the address stored on the player. Our skins use made-up addresses of
 * the form {@code betalarp:<character uuid>:<version>}; instead of going to the internet, we hand the
 * picture we already received from the server to the game.
 */
@Mixin(ThreadDownloadImage.class)
public class SkinDownloadMixin {
	@Inject(method = "run", at = @At("HEAD"), cancellable = true, remap = false)
	private void profiles$serverSkins(CallbackInfo ci) {
		ThreadDownloadImage self = (ThreadDownloadImage) (Object) this;
		String url = self.url;
		if (url == null || !url.startsWith("betalarp:")) {
			return;
		}
		ci.cancel();
		try {
			UUID id = UUID.fromString(url.split(":")[1]);
			ClientState.Skin skin = ClientState.cachedSkin(id);
			if (skin != null && skin.image != null) {
				BufferedImage image = skin.image;
				self.texture.image = self.imageParser != null ? self.imageParser.parseImage(image) : image;
			}
		} catch (RuntimeException ignored) {
			// Bad address: leave the default skin.
		}
	}
}
