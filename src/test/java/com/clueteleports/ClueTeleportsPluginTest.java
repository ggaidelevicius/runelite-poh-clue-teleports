package com.clueteleports;

import com.google.gson.Gson;
import java.awt.Graphics2D;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.ScriptEvent;
import net.runelite.api.Tile;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.StructComposition;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.SkillChallengeClue;
import net.runelite.client.plugins.cluescrolls.clues.LocationClueScroll;
import net.runelite.client.ui.overlay.components.PanelComponent;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClueTeleportsPluginTest
{
	@Rule public TemporaryFolder temporary = new TemporaryFolder();
	private GameState gameState = GameState.LOGGED_IN;
	private boolean inInstance;
	private int baseX = 1920;
	private int baseY = 7040;
	private boolean nexusOpen;
	private int nexusReads;
	private int tickCount;
	private Tile[][][] sceneTiles;
	private final Map<Integer, ObjectComposition> objectDefinitions = new HashMap<>();
	private final Map<Integer, Widget> extraWidgets = new HashMap<>();
	private final Scene scene = proxy(Scene.class, (proxy, method, args) ->
	{
		if ("getTiles".equals(method.getName()))
		{
			return sceneTiles;
		}
		throw new AssertionError("Unexpected scene call: " + method.getName());
	});
	private final CacheWrites writes = new CacheWrites();
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final Map<Integer, Integer> enumValues = new HashMap<>();
	private final Map<Integer, StructComposition> structs = new HashMap<>();
	private final Widget menu = proxy(Widget.class, (proxy, method, args) ->
	{
		if ("isHidden".equals(method.getName()))
		{
			return false;
		}
		throw new AssertionError("Unexpected widget call: " + method.getName());
	});
	private final Player localPlayer = proxy(Player.class, (proxy, method, args) ->
	{
		if ("getLocalLocation".equals(method.getName()))
		{
			return new LocalPoint(64, 64, 0);
		}
		throw new AssertionError("Unexpected player call: " + method.getName());
	});
	private final WorldView worldView = proxy(WorldView.class, (proxy, method, args) ->
	{
		switch (method.getName())
		{
			case "isInstance":
				return inInstance;
			case "getBaseX":
				return baseX;
			case "getBaseY":
				return baseY;
			case "getScene":
				return sceneTiles == null ? null : scene;
			case "getPlane":
			case "getId":
				return 0;
			default:
				throw new AssertionError("Unexpected world view call: " + method.getName());
		}
	});
	private final EnumComposition destinationEnum = proxy(EnumComposition.class, (proxy, method, args) ->
	{
		if ("getIntValue".equals(method.getName()))
		{
			return enumValues.getOrDefault((Integer) args[0], -1);
		}
		throw new AssertionError("Unexpected enum call: " + method.getName());
	});
	private final Client client = proxy(Client.class, (proxy, method, args) ->
	{
		switch (method.getName())
		{
			case "isClientThread":
				return true;
			case "getGameState":
				return gameState;
			case "getTopLevelWorldView":
			case "getWorldView":
				return worldView;
			case "getLocalPlayer":
				return localPlayer;
			case "getTickCount":
				return tickCount;
			case "getBaseX":
				return baseX;
			case "getBaseY":
				return baseY;
			case "getWidget":
				Widget extraWidget = extraWidgets.get((Integer) args[0]);
				return extraWidget != null ? extraWidget
					: (Integer) args[0] == InterfaceID.TelenexusTeleport.UNIVERSE && nexusOpen ? menu : null;
			case "getEnum":
				assertEquals(1377, args[0]);
				nexusReads++;
				return destinationEnum;
			case "getVarbitValue":
				return varbits.getOrDefault((Integer) args[0], 0);
			case "getStructComposition":
				return structs.get((Integer) args[0]);
			case "getObjectDefinition":
				return objectDefinitions.get((Integer) args[0]);
			default:
				throw new AssertionError("Unexpected client call: " + method.getName());
		}
	});
	private final ClueTeleportsPlugin plugin = new ClueTeleportsPlugin();
	private final ClueScrollPlugin coreClues = new ClueScrollPlugin();
	private final ClientThread clientThread = new ClientThread();
	private ConfigManager config;
	private PluginManager pluginManager;
	private TeleportDestination first;
	private TeleportDestination second;

	@Before
	public void setUp() throws Exception
	{
		EventBus events = new EventBus();
		events.register(writes);
		config = createInMemoryConfig(events);
		setField(config, "rsProfileKey", "character-one");
		Constructor<?> constructor = PluginManager.class.getDeclaredConstructors()[0];
		constructor.setAccessible(true);
		pluginManager = (PluginManager) constructor.newInstance(false, false, events, null, config, null, null);
		pluginManager.add(coreClues);
		setField(plugin, "client", client);
		setField(plugin, "clientThread", clientThread);
		setField(plugin, "configManager", config);
		setField(plugin, "pluginManager", pluginManager);
		setField(plugin, "running", true);
		setField(plugin, "routing", new FixtureRouting());
		first = TeleportCatalog.byId("nexus-varrock").get();
		second = TeleportCatalog.byId("nexus-falador").get();
		setClue(new FixedLocationClue(first.getLocation()));
		seedOtherSourcesEmpty();
		seedCache(first);
		writes.count = 0;
	}

	@After
	public void stopFixture() throws Exception
	{
		setField(plugin, "running", false);
	}

	@Test
	public void usesTheActualManagedCluePluginInstance() throws Exception
	{
		tick();
		assertNotNull(plugin.getDisplay());
		assertEquals(first.getName(), plugin.getDisplay().second);
		pluginManager.remove(coreClues);
		tick();
		assertNull(plugin.getDisplay());
	}

	@Test
	public void disabledCoreCluePluginHidesTheRecommendation() throws Exception
	{
		tick();
		assertNotNull(plugin.getDisplay());
		config.setConfiguration("runelite", "cluescrollplugin", "false");
		tick();
		assertNull(plugin.getDisplay());
	}

	@Test
	public void removingTheActiveClueClearsThePreviousRecommendation() throws Exception
	{
		tick();
		assertNotNull(plugin.getDisplay());
		setClue(null);
		tick();
		assertNull(plugin.getDisplay());
	}

	@Test
	public void loggingOutHidesTheRecommendationImmediately() throws Exception
	{
		tick();
		assertNotNull(plugin.getDisplay());
		gameState = GameState.LOGIN_SCREEN;
		GameStateChanged event = new GameStateChanged();
		event.setGameState(gameState);
		plugin.onGameStateChanged(event);
		assertNull(plugin.getDisplay());
		tick();
		assertNull(plugin.getDisplay());
	}

	@Test
	public void loggedInWithoutACharacterProfileHidesTheRecommendation() throws Exception
	{
		tick();
		setField(config, "rsProfileKey", null);
		tick();
		assertNull(plugin.getDisplay());
	}

	@Test
	public void newCharactersDoNotInheritThePreviousCharactersTeleports() throws Exception
	{
		tick();
		assertEquals(first.getName(), plugin.getDisplay().second);
		setField(config, "rsProfileKey", "character-two");
		tick();
		assertEquals("Visit your POH", plugin.getDisplay().first);
		seedOtherSourcesEmpty();
		seedCache(second);
		tick();
		assertEquals(second.getName(), plugin.getDisplay().second);
		setField(config, "rsProfileKey", "character-one");
		tick();
		assertEquals(first.getName(), plugin.getDisplay().second);
	}

	@Test
	public void changedCachesAreReadAgainWithinTheSameProfile() throws Exception
	{
		tick();
		assertEquals(first.getName(), plugin.getDisplay().second);
		seedCache(second);
		tick();
		assertEquals(second.getName(), plugin.getDisplay().second);
	}

	@Test
	public void onlyCachedAvailableDestinationsAreConsidered() throws Exception
	{
		seedCache(second);
		tick();
		assertEquals(second.getName(), plugin.getDisplay().second);
		assertEquals(second.getSource().getDisplayName(), plugin.getDisplay().first);
	}

	@Test
	public void obsoleteIdsDoNotEnableAnyDestination() throws Exception
	{
		config.setRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, TeleportSource.PORTAL_NEXUS.getCacheKey(),
			"destination-that-does-not-exist");
		tick();
		assertFalse(plugin.getDisplay().isLearning());
		assertEquals("No available POH teleports", plugin.getDisplay().first);
	}

	@Test
	public void unrelatedMapSpacesDoNotProduceARecommendation() throws Exception
	{
		setClue(new FixedLocationClue(new WorldPoint(3200, 10000, 0)));
		tick();
		assertEquals("No mapped walking route", plugin.getDisplay().first);
	}

	@Test
	public void homeEntryWithTwoLoadingEventsLearnsNexusWithoutAnyWelcomeChat()
	{
		clearNexusCache();
		enterOwnHouse();
		openNexus(first);
		tick();
		assertNull(encodedNexusCache());
		assertLearning(Set.of(TeleportSource.FAIRY_RING, TeleportSource.SPIRIT_TREE, TeleportSource.JEWELLERY_BOX));
		tick();
		assertEquals(Collections.singleton(first.getId()), cachedNexusIds());
		assertEquals(first.getName(), plugin.getDisplay().second);
		assertEquals(1, writes.count);
	}

	@Test
	public void unchangedAndReorderedMenusDoNotRewriteTheCharacterCache()
	{
		enterOwnHouse();
		openNexus(first, second);
		tick();
		tick();
		assertEquals(1, writes.count);
		openNexus(second, first);
		for (int index = 0; index < 5; index++)
		{
			tick();
		}
		assertEquals(1, writes.count);
		assertEquals(Set.of(first.getId(), second.getId()), cachedNexusIds());
	}

	@Test
	public void removedNexusDestinationsAreRemovedAfterAStableReading() throws Exception
	{
		config.setRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP,
			TeleportSource.PORTAL_NEXUS.getCacheKey(), AvailableTeleports.encode(Set.of(first.getId(), second.getId())));
		writes.count = 0;
		setClue(new FixedLocationClue(second.getLocation()));
		enterOwnHouse();
		openNexus(first);
		tick();
		assertEquals(second.getName(), plugin.getDisplay().second);
		tick();
		assertEquals(Collections.singleton(first.getId()), cachedNexusIds());
		assertEquals(first.getName(), plugin.getDisplay().second);
		assertEquals(1, writes.count);
	}

	@Test
	public void incompleteNexusReadingsCannotEraseTheLastConfirmedSnapshot()
	{
		enterOwnHouse();
		openNexus(first);
		varbits.put(TeleportCatalog.nexusSlotVarbits()[1], 999);
		tick();
		tick();
		tick();
		assertEquals(Collections.singleton(first.getId()), cachedNexusIds());
		assertEquals(0, writes.count);
		assertEquals(first.getName(), plugin.getDisplay().second);
	}

	@Test
	public void failedReadingInterruptsTheTwoTickConfirmation()
	{
		clearNexusCache();
		enterOwnHouse();
		openNexus(first);
		tick();
		nexusOpen = false;
		tick();
		nexusOpen = true;
		tick();
		assertNull(encodedNexusCache());
		tick();
		assertEquals(Collections.singleton(first.getId()), cachedNexusIds());
		assertEquals(1, writes.count);
	}

	@Test
	public void guestHouseInterfacesAreIgnoredInTheSamePohRegion()
	{
		openNexus(second);
		clickEntry("Friend's house", "<col=00ffff>Portal</col>", MenuAction.GAME_OBJECT_THIRD_OPTION, 0);
		changeGameState(GameState.LOADING);
		changeGameState(GameState.LOGGED_IN);
		tick();
		tick();
		assertEquals(0, nexusReads);
		assertEquals(Collections.singleton(first.getId()), cachedNexusIds());
		assertEquals(0, writes.count);
	}

	@Test
	public void ownEntryCannotAuthorizeAnInterfaceInAnUnrelatedRegion()
	{
		enterOwnHouse();
		baseX = 3200;
		baseY = 3200;
		openNexus(second);
		tick();
		tick();
		assertEquals(0, nexusReads);
		assertEquals(Collections.singleton(first.getId()), cachedNexusIds());
	}

	@Test
	public void loadingANewSceneInvalidatesThePreviousOwnHouseEvidence()
	{
		enterOwnHouse();
		tickCount += HouseContext.ENTRY_WINDOW_TICKS + 1;
		openNexus(second);
		changeGameState(GameState.LOADING);
		changeGameState(GameState.LOGGED_IN);
		tick();
		tick();
		assertEquals(0, nexusReads);
		assertEquals(Collections.singleton(first.getId()), cachedNexusIds());
	}

	@Test
	public void aTickBetweenChainedHouseLoadsDoesNotBlockNexusLearning()
	{
		clearNexusCache();
		clickEntry("Home", "<col=00ffff>Portal</col>", MenuAction.GAME_OBJECT_SECOND_OPTION, 0);
		changeGameState(GameState.LOADING);
		changeGameState(GameState.LOGGED_IN);
		tick();
		changeGameState(GameState.LOADING);
		changeGameState(GameState.LOGGED_IN);
		tick();
		openNexus(first);
		tick();
		tick();
		assertEquals(Collections.singleton(first.getId()), cachedNexusIds());
		assertEquals(first.getName(), plugin.getDisplay().second);
		assertEquals(1, writes.count);
	}

	@Test
	public void sourceUpdatesPreserveOtherTravelFamilies()
	{
		config.setRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP,
			TeleportSource.FAIRY_RING.getCacheKey(), "fairy-aiq");
		enterOwnHouse();
		openNexus(second);
		tick();
		tick();
		assertEquals("fairy-aiq", config.getRSProfileConfiguration(
			ClueTeleportsPlugin.CONFIG_GROUP, TeleportSource.FAIRY_RING.getCacheKey()));
		assertEquals(Collections.singleton(second.getId()), cachedNexusIds());
		assertEquals("", config.getRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, TeleportSource.SPIRIT_TREE.getCacheKey()));
		assertEquals("", config.getRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, TeleportSource.JEWELLERY_BOX.getCacheKey()));
		assertFalse(plugin.getDisplay().isLearning());
	}

	@Test
	public void pendingSnapshotAndOwnershipCannotCarryAcrossAProfileChange() throws Exception
	{
		clearNexusCache();
		enterOwnHouse();
		openNexus(second);
		tick();
		setField(config, "rsProfileKey", "character-two");
		tick();
		assertNull(encodedNexusCache());
		assertEquals("Visit your POH", plugin.getDisplay().first);
		enterOwnHouse();
		openNexus(second);
		tick();
		assertNull(encodedNexusCache());
		tick();
		assertEquals(Collections.singleton(second.getId()), cachedNexusIds());
		assertFalse(plugin.getDisplay().isLearning());
		assertEquals(second.getName(), plugin.getDisplay().second);
		setField(config, "rsProfileKey", "character-one");
		tick();
		assertNull(encodedNexusCache());
		assertLearning(Set.of(TeleportSource.FAIRY_RING, TeleportSource.SPIRIT_TREE, TeleportSource.JEWELLERY_BOX));
	}

	@Test
	public void teleportToHouseSpellLearnsANonInstancedPohWithoutWelcomeChat()
	{
		clearNexusCache();
		clickEntry("Cast", "<col=00ff00>Teleport to House</col>", MenuAction.CC_OP,
			InterfaceID.MagicSpellbook.TELEPORT_TO_YOUR_HOUSE);
		changeGameState(GameState.LOADING);
		changeGameState(GameState.LOGGED_IN);
		openNexus(first);
		tick();
		assertNull(encodedNexusCache());
		tick();
		assertEquals(Collections.singleton(first.getId()), cachedNexusIds());
		assertEquals(first.getName(), plugin.getDisplay().second);
		assertEquals(1, writes.count);
	}

	@Test
	public void ownEntryIntentWithoutSubsequentLoadingCannotAuthorizeLearning()
	{
		clearNexusCache();
		clickEntry("Home", "<col=00ffff>Portal</col>", MenuAction.GAME_OBJECT_SECOND_OPTION, 0);
		openNexus(first);
		tick();
		tick();
		assertNull(encodedNexusCache());
		assertEquals(0, nexusReads);
	}

	@Test
	public void missingOneSourceHidesAnOtherwiseExactClosestDestination()
	{
		config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, TeleportSource.SPIRIT_TREE.getCacheKey());
		tick();
		assertLearning(Set.of(TeleportSource.FAIRY_RING, TeleportSource.PORTAL_NEXUS, TeleportSource.JEWELLERY_BOX));
		assertFalse(first.getName().equals(plugin.getDisplay().second));
		assertNull(plugin.getDisplay().third);
	}

	@Test
	public void learningTheFourthSourceWithAnEmptySnapshotUnblocksTheClosest()
	{
		config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, TeleportSource.SPIRIT_TREE.getCacheKey());
		tick();
		assertTrue(plugin.getDisplay().isLearning());
		config.setRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, TeleportSource.SPIRIT_TREE.getCacheKey(), "");
		tick();
		assertFalse(plugin.getDisplay().isLearning());
		assertEquals(first.getName(), plugin.getDisplay().second);
	}

	@Test
	public void allFourLearnedEmptySourcesCompleteLearningWithoutInventingATeleport()
	{
		config.setRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, TeleportSource.PORTAL_NEXUS.getCacheKey(), "");
		tick();
		assertFalse(plugin.getDisplay().isLearning());
		assertEquals("No available POH teleports", plugin.getDisplay().first);
		assertNull(plugin.getDisplay().third);
		config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, TeleportSource.FAIRY_RING.getCacheKey());
		tick();
		assertLearning(Set.of(TeleportSource.SPIRIT_TREE, TeleportSource.PORTAL_NEXUS, TeleportSource.JEWELLERY_BOX));
	}

	@Test
	public void automaticSourceCachingContinuesWhileOtherSourcesAreStillMissing()
	{
		for (TeleportSource source : TeleportSource.values())
		{
			config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, source.getCacheKey());
		}
		writes.count = 0;
		enterOwnHouse();
		openNexus(first);
		tick();
		assertLearning(Collections.emptySet());
		tick();
		assertEquals(Collections.singleton(first.getId()), cachedNexusIds());
		assertLearning(Collections.singleton(TeleportSource.PORTAL_NEXUS));
		assertEquals(1, writes.count);
	}

	@Test
	public void optionalFacilitiesAreNotRequiredToCompleteLearning()
	{
		for (TeleportSource source : TeleportSource.values())
		{
			if (!TeleportSource.menuSources().contains(source))
			{
				assertNull(config.getRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, source.getCacheKey()));
			}
		}
		tick();
		assertFalse(plugin.getDisplay().isLearning());
		assertEquals(first.getName(), plugin.getDisplay().second);
	}

	@Test
	public void learnedOptionalFixturesCannotReplaceAMissingRequiredMenu()
	{
		config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, TeleportSource.SPIRIT_TREE.getCacheKey());
		config.setRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP,
			TeleportSource.WILDERNESS_OBELISK.getCacheKey(), "obelisk-13");
		tick();
		assertLearning(Set.of(TeleportSource.FAIRY_RING, TeleportSource.PORTAL_NEXUS, TeleportSource.JEWELLERY_BOX));
	}

	@Test
	public void freshHouseLearnsTheReportedRegionalXericMenuAfterTwoStableReadings() throws Exception
	{
		config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, ClueTeleportsPlugin.HOUSE_INVENTORY_KEY);
		for (TeleportSource source : TeleportSource.values())
		{
			config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, source.getCacheKey());
		}
		TeleportDestination glade = TeleportCatalog.byId("xeric-glade").get();
		setClue(new FixedLocationClue(glade.getLocation()));
		placeFixture(ObjectID.POH_AMULET_XERIC, "Xeric's talisman");
		enterOwnHouse();
		tick();
		assertEquals(Set.of(TeleportSource.MOUNTED_XERICS_TALISMAN), plugin.getDisplay().getRequiredSources());
		assertTrue(plugin.getDisplay().isLearning());
		extraWidgets.put(InterfaceID.Menu.LJ_LAYER1, regionalXericMenu());

		tick();
		assertNull(cachedSource(TeleportSource.MOUNTED_XERICS_TALISMAN));
		assertTrue(plugin.getDisplay().isLearning());
		tick();

		assertEquals(Set.of("xeric-lookout", "xeric-glade", "xeric-inferno", "xeric-heart", "xeric-honour"),
			AvailableTeleports.decode(cachedSource(TeleportSource.MOUNTED_XERICS_TALISMAN)));
		assertFalse(plugin.getDisplay().isLearning());
		assertEquals("Mounted Xeric's talisman", plugin.getDisplay().first);
		assertEquals(glade.getName(), plugin.getDisplay().second);
		assertEquals(glade.getId(), plugin.getGuidance().getDestinationId());
	}

	@Test
	public void regionalXericMenuInAGuestHouseCannotTeachTheSource()
	{
		placeFixture(ObjectID.POH_AMULET_XERIC, "Xeric's talisman");
		extraWidgets.put(InterfaceID.Menu.LJ_LAYER1, regionalXericMenu());
		clickEntry("Friend's house", "<col=00ffff>Portal</col>", MenuAction.GAME_OBJECT_THIRD_OPTION, 0);
		changeGameState(GameState.LOADING);
		changeGameState(GameState.LOGGED_IN);

		tick();
		tick();
		tick();

		assertNull(cachedSource(TeleportSource.MOUNTED_XERICS_TALISMAN));
		assertFalse(plugin.getGuidance().isOwnHouse());
	}

	@Test
	public void renamedXericMenuLearnsOriginalUnlocksAfterTwoReadings() throws Exception
	{
		prepareFreshMountedSource(ObjectID.POH_AMULET_XERIC);
		openRenamedNativeMenu(InterfaceID.Menu.LJ_LAYER1, "Xeric's Talisman teleports",
			"Xeric's Lookout", "Xeric's Glade", "Xeric's Inferno", "Xeric's Heart",
			"<str>Xeric's Honour</str>", "Nowhere");
		tick();
		assertNull(cachedSource(TeleportSource.MOUNTED_XERICS_TALISMAN));
		tick();
		assertEquals(Set.of("xeric-lookout", "xeric-glade", "xeric-inferno", "xeric-heart"),
			AvailableTeleports.decode(cachedSource(TeleportSource.MOUNTED_XERICS_TALISMAN)));
		assertNull(cachedSource(TeleportSource.MOUNTED_DIGSITE_PENDANT));
	}

	@Test
	public void renamedDigsiteMenuRetainsNativeLocksInTheModernInterface() throws Exception
	{
		prepareFreshMountedSource(ObjectID.POH_AMULET_DIGSITE);
		openRenamedNativeMenu(InterfaceID.MenuNew.TEXT, "Digsite Pendant teleport",
			"Digsite", "Fossil Island", "<str>Lithkren</str>", "Nowhere");
		tick();
		assertNull(cachedSource(TeleportSource.MOUNTED_DIGSITE_PENDANT));
		tick();
		assertEquals(Set.of("digsite-digsite", "digsite-fossil-island"),
			AvailableTeleports.decode(cachedSource(TeleportSource.MOUNTED_DIGSITE_PENDANT)));
		assertNull(cachedSource(TeleportSource.MOUNTED_XERICS_TALISMAN));
	}

	@Test
	public void closedMenuCannotReuseOriginalLabelsWhenTheSameWidgetObjectsReturn() throws Exception
	{
		prepareFreshMountedSource(ObjectID.POH_AMULET_XERIC);
		openRenamedNativeMenu(InterfaceID.Menu.LJ_LAYER1, "Xeric's Talisman teleports",
			"Xeric's Lookout", "Xeric's Glade", "Xeric's Inferno", "Xeric's Heart",
			"Xeric's Honour", "Nowhere");
		plugin.onWidgetClosed(new WidgetClosed(InterfaceID.MENU, 0, true));
		tick();
		tick();
		assertNull(cachedSource(TeleportSource.MOUNTED_XERICS_TALISMAN));
	}

	@Test
	public void guestMenuOriginalInputsCannotTeachMountedUnlocks() throws Exception
	{
		prepareFreshMountedSource(ObjectID.POH_AMULET_XERIC);
		clickEntry("Friend's house", "<col=00ffff>Portal</col>", MenuAction.GAME_OBJECT_THIRD_OPTION, 0);
		changeGameState(GameState.LOADING);
		changeGameState(GameState.LOGGED_IN);
		openRenamedNativeMenu(InterfaceID.Menu.LJ_LAYER1, "Xeric's Talisman teleports",
			"Xeric's Lookout", "Xeric's Glade", "Xeric's Inferno", "Xeric's Heart",
			"Xeric's Honour", "Nowhere");
		tick();
		tick();
		assertNull(cachedSource(TeleportSource.MOUNTED_XERICS_TALISMAN));
		assertFalse(plugin.getGuidance().isOwnHouse());
	}

	private void prepareFreshMountedSource(int objectId) throws Exception
	{
		config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, ClueTeleportsPlugin.HOUSE_INVENTORY_KEY);
		for (TeleportSource source : TeleportSource.values())
		{
			config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, source.getCacheKey());
		}
		placeFixture(objectId, "Mounted teleport");
		enterOwnHouse();
		tick();
	}

	private void openRenamedNativeMenu(int component, String title, String... originals)
	{
		int script = component == InterfaceID.Menu.LJ_LAYER1 ? 217 : 9142;
		Object[] arguments = script == 217
			? new Object[]{script, title, String.join("|", originals), 1}
			: new Object[]{script, 1, title, String.join("|", originals), 0, 0, 0};
		ScriptPreFired pre = new ScriptPreFired(script);
		pre.setScriptEvent(proxy(ScriptEvent.class, (proxy, method, args) ->
		{
			if ("getArguments".equals(method.getName())) return arguments;
			throw new AssertionError("Unexpected script event call: " + method.getName());
		}));
		plugin.onScriptPreFired(pre);
		Widget[] rows = new Widget[originals.length];
		for (int index = 0; index < rows.length; index++)
		{
			rows[index] = MenuReaderFixture.widget("<col=ff00ff>Custom destination</col>",
				0x808080, false, new Widget[0], component, index, null);
		}
		extraWidgets.put(component, MenuReaderFixture.widget("", 0, false, rows, component, -1, null));
		plugin.onScriptPostFired(new ScriptPostFired(script));
	}

	@Test
	public void deterministicObeliskIsLearnedAndCanWinTheComparison() throws Exception
	{
		TeleportDestination obelisk = TeleportCatalog.byId("obelisk-50").get();
		setClue(new FixedLocationClue(obelisk.getLocation()));
		varbits.put(VarbitID.WILDERNESS_HARD_REWARD, 1);
		enterOwnHouse();
		placeFixture(ObjectID.POH_WILDERNESS_OBELISK, "Obelisk");
		tick();
		assertNull(cachedSource(TeleportSource.WILDERNESS_OBELISK));
		tick();
		assertEquals(6, AvailableTeleports.decode(cachedSource(TeleportSource.WILDERNESS_OBELISK)).size());
		assertEquals("Wilderness obelisk", plugin.getDisplay().first);
		assertEquals(obelisk.getName(), plugin.getDisplay().second);
	}

	@Test
	public void randomOnlyObeliskDoesNotBecomeAClosestRecommendation() throws Exception
	{
		setClue(new FixedLocationClue(TeleportCatalog.byId("obelisk-50").get().getLocation()));
		placeFixture(ObjectID.POH_WILDERNESS_OBELISK, "Obelisk");
		enterOwnHouse();
		tick();
		tick();
		assertEquals("", cachedSource(TeleportSource.WILDERNESS_OBELISK));
		assertEquals(first.getName(), plugin.getDisplay().second);
	}

	@Test
	public void anotherPlayersHouseCannotTeachOptionalFixtures()
	{
		placeFixture(ObjectID.POH_WILDERNESS_OBELISK, "Obelisk");
		varbits.put(VarbitID.WILDERNESS_HARD_REWARD, 1);
		clickEntry("Friend's house", "<col=00ffff>Portal</col>", MenuAction.GAME_OBJECT_THIRD_OPTION, 0);
		changeGameState(GameState.LOADING);
		changeGameState(GameState.LOGGED_IN);
		tick();
		tick();
		assertNull(cachedSource(TeleportSource.WILDERNESS_OBELISK));
	}

	@Test
	public void spentDailyCapeTeleportsAreFilteredOutsideTheHouse() throws Exception
	{
		TeleportDestination hunter = TeleportCatalog.byId("cape-red-chinchompas").get();
		config.setRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP,
			TeleportSource.CAPE_HANGER.getCacheKey(), hunter.getId());
		String inventory = config.getRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, ClueTeleportsPlugin.HOUSE_INVENTORY_KEY);
		config.setRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, ClueTeleportsPlugin.HOUSE_INVENTORY_KEY,
			inventory + ";0:1904:7040=CAPE_HANGER");
		setClue(new FixedLocationClue(hunter.getLocation()));
		tick();
		assertEquals(hunter.getName(), plugin.getDisplay().second);
		varbits.put(VarbitID.CHINCHOMPA_TELEPORTS, 5);
		tick();
		assertEquals(first.getName(), plugin.getDisplay().second);
		assertEquals(hunter.getId(), cachedSource(TeleportSource.CAPE_HANGER));
		varbits.put(VarbitID.CHINCHOMPA_TELEPORTS, 0);
		tick();
		assertEquals(hunter.getName(), plugin.getDisplay().second);
	}

	@Test
	public void loadedPortalReplacementUpdatesItsCachedArrival() throws Exception
	{
		TeleportDestination barrows = TeleportCatalog.byId("portal-barrows").get();
		setClue(new FixedLocationClue(barrows.getLocation()));
		enterOwnHouse();
		placeFixture(ObjectID.POH_PORTAL_TEAK_BARROWS, "Barrows Portal");
		tick();
		assertNull(cachedSource(TeleportSource.PORTAL_CHAMBER));
		tick();
		assertEquals("portal-barrows", cachedSource(TeleportSource.PORTAL_CHAMBER));
		assertEquals(barrows.getName(), plugin.getDisplay().second);
		placeFixture(ObjectID.POH_PORTAL_TEAK_VARROCK, "Varrock Portal");
		tick();
		assertEquals("portal-barrows", cachedSource(TeleportSource.PORTAL_CHAMBER));
		tick();
		assertEquals("portal-varrock", cachedSource(TeleportSource.PORTAL_CHAMBER));
		assertFalse(barrows.getName().equals(plugin.getDisplay().second));
	}


	@Test
	public void oldDestinationCachesCannotBypassTheFirstHouseVisit()
	{
		config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, ClueTeleportsPlugin.HOUSE_INVENTORY_KEY);
		tick();
		assertEquals("Visit your POH", plugin.getDisplay().first);
		assertFalse(plugin.getDisplay().isLearning());
		assertEquals(first.getId(), cachedSource(TeleportSource.PORTAL_NEXUS));
	}

	@Test
	public void enteringAHouseRequiresOnlyTheMenusOfDetectedFacilities()
	{
		config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, ClueTeleportsPlugin.HOUSE_INVENTORY_KEY);
		config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, TeleportSource.JEWELLERY_BOX.getCacheKey());
		placeFixture(ObjectID.POH_JEWELLERY_BOX_1, "Jewellery box");
		enterOwnHouse();
		assertEquals("Checking POH facilities", plugin.getDisplay().first);
		tick();
		assertLearning(Collections.emptySet());
		assertEquals(Set.of(TeleportSource.JEWELLERY_BOX), plugin.getDisplay().getRequiredSources());
		assertEquals("Learn POH teleports (0/1)", plugin.getDisplay().first);
		assertEquals(Set.of(TeleportSource.JEWELLERY_BOX), plugin.getGuidance().getPendingSources());
	}

	@Test
	public void aHouseWithoutMenuFacilitiesCanRecommendItsExit() throws Exception
	{
		config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, ClueTeleportsPlugin.HOUSE_INVENTORY_KEY);
		placeFixture(ObjectID.POH_EXIT_PORTAL, "Portal");
		varbits.put(VarbitID.POH_HOUSE_LOCATION, 1);
		setClue(new FixedLocationClue(TeleportCatalog.byId("exit-rimmington").get().getLocation()));
		enterOwnHouse();
		tick();
		assertFalse(plugin.getDisplay().isLearning());
		assertEquals("House exit portal", plugin.getDisplay().first);
		assertEquals("Rimmington", plugin.getDisplay().second);
		assertEquals("exit-rimmington", plugin.getGuidance().getDestinationId());
	}

	@Test
	public void aLaterDiscoveredMenuFacilityReopensTheChecklist()
	{
		config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, ClueTeleportsPlugin.HOUSE_INVENTORY_KEY);
		placeFixture(ObjectID.POH_EXIT_PORTAL, "Portal");
		varbits.put(VarbitID.POH_HOUSE_LOCATION, 1);
		enterOwnHouse();
		tick();
		assertFalse(plugin.getDisplay().isLearning());
		placeFixture(ObjectID.POH_JEWELLERY_BOX_1, "Jewellery box");
		tick();
		tick();
		assertEquals("Learn POH teleports (0/1)", plugin.getDisplay().first);
		assertNull(cachedSource(TeleportSource.JEWELLERY_BOX));
	}

	@Test
	public void removingTheLastVisibleMenuFixtureClearsItsRequirement()
	{
		config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, ClueTeleportsPlugin.HOUSE_INVENTORY_KEY);
		placeFixture(ObjectID.POH_JEWELLERY_BOX_1, "Jewellery box");
		enterOwnHouse();
		tick();
		config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, TeleportSource.JEWELLERY_BOX.getCacheKey());
		tick();
		assertTrue(plugin.getDisplay().isLearning());
		Tile empty = proxy(Tile.class, (proxy, method, args) ->
		{
			if ("getGameObjects".equals(method.getName())) return new GameObject[0];
			if ("getWallObject".equals(method.getName()) || "getDecorativeObject".equals(method.getName())) return null;
			throw new AssertionError("Unexpected empty tile call: " + method.getName());
		});
		sceneTiles = new Tile[][][]{{{empty}}};
		tick();
		tick();
		assertFalse(plugin.getDisplay().isLearning());
		assertEquals("No available POH teleports", plugin.getDisplay().first);
	}

	@Test
	public void bloodRuneChallengeRecommendsTheClosestLearnedPohDestination() throws Exception
	{
		String task = "bind a blood rune at the blood altar.";
		SkillChallengeClue clue = SkillChallengeClue.forText(task, task);
		assertNotNull(clue);
		setClue(clue);
		config.setRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP,
			TeleportSource.FAIRY_RING.getCacheKey(), "fairy-cis,fairy-aiq");
		tick();
		assertEquals("Fairy ring", plugin.getDisplay().first);
		assertEquals(TeleportCatalog.byId("fairy-cis").get().getName(), plugin.getDisplay().second);
		assertNotNull(plugin.getDisplay().third);
	}


	@Test
	public void jewelleryTierChangeInvalidatesPreviouslyLearnedDestinations() throws Exception
	{
		config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, ClueTeleportsPlugin.HOUSE_INVENTORY_KEY);
		placeFixture(ObjectID.POH_JEWELLERY_BOX_3, "Ornate jewellery box");
		enterOwnHouse();
		tick();
		TeleportDestination fishing = TeleportCatalog.byId("jewellery-fishing-guild").get();
		config.setRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP,
			TeleportSource.JEWELLERY_BOX.getCacheKey(), fishing.getId());
		setClue(new FixedLocationClue(fishing.getLocation()));
		tick();
		assertEquals(fishing.getName(), plugin.getDisplay().second);
		placeFixture(ObjectID.POH_JEWELLERY_BOX_1, "Basic jewellery box");
		tick();
		tick();
		assertEquals("Learn POH teleports (0/1)", plugin.getDisplay().first);
		assertNull(cachedSource(TeleportSource.JEWELLERY_BOX));
		assertNull(plugin.getDisplay().third);
	}

	@Test
	public void installedMenuSetupRemainsVisibleInTheHouseWithoutAClue() throws Exception
	{
		config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, ClueTeleportsPlugin.HOUSE_INVENTORY_KEY);
		config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, TeleportSource.JEWELLERY_BOX.getCacheKey());
		setClue(null);
		placeFixture(ObjectID.POH_JEWELLERY_BOX_1, "Jewellery box");
		enterOwnHouse();
		tick();
		assertEquals("Learn POH teleports (0/1)", plugin.getDisplay().first);
		config.setRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, TeleportSource.JEWELLERY_BOX.getCacheKey(), "");
		tick();
		assertNull(plugin.getDisplay());
	}


	@Test
	public void pendingRouteSearchHidesThePreviousRecommendation() throws Exception
	{
		tick();
		assertEquals(first.getName(), plugin.getDisplay().second);
		setField(plugin, "routing", (TeleportRouting) (client, profile, targets, arrivals, radius) ->
			TeleportRouting.Plan.status(TeleportRouting.Plan.Status.SEARCHING));
		tick();
		assertEquals("Finding best POH route", plugin.getDisplay().first);
		assertNull(plugin.getDisplay().third);
		assertNull(plugin.getGuidance().getDestinationId());
	}

	@Test
	public void routeFailureNeverFallsBackToAnExactGeographicDestination() throws Exception
	{
		setField(plugin, "routing", (TeleportRouting) (client, profile, targets, arrivals, radius) ->
			TeleportRouting.Plan.status(TeleportRouting.Plan.Status.NO_ROUTE));
		tick();
		assertEquals("No mapped walking route", plugin.getDisplay().first);
		assertNull(plugin.getDisplay().third);
		assertNull(plugin.getGuidance().getDestinationId());
	}

	@Test
	public void routeDisplayUsesWalkedTilesAndTransitionTimeInsteadOfGeographicDistance() throws Exception
	{
		setField(plugin, "routing", (TeleportRouting) (client, profile, targets, arrivals, radius) ->
			TeleportRouting.Plan.found(first, targets.get(0), 42, 10, 2));
		tick();
		assertEquals("~13s running, 10 walking tiles", plugin.getDisplay().third);
	}

	private String cachedSource(TeleportSource source)
	{
		return config.getRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, source.getCacheKey());
	}

	private void placeFixture(int id, String name)
	{
		ObjectComposition definition = proxy(ObjectComposition.class, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getId": return id;
				case "getName": return name;
				case "getActions": return new String[5];
				case "getImpostorIds": return null;
				default: throw new AssertionError("Unexpected object definition call: " + method.getName());
			}
		});
		objectDefinitions.put(id, definition);
		GameObject object = proxy(GameObject.class, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getId": return id;
				case "getSceneMinLocation": return null;
				default: throw new AssertionError("Unexpected fixture call: " + method.getName());
			}
		});
		Tile tile = proxy(Tile.class, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getGameObjects": return new GameObject[]{object};
				case "getWallObject":
				case "getDecorativeObject": return null;
				default: throw new AssertionError("Unexpected fixture tile call: " + method.getName());
			}
		});
		sceneTiles = new Tile[][][]{{{tile}}};
	}

	private void tick()
	{
		tickCount++;
		plugin.onGameTick(new GameTick());
	}

	private void setClue(ClueScroll clue) throws Exception
	{
		setField(coreClues, "clue", clue);
	}

	private void assertLearning(Set<TeleportSource> learned)
	{
		assertNotNull(plugin.getDisplay());
		assertTrue(plugin.getDisplay().isLearning());
		assertEquals(learned, plugin.getDisplay().getLearnedSources());
	}

	private void seedOtherSourcesEmpty()
	{
		config.setRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, ClueTeleportsPlugin.HOUSE_INVENTORY_KEY,
			"0:1900:7040=FAIRY_RING;0:1901:7040=SPIRIT_TREE;0:1902:7040=PORTAL_NEXUS;0:1903:7040=JEWELLERY_BOX");
		for (TeleportSource source : Set.of(TeleportSource.FAIRY_RING, TeleportSource.SPIRIT_TREE, TeleportSource.PORTAL_NEXUS, TeleportSource.JEWELLERY_BOX))
		{
			if (source != TeleportSource.PORTAL_NEXUS)
			{
				config.setRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, source.getCacheKey(), "");
			}
		}
	}

	private void seedCache(TeleportDestination destination)
	{
		config.setRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, TeleportSource.PORTAL_NEXUS.getCacheKey(),
			AvailableTeleports.encode(Collections.singleton(destination.getId())));
	}

	private void changeGameState(GameState state)
	{
		gameState = state;
		GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		plugin.onGameStateChanged(event);
	}

	private void enterOwnHouse()
	{
		// Entering a house closes existing travel menus. Real entry can emit
		// multiple LOADING events and need not emit any welcome chat message.
		nexusOpen = false;
		clickEntry("Home", "<col=00ffff>Portal</col>", MenuAction.GAME_OBJECT_SECOND_OPTION, 0);
		changeGameState(GameState.LOADING);
		changeGameState(GameState.LOADING);
		changeGameState(GameState.LOGGED_IN);
		tick();
	}

	private void clickEntry(String option, String target, MenuAction action, int widgetId)
	{
		MenuEntry entry = proxy(MenuEntry.class, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getOption":
					return option;
				case "getTarget":
					return target;
				case "getType":
					return action;
				case "getParam1":
					return widgetId;
				case "getParam0":
				case "getIdentifier":
					return 0;
				case "getWidget":
					return null;
				case "isItemOp":
					return false;
				default:
					throw new AssertionError("Unexpected menu entry call: " + method.getName());
			}
		});
		plugin.onMenuOptionClicked(new MenuOptionClicked(entry));
	}

	private void openNexus(TeleportDestination... destinations)
	{
		placeFixture(ObjectID.POH_NEXUS_PORTAL_1, "Portal nexus");
		nexusOpen = true;
		varbits.clear();
		enumValues.clear();
		structs.clear();
		int[] slots = TeleportCatalog.nexusSlotVarbits();
		for (int index = 0; index < destinations.length; index++)
		{
			int key = index + 1;
			int structId = index + 100;
			String name = destinations[index].getName();
			varbits.put(slots[index], key);
			enumValues.put(key, structId);
			structs.put(structId, proxy(StructComposition.class, (proxy, method, args) ->
			{
				if ("getStringValue".equals(method.getName()))
				{
					assertEquals(660, args[0]);
					return name;
				}
				if ("getIntValue".equals(method.getName()))
				{
					return -1;
				}
				throw new AssertionError("Unexpected struct call: " + method.getName());
			}));
		}
	}

	private Widget regionalXericMenu()
	{
		return MenuReaderFixture.container(
			MenuReaderFixture.text("1: Shayzien"), MenuReaderFixture.text("2: Hosidius"),
			MenuReaderFixture.text("3: Lovakengj"), MenuReaderFixture.text("4: Kourend Castle"),
			MenuReaderFixture.text("5: Chambers of Xeric"), MenuReaderFixture.text("6: Nowhere"));
	}

	private void clearNexusCache()
	{
		config.unsetRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, TeleportSource.PORTAL_NEXUS.getCacheKey());
		writes.count = 0;
	}

	private String encodedNexusCache()
	{
		return config.getRSProfileConfiguration(ClueTeleportsPlugin.CONFIG_GROUP, TeleportSource.PORTAL_NEXUS.getCacheKey());
	}

	private Set<String> cachedNexusIds()
	{
		return AvailableTeleports.decode(encodedNexusCache());
	}

	private ConfigManager createInMemoryConfig(EventBus events) throws Exception
	{
		// ConfigManager normally schedules disk/cloud persistence. This executor
		// records no work and starts no thread; all profile data stays in memory.
		ScheduledExecutorService scheduler = (ScheduledExecutorService) Proxy.newProxyInstance(
			ScheduledExecutorService.class.getClassLoader(), new Class<?>[]{ScheduledExecutorService.class},
			(proxy, method, args) ->
			{
				if ("scheduleWithFixedDelay".equals(method.getName()))
				{
					return null;
				}
				throw new UnsupportedOperationException(method.getName());
			});
		Constructor<?> constructor = ConfigManager.class.getDeclaredConstructors()[0];
		constructor.setAccessible(true);
		ConfigManager manager = (ConfigManager) constructor.newInstance("test", scheduler, events, client,
			new Gson(), null, null, null);
		Class<?> dataType = Class.forName("net.runelite.client.config.ConfigData");
		Constructor<?> dataConstructor = dataType.getDeclaredConstructor(File.class);
		dataConstructor.setAccessible(true);
		Object data = dataConstructor.newInstance(temporary.newFile("config.properties"));
		setField(manager, "configProfile", data);
		setField(manager, "rsProfileConfigProfile", data);
		return manager;
	}

	private static void setField(Object instance, String name, Object value) throws Exception
	{
		Field field = instance.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(instance, value);
	}

	private static <T> T proxy(Class<T> type, InvocationHandler handler)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
	}


	/** Availability/UI tests use a synchronous open-map fixture, not the production routing worker. */
	private static final class FixtureRouting implements TeleportRouting
	{
		@Override
		public Plan update(Client client, String profile, java.util.List<WorldPoint> targets,
			java.util.Collection<TeleportDestination> arrivals, int interactionRadius)
		{
			TeleportDestination best = null;
			WorldPoint bestTarget = null;
			int bestCost = Integer.MAX_VALUE;
			for (WorldPoint target : targets)
			{
				for (TeleportDestination arrival : arrivals)
				{
					// This test graph has no fixture passage between its surface and dungeon maps.
					if ((target.getY() >= 6400) != (arrival.getLocation().getY() >= 6400)) continue;
					int cost = target.distanceTo2D(arrival.getLocation());
					if (best == null || cost < bestCost || cost == bestCost
						&& (arrival.getSource().ordinal() < best.getSource().ordinal()
							|| arrival.getSource() == best.getSource() && arrival.getId().compareTo(best.getId()) < 0))
					{
						best = arrival;
						bestTarget = target;
						bestCost = cost;
					}
				}
			}
			return best == null ? Plan.status(Plan.Status.NO_ROUTE) : Plan.found(best, bestTarget, bestCost, bestCost, 0);
		}
	}

	public static final class CacheWrites
	{
		private int count;

		@Subscribe
		public void onConfigChanged(ConfigChanged event)
		{
			if (ClueTeleportsPlugin.CONFIG_GROUP.equals(event.getGroup())
				&& java.util.Arrays.stream(TeleportSource.values()).anyMatch(source -> source.getCacheKey().equals(event.getKey())))
			{
				count++;
			}
		}
	}

	private static final class FixedLocationClue extends ClueScroll implements LocationClueScroll
	{
		private final WorldPoint location;

		private FixedLocationClue(WorldPoint location)
		{
			this.location = location;
		}

		@Override
		public WorldPoint getLocation(ClueScrollPlugin plugin)
		{
			return location;
		}

		@Override
		public void makeOverlayHint(PanelComponent panelComponent, ClueScrollPlugin plugin)
		{
		}

		@Override
		public void makeWorldOverlayHint(Graphics2D graphics, ClueScrollPlugin plugin)
		{
		}
	}
}
