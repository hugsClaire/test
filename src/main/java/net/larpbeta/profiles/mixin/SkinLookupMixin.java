package net.larpbeta.profiles.mixin;

import net.larpbeta.profiles.client.ClientState;
import net.minecraft.client.entity.player.PlayerLocal;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.util.helper.GetSkinUrlThread;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * BTA normally looks a player's skin up on Mojang's servers by their NAME. For a character that
 * would show the skin of whichever real account happens to have that name (or none), and it would
 * send characters' names to Mojang. On a BETA LARP server we skip that entirely and ask our own
 * server for the character's skin instead.
 */
@Mixin(GetSkinUrlThread.class)
public class SkinLookupMixin {
	@Shadow(remap = false)
	private Player player;

	@Inject(method = "run", at = @At("HEAD"), cancellable = true, remap = false)
	private void profiles$useServerSkins(CallbackInfo ci) {
		if (!ClientState.active) {
			return; // not on a BETA LARP server: leave BTA's behaviour alone
		}
		ci.cancel();
		// Our own player entity does not know its character's UUID; the picker does.
		UUID id = player instanceof PlayerLocal && ClientState.selected != null ? ClientState.selected : player.uuid;
		ClientState.Skin skin = ClientState.fetchSkin(id);
		if (skin != null && skin.image != null) {
			player.slimModel = skin.slim;
			player.skinURL = "betalarp:" + id + ":" + skin.version;
		}
	}
}
