# POH Clue Teleports

<img src="icon.png" alt="" align="right" width="48">

Shows which teleport in your house gets you to your current clue step fastest.

The plugin checks the teleports your house actually has, works out real walking routes from each one, and recommends the quickest. It also highlights that teleport when you open the menu.

## Features

- **Best teleport for supported clue steps**, with the walking time and number of tiles.
- **Uses your account**: unlocked destinations, quests, diaries, and the agility shortcuts you can use.
- **Highlights the teleport** in the portal nexus, jewellery box, fairy ring, spirit tree, and mounted item menus.
- **Minimap arrow** that guides you from where you land to the clue.
- **Optional chat message** with the recommendation, for players who prefer chat to overlays.
- Works with any house setup. You don't need any particular facility.

## Getting started

1. Install **POH Clue Teleports** from the Plugin Hub. Make sure RuneLite's built-in **Clue Scroll** plugin is enabled.
2. Enter your house with **Home**, **Teleport to House**, a house tab, or a construction cape. If you're already inside, leave and come back in.
3. Walk through the rooms that have teleports.
4. For each facility marked **Not learned**, open its teleport menu and leave it open briefly.
5. Read a clue. The overlay shows the best teleport.

You only need to do this once per character. To learn new destinations later, open that menu again.

## Settings

- **Overlay mode**: **Full** (default) shows the route details, **Compact** shows only the teleport, and **Hidden** turns the panel off but keeps menu highlights.
- **Chat messages**: posts the recommendation in chat. Off by default.
- **Minimap arrow**: shows a guide arrow after you teleport. On by default.
- **Highlight in house**: outlines the teleport to use in your house. On by default. Pick its colour with **Highlight colour**.

## Good to know

- Only teleports inside your own house are compared. The plugin doesn't consider teleports from items in your inventory or from your spellbook.
- Rebuilt your house or removed a room? Right-click the overlay and choose **Forget learned sources**, or type `::pohcluereset`. Then learn your house again.
- The plugin never clicks, dials, or teleports for you.
- Works with Easy Teleports and Teleport Maps. See the [compatibility notes](docs/teleport-plugin-compatibility.md).

## More

- [How it works](docs/how-it-works.md): learning, routing, supported clues, and limitations
- [Development](docs/development.md): building, running, and testing locally

Found a bug? [Open an issue](https://github.com/ggaidelevicius/runelite-poh-clue-teleports/issues).

## Credits

The walking routes use collision and transport data from [Shortest Path](https://github.com/Skretzo/shortest-path). The plugin also uses data from:

- [RuneLite](https://github.com/runelite/runelite): clue logic, skill challenges, transport data, and [game scripts](https://github.com/runelite/cs2-scripts/blob/master/scripts/%5Bproc%2Cscript2674%5D.cs2)
- [clue-helper](https://github.com/emerald000/clue-helper/tree/b1a5e772d45373bac133bfa28a1ded6a38b910b3): challenge task sites
- [data_osrs](https://github.com/mejrs/data_osrs/tree/78864831dada068f2092d5d87a34f93eb01526ad/npcids) and [wasp-map-layers](https://github.com/Torwent/wasp-map-layers/blob/390e8515bcaa3dc134ef62a3fcbcd5b714b05828/files/npcs.zip): NPC positions
- [Quest Helper](https://github.com/Zoinkwiz/quest-helper/tree/b9b93706618a7b7fe7e5ea30e9a9ef35ba09673e/src/main/java/com/questhelper/helpers/quests): quest progress checks

Full notices are in [THIRD_PARTY_NOTICES.txt](src/main/resources/THIRD_PARTY_NOTICES.txt). Licensed under [BSD 2-Clause](LICENSE).
