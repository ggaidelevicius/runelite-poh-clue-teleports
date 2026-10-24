package com.clueteleports;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/** Observes loaded fixtures in a positively identified, player-owned house. */
public final class HouseTransportReader
{
	private static final Map<Integer, String> PORTALS = portalIds();
	private static final Pattern KEY_PREFIX = Pattern.compile("^[^:]+:\\s*");
	private static final Pattern COLORED_KEY_PREFIX = Pattern.compile(
		"^<col=[0-9a-fA-F]+>[^<]*(?::</col>|</col>\\s*:)\\s*");
	private static final Set<String> GLORY = Set.of("glory-edgeville", "glory-karamja",
		"glory-draynor-village", "glory-al-kharid");
	private static final Set<Integer> DIGSITE_OBJECTS = Set.of(ObjectID.POH_AMULET_DIGSITE,
		ObjectID.POH_AMULET_DIG_DIGSITE, ObjectID.POH_AMULET_DIG_FOSSIL, ObjectID.POH_AMULET_DIG_LITHKREN);
	private static final Set<Integer> XERIC_OBJECTS = Set.of(ObjectID.POH_AMULET_XERIC,
		ObjectID.POH_AMULET_XERIC_LOOKOUT, ObjectID.POH_AMULET_XERIC_GLADE,
		ObjectID.POH_AMULET_XERIC_INFERNO, ObjectID.POH_AMULET_XERIC_HEART, ObjectID.POH_AMULET_XERIC_HONOUR);
	private static final Map<String, String> DIGSITE_LABELS = Map.of(
		"digsite", "digsite-digsite", "fossil island", "digsite-fossil-island", "lithkren", "digsite-lithkren");
	// The in-game menu captured in issue #1 uses these five district/location
	// names. Its numbered order matches the canonical arrivals in the pinned
	// Shortest Path teleportation_items.tsv, including their existing coordinates.
	// https://github.com/ggaidelevicius/runelite-poh-clue-teleports/issues/1
	// https://github.com/Skretzo/shortest-path/blob/50f30435f37d1d5c5e2af790925fa7aa70b7840d/src/main/resources/transports/teleportation_items.tsv
	private static final Map<String, String> XERIC_LABELS = Map.of(
		"xeric's lookout", "xeric-lookout", "shayzien", "xeric-lookout",
		"xeric's glade", "xeric-glade", "hosidius", "xeric-glade",
		"xeric's inferno", "xeric-inferno", "lovakengj", "xeric-inferno",
		"xeric's heart", "xeric-heart", "kourend castle", "xeric-heart",
		"xeric's honour", "xeric-honour", "chambers of xeric", "xeric-honour");

	private HouseTransportReader()
	{
	}

	/** Invoke on the client thread, only after independently establishing house ownership. */
	public static Map<TeleportSource, Set<String>> read(Client client)
	{
		return readSnapshot(client).sources;
	}

	/**
	 * Unseen rooms do not prove absence. The caller reconciles portal and cape
	 * fixtures by position, replacing old entries only for covered, loaded tiles.
	 * POH scenes are not necessarily instances. WorldView base coordinates and
	 * tile indices provide stable keys without relying on instance conversion.
	 */
	public static Observation readSnapshot(Client client)
	{
		Map<TeleportSource, Set<String>> sources = new EnumMap<>(TeleportSource.class);
		Map<String, Set<String>> portals = new LinkedHashMap<>();
		Map<String, Set<String>> capes = new LinkedHashMap<>();
		Map<String, Set<TeleportSource>> installed = new LinkedHashMap<>();
		Map<String, Set<Integer>> installedIds = new LinkedHashMap<>();
		Map<TeleportSource, Set<Integer>> objectIds = new EnumMap<>(TeleportSource.class);
		Set<String> covered = new LinkedHashSet<>();
		WorldView world = client == null ? null : client.getTopLevelWorldView();
		Scene scene = world == null ? null : world.getScene();
		Tile[][][] tiles = scene == null ? null : scene.getTiles();
		if (tiles == null)
		{
			return new Observation(sources, portals, capes, covered, installed, objectIds, installedIds);
		}
		Set<TileObject> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		Set<Integer> incomplete = new LinkedHashSet<>();
		Map<Integer, ObjectComposition> definitions = new HashMap<>();
		boolean digsiteSeen = false;
		boolean xericSeen = false;
		for (int plane = 0; plane < tiles.length; plane++)
		{
			Tile[][] level = tiles[plane];
			if (level == null)
			{
				continue;
			}
			for (int x = 0; x < level.length; x++)
			{
				Tile[] column = level[x];
				if (column == null)
				{
					continue;
				}
				for (int y = 0; y < column.length; y++)
				{
					Tile tile = column[y];
					if (tile == null)
					{
						continue;
					}
					String tileKey = key(world, plane, x, y);
					boolean complete = true;
					Set<TileObject> objects = Collections.newSetFromMap(new IdentityHashMap<>());
					Collections.addAll(objects, tile.getWallObject(), tile.getDecorativeObject());
					GameObject[] gameObjects = tile.getGameObjects();
					if (gameObjects != null)
					{
						Collections.addAll(objects, gameObjects);
					}
					for (TileObject object : objects)
					{
						if (object == null)
						{
							continue;
						}
						int rawId = object.getId();
						if (!definitions.containsKey(rawId) && !incomplete.contains(rawId))
						{
							ObjectComposition definition = client.getObjectDefinition(rawId);
							if (definition == null)
							{
								incomplete.add(rawId);
							}
							else
							{
								definitions.put(rawId, definition.getImpostorIds() == null
									? definition : definition.getImpostor());
							}
						}
						if (incomplete.contains(rawId))
						{
							complete = false;
							continue;
						}
						ObjectComposition live = definitions.get(rawId);
						if (live == null)
						{
							// A not-yet-resolved multiloc cannot prove removal.
							complete = false;
							continue;
						}
						if (!visited.add(object))
						{
							continue;
						}
						int id = live.getId();
						String fixtureKey = tileKey;
						if (object instanceof GameObject)
						{
							Point origin = ((GameObject) object).getSceneMinLocation();
							if (origin != null)
							{
								fixtureKey = key(world, plane, origin.getX(), origin.getY());
							}
						}
						Set<TeleportSource> facilities = EnumSet.noneOf(TeleportSource.class);
						facilities.addAll(interfaceSources(id));
						if (PORTALS.containsKey(id))
						{
							Set<String> available = portalDestinations(client, live);
							putFixture(portals, fixtureKey, available);
							add(sources, TeleportSource.PORTAL_CHAMBER, available);
							facilities.add(TeleportSource.PORTAL_CHAMBER);
						}
						Optional<Set<String>> cape = CapeReader.read(client, id);
						if (cape.isPresent())
						{
							putFixture(capes, fixtureKey, cape.get());
							add(sources, TeleportSource.CAPE_HANGER, cape.get());
							facilities.add(TeleportSource.CAPE_HANGER);
						}
						if (id == ObjectID.POH_WILDERNESS_OBELISK)
						{
							add(sources, TeleportSource.WILDERNESS_OBELISK, ObeliskReader.read(client, true));
							facilities.add(TeleportSource.WILDERNESS_OBELISK);
						}
						else if (id == ObjectID.POH_TROPHY_AMULETOFGLORY_4)
						{
							add(sources, TeleportSource.MOUNTED_GLORY, GLORY);
							facilities.add(TeleportSource.MOUNTED_GLORY);
						}
						else if (id == ObjectID.POH_TROPHY_MYTHICAL_CAPE)
						{
							add(sources, TeleportSource.MOUNTED_MYTHICAL_CAPE, Set.of("mythical-myths-guild"));
							facilities.add(TeleportSource.MOUNTED_MYTHICAL_CAPE);
						}
						else if (id == ObjectID.POH_EXIT_PORTAL || id == ObjectID.POH_EXIT_PORTAL_WILDERNESS)
						{
							houseExit(client).ifPresent(exit -> add(sources, TeleportSource.HOUSE_EXIT, Set.of(exit)));
							facilities.add(TeleportSource.HOUSE_EXIT);
						}
						if (!facilities.isEmpty())
						{
							installed.computeIfAbsent(fixtureKey, ignored -> EnumSet.noneOf(TeleportSource.class))
								.addAll(facilities);
							installedIds.computeIfAbsent(fixtureKey, ignored -> new LinkedHashSet<>()).add(id);
							for (TeleportSource facility : facilities)
							{
								objectIds.computeIfAbsent(facility, ignored -> new LinkedHashSet<>()).add(id);
							}
						}
						digsiteSeen |= DIGSITE_OBJECTS.contains(id);
						xericSeen |= XERIC_OBJECTS.contains(id);
					}
					if (complete)
					{
						covered.add(tileKey);
					}
				}
			}
		}
		// Fossil Island activation and the Ancient Tablet unlock are server-only
		// in the exposed native vars. A configured left click is not proof of an
		// unlock, and quest completion is not activation. Only complete native
		// destination menus may replace these sources; a closed menu leaves cache
		// intact. Generic menu scripts are [clientscript,menu],
		// [proc,menu_createentry], and [proc,menu_new_createentry] in runelite/cs2-scripts.
		if (digsiteSeen)
		{
			readMountedMenu(client, DIGSITE_LABELS).ifPresent(ids ->
				add(sources, TeleportSource.MOUNTED_DIGSITE_PENDANT, ids));
		}
		if (xericSeen)
		{
			readMountedMenu(client, XERIC_LABELS).ifPresent(ids ->
				add(sources, TeleportSource.MOUNTED_XERICS_TALISMAN, ids));
		}
		return new Observation(sources, portals, capes, covered, installed, objectIds, installedIds);
	}

	/**
	 * Built facilities with a destination interface, identified by the live
	 * native gameval ID. The combined spirit tree/fairy ring supplies both.
	 * Decorative skins retain the same interfaces; unbuilt hotspots do not.
	 * This shared classification also lets scene guidance identify the exact
	 * installed object without maintaining a separate overlay ID list.
	 */
	public static Set<TeleportSource> interfaceSources(int transformedObjectId)
	{
		switch (transformedObjectId)
		{
			case ObjectID.POH_FAIRY_RING:
				return Set.of(TeleportSource.FAIRY_RING);
			case ObjectID.POH_SPIRIT_TREE:
			case ObjectID.XMAS20_POH_SPIRIT_TREE:
			case ObjectID.LEAGUE_5_POH_SPIRIT_TREE:
				return Set.of(TeleportSource.SPIRIT_TREE);
			case ObjectID.POH_SPIRIT_RING:
			case ObjectID.XMAS20_POH_SPIRIT_RING:
			case ObjectID.LEAGUE_5_POH_SPIRIT_RING:
				return Set.of(TeleportSource.FAIRY_RING, TeleportSource.SPIRIT_TREE);
			case ObjectID.POH_TELENEXUS_1:
			case ObjectID.POH_TELENEXUS_2_MIDDLE:
			case ObjectID.POH_TELENEXUS_2_SIDE:
			case ObjectID.POH_TELENEXUS_2_CORNER:
			case ObjectID.POH_TELENEXUS_3:
			case ObjectID.POH_NEXUS_PORTAL_1:
			case ObjectID.POH_NEXUS_PORTAL_2:
			case ObjectID.POH_NEXUS_PORTAL_3:
			case ObjectID.POH_NEXUS_PORTAL_LEAGUE_5:
				return Set.of(TeleportSource.PORTAL_NEXUS);
			case ObjectID.POH_JEWELLERY_BOX_1:
			case ObjectID.POH_JEWELLERY_BOX_2:
			case ObjectID.POH_JEWELLERY_BOX_3:
			case ObjectID.POH_JEWELLERY_BOX_1_COLOSSEUM:
			case ObjectID.POH_JEWELLERY_BOX_2_COLOSSEUM:
			case ObjectID.POH_JEWELLERY_BOX_3_COLOSSEUM:
				return Set.of(TeleportSource.JEWELLERY_BOX);
			default:
				// Every native ID in this contiguous range is a built box with
				// its current left-click destination, across all three tiers.
				if (transformedObjectId >= ObjectID.POH_JEWELLERY_BOX_1_BASE
					&& transformedObjectId <= ObjectID.POH_JEWELLERY_BOX_3_KHARID)
				{
					return Set.of(TeleportSource.JEWELLERY_BOX);
				}
				if (DIGSITE_OBJECTS.contains(transformedObjectId))
				{
					return Set.of(TeleportSource.MOUNTED_DIGSITE_PENDANT);
				}
				if (XERIC_OBJECTS.contains(transformedObjectId))
				{
					return Set.of(TeleportSource.MOUNTED_XERICS_TALISMAN);
				}
				return Collections.emptySet();
		}
	}

	/** Resolves a known row after its caller removes keys/tags and normalizes case. */
	static String xericDestinationId(String normalizedLabel)
	{
		return normalizedLabel == null ? null : XERIC_LABELS.get(normalizedLabel);
	}

	private static Optional<Set<String>> readMountedMenu(Client client, Map<String, String> labels)
	{
		Optional<Set<String>> current = readMountedMenu(client.getWidget(InterfaceID.MenuNew.TEXT), labels);
		return current.isPresent() ? current : readMountedMenu(client.getWidget(InterfaceID.Menu.LJ_LAYER1), labels);
	}

	private static Optional<Set<String>> readMountedMenu(Widget container, Map<String, String> labels)
	{
		if (container == null || container.isHidden())
		{
			return Optional.empty();
		}
		Set<String> expected = new LinkedHashSet<>(labels.values());
		Widget[] rows = container.getDynamicChildren();
		if (rows == null || rows.length < expected.size())
		{
			return Optional.empty();
		}
		Set<String> listed = new LinkedHashSet<>();
		Set<String> available = new LinkedHashSet<>();
		for (Widget row : rows)
		{
			if (row == null)
			{
				return Optional.empty();
			}
			String raw = row.getText();
			if (raw == null || raw.trim().isEmpty())
			{
				if (row.isHidden())
				{
					continue;
				}
				return Optional.empty();
			}
			String markup = COLORED_KEY_PREFIX.matcher(raw.trim()).replaceFirst("");
			String label = KEY_PREFIX.matcher(Text.removeTags(markup).trim()).replaceFirst("")
				.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
			// Mounted teleport menus use "Nowhere" for their non-teleport cancel option.
			if ("cancel".equals(label) || "close".equals(label) || "nowhere".equals(label))
			{
				continue;
			}
			String id = labels.get(label);
			if (id == null || !listed.add(id))
			{
				return Optional.empty();
			}
			markup = markup.toLowerCase(Locale.ROOT);
			if (!row.isHidden() && !markup.contains("<col=") && !markup.contains("<str"))
			{
				available.add(id);
			}
		}
		return listed.equals(expected)
			? Optional.of(Collections.unmodifiableSet(available)) : Optional.empty();
	}

	private static String key(WorldView world, int plane, int x, int y)
	{
		return plane + ":" + (world.getBaseX() + x) + ":" + (world.getBaseY() + y);
	}

	private static void add(Map<TeleportSource, Set<String>> sources, TeleportSource source, Set<String> ids)
	{
		sources.computeIfAbsent(source, ignored -> new LinkedHashSet<>()).addAll(ids);
	}

	private static void putFixture(Map<String, Set<String>> fixtures, String key, Set<String> ids)
	{
		fixtures.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).addAll(ids);
	}

	private static Set<String> portalDestinations(Client client, ObjectComposition live)
	{
		String selected = PORTALS.get(live.getId());
		Set<String> available = new LinkedHashSet<>();
		if (selected.isEmpty())
		{
			return available;
		}
		if (portalUnlocked(client, selected))
		{
			available.add("portal-" + selected);
		}
		Set<String> alternatives;
		switch (selected)
		{
			case "varrock":
			case "grand-exchange":
				alternatives = Set.of("portal-varrock", "portal-grand-exchange");
				break;
			case "camelot":
			case "seers-village":
				alternatives = Set.of("portal-camelot", "portal-seers-village");
				break;
			case "watchtower":
			case "yanille":
				alternatives = Set.of("portal-watchtower", "portal-yanille");
				break;
			default:
				return available;
		}
		// A _1ST variant establishes the default arrival. Its live native
		// actions can additionally prove the other diary destination is usable.
		String[] actions = live.getActions();
		if (actions != null)
		{
			for (String action : actions)
			{
				TeleportCatalog.portalDestination(action)
					.filter(destination -> alternatives.contains(destination.getId()))
					.filter(destination -> portalUnlocked(client,
						destination.getId().substring("portal-".length())))
					.ifPresent(destination -> available.add(destination.getId()));
			}
		}
		return available;
	}

	private static boolean portalUnlocked(Client client, String destination)
	{
		switch (destination)
		{
			case "grand-exchange":
				return rewarded(client, VarbitID.VARROCK_MEDIUM_REWARD,
					VarbitID.VARROCK_HARD_REWARD, VarbitID.VARROCK_ELITE_REWARD);
			case "seers-village":
				return rewarded(client, VarbitID.KANDARIN_HARD_REWARD, VarbitID.KANDARIN_ELITE_REWARD);
			case "yanille":
				return rewarded(client, VarbitID.ARDOUGNE_HARD_REWARD, VarbitID.ARDOUGNE_ELITE_REWARD);
			default:
				return true;
		}
	}

	private static boolean rewarded(Client client, int... varbits)
	{
		for (int varbit : varbits)
		{
			if (client.getVarbitValue(varbit) == 1)
			{
				return true;
			}
		}
		return false;
	}

	private static Optional<String> houseExit(Client client)
	{
		// Native [clientscript,poh_board_explain_location] / [proc,poh_board_loc].
		// Values 7, 10, 11, and 12 are not fixed supported house arrivals.
		switch (client.getVarbitValue(VarbitID.POH_HOUSE_LOCATION))
		{
			case 1: return Optional.of("exit-rimmington");
			case 2: return Optional.of("exit-taverley");
			case 3: return Optional.of("exit-pollnivneach");
			case 4: return Optional.of("exit-rellekka");
			case 5: return Optional.of("exit-brimhaven");
			case 6: return Optional.of("exit-yanille");
			case 8: return Optional.of("exit-hosidius");
			case 9: return Optional.of("exit-prifddinas");
			case 13: return Optional.of("exit-aldarin");
			default: return Optional.empty();
		}
	}

	private static Map<Integer, String> portalIds()
	{
		// Native RuneLite 1.13.3 gameval ObjectID (including inherited ObjectID1).
		// _1ST variants select the actual native arrival, as also enumerated in:
		// https://github.com/Skretzo/shortest-path/blob/master/src/main/resources/transports/teleportation_portals_poh.tsv
		// Respawn and boat portals have variable arrivals and are known empty here.
		Map<Integer, String> ids = new HashMap<>();
		portals(ids, "varrock",
			ObjectID.POH_PORTAL_TEAK_VARROCK,
			ObjectID.POH_PORTAL_MAG_VARROCK,
			ObjectID.POH_PORTAL_MARBLE_VARROCK,
			ObjectID.POH_PORTAL_LEAGUE_5_VARROCK);
		portals(ids, "lumbridge",
			ObjectID.POH_PORTAL_TEAK_LUMBRIDGE,
			ObjectID.POH_PORTAL_MAG_LUMBRIDGE,
			ObjectID.POH_PORTAL_MARBLE_LUMBRIDGE,
			ObjectID.POH_PORTAL_LEAGUE_5_LUMBRIDGE);
		portals(ids, "falador",
			ObjectID.POH_PORTAL_TEAK_FALADOR,
			ObjectID.POH_PORTAL_MAG_FALADOR,
			ObjectID.POH_PORTAL_MARBLE_FALADOR,
			ObjectID.POH_PORTAL_LEAGUE_5_FALADOR);
		portals(ids, "camelot",
			ObjectID.POH_PORTAL_TEAK_CAMELOT,
			ObjectID.POH_PORTAL_MAG_CAMELOT,
			ObjectID.POH_PORTAL_MARBLE_CAMELOT,
			ObjectID.POH_PORTAL_LEAGUE_5_CAMELOT);
		portals(ids, "ardougne",
			ObjectID.POH_PORTAL_TEAK_ARDOUGNE,
			ObjectID.POH_PORTAL_MAG_ARDOUGNE,
			ObjectID.POH_PORTAL_MARBLE_ARDOUGNE,
			ObjectID.POH_PORTAL_LEAGUE_5_ARDOUGNE);
		portals(ids, "watchtower",
			ObjectID.POH_PORTAL_TEAK_YANILLE,
			ObjectID.POH_PORTAL_MAG_YANILLE,
			ObjectID.POH_PORTAL_MARBLE_YANILLE,
			ObjectID.POH_PORTAL_LEAGUE_5_YANILLE);
		portals(ids, "",
			ObjectID.POH_PORTAL_TEAK_EMPTY,
			ObjectID.POH_PORTAL_MAG_EMPTY,
			ObjectID.POH_PORTAL_MARBLE_EMPTY,
			ObjectID.POH_PORTAL_LEAGUE_5_EMPTY);
		portals(ids, "kharyrll",
			ObjectID.POH_PORTAL_TEAK_KHARYRLL,
			ObjectID.POH_PORTAL_MAG_KHARYRLL,
			ObjectID.POH_PORTAL_MARBLE_KHARYRLL,
			ObjectID.POH_PORTAL_LEAGUE_5_KHARYRLL);
		portals(ids, "lunar-isle",
			ObjectID.POH_PORTAL_TEAK_LUNARISLE,
			ObjectID.POH_PORTAL_MAG_LUNARISLE,
			ObjectID.POH_PORTAL_MARBLE_LUNARISLE,
			ObjectID.POH_PORTAL_LEAGUE_5_LUNARISLE);
		portals(ids, "senntisten",
			ObjectID.POH_PORTAL_TEAK_SENNTISTEN,
			ObjectID.POH_PORTAL_MAG_SENNTISTEN,
			ObjectID.POH_PORTAL_MARBLE_SENNTISTEN,
			ObjectID.POH_PORTAL_LEAGUE_5_SENNTISTEN);
		portals(ids, "annakarl",
			ObjectID.POH_PORTAL_TEAK_ANNAKARL,
			ObjectID.POH_PORTAL_MAG_ANNAKARL,
			ObjectID.POH_PORTAL_MARBLE_ANNAKARL,
			ObjectID.POH_PORTAL_LEAGUE_5_ANNAKARL);
		portals(ids, "waterbirth-island",
			ObjectID.POH_PORTAL_TEAK_WATERBIRTH,
			ObjectID.POH_PORTAL_MAG_WATERBIRTH,
			ObjectID.POH_PORTAL_MARBLE_WATERBIRTH,
			ObjectID.POH_PORTAL_LEAGUE_5_WATERBIRTH);
		portals(ids, "fishing-guild",
			ObjectID.POH_PORTAL_TEAK_FISHINGGUILD,
			ObjectID.POH_PORTAL_MAG_FISHINGGUILD,
			ObjectID.POH_PORTAL_MARBLE_FISHINGGUILD,
			ObjectID.POH_PORTAL_LEAGUE_5_FISHINGGUILD);
		portals(ids, "marim",
			ObjectID.POH_PORTAL_TEAK_MARIM,
			ObjectID.POH_PORTAL_MAG_MARIM,
			ObjectID.POH_PORTAL_MARBLE_MARIM,
			ObjectID.POH_PORTAL_LEAGUE_5_MARIM);
		portals(ids, "kourend",
			ObjectID.POH_PORTAL_TEAK_KOUREND,
			ObjectID.POH_PORTAL_MAG_KOUREND,
			ObjectID.POH_PORTAL_MARBLE_KOUREND,
			ObjectID.POH_PORTAL_LEAGUE_5_KOUREND);
		portals(ids, "varrock",
			ObjectID.POH_PORTAL_TEAK_VARROCK_VARROCK1ST,
			ObjectID.POH_PORTAL_MAHOGANY_VARROCK_VARROCK1ST,
			ObjectID.POH_PORTAL_MARBLE_VARROCK_VARROCK1ST,
			ObjectID.POH_PORTAL_LEAGUE_5_VARROCK_VARROCK1ST);
		portals(ids, "grand-exchange",
			ObjectID.POH_PORTAL_TEAK_VARROCK_GE1ST,
			ObjectID.POH_PORTAL_MAHOGANY_VARROCK_GE1ST,
			ObjectID.POH_PORTAL_MARBLE_VARROCK_GE1ST,
			ObjectID.POH_PORTAL_LEAGUE_5_VARROCK_GE1ST);
		portals(ids, "camelot",
			ObjectID.POH_PORTAL_TEAK_CAMELOT_CAMELOT1ST,
			ObjectID.POH_PORTAL_MAHOGANY_CAMELOT_CAMELOT1ST,
			ObjectID.POH_PORTAL_MARBLE_CAMELOT_CAMELOT1ST,
			ObjectID.POH_PORTAL_LEAGUE_5_CAMELOT_CAMELOT1ST);
		portals(ids, "seers-village",
			ObjectID.POH_PORTAL_TEAK_CAMELOT_SEERS1ST,
			ObjectID.POH_PORTAL_MAHOGANY_CAMELOT_SEERS1ST,
			ObjectID.POH_PORTAL_MARBLE_CAMELOT_SEERS1ST,
			ObjectID.POH_PORTAL_LEAGUE_5_CAMELOT_SEERS1ST);
		portals(ids, "watchtower",
			ObjectID.POH_PORTAL_TEAK_YANILLE_WATCHTOWER1ST,
			ObjectID.POH_PORTAL_MAHOGANY_YANILLE_WATCHTOWER1ST,
			ObjectID.POH_PORTAL_MARBLE_YANILLE_WATCHTOWER1ST,
			ObjectID.POH_PORTAL_LEAGUE_5_YANILLE_WATCHTOWER1ST);
		portals(ids, "yanille",
			ObjectID.POH_PORTAL_TEAK_YANILLE_YANILLE1ST,
			ObjectID.POH_PORTAL_MAHOGANY_YANILLE_YANILLE1ST,
			ObjectID.POH_PORTAL_MARBLE_YANILLE_YANILLE1ST,
			ObjectID.POH_PORTAL_LEAGUE_5_YANILLE_YANILLE1ST);
		portals(ids, "troll-stronghold",
			ObjectID.POH_PORTAL_TEAK_STRONGHOLD,
			ObjectID.POH_PORTAL_MAG_STRONGHOLD,
			ObjectID.POH_PORTAL_MARBLE_STRONGHOLD,
			ObjectID.POH_PORTAL_LEAGUE_5_STRONGHOLD);
		portals(ids, "catherby",
			ObjectID.POH_PORTAL_TEAK_CATHERBY,
			ObjectID.POH_PORTAL_MAG_CATHERBY,
			ObjectID.POH_PORTAL_MARBLE_CATHERBY,
			ObjectID.POH_PORTAL_LEAGUE_5_CATHERBY);
		portals(ids, "ghorrock",
			ObjectID.POH_PORTAL_TEAK_GHORROCK,
			ObjectID.POH_PORTAL_MAG_GHORROCK,
			ObjectID.POH_PORTAL_MARBLE_GHORROCK,
			ObjectID.POH_PORTAL_LEAGUE_5_GHORROCK);
		portals(ids, "carrallanger",
			ObjectID.POH_PORTAL_TEAK_CARRALLANGAR,
			ObjectID.POH_PORTAL_MAG_CARRALLANGAR,
			ObjectID.POH_PORTAL_MARBLE_CARRALLANGAR,
			ObjectID.POH_PORTAL_LEAGUE_5_CARRALLANGAR);
		portals(ids, "weiss",
			ObjectID.POH_PORTAL_TEAK_WEISS,
			ObjectID.POH_PORTAL_MAG_WEISS,
			ObjectID.POH_PORTAL_MARBLE_WEISS,
			ObjectID.POH_PORTAL_LEAGUE_5_WEISS);
		portals(ids, "draynor-manor",
			ObjectID.POH_PORTAL_TEAK_DRAYNOR_MANOR,
			ObjectID.POH_PORTAL_MAG_DRAYNOR_MANOR,
			ObjectID.POH_PORTAL_MARBLE_DRAYNOR_MANOR,
			ObjectID.POH_PORTAL_LEAGUE_5_DRAYNOR_MANOR);
		portals(ids, "battlefront",
			ObjectID.POH_PORTAL_TEAK_BATTLEFRONT,
			ObjectID.POH_PORTAL_MAG_BATTLEFRONT,
			ObjectID.POH_PORTAL_MARBLE_BATTLEFRONT,
			ObjectID.POH_PORTAL_LEAGUE_5_BATTLEFRONT);
		portals(ids, "mind-altar",
			ObjectID.POH_PORTAL_TEAK_MIND_ALTAR,
			ObjectID.POH_PORTAL_MAG_MIND_ALTAR,
			ObjectID.POH_PORTAL_MARBLE_MIND_ALTAR,
			ObjectID.POH_PORTAL_LEAGUE_5_MIND_ALTAR);
		portals(ids, "salve-graveyard",
			ObjectID.POH_PORTAL_TEAK_SALVE_GRAVEYARD,
			ObjectID.POH_PORTAL_MAG_SALVE_GRAVEYARD,
			ObjectID.POH_PORTAL_MARBLE_SALVE_GRAVEYARD,
			ObjectID.POH_PORTAL_LEAGUE_5_SALVE_GRAVEYARD);
		portals(ids, "fenkenstrains-castle",
			ObjectID.POH_PORTAL_TEAK_FENKENSTRAIN,
			ObjectID.POH_PORTAL_MAG_FENKENSTRAIN,
			ObjectID.POH_PORTAL_MARBLE_FENKENSTRAIN,
			ObjectID.POH_PORTAL_LEAGUE_5_FENKENSTRAIN);
		portals(ids, "west-ardougne",
			ObjectID.POH_PORTAL_TEAK_WEST_ARDOUGNE,
			ObjectID.POH_PORTAL_MAG_WEST_ARDOUGNE,
			ObjectID.POH_PORTAL_MARBLE_WEST_ARDOUGNE,
			ObjectID.POH_PORTAL_LEAGUE_5_WEST_ARDOUGNE);
		portals(ids, "harmony-island",
			ObjectID.POH_PORTAL_TEAK_HARMONY_ISLAND,
			ObjectID.POH_PORTAL_MAG_HARMONY_ISLAND,
			ObjectID.POH_PORTAL_MARBLE_HARMONY_ISLAND,
			ObjectID.POH_PORTAL_LEAGUE_5_HARMONY_ISLAND);
		portals(ids, "cemetery",
			ObjectID.POH_PORTAL_TEAK_CEMETERY,
			ObjectID.POH_PORTAL_MAG_CEMETERY,
			ObjectID.POH_PORTAL_MARBLE_CEMETERY,
			ObjectID.POH_PORTAL_LEAGUE_5_CEMETERY);
		portals(ids, "barrows",
			ObjectID.POH_PORTAL_TEAK_BARROWS,
			ObjectID.POH_PORTAL_MAG_BARROWS,
			ObjectID.POH_PORTAL_MARBLE_BARROWS,
			ObjectID.POH_PORTAL_LEAGUE_5_BARROWS);
		portals(ids, "ape-atoll-dungeon",
			ObjectID.POH_PORTAL_TEAK_APE_ATOLL,
			ObjectID.POH_PORTAL_MAG_APE_ATOLL,
			ObjectID.POH_PORTAL_MARBLE_APE_ATOLL,
			ObjectID.POH_PORTAL_LEAGUE_5_APE_ATOLL);
		portals(ids, "arceuus-library",
			ObjectID.POH_PORTAL_TEAK_ARCEUUS_LIBRARY,
			ObjectID.POH_PORTAL_MAG_ARCEUUS_LIBRARY,
			ObjectID.POH_PORTAL_MARBLE_ARCEUUS_LIBRARY,
			ObjectID.POH_PORTAL_LEAGUE_5_ARCEUUS_LIBRARY);
		portals(ids, "civitas-illa-fortis",
			ObjectID.POH_PORTAL_TEAK_FORTIS,
			ObjectID.POH_PORTAL_MAG_FORTIS,
			ObjectID.POH_PORTAL_MARBLE_FORTIS,
			ObjectID.POH_PORTAL_LEAGUE_5_FORTIS);
		portals(ids, "trollheim",
			ObjectID.POH_PORTAL_LEAGUE_5_TROLLHEIM,
			ObjectID.POH_PORTAL_TEAK_TROLLHEIM,
			ObjectID.POH_PORTAL_MAG_TROLLHEIM,
			ObjectID.POH_PORTAL_MARBLE_TROLLHEIM);
		portals(ids, "paddewwa",
			ObjectID.POH_PORTAL_LEAGUE_5_PADDEWWA,
			ObjectID.POH_PORTAL_TEAK_PADDEWWA,
			ObjectID.POH_PORTAL_MAG_PADDEWWA,
			ObjectID.POH_PORTAL_MARBLE_PADDEWWA);
		portals(ids, "lassar",
			ObjectID.POH_PORTAL_LEAGUE_5_LASSAR,
			ObjectID.POH_PORTAL_TEAK_LASSAR,
			ObjectID.POH_PORTAL_MAG_LASSAR,
			ObjectID.POH_PORTAL_MARBLE_LASSAR);
		portals(ids, "dareeyak",
			ObjectID.POH_PORTAL_LEAGUE_5_DAREEYAK,
			ObjectID.POH_PORTAL_TEAK_DAREEYAK,
			ObjectID.POH_PORTAL_MAG_DAREEYAK,
			ObjectID.POH_PORTAL_MARBLE_DAREEYAK);
		portals(ids, "ourania",
			ObjectID.POH_PORTAL_LEAGUE_5_OURANIA,
			ObjectID.POH_PORTAL_TEAK_OURANIA,
			ObjectID.POH_PORTAL_MAG_OURANIA,
			ObjectID.POH_PORTAL_MARBLE_OURANIA);
		portals(ids, "barbarian-outpost",
			ObjectID.POH_PORTAL_LEAGUE_5_BARBARIAN,
			ObjectID.POH_PORTAL_TEAK_BARBARIAN,
			ObjectID.POH_PORTAL_MAG_BARBARIAN,
			ObjectID.POH_PORTAL_MARBLE_BARBARIAN);
		portals(ids, "port-khazard",
			ObjectID.POH_PORTAL_LEAGUE_5_KHAZARD,
			ObjectID.POH_PORTAL_TEAK_KHAZARD,
			ObjectID.POH_PORTAL_MAG_KHAZARD,
			ObjectID.POH_PORTAL_MARBLE_KHAZARD);
		portals(ids, "ice-plateau",
			ObjectID.POH_PORTAL_LEAGUE_5_ICEPLATEAU,
			ObjectID.POH_PORTAL_TEAK_ICEPLATEAU,
			ObjectID.POH_PORTAL_MAG_ICEPLATEAU,
			ObjectID.POH_PORTAL_MARBLE_ICEPLATEAU);
		portals(ids, "",
			ObjectID.POH_PORTAL_LEAGUE_5_RESPAWN,
			ObjectID.POH_PORTAL_TEAK_RESPAWN,
			ObjectID.POH_PORTAL_MAG_RESPAWN,
			ObjectID.POH_PORTAL_MARBLE_RESPAWN);
		portals(ids, "",
			ObjectID.POH_PORTAL_LEAGUE_5_BOAT,
			ObjectID.POH_PORTAL_TEAK_BOAT,
			ObjectID.POH_PORTAL_MAG_BOAT,
			ObjectID.POH_PORTAL_MARBLE_BOAT);
		return Collections.unmodifiableMap(ids);
	}

	private static void portals(Map<Integer, String> ids, String suffix, int... objectIds)
	{
		for (int id : objectIds)
		{
			ids.put(id, suffix);
		}
	}

	static final class Observation
	{
		final Map<TeleportSource, Set<String>> sources;
		final Map<String, Set<String>> portalFixtures;
		final Map<String, Set<String>> capeFixtures;
		final Set<String> coveredTiles;
		final Set<TeleportSource> installedSources;
		final Map<String, Set<TeleportSource>> installedFixtures;
		final Map<TeleportSource, Set<Integer>> fixtureObjectIds;
		final Map<String, Set<Integer>> installedFixtureIds;
		/**
		 * A nonblank loaded-scene observation, not proof of the entire layout.
		 * Never infer that an unlisted source is unbuilt. Persist positive
		 * positions and reconcile only covered tiles as rooms load or change.
		 */
		final boolean sceneReady;

		private Observation(Map<TeleportSource, Set<String>> sources,
			Map<String, Set<String>> portals, Map<String, Set<String>> capes, Set<String> covered,
			Map<String, Set<TeleportSource>> installed, Map<TeleportSource, Set<Integer>> objectIds,
			Map<String, Set<Integer>> installedIds)
		{
			this.sources = freeze(sources);
			portalFixtures = freeze(portals);
			capeFixtures = freeze(capes);
			coveredTiles = Collections.unmodifiableSet(new LinkedHashSet<>(covered));
			installedFixtures = freeze(installed);
			fixtureObjectIds = freeze(objectIds);
			installedFixtureIds = freeze(installedIds);
			Set<TeleportSource> facilities = EnumSet.noneOf(TeleportSource.class);
			installed.values().forEach(facilities::addAll);
			installedSources = Collections.unmodifiableSet(facilities);
			sceneReady = !coveredTiles.isEmpty() && !installedSources.isEmpty();
		}

		private static <K, V> Map<K, Set<V>> freeze(Map<K, Set<V>> source)
		{
			Map<K, Set<V>> frozen = new LinkedHashMap<>();
			source.forEach((key, values) -> frozen.put(key,
				Collections.unmodifiableSet(new LinkedHashSet<>(values))));
			return Collections.unmodifiableMap(frozen);
		}
	}
}
