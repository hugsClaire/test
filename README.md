# BETA LARP

A mod for [Better Than Adventure](https://www.betterthanadventure.net/) (BTA) 8.0.1 servers, built on Babric. Nobody plays under their real account name: each player joins as an anonymous character with its own name, UUID and save data, so a roleplay server stays in character.

## What it does today

- Players still log in with their real Minecraft accounts. BTA checks the account itself (keep `online-mode=true` in `server.properties`).
- Right before the server creates the player, this mod swaps the account's name and UUID for those of the account's character. Everything after that (saved inventory and position, chat, the join message, what other players see) uses the character.
- A first-time account gets a new character with a generated name like `Stranger1234`.

Planned: a character-slot screen on joining (several characters per account), and custom skins per character.

## Install

- Put the jar from `build/libs` (the one without `-sources`) in the server's `mods` folder. Clients will need it too once the slot screen exists.
- Do not install RetroAuth or the OSL mods. They are for vanilla Beta 1.7.3 and stop BTA from starting.

## For admins

- Characters live in `config/profiles/slots.tsv` on the server (account UUID, character UUID, character name). Each character's save file is `players/<character UUID>.dat` in the world folder.
- **Bans apply to the character**, not the person behind it. The ban check happens at login, so for now a banned character locks its account out until its line is removed from `slots.tsv` (which gives that account a fresh character).
- **Ops and the whitelist apply to the real account**, so an op is op on every character.
- The server log shows which account joined as which character, so keep the logs private if the goal is anonymity from other players.

## Building

JDK 17 compiles the mod and Gradle itself runs on JDK 21. Run `gradlew build` (`./gradlew build` on Mac or Linux); the jar ends up in `build/libs`.

Built from the BTA example mod template by Turnip Labs.
