# POH Clue Teleports

A RuneLite companion plugin that keeps one POH teleport recommendation visible for the active clue. It uses the official **Clue Scroll** plugin's current target and updates as the clue changes.

It compares travel available through fixtures in your own player-owned house. Once the menus for detected facilities have been learned, the overlay identifies the best available POH destination or fairy ring code and shows estimated running time and walking tiles to the clue. Inventory items, standalone spellbook teleports, and travel facilities outside the POH are outside the plugin's scope.

## Setup and supported facilities

**You do not need any particular POH facility.** The plugin uses whichever supported facilities it detects in your own house. Every facility below contributes its usable destinations to the same teleport comparison.

The setup steps depend on how destinations are learned: some facilities need their travel menu opened, while others are read directly from the loaded house fixture.

1. With the plugin enabled, enter your own POH through **Home** or **Teleport to House**.
2. Walk through the rooms containing your travel facilities. The plugin detects and observes loaded fixtures over two stable game ticks, about 1.2 seconds once loaded.
3. If the overlay lists a facility as **Not learned**, open that facility's travel menu, or its code selector for a fairy ring, and leave it open for two stable game ticks. Recommendations begin once every menu listed for your house has been learned.

The checklist includes only detected facilities that need menu reading. For example, a house with a mounted glory and no menu-based facilities can start recommending after the room is observed, without opening any travel menus. There is no manual import or destination selection.

| POH facility | How to learn its destinations | Available destinations |
| --- | --- | --- |
| Cape hanger | Observe the loaded fixture; no menu opening needed. | Teleports provided by the actual mounted cape, including supported skill, quest, music, and max cape variants. Decorative capes add no destinations. Limited daily teleports are checked again before recommending them. |
| Fairy ring | Open the code selector. | Ordinary destinations, including unvisited codes, filtered by quest and regional access checks. |
| House exit portal | Observe the loaded fixture; no menu opening needed. | The house's configured outside location. |
| Jewellery box | Open its teleport menu. | Destinations allowed by the built box tier and the account's individual destination locks. |
| Mounted digsite pendant | Open its teleport menu. | The Digsite and the account's unlocked Fossil Island and Lithkren destinations. |
| Mounted glory | Observe the loaded fixture; no menu opening needed. | Edgeville, Karamja, Draynor Village, and Al Kharid. |
| Mounted mythical cape | Observe the loaded fixture; no menu opening needed. | Myths' Guild. |
| Mounted Xeric's talisman | Open its teleport menu. | The account's unlocked talisman destinations. |
| Portal chambers | Observe the loaded fixtures; no menu opening needed. | The installed fixed portal destinations, including supported diary alternatives. |
| Portal nexus | Open its teleport menu. | Installed destination slots, live destination names, and supported diary alternatives. |
| Spirit tree | Open its travel menu. | Planted and quest destinations enabled in the old or new travel menu. |
| Wilderness obelisk | Observe the loaded fixture; no menu opening needed. | Its six fixed arrivals, once the collected Wilderness Hard Diary reward allows choosing a destination. Random-only activation is excluded. |

Fairy ring availability comes from the code selector and account access checks, rather than travel-log history. BJR, AKP, and CJQ have conservative partial-quest or island-access checks; they may be withheld until quest completion or a first successful trip.

Each character needs an initial house observation before recommendations are shown. A combined spirit tree and fairy ring supplies both sources. Rooms discovered later add their facilities to the comparison and, where needed, extend the menu checklist. A fixture that has not been seen is not assumed to be built. A distant, unloaded room cannot erase a previously learned fixture. Portal chambers and cape hangers may occupy multiple rooms; their saved arrivals are tracked by fixture position, so a replacement or removal on a loaded tile updates that position while unloaded rooms retain their last observation.

The plugin saves separate snapshots for each source and RuneScape character. Reopening a menu compares its complete available set with the saved set. Additions and removals update that source; identical sets do not trigger another save. Closed, incomplete, hidden, or changing menus leave the previous snapshot intact. Saved availability remains usable between house visits, sessions, and world hops.

There is no sidebar. Before the first house observation, the overlay asks for a house visit. Any subsequent checklist shows **Learned** or **Not learned** for the detected facilities that need menu reading. Changes to a known jewellery box tier invalidate its old capabilities and require learning its menu again.

Amber outlines identify an open interface while it is being read. Once learning is complete, cyan outlines identify the recommended destination in an open nexus, jewellery, spirit tree, or mounted jewellery menu. Fairy ring and portal nexus guidance bring the recommended existing entry into view once when the menu opens or the recommendation changes, then highlight that entry. Subsequent manual scrolling is preserved. Fairy ring guidance also synchronizes its native saved scroll position so menu refreshes retain it. If the entry is unavailable, no fairy-ring interface guide is drawn; the main recommendation still shows the code. It never sets dial values or selects a teleport automatically. The overlay sizes itself to its current text, with a maximum width for long names. Guidance only draws in the positively identified own house and never clicks a control.

A learned source with no available destinations still counts as learned. The checklist lists facilities, without individual destination checkboxes. Once all menus listed for your house are learned, the overlay shows the closest available teleport for the active clue. Setup guidance can appear in your house without an active clue; the recommendation itself follows the core clue state.

House ownership is confirmed by an explicit own-house entry action followed by loading an actual POH region. Use the portal's **Home** option or **Teleport to House** after enabling the plugin; house tablets and construction cape POH teleports are supported too. No welcome chat message is required. Guest-house entry actions invalidate that confirmation. If you enable the plugin while already inside your house, leave and re-enter through one of these actions before learning menus. Opening a network outside your POH does not establish that you own its POH facility.

The cache represents the last observed menu or fixture. Removing an entire facility or room, or rebuilding the house layout, can leave old fixture arrivals cached because an unloaded room does not establish that something was removed. After those changes, right-click the overlay and choose **Forget learned sources**, then re-enter your house, open the detected travel menus, and walk through the other travel rooms. The same action can reset stale data after a game update. Renaming the plugin preserves the original per-character caches.

## Distance and clue coverage

The recommendation uses a collision-aware route from each available POH arrival to the clue. It follows mapped walkable tiles, observes walls and diagonal corner restrictions, and uses directed physical passages for stairs, ladders, dungeon entrances, doors, and agility shortcuts. It does not project underground coordinates onto the surface or ignore an upstairs clue's floor.

The plugin bundles version-pinned Shortest Path collision and physical-passage data. It works without installing or configuring the Shortest Path plugin, makes no routing network requests, and does not alter another plugin's route. Only POH destinations can be recommended. Further item/spell teleports and external fairy ring/spirit tree networks are not part of the onward walking comparison.

Physical passage eligibility uses the character's current levels, quests, native unlock flags, and carried inventory/equipment. Agility uses the current boosted or drained level; Prayer level requirements use the real level, rather than current prayer points. Grapple shortcuts require a suitable equipped crossbow and a carried or equipped grapple. Banked items do not establish access. Unsupported or unexposed requirements exclude that passage. Explicit gated passages are also blocked for ordinary walking, so an open door in the static collision map cannot bypass a listed requirement.

A reverse Dijkstra search finds the minimum modeled route across all currently available arrivals and valid task sites. Ordinary movement costs one unit per tile; a passage costs twice its documented game-tick duration. This models continuous running at two tiles per tick, including listed passage delays. The overlay shows estimated running time and the actual number of ordinary walking tiles in the selected route. This estimate excludes the initial POH teleport animation, movement between house fixtures, run-energy depletion, shortcut failure chance, and boost expiry. Required consumables are checked against carried stock but are not deducted across successive passages.

Route searches run on a cancellable worker. Changing the clue, profile, available destinations, or set of usable physical passages invalidates the result. Inventory, equipment, level, quest, and unlock changes are checked against passage requirements. Changes that leave the usable passage set unchanged reuse the current search or completed result, so ordinary banking does not restart routing. Required-item quantity thresholds and shortcut eligibility still trigger a new search when crossed. Missing map data, inaccessible goals, exhausted work/time budgets, and loading errors show a route status; none fall back to geographic distance or a provisional winner.

Dig and coordinate clues require their exact mapped standing tile. Known NPC/object interactions can end at a nearby mapped interaction face; fixed challenge markers may use the nearest cardinal face within two tiles when the object itself is solid. Static collision data cannot establish every object orientation, temporary obstruction, instance layout, or unlisted server-side gate. Representative teleport landing tiles and task-site markers may also differ slightly from the exact game position. The result is a minimum for the bundled graph and current account snapshot, not a guarantee of exact elapsed time or complete game simulation. See `src/main/resources/routes/README.md` for data provenance and update instructions.

The companion follows the clue already identified by RuneLite. Read the clue once with **Clue Scroll** enabled; the recommendation remains after closing the clue interface. Coordinate, map, cryptic, anagram, cipher, emote, music, fairy ring, resolved hot/cold, Falo, and completed Charlie/Sherlock return tasks are supported through the core clue state. Location-specific Sherlock challenges include binding a blood rune, nature and cosmic runes, the named agility courses, Skullball, Lake Molch, tecu salamanders, the Arceuus Barrows tablet lectern, named Ardougne theft tasks, Fiyr remains, and the named Slayer Tower tasks. Both blood altars are valid alternatives once the true altar's Sins of the Father gate is met; the route comparison uses the unlocked valid task sites. Multiple unresolved clue targets and flexible tasks such as burning a log or smithing at any anvil show a status instead of selecting an arbitrary site. Completed challenges switch the target back to Sherlock or Charlie. The overlay disappears when the core plugin clears the clue or you log out.

Dynamic **Respawn** and **Teleport to Boat** portal targets, and Sailing cape teleports to owned boats, are excluded until their current arrival can be established reliably. The mounted Hunter cape's separately unlocked Hunter Guild teleport is currently excluded because its conversation unlock is not exposed in the available native state. Teleporting back inside the same house is not an onward clue destination.

## Build and try it

Install JDK 17, then run the finite headless checks from this project's directory:

```sh
./gradlew build
```

Start the development client yourself when ready:

```sh
./gradlew run
```

Source compiles to Java 11 bytecode, following the RuneLite plugin template. The macOS development task includes the native fullscreen adapter's required Java exports. The optional `gradle-local` helper uses a project-local JDK and Gradle cache when configured; `./gradle-local build --offline` works after dependencies have been cached.

Enable **POH Clue Teleports** in the development client, enter your own house, and open only the detected travel menus to learn their available destinations. Read a clue with **Clue Scroll** enabled. The ordinary JAR at `build/libs/poh-clue-teleports-1.0.0.jar` is a development build artifact. Source is available at [ggaidelevicius/runelite-poh-clue-teleports](https://github.com/ggaidelevicius/runelite-poh-clue-teleports). Updates use versioned source commits submitted to Plugin Hub, where RuneLite reviews and merges the commit pin.

## Manual verification

Automated tests do not establish live gameplay behavior. In a user-started development client, verify:

1. Enable **POH Clue Teleports**, enter your own POH through **Home** or **Teleport to House**, and wait for fixture discovery, and open each listed menu for at least two ticks. Confirm a house with only a jewellery box does not request a fairy ring, spirit tree, or nexus. Check a combined ring/tree, a newly built facility, a tier change, and a removed loaded fixture. Walk through distant rooms and confirm discoveries are added without clearing unloaded fixtures.
2. Add or remove a nexus destination, downgrade a jewellery box, or unlock or plant a spirit tree. Reopen the relevant menu and confirm its cached destinations change while the other sources stay intact.
3. Open a menu in another player's house. Confirm it does not change your saved availability. Close menus early or during loading and check that the previous snapshot remains.
4. Read a clue and close its interface. Confirm one recommendation remains. Change clue steps, drop the clue, log out, hop worlds, and switch characters; each character should keep its own cache.
5. Compare a clue on opposite sides of a wall or river, a dungeon clue, and an upstairs clue. Verify the chosen arrival follows a usable route. Repeat near an agility shortcut below its level, after a boost, and with any required diary/tool gate missing. Check the blood-rune challenge before and after completion, an unfinished hot/cold clue, and a three-step master clue.
6. With an obelisk built, compare accounts with and without the collected Wilderness Hard Diary reward. Only deterministic destination selection should enter the comparison. Check a mounted cape after its limited daily teleports have been used.
7. Disable and re-enable the plugin. Confirm the overlay cleans up and returns once, with no sidebar button. Re-enter the house before learning new menus.

## Sources

- [RuneLite clue interfaces](https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/plugins/cluescrolls)
- [RuneLite skill challenges](https://github.com/runelite/runelite/blob/runelite-parent-1.13.3/runelite-client/src/main/java/net/runelite/client/plugins/cluescrolls/clues/SkillChallengeClue.java)
- [Challenge task-site data](https://github.com/emerald000/clue-helper/tree/b1a5e772d45373bac133bfa28a1ded6a38b910b3)
- [RuneLite transport data](https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/plugins/worldmap)
- [Shortest Path landing-point data](https://github.com/Skretzo/shortest-path/tree/50f30435f37d1d5c5e2af790925fa7aa70b7840d/src/main/resources/transports)
- [Game scripts for nexus slots and destination data](https://github.com/runelite/cs2-scripts/blob/master/scripts/%5Bproc%2Cscript2674%5D.cs2)

Third-party data notices are included in `src/main/resources/THIRD_PARTY_NOTICES.txt` and the catalog source.
