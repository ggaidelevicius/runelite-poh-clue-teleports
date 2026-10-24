# Teleport menu plugin compatibility

This is a primary-source compatibility sweep of selected travel, menu, and POH
plugins from the [Plugin Hub manifest inventory at
29060a07f06659ad51ffa65f8aff44bd58b9ba02](https://github.com/runelite/plugin-hub/tree/29060a07f06659ad51ffa65f8aff44bd58b9ba02/plugins).
The links below pin the inspected source versions. This is not an audit of every
Hub plugin, a popularity ranking, or a claim of live integration testing.

## Availability and guidance rules

Availability follows the game's original input, not another plugin's displayed
names, colors, filtering, or map selection. Generic menu setup scripts 217 and
9142 provide native destination labels and disabled markup before presentation
plugins run. Captures are associated once with the resulting native widget rows
using the native initialization arguments, row shape, and object identity. Menu closure, a
profile change, or loss of confirmed own-house context invalidates the capture.
Only rows with that verified original input may ignore cosmetic hidden state.
When original input is missing, hidden rows cannot establish removals. Incomplete,
unknown, or changing menus also preserve the previous availability snapshot.
Known, visible native-format labels remain readable without a capture. The
original native lock markup still excludes a locked destination.

For jewellery boxes, the native destination callback (script 1690) retains the
original formatted destination and lock markup even when the visible label is
renamed. The reader validates that native callback and section before using its
text. Arbitrary custom text is never an unlock or a new catalog destination.
These compatibility paths do not add transport sources or change arrival points.

Guidance is stricter than availability: an outline needs a visible, enabled,
identifiable control. Captured original labels can identify a renamed native
row, but a hidden native list is not an invitation to unhide it or draw over a
replacement map. A dedicated read-only adapter supports Teleport Maps' spirit-tree,
mounted Xeric, and wilderness obelisk buttons. It requires a recognized map,
captured original destination data, and one visible matching Travel/Set control.
It never invokes the control or changes the other plugin's map. Other custom map
controls remain outside guidance. The main closest-teleport recommendation is
independent of whether the current view exposes a matching control.

## Inspected presentation changes

| Plugin and pinned source | Changes relevant to POH travel | Compatibility boundary |
| --- | --- | --- |
| [Easy Teleports](https://github.com/DapperMickie/easy-teleports/tree/2b18c2bfa57494185fdffbc93c9d91042a3b9cf5), Hub slug `easy-pharaoh-sceptre` | Rewrites jewellery-box section labels and colors from arbitrary configuration strings, and rewrites legacy mounted pendant/Xeric rows. | Use validated original jewellery callbacks and captured generic-menu input. Visible custom names and colors cannot create capabilities or remove native locks. |
| [Better Teleport Menu](https://github.com/abextm/better-teleport-menu/blob/f11546c7b0552e3efc4572bef7504ac34c16cfb1/src/main/java/abex/os/telemenu/BetterTeleportMenuPlugin.java) | Replaces colored shortcut prefixes with chords or no key, may add shadow markup, hides disabled legacy rows, and substitutes fixed nexus aliases with the original name in parentheses. Its background overlay resizes native widgets. | Normalize presentation keys and known aliases, retain original lock state, and use current widget bounds. Hidden first rows must not change menu-source classification. It does not account for Xeric district-name labels by itself. |
| [Spirit Tree Menu](https://github.com/tcpowell/spirit-tree-menu/blob/c1e7cf69b99b8541b3807fc41367ea7c6ed3e242/src/main/java/com/spirit/SpiritTreeMenuPlugin.java) | Reflows the legacy tree list into two columns, writes `(key) Location` or `Location (key)`, optionally removes keys, shortens `Battlefield of Khazard` to `B'field of Khazard` for large fonts, and may hide unavailable rows. | Preserve the original destination identity and native disabled markup. Reading only the displayed colon-prefix format fails here; geometry must come from the current row. |
| [Spirit Tree Atlas](https://github.com/Dewdul/spirit-tree-atlas/blob/a7925574c0513d4a6b8556184cb28bafbcc3bd1f/src/main/java/com/spirittreeatlas/TreeMenu.java) | In map mode, hides all native rows except a selected Travel row and moves that row into a small button, in both legacy and modern menus. Map mode is the default. | Cosmetic hiding must not reduce the saved network to the selected tree or an empty set. Use captured original inputs for availability; outline only the visible native Travel row when it matches. Custom map markers have no native-row adapter. |
| [Normal Ancient Teleports](https://github.com/zoltander/Normal-Ancient-Teleports/blob/8f1b6528f9ef7ef8cc781f953ab561304ef19ff2/src/main/java/com/normalancientteleports/NormalAncientTeleportsPlugin.java) | Renames eight Ancient nexus destinations to place names, including `Ghorrock` to `Frozen Waste`; nexus rewriting is enabled by default. | Nexus availability comes from installed slot/struct data. The catalog resolves the fixed place aliases, including `Frozen Waste`, to the existing canonical arrivals for row matching. |
| [Teleport Maps](https://github.com/MJHylkema/spirit-tree-map/blob/25382eeebed8d5b88abfb9710d4040f986687caf/src/main/java/com/mjhylkema/TeleportMaps/components/adventureLog/AdventureLogComposite.java), Hub slug `spirit-tree-map` | Replaces spirit-tree, Xeric, skills-necklace, and obelisk travel menus with maps. It hides the legacy list or modern content-scroll ancestor, then creates its own map controls. Spirit-tree, Xeric, and obelisk maps default to enabled. | Original generic-menu input establishes tree/Xeric availability while the native list is hidden. A dedicated adapter highlights one visible, enabled spirit-tree, mounted Xeric, or obelisk map button using its current bounds, retained native row identity, name, and Travel/Set action. Missing, ambiguous, locked, and unknown controls receive no outline. Obelisk fixture/diary availability remains independent of the map. Skills-necklace maps are item interfaces and do not add a POH source. |
| [Nexus Map](https://github.com/Antipixel/nexus-map/blob/f88465dd3f95a10ad9ae2225a60b448262f45a0f/src/main/java/net/antipixel/nexus/NexusMapPlugin.java) and [Zoom Out Nexus](https://github.com/Ingelberg/zoom-out-nexus/blob/8079f3c5fc1e5b9d4c5c97c7fe939c1960976dc5/src/main/java/com/zoomoutnexus/ZoomOutNexusPlugin.java) | Nexus Map hides the original list and builds map buttons with original, place, or combined names. Defaults to opening the map. Zoom Out Nexus adds overview icons to that custom map. | Installed slot/struct availability is unaffected. The native list is hidden, so native-row guidance is suppressed; custom map-button guidance is not established. |
| [Portal Nexus Search](https://github.com/wouterrutgers/portal-nexus-search/blob/65d5fef9b53224fff901492c73800620ab7d6345/src/main/java/com/portalnexussearch/PortalNexusSearchPlugin.java) | Filters native nexus rows by hiding and moving rows, changes scroll height, and restores them when search closes. | Filtering does not change installed destinations. Highlighting respects the currently visible rows; it must not clear or override the user's search to reveal another entry. |
| [Portal Nexus Teleport Hider](https://github.com/friw0w/portal-nexus-teleport-hider/blob/23308ceea369a52240493601d7135462e2a50466/src/main/java/com/portalnexusteleporthider/PortalNexusTeleportHiderPlugin.java) | Hides and blanks selected nexus text rows, removes their key listeners, compacts slots, and marks hidden entries with strike/gray text in edit mode. | User hiding is not an account unlock change: installed-slot availability is preserved. No outline is drawn for a hidden or marked native row. The plugin does not undo the user's hiding preference. |
| [Fairy Ring Organizer](https://github.com/YaBoiStery/Fairy-Ring-Organizer/blob/3b9859094f8340b6e7efe5d53dbdcf012d39d10e/src/main/java/com/fairyringorganizer/FairyRingNativeStateService.java) | Applies custom destination labels, colors, sorting, wrapping, and native travel-log row positions. | Fairy availability uses native permissions, quests, and regional access rather than display names. Guidance identifies the fixed native code widget, requires its existing `Use code` action, and uses live bounds. |
| [Fairy Ring Atlas](https://github.com/Dewdul/fairy-ring-atlas/blob/b8c51b57d2ca7cde12a87350e50f1a1291e00a62/src/main/java/com/fairyringatlas/TravelLogController.java) | Adds a map over the dials, keeps the confirm button, and can filter the log down to one selected destination while moving/coloring that row. | A filtered travel log is not an unlock list. Native account checks still establish availability; only a visible usable log entry can receive guidance. Custom map markers are not highlighted. |
| [Fairy Ring Map](https://github.com/jdkrupajk/fairy-ring-map/blob/438fee3b6f622f60e6f5eb4ba591a941b2cea044/src/main/java/com/fairyringmap/FairyRingMap.java) | Adds map widgets over the dials and, after selection, can hide every other travel-log row while retaining the selected native row and star. | The same account-state and visible-native-row boundaries apply. Existing cosmetic filtering is preserved. |

## Other inspected candidates

[Fairy Ring Favourites](https://github.com/h1pstr/fairy-ring-favourites/blob/36f914cfb5d0841a8e321f4e8bb67f70e9635d22/src/main/java/com/github/h1pstr/fairyringfavourites/FairyRingFavouritesPlugin.java),
[Menu Swapper Extended](https://github.com/raiyni/menu-swapper-extended/blob/9a5a9cc3278fd552b948bfc62c2ee1a461f6fde1/src/main/java/io/ryoung/menuswapperextended/MenuSwapperPlugin.java),
and [Max Cape Swap](https://github.com/KariKakkamaa/Max-Cape-Swap/blob/ac7cd7f739bb25414868792affd538621da28a8a/src/main/java/com.maxcapeswap/MaxCapeSwapPlugin.java)
change context-menu choices rather than the destination-list labels inspected
above. [POH Portal Labels](https://github.com/jcbmcn/poh-portal-labels/tree/fbe5191f97f73faad5e303d527f32d8c0cde0159)
and the inspected [Spirit Tree Makeover](https://github.com/flawson-rs/spirit-tree-makeover/tree/1a16eb1b0865bb7b1aa8ae4c709694d9a5418b68)
and [POH Cosmetic Transmogs](https://github.com/jtclowner-plugins/poh-cosmetic-transmogs/tree/756f0f1d8b01318e5de375dadbaef835b5d03567)
paths add labels or replace rendering, without replacing native destination
identities in the inspected paths. No additional label alias was inferred from
those plugins' names.

## Validation boundary

Regression fixtures cover original labels and known alternatives, native lock
preservation, duplicate and incomplete rejection, renamed jewellery callbacks,
cosmetically hidden captured rows, source-specific matching, and visible-row
highlight geometry. Teleport Maps fixtures cover both menu styles, deferred map
construction and root rebuilding, actionable map-button metadata, original locks,
custom names, and ambiguous or stale controls, without executing callbacks.
The `Frozen Waste` regression also asserts the original Ghorrock ID, source, and
landing point. These automated fixtures validate the
modeled inputs; they do not prove behavior with every plugin configuration or
multiple simultaneous plugins in a live client. A new presentation format must
fail closed until its original identity and lock semantics are established.
