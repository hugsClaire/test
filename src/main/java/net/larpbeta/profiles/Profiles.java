package net.larpbeta.profiles;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turniplabs.halplibe.HalpLibe;
import turniplabs.halplibe.event.defs.CommonEvents;
import turniplabs.halplibe.util.dependency.Key;

import java.io.IOException;
import java.nio.file.Path;

public class Profiles implements ModInitializer {
	public static final String MOD_ID = HalpLibe.registerMod("profiles", true);
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ProfileRegistry registry;

	/** The server-side record of which character slots each account owns. Null if it failed to load. */
	public static ProfileRegistry registry() {
		return registry;
	}

	@Override
	public void onInitialize() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("profiles").resolve("slots.tsv");
		ProfileRegistry loaded = new ProfileRegistry(file, message -> LOGGER.warn("{}", message));
		try {
			loaded.load();
			registry = loaded;
		} catch (IOException e) {
			// Leave the registry null: the login hook refuses logins rather than letting
			// players in under their real account names.
			LOGGER.error("Could not read {}; logins will be refused until this is fixed.", file, e);
		}

		CommonEvents.BEFORE_GAME_START.listen(Key.of(MOD_ID), this::beforeGameStart);
		CommonEvents.AFTER_GAME_START.listen(Key.of(MOD_ID), this::afterGameStart);
		LOGGER.info("Profiles initialized.");
	}

	public void beforeGameStart() {

	}

	public void afterGameStart() {

	}
}
