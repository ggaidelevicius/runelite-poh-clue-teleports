package com.clueteleports;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HouseTransportReaderTest
{
	private final Fixture fixture = new Fixture();

	@Test
	public void missingSceneNeverInventsOrClearsFixtures()
	{
		assertTrue(HouseTransportReader.read(null).isEmpty());
		fixture.worldPresent = false;
		assertTrue(read().coveredTiles.isEmpty());
		fixture.worldPresent = true;
		fixture.scenePresent = false;
		assertTrue(read().sources.isEmpty());
		fixture.scenePresent = true;
		fixture.tiles = null;
		assertTrue(read().coveredTiles.isEmpty());
	}

	@Test
	public void onlyNonNullLoadedTilesProveCoverage()
	{
		fixture.tiles = new Tile[][][]{null, {null, {null, emptyTile()}}};

		HouseTransportReader.Observation observed = read();

		assertTrue(observed.sources.isEmpty());
		assertEquals(Set.of("1:1841:7041"), observed.coveredTiles);
	}

	@Test
	public void sceneScannerReadsGameWallAndDecorativeFixturesWithoutInstanceRequirement()
	{
		fixture.varbits.put(VarbitID.WILDERNESS_HARD_REWARD, 1);
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_WILDERNESS_OBELISK)},
			fixture.wall(ObjectID.POH_TROPHY_MYTHICAL_CAPE),
			fixture.decorative(ObjectID.POH_TROPHY_AMULETOFGLORY_4));

		Map<TeleportSource, Set<String>> sources = read().sources;

		assertEquals(6, sources.get(TeleportSource.WILDERNESS_OBELISK).size());
		assertEquals(Set.of("mythical-myths-guild"), sources.get(TeleportSource.MOUNTED_MYTHICAL_CAPE));
		assertEquals(Set.of("glory-edgeville", "glory-karamja", "glory-draynor-village", "glory-al-kharid"),
			sources.get(TeleportSource.MOUNTED_GLORY));
		assertFalse(sources.containsKey(TeleportSource.HOUSE_EXIT));
	}

	@Test
	public void transformsRawMultilocBeforeClassifyingMountedFixture()
	{
		int raw = 999001;
		fixture.definitions.put(raw, definition(raw, definition(ObjectID.POH_TROPHY_AMULETOFGLORY_4)));
		fixture.tiles[0][0][0] = tile(new GameObject[]{game(raw, null)}, null, null);

		assertEquals(4, read().sources.get(TeleportSource.MOUNTED_GLORY).size());
		assertEquals(1, fixture.definitionReads.get(raw).intValue());
	}

	@Test
	public void unloadedDefinitionsDoNotCoverTileOrFabricateFixture()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{game(ObjectID.POH_PORTAL_TEAK_TROLLHEIM, null)}, null, null);
		fixture.tiles[0][1][0] = emptyTile();

		HouseTransportReader.Observation observed = read();

		assertTrue(observed.sources.isEmpty());
		assertEquals(Set.of("0:1841:7040"), observed.coveredTiles);
	}

	@Test
	public void unresolvedMorphDoesNotCoverTileOrErasePreviouslyLearnedFixture()
	{
		fixture.definitions.put(ObjectID.POH_PORTAL_TEAK_VARROCK,
			definition(ObjectID.POH_PORTAL_TEAK_VARROCK, null));
		fixture.tiles[0][0][0] = tile(new GameObject[]{game(ObjectID.POH_PORTAL_TEAK_VARROCK, null)}, null, null);

		HouseTransportReader.Observation observed = read();

		assertTrue(observed.sources.isEmpty());
		assertTrue(observed.coveredTiles.isEmpty());
	}

	@Test
	public void transformedPortalUsesOnlySelectedArrivalAndRequiresCollectedReward()
	{
		fixture.definitions.put(ObjectID.POH_PORTAL_TEAK_VARROCK,
			definition(ObjectID.POH_PORTAL_TEAK_VARROCK, definition(ObjectID.POH_PORTAL_TEAK_VARROCK_GE1ST)));
		fixture.tiles[0][0][0] = tile(new GameObject[]{game(ObjectID.POH_PORTAL_TEAK_VARROCK, null)}, null, null);
		assertTrue(read().sources.get(TeleportSource.PORTAL_CHAMBER).isEmpty());
		fixture.varbits.put(VarbitID.VARROCK_MEDIUM_REWARD, 1);

		assertEquals(Set.of("portal-grand-exchange"), read().sources.get(TeleportSource.PORTAL_CHAMBER));
	}

	@Test
	public void diaryAlternateVariantsNeverAddUnselectedBaseDestination()
	{
		int[] ids = {ObjectID.POH_PORTAL_MAHOGANY_CAMELOT_SEERS1ST,
			ObjectID.POH_PORTAL_LEAGUE_5_YANILLE_YANILLE1ST};
		int[] rewards = {VarbitID.KANDARIN_ELITE_REWARD, VarbitID.ARDOUGNE_HARD_REWARD};
		String[] destinations = {"portal-seers-village", "portal-yanille"};
		for (int i = 0; i < ids.length; i++)
		{
			fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ids[i])}, null, null);
			fixture.varbits.clear();
			assertTrue(read().sources.get(TeleportSource.PORTAL_CHAMBER).isEmpty());
			fixture.varbits.put(rewards[i], 1);
			assertEquals(Set.of(destinations[i]), read().sources.get(TeleportSource.PORTAL_CHAMBER));
		}
	}

	@Test
	public void nativeCounterpartActionAndDiaryRewardAreBothRequired()
	{
		int id = ObjectID.POH_PORTAL_TEAK_VARROCK_VARROCK1ST;
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(id)}, null, null);
		fixture.varbits.put(VarbitID.VARROCK_MEDIUM_REWARD, 1);
		assertEquals(Set.of("portal-varrock"), read().sources.get(TeleportSource.PORTAL_CHAMBER));
		fixture.definitions.put(id, definition(id, false, null, "Varrock", "Grand Exchange", "Configure"));
		assertEquals(Set.of("portal-varrock", "portal-grand-exchange"),
			read().sources.get(TeleportSource.PORTAL_CHAMBER));
		fixture.varbits.clear();
		assertEquals(Set.of("portal-varrock"), read().sources.get(TeleportSource.PORTAL_CHAMBER));
	}

	@Test
	public void alternateFirstPortalCanProveBaseActionWithoutAddingOtherFamilies()
	{
		int id = ObjectID.POH_PORTAL_LEAGUE_5_CAMELOT_SEERS1ST;
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(id)}, null, null);
		fixture.definitions.put(id, definition(id, false, null, "Seers' Village", "Camelot", "Varrock"));
		assertEquals(Set.of("portal-camelot"), read().sources.get(TeleportSource.PORTAL_CHAMBER));
		fixture.varbits.put(VarbitID.KANDARIN_HARD_REWARD, 1);
		assertEquals(Set.of("portal-camelot", "portal-seers-village"),
			read().sources.get(TeleportSource.PORTAL_CHAMBER));
	}

	@Test
	public void mapsEveryMaterialLeagueAndCurrentFixedPortalSuffix()
	{
		int[] ids = {ObjectID.POH_PORTAL_TEAK_TROLLHEIM, ObjectID.POH_PORTAL_MAG_PADDEWWA,
			ObjectID.POH_PORTAL_MARBLE_OURANIA, ObjectID.POH_PORTAL_LEAGUE_5_ICEPLATEAU,
			ObjectID.POH_PORTAL_LEAGUE_5_FORTIS, ObjectID.POH_PORTAL_TEAK_APE_ATOLL,
			ObjectID.POH_PORTAL_MAG_FENKENSTRAIN, ObjectID.POH_PORTAL_TEAK_YANILLE};
		fixture.tiles[0][0][0] = tile(Arrays.stream(ids).mapToObj(fixture::game).toArray(GameObject[]::new), null, null);

		assertEquals(Set.of("portal-trollheim", "portal-paddewwa", "portal-ourania", "portal-ice-plateau",
			"portal-civitas-illa-fortis", "portal-ape-atoll-dungeon", "portal-fenkenstrains-castle", "portal-watchtower"),
			read().sources.get(TeleportSource.PORTAL_CHAMBER));
	}

	@Test
	public void allMappedFixedPortalIdsExistInCatalog() throws Exception
	{
		Field field = HouseTransportReader.class.getDeclaredField("PORTALS");
		field.setAccessible(true);
		@SuppressWarnings("unchecked")
		Map<Integer, String> mapping = (Map<Integer, String>) field.get(null);
		assertEquals(192, mapping.size());
		for (String suffix : mapping.values())
		{
			assertTrue(suffix, suffix.isEmpty() || TeleportCatalog.byId("portal-" + suffix).isPresent());
		}
	}

	@Test
	public void emptyAndVariableArrivalPortalsAreKnownEmptyFixtures()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_PORTAL_LEAGUE_5_EMPTY),
			fixture.game(ObjectID.POH_PORTAL_MAG_RESPAWN), fixture.game(ObjectID.POH_PORTAL_TEAK_BOAT)}, null, null);

		HouseTransportReader.Observation observed = read();

		assertEquals(Collections.emptySet(), observed.sources.get(TeleportSource.PORTAL_CHAMBER));
		assertEquals(Map.of("0:1840:7040", Collections.emptySet()), observed.portalFixtures);
	}

	@Test
	public void fixtureOriginsStayStableAndDeduplicateMultiTileGameObjects()
	{
		GameObject portal = game(ObjectID.POH_PORTAL_TEAK_TROLLHEIM, new Point(0, 0));
		fixture.definitions.put(ObjectID.POH_PORTAL_TEAK_TROLLHEIM, definition(ObjectID.POH_PORTAL_TEAK_TROLLHEIM));
		fixture.tiles[0][0][1] = tile(new GameObject[]{portal}, null, null);
		fixture.tiles[0][1][0] = tile(new GameObject[]{portal}, null, null);

		HouseTransportReader.Observation observed = read();

		assertEquals(Map.of("0:1840:7040", Set.of("portal-trollheim")), observed.portalFixtures);
		assertEquals(1, fixture.definitionReads.get(ObjectID.POH_PORTAL_TEAK_TROLLHEIM).intValue());
		assertEquals(Set.of("0:1840:7041", "0:1841:7040"), observed.coveredTiles);
	}

	@Test
	public void nonTeleportCapeRecordsEmptyPositionForReplacement()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_MOUNTED_FIRE_CAPE)}, null, null);
		fixture.tiles[1][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_MOUNTED_CRAFTING_CAPE)}, null, null);

		HouseTransportReader.Observation observed = read();

		assertEquals(Collections.emptySet(), observed.capeFixtures.get("0:1840:7040"));
		assertEquals(Set.of("cape-crafting-guild"), observed.capeFixtures.get("1:1840:7040"));
	}

	@Test
	public void houseExitUsesNativeLocationIncludingNonContiguousNewLocations()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_EXIT_PORTAL)}, null, null);
		int[] values = {1, 2, 3, 4, 5, 6, 8, 9, 13};
		String[] names = {"rimmington", "taverley", "pollnivneach", "rellekka", "brimhaven", "yanille",
			"hosidius", "prifddinas", "aldarin"};
		for (int i = 0; i < values.length; i++)
		{
			fixture.varbits.put(VarbitID.POH_HOUSE_LOCATION, values[i]);
			assertEquals(Set.of("exit-" + names[i]), read().sources.get(TeleportSource.HOUSE_EXIT));
		}
		fixture.varbits.put(VarbitID.POH_HOUSE_LOCATION, 0);
		assertFalse(read().sources.containsKey(TeleportSource.HOUSE_EXIT));
	}

	@Test
	public void noFixtureMeansNoMenuLearningEvenWithPendantMenuVisible()
	{
		fixture.widgets.put(InterfaceID.Menu.LJ_LAYER1, digsiteMenu());

		assertFalse(read().sources.containsKey(TeleportSource.MOUNTED_DIGSITE_PENDANT));
	}

	@Test
	public void configuredDefaultDoesNotInventServerOnlyMountedUnlocks()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_AMULET_DIG_FOSSIL),
			fixture.game(ObjectID.POH_AMULET_XERIC_HONOUR)}, null, null);
		fixture.varbits.put(VarbitID.LITHKREN_RUBY_NECKLACE_REDIRECT, 1);

		assertFalse(read().sources.containsKey(TeleportSource.MOUNTED_DIGSITE_PENDANT));
		assertFalse(read().sources.containsKey(TeleportSource.MOUNTED_XERICS_TALISMAN));
	}

	@Test
	public void completeNativePendantMenuLearnsFossilAndExcludesStruckLithkren()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_AMULET_DIGSITE)}, null, null);
		fixture.widgets.put(InterfaceID.Menu.LJ_LAYER1, digsiteMenu());

		Map<TeleportSource, Set<String>> previous = read().sources;

		assertEquals(Set.of("digsite-digsite", "digsite-fossil-island"),
			previous.get(TeleportSource.MOUNTED_DIGSITE_PENDANT));
		fixture.widgets.clear();
		assertFalse(read().sources.containsKey(TeleportSource.MOUNTED_DIGSITE_PENDANT));
		assertEquals(2, previous.get(TeleportSource.MOUNTED_DIGSITE_PENDANT).size());
	}

	@Test
	public void liveNativePendantMenuAcceptsNowhereAsANonTeleportOption()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_AMULET_DIGSITE)}, null, null);
		fixture.widgets.put(InterfaceID.Menu.LJ_LAYER1, MenuReaderFixture.container(
			MenuReaderFixture.text("<col=735a28>1</col>: Digsite"),
			MenuReaderFixture.text("<col=735a28>2</col>: Fossil Island"),
			MenuReaderFixture.text("<col=735a28>3</col>: Lithkren"),
			MenuReaderFixture.text("<col=735a28>4</col>: Nowhere")));

		assertEquals(Set.of("digsite-digsite", "digsite-fossil-island", "digsite-lithkren"),
			read().sources.get(TeleportSource.MOUNTED_DIGSITE_PENDANT));
	}

	@Test
	public void nativeXericNowhereOptionDoesNotOverrideALockedDestination()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_AMULET_XERIC)}, null, null);
		fixture.widgets.put(InterfaceID.Menu.LJ_LAYER1, MenuReaderFixture.container(
			MenuReaderFixture.text("<col=735a28>1</col>: Xeric's Lookout"),
			MenuReaderFixture.text("<col=735a28>2</col>: Xeric's Glade"),
			MenuReaderFixture.text("<col=735a28>3</col>: Xeric's Inferno"),
			MenuReaderFixture.text("<col=735a28>4</col>: Xeric's Heart"),
			MenuReaderFixture.text("<col=735a28>5</col>: <str>Xeric's Honour</str>"),
			MenuReaderFixture.text("<col=735a28>6</col>: Nowhere")));

		assertEquals(Set.of("xeric-lookout", "xeric-glade", "xeric-inferno", "xeric-heart"),
			read().sources.get(TeleportSource.MOUNTED_XERICS_TALISMAN));
	}

	@Test
	public void nowhereDoesNotMakeMissingOrUnknownPendantDestinationsComplete()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_AMULET_DIGSITE)}, null, null);
		fixture.widgets.put(InterfaceID.Menu.LJ_LAYER1, MenuReaderFixture.container(
			MenuReaderFixture.text("Digsite"), MenuReaderFixture.text("Fossil Island"),
			MenuReaderFixture.text("Nowhere")));
		assertFalse(read().sources.containsKey(TeleportSource.MOUNTED_DIGSITE_PENDANT));
		fixture.widgets.put(InterfaceID.Menu.LJ_LAYER1, MenuReaderFixture.container(
			MenuReaderFixture.text("Digsite"), MenuReaderFixture.text("Fossil Island"),
			MenuReaderFixture.text("Lithkren"), MenuReaderFixture.text("Nowhere"),
			MenuReaderFixture.text("Unknown")));
		assertFalse(read().sources.containsKey(TeleportSource.MOUNTED_DIGSITE_PENDANT));
	}

	@Test
	public void completeNewXericMenuHandlesChordsAndHiddenLockedOption()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_AMULET_XERIC)}, null, null);
		fixture.widgets.put(InterfaceID.MenuNew.TEXT, MenuReaderFixture.container(
			MenuReaderFixture.widget("", 0xffffff, true, new Widget[0]),
			MenuReaderFixture.text("<col=ccccff>Ctrl+Q:</col> Xeric's Lookout"),
			MenuReaderFixture.text("<col=ccccff>W</col>: Xeric's Glade"),
			MenuReaderFixture.text("Xeric's Inferno"), MenuReaderFixture.text("4: Xeric's Heart"),
			MenuReaderFixture.widget("5: <col=808080>Xeric's Honour</col>", 0xffffff, true, new Widget[0])));

		assertEquals(Set.of("xeric-lookout", "xeric-glade", "xeric-inferno", "xeric-heart"),
			read().sources.get(TeleportSource.MOUNTED_XERICS_TALISMAN));
	}

	@Test
	public void completeEnabledXericMenuLearnsHonourWithoutGuessingQuestOrRaidCount()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_AMULET_XERIC_HEART)}, null, null);
		fixture.widgets.put(InterfaceID.Menu.LJ_LAYER1, MenuReaderFixture.container(
			MenuReaderFixture.text("Xeric's Lookout"), MenuReaderFixture.text("Xeric's Glade"),
			MenuReaderFixture.text("Xeric's Inferno"), MenuReaderFixture.text("Xeric's Heart"),
			MenuReaderFixture.text("Xeric's Honour")));

		assertEquals(5, read().sources.get(TeleportSource.MOUNTED_XERICS_TALISMAN).size());
	}

	@Test
	public void districtNamedXericMenuFromTheIssueLearnsAllFiveArrivalsInBothInterfaces()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_AMULET_XERIC)}, null, null);
		for (int component : new int[]{InterfaceID.Menu.LJ_LAYER1, InterfaceID.MenuNew.TEXT})
		{
			fixture.widgets.clear();
			fixture.widgets.put(component, districtXericMenu(MenuReaderFixture.text("5: Chambers of Xeric")));

			assertEquals(Set.of("xeric-lookout", "xeric-glade", "xeric-inferno", "xeric-heart", "xeric-honour"),
				read().sources.get(TeleportSource.MOUNTED_XERICS_TALISMAN));
		}
	}

	@Test
	public void districtNamedXericMenuPreservesStruckHiddenAndColouredChambersLocks()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_AMULET_XERIC)}, null, null);
		Widget[] locked = {
			MenuReaderFixture.text("5: <str>Chambers of Xeric</str>"),
			MenuReaderFixture.widget("5: Chambers of Xeric", 0xffffff, true, new Widget[0]),
			MenuReaderFixture.text("5: <col=808080>Chambers of Xeric</col>")};
		for (int component : new int[]{InterfaceID.Menu.LJ_LAYER1, InterfaceID.MenuNew.TEXT})
		{
			for (Widget chambers : locked)
			{
				fixture.widgets.clear();
				fixture.widgets.put(component, districtXericMenu(chambers));

				assertEquals(Set.of("xeric-lookout", "xeric-glade", "xeric-inferno", "xeric-heart"),
					read().sources.get(TeleportSource.MOUNTED_XERICS_TALISMAN));
			}
		}
	}

	@Test
	public void districtXericAliasesCannotMakeMissingUnknownOrDuplicateDestinationsComplete()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_AMULET_XERIC)}, null, null);
		Widget[][] malformed = {
			{MenuReaderFixture.text("1: Shayzien"), MenuReaderFixture.text("2: Hosidius"),
				MenuReaderFixture.text("3: Lovakengj"), MenuReaderFixture.text("4: Kourend Castle"),
				MenuReaderFixture.text("6: Nowhere")},
			{MenuReaderFixture.text("1: Shayzien"), MenuReaderFixture.text("2: Hosidius"),
				MenuReaderFixture.text("3: Lovakengj"), MenuReaderFixture.text("4: Kourend Castle"),
				MenuReaderFixture.text("5: Unknown"), MenuReaderFixture.text("6: Nowhere")},
			{MenuReaderFixture.text("1: Shayzien"), MenuReaderFixture.text("2: Hosidius"),
				MenuReaderFixture.text("3: Lovakengj"), MenuReaderFixture.text("4: Kourend Castle"),
				MenuReaderFixture.text("5: Xeric's Lookout"), MenuReaderFixture.text("6: Nowhere")},
			{MenuReaderFixture.text("1: Shayzien"), MenuReaderFixture.text("2: Hosidius"),
				MenuReaderFixture.text("3: Lovakengj"), MenuReaderFixture.text("4: Kourend Castle"),
				MenuReaderFixture.text("5: Chambers of Xeric"), MenuReaderFixture.text("6: Xeric's Lookout"),
				MenuReaderFixture.text("7: Nowhere")}};
		for (int component : new int[]{InterfaceID.Menu.LJ_LAYER1, InterfaceID.MenuNew.TEXT})
		{
			for (Widget[] rows : malformed)
			{
				fixture.widgets.clear();
				fixture.widgets.put(component, MenuReaderFixture.container(rows));

				assertFalse(read().sources.containsKey(TeleportSource.MOUNTED_XERICS_TALISMAN));
			}
		}
	}

	@Test
	public void districtXericMenuStillNeedsALoadedMountedFixture()
	{
		for (int component : new int[]{InterfaceID.Menu.LJ_LAYER1, InterfaceID.MenuNew.TEXT})
		{
			fixture.widgets.clear();
			fixture.widgets.put(component, districtXericMenu(MenuReaderFixture.text("5: Chambers of Xeric")));

			assertFalse(read().sources.containsKey(TeleportSource.MOUNTED_XERICS_TALISMAN));
		}
	}

	@Test
	public void normalizedXericLabelsExposeOnlyTheKnownCanonicalDestinations()
	{
		String[][] names = {{"xeric's lookout", "shayzien", "xeric-lookout"},
			{"xeric's glade", "hosidius", "xeric-glade"},
			{"xeric's inferno", "lovakengj", "xeric-inferno"},
			{"xeric's heart", "kourend castle", "xeric-heart"},
			{"xeric's honour", "chambers of xeric", "xeric-honour"}};
		for (String[] name : names)
		{
			assertEquals(name[2], HouseTransportReader.xericDestinationId(name[0]));
			assertEquals(name[2], HouseTransportReader.xericDestinationId(name[1]));
		}
		assertEquals(null, HouseTransportReader.xericDestinationId("unknown"));
		assertEquals(null, HouseTransportReader.xericDestinationId(null));
	}

	private static Widget districtXericMenu(Widget chambers)
	{
		return MenuReaderFixture.container(
			MenuReaderFixture.text("1: Shayzien"), MenuReaderFixture.text("2: Hosidius"),
			MenuReaderFixture.text("3: Lovakengj"), MenuReaderFixture.text("4: Kourend Castle"),
			chambers, MenuReaderFixture.text("6: Nowhere"));
	}

	@Test
	public void incompleteAmbiguousAndDuplicateMenusNeverReplaceMountedSnapshot()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_AMULET_DIG_DIGSITE)}, null, null);
		Widget[][] malformed = {
			{MenuReaderFixture.text("Digsite"), MenuReaderFixture.text("Fossil Island")},
			{MenuReaderFixture.text("Digsite"), null, MenuReaderFixture.text("Lithkren")},
			{MenuReaderFixture.text("Digsite"), MenuReaderFixture.text(""), MenuReaderFixture.text("Lithkren")},
			{MenuReaderFixture.text("Digsite"), MenuReaderFixture.text("Fossil Island"), MenuReaderFixture.text("Unknown")},
			{MenuReaderFixture.text("Digsite"), MenuReaderFixture.text("Fossil Island"), MenuReaderFixture.text("Digsite")}};
		for (Widget[] rows : malformed)
		{
			fixture.widgets.put(InterfaceID.Menu.LJ_LAYER1, MenuReaderFixture.container(rows));
			assertFalse(read().sources.containsKey(TeleportSource.MOUNTED_DIGSITE_PENDANT));
		}
	}

	@Test
	public void snapshotsAndNestedFixtureSetsAreImmutable()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_PORTAL_TEAK_TROLLHEIM)}, null, null);
		HouseTransportReader.Observation observed = read();

		assertImmutable(() -> observed.sources.clear());
		assertImmutable(() -> observed.sources.get(TeleportSource.PORTAL_CHAMBER).clear());
		assertImmutable(() -> observed.portalFixtures.clear());
		assertImmutable(() -> observed.portalFixtures.get("0:1840:7040").clear());
		assertImmutable(() -> observed.coveredTiles.clear());
		fixture.tiles[0][0][0] = emptyTile();
		assertTrue(read().sources.isEmpty());
		assertEquals(Set.of("portal-trollheim"), observed.sources.get(TeleportSource.PORTAL_CHAMBER));
	}

	@Test
	public void installedInterfacesAreDiscoveredBeforeTheirMenusAreOpened()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_SPIRIT_RING),
			fixture.game(ObjectID.POH_TELENEXUS_3), fixture.game(ObjectID.POH_JEWELLERY_BOX_2_BASE)}, null, null);

		HouseTransportReader.Observation observed = read();

		assertEquals(Set.of(TeleportSource.FAIRY_RING, TeleportSource.SPIRIT_TREE, TeleportSource.PORTAL_NEXUS, TeleportSource.JEWELLERY_BOX), observed.installedSources);
		assertEquals(Set.of(TeleportSource.FAIRY_RING, TeleportSource.SPIRIT_TREE, TeleportSource.PORTAL_NEXUS, TeleportSource.JEWELLERY_BOX), observed.installedFixtures.get("0:1840:7040"));
		assertEquals(Set.of(ObjectID.POH_SPIRIT_RING), observed.fixtureObjectIds.get(TeleportSource.FAIRY_RING));
		assertEquals(Set.of(ObjectID.POH_SPIRIT_RING), observed.fixtureObjectIds.get(TeleportSource.SPIRIT_TREE));
		assertTrue(observed.sources.isEmpty());
		assertTrue(observed.sceneReady);
	}

	@Test
	public void originalInterfaceSkinsAndDefaultDestinationVariantsShareDiscovery()
	{
		int[] trees = {ObjectID.POH_SPIRIT_TREE, ObjectID.XMAS20_POH_SPIRIT_TREE, ObjectID.LEAGUE_5_POH_SPIRIT_TREE};
		for (int id : trees)
		{
			assertEquals(Set.of(TeleportSource.SPIRIT_TREE), HouseTransportReader.interfaceSources(id));
		}
		int[] combined = {ObjectID.POH_SPIRIT_RING, ObjectID.XMAS20_POH_SPIRIT_RING,
			ObjectID.LEAGUE_5_POH_SPIRIT_RING};
		for (int id : combined)
		{
			assertEquals(Set.of(TeleportSource.SPIRIT_TREE, TeleportSource.FAIRY_RING),
				HouseTransportReader.interfaceSources(id));
		}
		int[] nexus = {ObjectID.POH_TELENEXUS_1, ObjectID.POH_TELENEXUS_2_MIDDLE,
			ObjectID.POH_TELENEXUS_2_SIDE, ObjectID.POH_TELENEXUS_2_CORNER, ObjectID.POH_TELENEXUS_3,
			ObjectID.POH_NEXUS_PORTAL_1, ObjectID.POH_NEXUS_PORTAL_2,
			ObjectID.POH_NEXUS_PORTAL_3, ObjectID.POH_NEXUS_PORTAL_LEAGUE_5};
		for (int id : nexus)
		{
			assertEquals(Set.of(TeleportSource.PORTAL_NEXUS), HouseTransportReader.interfaceSources(id));
		}
		int[] boxes = {ObjectID.POH_JEWELLERY_BOX_1, ObjectID.POH_JEWELLERY_BOX_2, ObjectID.POH_JEWELLERY_BOX_3,
			ObjectID.POH_JEWELLERY_BOX_1_COLOSSEUM, ObjectID.POH_JEWELLERY_BOX_2_COLOSSEUM,
			ObjectID.POH_JEWELLERY_BOX_3_COLOSSEUM};
		for (int id : boxes)
		{
			assertEquals(Set.of(TeleportSource.JEWELLERY_BOX), HouseTransportReader.interfaceSources(id));
		}
		for (int id = ObjectID.POH_JEWELLERY_BOX_1_BASE; id <= ObjectID.POH_JEWELLERY_BOX_3_KHARID; id++)
		{
			assertEquals(Set.of(TeleportSource.JEWELLERY_BOX), HouseTransportReader.interfaceSources(id));
		}
	}

	@Test
	public void installedInventoryCoversAllSourcesEvenWhenCapabilitySetIsEmptyOrUnknown()
	{
		int[] ids = {ObjectID.POH_SPIRIT_RING, ObjectID.POH_TELENEXUS_1, ObjectID.POH_JEWELLERY_BOX_1,
			ObjectID.POH_WILDERNESS_OBELISK, ObjectID.POH_PORTAL_TEAK_EMPTY,
			ObjectID.POH_TROPHY_AMULETOFGLORY_4, ObjectID.POH_AMULET_DIGSITE, ObjectID.POH_AMULET_XERIC,
			ObjectID.POH_TROPHY_MYTHICAL_CAPE, ObjectID.POH_MOUNTED_CAPESTAND_BLANK, ObjectID.POH_EXIT_PORTAL};
		fixture.tiles[0][0][0] = tile(Arrays.stream(ids).mapToObj(fixture::game).toArray(GameObject[]::new), null, null);

		HouseTransportReader.Observation observed = read();

		assertEquals(EnumSet.allOf(TeleportSource.class), observed.installedSources);
		assertEquals(EnumSet.allOf(TeleportSource.class), observed.fixtureObjectIds.keySet());
		assertEquals(EnumSet.allOf(TeleportSource.class), observed.installedFixtures.get("0:1840:7040"));
		assertTrue(observed.sources.get(TeleportSource.PORTAL_CHAMBER).isEmpty());
		assertTrue(observed.sources.get(TeleportSource.WILDERNESS_OBELISK).isEmpty());
		assertFalse(observed.sources.containsKey(TeleportSource.MOUNTED_DIGSITE_PENDANT));
		assertTrue(observed.sceneReady);
	}

	@Test
	public void surveyReadinessRequiresPositivePohFixtureAndLoadedCoverage()
	{
		fixture.tiles[0][0][0] = emptyTile();
		assertFalse(read().sceneReady);
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(100)}, null, null);
		assertFalse(read().sceneReady);
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_EXIT_PORTAL)}, null, null);
		assertTrue(read().sceneReady);
		// An unresolved definition prevents the only loaded tile proving readiness.
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_EXIT_PORTAL), game(999002, null)}, null, null);
		assertFalse(read().sceneReady);
	}

	@Test
	public void unresolvedOriginalInterfaceDoesNotCreateInstalledPresence()
	{
		int raw = 999003;
		fixture.definitions.put(raw, definition(raw, null));
		fixture.tiles[0][0][0] = tile(new GameObject[]{game(raw, null)}, null, null);

		HouseTransportReader.Observation observed = read();

		assertFalse(observed.sceneReady);
		assertTrue(observed.installedSources.isEmpty());
		assertTrue(observed.installedFixtures.isEmpty());
		assertTrue(observed.fixtureObjectIds.isEmpty());
	}

	@Test
	public void builtCombinedMultilocRecordsBothFacilitiesAtOneStableOrigin()
	{
		int raw = 999004;
		fixture.definitions.put(raw, definition(raw, definition(ObjectID.XMAS20_POH_SPIRIT_RING)));
		fixture.tiles[0][1][1] = tile(new GameObject[]{game(raw, new Point(1, 1))}, null, null);

		HouseTransportReader.Observation observed = read();

		assertEquals(Set.of(TeleportSource.FAIRY_RING, TeleportSource.SPIRIT_TREE), observed.installedSources);
		assertEquals(Map.of("0:1841:7041", observed.installedSources), observed.installedFixtures);
		assertEquals(Set.of(ObjectID.XMAS20_POH_SPIRIT_RING), observed.fixtureObjectIds.get(TeleportSource.FAIRY_RING));
	}

	@Test
	public void unbuiltHotspotsAndExternalTreesNeverRequireHouseInterfaces()
	{
		int[] ids = {ObjectID.POH_SUPERIOR_GARDEN_HOTSPOT_TREERING,
			ObjectID.POH_ACHIEVEMENT_HOTSPOT_JEWELLERYBOX, ObjectID.FARMING_SPIRIT_TREE_PATCH_1,
			ObjectID.SPIRIT_TREE_FULLYGROWN};
		fixture.tiles[0][0][0] = tile(Arrays.stream(ids).mapToObj(fixture::game).toArray(GameObject[]::new), null, null);

		HouseTransportReader.Observation observed = read();

		assertTrue(observed.installedSources.isEmpty());
		assertFalse(observed.sceneReady);
		for (int id : ids)
		{
			assertTrue(HouseTransportReader.interfaceSources(id).isEmpty());
		}
	}

	@Test
	public void partialRoomDiscoveryDoesNotDeclareUnseenFacilitiesAbsent()
	{
		fixture.tiles[0][0][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_SPIRIT_TREE)}, null, null);
		HouseTransportReader.Observation previous = read();
		fixture.tiles[0][0][0] = null;
		fixture.tiles[0][1][0] = tile(new GameObject[]{fixture.game(ObjectID.POH_FAIRY_RING)}, null, null);
		HouseTransportReader.Observation current = read();

		assertEquals(Set.of(TeleportSource.SPIRIT_TREE), previous.installedSources);
		assertEquals(Set.of(TeleportSource.FAIRY_RING), current.installedSources);
		assertFalse(current.coveredTiles.contains("0:1840:7040"));
		assertTrue(current.sceneReady);
		assertImmutable(() -> current.installedSources.clear());
		assertImmutable(() -> current.installedFixtures.clear());
		assertImmutable(() -> current.installedFixtures.get("0:1841:7040").clear());
		assertImmutable(() -> current.fixtureObjectIds.clear());
		assertImmutable(() -> current.fixtureObjectIds.get(TeleportSource.FAIRY_RING).clear());
	}

	@Test
	public void perPositionVersionsContainTransformedFixtureIdsAcrossFloors()
	{
		int raw = 999005;
		fixture.definitions.put(raw, definition(raw, definition(ObjectID.POH_JEWELLERY_BOX_3_EDGEVILLE)));
		fixture.tiles[0][0][0] = tile(new GameObject[]{game(raw, null)}, null,
			fixture.decorative(ObjectID.POH_TROPHY_AMULETOFGLORY_4));
		fixture.tiles[1][1][1] = tile(new GameObject[]{fixture.game(ObjectID.XMAS20_POH_SPIRIT_RING)}, null, null);

		HouseTransportReader.Observation observed = read();

		assertEquals(Map.of("0:1840:7040", Set.of(ObjectID.POH_JEWELLERY_BOX_3_EDGEVILLE,
			ObjectID.POH_TROPHY_AMULETOFGLORY_4), "1:1841:7041", Set.of(ObjectID.XMAS20_POH_SPIRIT_RING)),
			observed.installedFixtureIds);
		assertFalse(observed.installedFixtureIds.get("0:1840:7040").contains(raw));
		assertImmutable(() -> observed.installedFixtureIds.clear());
		assertImmutable(() -> observed.installedFixtureIds.get("0:1840:7040").clear());
	}

	@Test
	public void nullWorldSceneAndTilesReturnNoSurveyOrVersionEvidence()
	{
		assertEmptyDiscovery(HouseTransportReader.readSnapshot(null));
		fixture.worldPresent = false;
		assertEmptyDiscovery(read());
		fixture.worldPresent = true;
		fixture.scenePresent = false;
		assertEmptyDiscovery(read());
		fixture.scenePresent = true;
		fixture.tiles = null;
		assertEmptyDiscovery(read());
	}

	@Test
	public void unresolvedMultilocAndUnbuiltHotspotsSupplyNoVersionEvidence()
	{
		int raw = 999006;
		fixture.definitions.put(raw, definition(raw, null));
		fixture.tiles[0][0][0] = tile(new GameObject[]{game(raw, null),
			fixture.game(ObjectID.POH_ACHIEVEMENT_HOTSPOT_JEWELLERYBOX)}, null, null);

		assertEmptyDiscovery(read());
	}

	private static void assertEmptyDiscovery(HouseTransportReader.Observation observed)
	{
		assertFalse(observed.sceneReady);
		assertTrue(observed.sources.isEmpty());
		assertTrue(observed.coveredTiles.isEmpty());
		assertTrue(observed.installedSources.isEmpty());
		assertTrue(observed.installedFixtures.isEmpty());
		assertTrue(observed.installedFixtureIds.isEmpty());
		assertTrue(observed.fixtureObjectIds.isEmpty());
	}

	private HouseTransportReader.Observation read()
	{
		return HouseTransportReader.readSnapshot(fixture.client);
	}

	private static Widget digsiteMenu()
	{
		return MenuReaderFixture.container(MenuReaderFixture.text("<col=ccccff>1:</col> Digsite"),
			MenuReaderFixture.text("<col=ccccff>2:</col> Fossil Island"),
			MenuReaderFixture.text("<col=ccccff>3:</col> <str>Lithkren</str>"));
	}

	private static void assertImmutable(Runnable action)
	{
		try
		{
			action.run();
			throw new AssertionError("Expected an immutable snapshot");
		}
		catch (UnsupportedOperationException expected)
		{
			// Expected.
		}
	}

	private static Tile emptyTile()
	{
		return tile(null, null, null);
	}

	private static Tile tile(GameObject[] games, WallObject wall, DecorativeObject decorative)
	{
		return proxy(Tile.class, (name, args) ->
		{
			switch (name)
			{
				case "getGameObjects": return games;
				case "getWallObject": return wall;
				case "getDecorativeObject": return decorative;
				default: throw new UnsupportedOperationException(name);
			}
		});
	}

	private static GameObject game(int id, Point origin)
	{
		return proxy(GameObject.class, (name, args) ->
		{
			switch (name)
			{
				case "getId": return id;
				case "getSceneMinLocation": return origin;
				default: throw new UnsupportedOperationException(name);
			}
		});
	}

	private static ObjectComposition definition(int id)
	{
		return definition(id, false, null);
	}

	private static ObjectComposition definition(int id, ObjectComposition live)
	{
		return definition(id, true, live);
	}

	private static ObjectComposition definition(int id, boolean transformed, ObjectComposition live, String... actions)
	{
		return proxy(ObjectComposition.class, (name, args) ->
		{
			switch (name)
			{
				case "getId": return id;
				case "getImpostorIds": return transformed ? new int[]{-1, id} : null;
				case "getImpostor": return live;
				case "getActions": return actions;
				default: throw new UnsupportedOperationException(name);
			}
		});
	}

	private static <T> T proxy(Class<T> type, Calls calls)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(instance, method, args) -> calls.call(method.getName(), args)));
	}

	private interface Calls
	{
		Object call(String name, Object[] args);
	}

	private static final class Fixture
	{
		private Tile[][][] tiles = new Tile[2][4][4];
		private boolean worldPresent = true;
		private boolean scenePresent = true;
		private final Map<Integer, ObjectComposition> definitions = new HashMap<>();
		private final Map<Integer, Integer> definitionReads = new HashMap<>();
		private final Map<Integer, Integer> varbits = new HashMap<>();
		private final Map<Integer, Widget> widgets = new HashMap<>();
		private final Scene scene = proxy(Scene.class, (name, args) ->
		{
			if ("getTiles".equals(name))
			{
				return tiles;
			}
			throw new UnsupportedOperationException(name);
		});
		private final WorldView world = proxy(WorldView.class, (name, args) ->
		{
			switch (name)
			{
				case "getScene": return scenePresent ? scene : null;
				case "getBaseX": return 1840;
				case "getBaseY": return 7040;
				default: throw new UnsupportedOperationException(name);
			}
		});
		private final Client client = proxy(Client.class, (name, args) ->
		{
			switch (name)
			{
				case "getTopLevelWorldView": return worldPresent ? world : null;
				case "getObjectDefinition":
					definitionReads.merge((Integer) args[0], 1, Integer::sum);
					return definitions.get(args[0]);
				case "getVarbitValue": return varbits.getOrDefault(args[0], 0);
				case "getWidget": return widgets.get(args[0]);
				default: throw new UnsupportedOperationException(name);
			}
		});

		private GameObject game(int id)
		{
			definitions.put(id, definition(id));
			return HouseTransportReaderTest.game(id, null);
		}

		private WallObject wall(int id)
		{
			definitions.put(id, definition(id));
			return proxy(WallObject.class, (name, args) ->
			{
				if ("getId".equals(name))
				{
					return id;
				}
				throw new UnsupportedOperationException(name);
			});
		}

		private DecorativeObject decorative(int id)
		{
			definitions.put(id, definition(id));
			return proxy(DecorativeObject.class, (name, args) ->
			{
				if ("getId".equals(name))
				{
					return id;
				}
				throw new UnsupportedOperationException(name);
			});
		}
	}
}
