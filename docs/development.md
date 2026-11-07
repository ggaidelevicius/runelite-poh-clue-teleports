# Development

## Build and run

Install JDK 17, then run the finite headless checks from this project's directory:

```sh
./gradlew build
```

Start the development client yourself when ready:

```sh
./gradlew run
```

Source compiles to Java 11 bytecode, following the RuneLite plugin template. The macOS development task includes the native fullscreen adapter's required Java exports. The optional `gradle-local` helper uses a project-local JDK and Gradle cache when configured; `./gradle-local build --offline` works after dependencies have been cached.

Enable **POH Clue Teleports** in the development client, enter your own house, and open only the detected travel menus to learn their available destinations. Read a clue with **Clue Scroll** enabled. The ordinary JAR in `build/libs/` is a development build artifact. Source is available at [ggaidelevicius/runelite-poh-clue-teleports](https://github.com/ggaidelevicius/runelite-poh-clue-teleports). Updates use versioned source commits submitted to Plugin Hub, where RuneLite reviews and merges the commit pin.

## Manual verification

Automated tests do not establish live gameplay behavior. In a user-started development client, verify:

1. Enable **POH Clue Teleports**, enter your own POH through **Home** or **Teleport to House**, and wait for fixture discovery, and open each listed menu for at least two ticks. Confirm a house with only a jewellery box does not request a fairy ring, spirit tree, or nexus. Check a combined ring/tree, a newly built facility, a tier change, and a removed loaded fixture. Walk through distant rooms and confirm discoveries are added without clearing unloaded fixtures.
2. Add or remove a nexus destination, downgrade a jewellery box, or unlock or plant a spirit tree. Reopen the relevant menu and confirm its cached destinations change while the other sources stay intact.
3. Open a menu in another player's house. Confirm it does not change your saved availability. Close menus early or during loading and check that the previous snapshot remains.
4. Read a clue and close its interface. Confirm one recommendation remains. Change clue steps, drop the clue, log out, hop worlds, and switch characters; each character should keep its own cache.
5. Compare a clue on opposite sides of a wall or river, a dungeon clue, and an upstairs clue. Verify the chosen arrival follows a usable route. Repeat near an agility shortcut below its level, after a boost, and with any required diary/tool gate missing. Check the blood-rune challenge before and after completion, an unfinished hot/cold clue, and a three-step master clue.
6. With an obelisk built, compare accounts with and without the collected Wilderness Hard Diary reward. Only deterministic destination selection should enter the comparison. Check a mounted cape after its limited daily teleports have been used.
7. Disable and re-enable the plugin. Confirm the overlay cleans up and returns once, with no sidebar button. Re-enter the house before learning new menus.
8. With **Teleport Maps** enabled, open the spirit-tree, mounted Xeric, and POH obelisk maps in both classic and modern menu styles. Confirm learning completes and the recommended destination gets one outline on its clickable map button. Check an unavailable destination, a custom Xeric name, and a closed/reopened map. Other players’ houses must not update your cache or show guidance.
9. Change **Overlay mode** between Full, Compact, and Hidden. Confirm Full retains the current panel, Compact shows only the facility and destination, and Hidden retains menu highlights and scrolling. With Hidden selected, use **`::pohcluereset`** and confirm the full setup panel returns, only this character's cache resets, and Hidden resumes after learning completes. Repeat setup with Compact selected and with no active clue in your own house.
10. Enable **Chat messages**, including with Hidden selected. Confirm one local recommendation appears per clue step or changed teleport, with no repeats from menu reopening, loading, world hopping, or recalculating the same route. Check a new step that uses the same destination, enable chat during an active clue, and switch characters.
11. Take the recommended teleport from your house and check the pulsing waypoint in fixed and resizable layouts. Follow a route with a turn and a manual passage, then check that a different landing, a new clue, and a hidden minimap clear the arrow.
