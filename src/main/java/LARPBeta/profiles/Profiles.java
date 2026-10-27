package LARPBeta.profiles;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import LARPBeta.halplibe.HalpLibe;
import LARPBeta.halplibe.event.defs.CommonEvents;
import LARPBeta.halplibe.util.dependency.Key;

public class profiles implements ModInitializer {
	public static final String MOD_ID = HalpLibe.registerMod("profiles", true);
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CommonEvents.BEFORE_GAME_START.listen(Key.of(MOD_ID), this::beforeGameStart);
		CommonEvents.AFTER_GAME_START.listen(Key.of(MOD_ID), this::afterGameStart);
		LOGGER.info("Profiles initialized.");
	}

	public void beforeGameStart() {

	}

	public void afterGameStart() {

	}
}
