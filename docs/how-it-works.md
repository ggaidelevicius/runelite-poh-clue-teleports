# How it works

Detailed behaviour, edge cases, and limitations. For a quick overview, see the [README](../README.md).

## House setup

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
| Mounted Xeric's talisman | Open its teleport menu. | The account's unlocked talisman destinations, whether the menu shows talisman names or place names. |
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

The cache represents the last observed menu or fixture. Removing an entire facility or room, or rebuilding the house layout, can leave old fixture arrivals cached because an unloaded room does not establish that something was removed. After those changes, right-click the overlay and choose **Forget learned sources**, or enter the local command **`::pohcluereset`**, then re-enter your house, open the detected travel menus, and walk through the other travel rooms. The command also works with the overlay hidden. Both actions reset only the current character's learned sources and house observations, preserve display and chat settings, and confirm the reset in local chat. The same actions can reset stale data after a game update. Renaming the plugin preserves the original per-character caches.

## Display and chat settings

**Overlay mode** offers three modes:

- **Full** (default): the current panel with the facility, destination, estimated running time, and walking tiles.
- **Compact**: the facility and destination, including the fairy ring code where applicable, without the heading or route statistics. Long names wrap within the usual maximum width. Route statuses use a single message.
- **Hidden**: no recommendation or route-status panel. Learning, route calculation, destination highlights, and automatic menu scrolling continue.

Full and Compact use white for the facility and light grey for the destination.

Initial house setup and the learning checklist always use the full panel, regardless of the selected mode. This includes setup after forgetting learned sources or discovering a facility that needs learning. Once setup is complete, the selected mode applies automatically. Setup guidance can still appear in your own house without an active clue.

**Chat messages** is off by default and works independently of the overlay mode. When enabled, a local message such as `[POH Clue Teleports] Jewellery box: Fishing Guild` announces a clue step's first available recommendation and any change to its recommended facility or destination. New steps can announce the same teleport again. Reopening menus, ordinary loading, world hops, and recalculating the same recommendation do not repeat it. Setup and route-progress messages are not announced. Enabling chat during a clue announces the current recommendation once it is available.

**Minimap arrow** is on by default and works with any panel mode. After you leave your house and arrive at the recommended landing, a pulsing arrow marks where to click for the next stretch of the planned walk. The arrow pauses at manual passages such as doors and ladders, and it resumes when you reach the next walking stretch. It hides when you reach the task, move away from the mapped route, or lose access to a remaining passage.

## Routing and clue coverage

The recommendation uses a collision-aware route from each available POH arrival to the clue. It follows mapped walkable tiles, observes walls and diagonal corner restrictions, and uses directed physical passages for stairs, ladders, dungeon entrances, doors, and agility shortcuts. It does not project underground coordinates onto the surface or ignore an upstairs clue's floor.

The plugin bundles version-pinned Shortest Path collision and physical-passage data. It works without installing or configuring the Shortest Path plugin, makes no routing network requests, and does not alter another plugin's route. Only POH destinations can be recommended. Further item/spell teleports and external fairy ring/spirit tree networks are not part of the onward walking comparison.

Physical passage eligibility uses the character's current levels, quests, native unlock flags, and carried inventory/equipment. Agility uses the current boosted or drained level; Prayer level requirements use the real level, rather than current prayer points. Grapple shortcuts require a suitable equipped crossbow and a carried or equipped grapple. Banked items do not establish access. Unsupported or unexposed requirements exclude that passage. Explicit gated passages are also blocked for ordinary walking, so an open door in the static collision map cannot bypass a listed requirement.

A reverse Dijkstra search finds the minimum modeled route across all currently available arrivals and valid task sites. Ordinary movement costs one unit per tile; a passage costs twice its documented game-tick duration. This models continuous running at two tiles per tick, including listed passage delays. The overlay shows estimated running time and the actual number of ordinary walking tiles in the selected route. This estimate excludes the initial POH teleport animation, movement between house fixtures, run-energy depletion, shortcut failure chance, and boost expiry. Required consumables are checked against carried stock but are not deducted across successive passages.

Route searches run on a cancellable worker. Changing the clue, profile, available destinations, or set of usable physical passages invalidates the result. Inventory, equipment, level, quest, and unlock changes are checked against passage requirements. Changes that leave the usable passage set unchanged reuse the current search or completed result, so ordinary banking does not restart routing. Required-item quantity thresholds and shortcut eligibility still trigger a new search when crossed. Missing map data, inaccessible goals, exhausted work/time budgets, and loading errors show a route status; none fall back to geographic distance or a provisional winner.

Dig and coordinate clues require their exact mapped standing tile. Known NPC/object interactions can end at a nearby mapped interaction face; fixed challenge markers may use the nearest cardinal face within two tiles when the object itself is solid. Static collision data cannot establish every object orientation, temporary obstruction, instance layout, or unlisted server-side gate. Representative teleport landing tiles and task-site markers may also differ slightly from the exact game position. The result is a minimum for the bundled graph and current account snapshot, not a guarantee of exact elapsed time or complete game simulation. See [`src/main/resources/routes/README.md`](../src/main/resources/routes/README.md) for data provenance and update instructions.

The companion follows the clue already identified by RuneLite. Read the clue once with **Clue Scroll** enabled. The recommendation remains after closing the clue interface and disappears when the core plugin clears the clue or you log out. Supported types include coordinate, map, cryptic, anagram, cipher, emote, music, fairy ring, resolved hot/cold, Falo, and completed Charlie and Sherlock tasks.

Sherlock recommendations cover verified sites for runecrafting, named agility courses, Skullball, Lake Molch, tecu salamanders, black warlocks, red chinchompas, shore anglerfish, redwoods, Barrows tablet crafting, named Ardougne thefts, elf pickpocketing, both Dorgesh-Kaan tasks, Fiyr shades and cremation, named Slayer Tower tasks, dust devils, lizardman shamans, spiritual mages, and reanimating an abyssal demon at the Dark Altar. The router compares accessible alternatives, including both blood altars after Sins of the Father. Estimates end at the access point for Skullball, Woodcutting Guild redwoods, Dorgesh-Kaan lamp repair, Shade Catacombs, and God Wars Dungeon. Bring the required equipment for the task, such as a silver or gold shade key for Fiyr shades or gear matching the spiritual mage's faction.

The Frost Nagua and Earthen Nagua elite cryptics use the Twilight Temple and Tonali routes once their lift access is unlocked. Flexible tasks such as burning a log or smithing at any anvil have no fixed destination. Nickel mining and teleporting to a personally planted spirit tree are outside the current walking-route comparison. Completed challenges route back to Sherlock or Charlie.

Dynamic **Respawn** and **Teleport to Boat** portal targets, and Sailing cape teleports to owned boats, are excluded until their current arrival can be established reliably. The mounted Hunter cape's separately unlocked Hunter Guild teleport is currently excluded because its conversation unlock is not exposed in the available native state. Teleporting back inside the same house is not an onward clue destination.

## Other teleport menu plugins

Easy Teleports (listed under `easy-pharaoh-sceptre` in the Hub) can rename and recolor jewellery-box and mounted teleport options. Learning uses the original native destination data, including its locks, so custom names do not change what is available. Spirit-tree menu layouts and maps can also hide or rename rows without changing the learned network. Reopen the affected menu after enabling POH Clue Teleports to capture its native data.

Guidance highlights visible native controls and Teleport Maps buttons for spirit trees, mounted Xeric’s talismans, and POH wilderness obelisks. Other custom map buttons are not highlighted, and filtered or hidden controls stay hidden. See the [pinned-source compatibility audit](teleport-plugin-compatibility.md) for inspected plugins and verification limits.
