# BETA LARP

A mod for [Better Than Adventure](https://www.betterthanadventure.net/) (BTA) 8.0.1 servers, built on Babric. Nobody plays under their real account name: each player joins as an anonymous character with its own name, UUID and save data, so a roleplay server stays in character.

## What it does

- Players still log in with their real Minecraft accounts. BTA checks the account itself (keep `online-mode=true` in `server.properties`).
- After connecting, the player sees a **character picker** before entering the world. They can pick a character, create one, edit one (rename it, change its skin or arm model) or delete one. Banned characters are not listed in the picker; a "Banned" button shows a read-only list of the ones you own. They cannot be played, edited or deleted. Up to 8 playable characters per account; banned characters do not count towards the limit, so being banned can never stop someone from playing.
- When a character is picked, the server swaps the account's name and UUID for the character's right before it creates the player. Everything after that (saved inventory and position, chat, the join message, what other players see) uses the character. A brand-new character starts like a first join.
- **Skins** are per character. The player picks a 64x64 (or legacy 64x32) PNG on their own computer, using the system file chooser; it is checked, stored on the server (`config/profiles/skins`) and sent to other players' clients. The game's usual skin lookup (which asks Mojang by player *name*) is switched off on BETA LARP servers, so a character never borrows the skin of a real account with the same name, and character names are never sent to Mojang.
- `/character list`, `/character new <name>` and `/character switch <name>` still work in game as a fallback. They save and disconnect you; rejoin and you are the new character.

## Install

- Put the jar from `build/libs` (the one without `-sources`) in the `mods` folder of the **server and every player's game**. A player without it is disconnected after about 20 seconds with a message saying the mod is needed (the server is waiting for them to pick a character).
- Do not install RetroAuth or the OSL mods. They are for vanilla Beta 1.7.3 and stop BTA from starting.

## For admins

- Characters live in `config/profiles/slots.tsv` on the server (account UUID, character UUID, character name). Each character's save file is `players/<character UUID>.dat` in the world folder.
- **Bans apply to the character**, not the person behind it. A banned character does not count towards the 8-character limit and is moved to the "Banned" list in its owner's picker, and the owner can still play their other characters or make a new one. Use `/ban username <character name>` and `/unban username <character name>` (character names work in these commands, and so do `/op` and the whitelist commands, which apply to the owning account). BTA 8.0.1's own `/unban username` has a bug (it bans again instead of unbanning); this mod fixes it.
- Deleting a character in the picker removes it and its skin, but leaves the world's saved player file (`players/<character UUID>.dat`) in place, so nothing is lost by accident.
- **Ops and the whitelist apply to the real account**, so an op is op on every character.
- The server log shows which account joined as which character, so keep the logs private if the goal is anonymity from other players.

## Building

JDK 17 compiles the mod and Gradle itself runs on JDK 21. Run `gradlew build` (`./gradlew build` on Mac or Linux); the jar ends up in `build/libs`.

Built from the BTA example mod template by Turnip Labs.

## Better With Discord

If the Better With Discord mod is on the server, BETA LARP sends character death messages to Discord itself (that mod's own death hook did not deliver them). Messages use the character's name. The Discord mod is not modified, and nothing happens if it is not installed. The server log shows a "Sending death message to Discord" line for each death.
