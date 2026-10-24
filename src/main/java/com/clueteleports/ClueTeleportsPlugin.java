package com.clueteleports;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "POH Clue Teleports",
	description = "Compare available POH teleports using account-aware walking routes to the active clue",
	tags = {"poh", "house", "clue", "teleport", "nexus", "obelisk"}
)
@PluginDependency(ClueScrollPlugin.class)
public class ClueTeleportsPlugin extends Plugin
{
	static final String CONFIG_GROUP = "clueteleports";
	static final String HOUSE_INVENTORY_KEY = "houseFacilities";
	private static final Logger log = LoggerFactory.getLogger(ClueTeleportsPlugin.class);
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ConfigManager configManager;
	@Inject private PluginManager pluginManager;
	@Inject private OverlayManager overlayManager;
	@Inject private ClueTeleportsOverlay overlay;
	private final ClueTargetResolver targetResolver = new ClueTargetResolver();
	private final FairyRingTravelLog fairyTravelLog = new FairyRingTravelLog();
	private final NexusMenuScroll nexusMenuScroll = new NexusMenuScroll();
	private TeleportRouting routing;
	private final HouseContext houseContext = new HouseContext();
	private final MenuObservations observations = new MenuObservations();
	private final NativeTeleportMenus nativeMenus = new NativeTeleportMenus();
	private final Map<TeleportSource, String> pendingFixtures = new EnumMap<>(TeleportSource.class);
	private AvailabilityCache availability = new AvailabilityCache();
	private HouseInventory houseInventory = new HouseInventory();
	private String pendingInventory;
	private boolean ownHouse;
	private HouseLearningOverlay learningOverlay;
	private volatile HouseLearningOverlay.Guidance guidance = new HouseLearningOverlay.Guidance(false,
		Collections.emptySet(), null, null);
	private volatile ClueTeleportsOverlay.Display display;
	private volatile boolean running;
	private String profileKey;
	private String learningHint = "Enter via Home or Teleport to House";

	@Override
	protected void startUp()
	{
		running = true;
		routing = new AsyncRoutePlanner();
		log.info("POH Clue Teleports started with house entry detection");
		houseContext.reset();
		observations.clear();
		nativeMenus.clear();
		pendingFixtures.clear();
		pendingInventory = null;
		overlayManager.add(overlay);
		learningOverlay = new HouseLearningOverlay(client, this::getGuidance, nativeMenus);
		overlayManager.add(learningOverlay);
		clientThread.invokeLater(() -> refresh(false));
	}

	@Override
	protected void shutDown()
	{
		running = false;
		if (routing != null) routing.close();
		routing = null;
		clearGuidance();
		ownHouse = false;
		houseInventory = new HouseInventory();
		display = null;
		profileKey = null;
		availability = new AvailabilityCache();
		houseContext.reset();
		observations.clear();
		nativeMenus.clear();
		pendingFixtures.clear();
		pendingInventory = null;
		overlayManager.remove(overlay);
		if (learningOverlay != null) overlayManager.remove(learningOverlay);
		learningOverlay = null;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		refresh(true);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (running)
		{
			houseContext.onMenuOptionClicked(event, client);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		int group = event.getGroupId();
		if (running) nativeMenus.onWidgetLoaded(group);
		if (running && (group == InterfaceID.FAIRYRINGS || group == InterfaceID.FAIRYRINGS_LOG))
		{
			fairyTravelLog.reset();
		}
		if (running && group == InterfaceID.TELENEXUS_TELEPORT)
		{
			nexusMenuScroll.reset();
		}
		if (running && (group == InterfaceID.TELENEXUS_TELEPORT || group == InterfaceID.POH_JEWELLERY_BOX
			|| group == InterfaceID.FAIRYRINGS || group == InterfaceID.MENU || group == InterfaceID.MENU_NEW))
		{
			log.debug("Travel interface {} opened; own house confirmed={}", group, houseContext.isOwnHouse(client));
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (running && NativeTeleportMenus.isMenuScript(event.getScriptId()))
		{
			if (houseContext.isOwnHouse(client)) nativeMenus.onScriptPreFired(event);
			else nativeMenus.clear();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (running && NativeTeleportMenus.isMenuScript(event.getScriptId()))
		{
			if (houseContext.isOwnHouse(client)) nativeMenus.onScriptPostFired(client, event);
			else nativeMenus.clear();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		nativeMenus.onWidgetClosed(event.getGroupId());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		houseContext.onGameStateChanged(event.getGameState());
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			invalidateRoute();
			ownHouse = false;
			clearGuidance();
			display = null;
			observations.clear();
			nativeMenus.clear();
			pendingFixtures.clear();
			pendingInventory = null;
		}
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			profileKey = null;
			availability = new AvailabilityCache();
		}
	}

	private void refresh(boolean learnFromHouse)
	{
		refreshDisplay(learnFromHouse);
		HouseLearningOverlay.Guidance current = guidance;
		fairyTravelLog.update(client, running && current.isOwnHouse() && current.getSource() == TeleportSource.FAIRY_RING
			? current.getDestinationId() : null);
		nexusMenuScroll.update(client, running && current.isOwnHouse() && current.getSource() == TeleportSource.PORTAL_NEXUS
			? current.getDestinationId() : null);
	}

	private void refreshDisplay(boolean learnFromHouse)
	{
		if (!running)
		{
			return;
		}
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		String currentProfile = loggedIn ? configManager.getRSProfileKey() : null;
		if (!Objects.equals(profileKey, currentProfile))
		{
			if (profileKey != null)
			{
				houseContext.reset();
			}
			invalidateRoute();
			profileKey = currentProfile;
			availability = new AvailabilityCache();
			houseInventory = new HouseInventory();
			ownHouse = false;
			clearGuidance();
			observations.clear();
			nativeMenus.clear();
			pendingFixtures.clear();
			pendingInventory = null;
		}
		if (!loggedIn || currentProfile == null)
		{
			invalidateRoute();
			clearGuidance();
			display = null;
			return;
		}

		for (TeleportSource source : TeleportSource.values())
		{
			availability.restore(source, configManager.getRSProfileConfiguration(CONFIG_GROUP, source.getCacheKey()));
		}
		houseInventory.restore(configManager.getRSProfileConfiguration(CONFIG_GROUP, HOUSE_INVENTORY_KEY));
		if (learnFromHouse)
		{
			observeHouseTravel();
		}
		Set<String> selected = availability.availableIds().stream()
			.filter(id -> TeleportCatalog.byId(id).map(destination -> houseInventory.installedSources().contains(destination.getSource())).orElse(false))
			.filter(id -> CapeReader.availableNow(client, id)).collect(Collectors.toSet());

		// Use the managed dependency; injecting its class can construct an inactive
		// second instance on clients that export only ClueScrollService.
		ClueScrollPlugin clues = pluginManager.getPlugins().stream()
			.filter(ClueScrollPlugin.class::isInstance).map(ClueScrollPlugin.class::cast)
			.filter(pluginManager::isPluginEnabled).findFirst().orElse(null);
		ClueTargetResolver.Resolution target = targetResolver.resolve(clues);
		Map<TeleportSource, Set<String>> learned = availability.snapshot();
		Set<TeleportSource> required = houseInventory.requiredMenus();
		Set<TeleportSource> pending = EnumSet.noneOf(TeleportSource.class);
		pending.addAll(required);
		pending.removeAll(learned.keySet());
		guidance = new HouseLearningOverlay.Guidance(ownHouse, pending, null, null);
		boolean hasClue = target.getState() != ClueTargetResolver.State.NO_CLUE;
		if (!hasClue && !ownHouse)
		{
			invalidateRoute();
			display = null;
			return;
		}
		if (!houseInventory.isSurveyed())
		{
			invalidateRoute();
			display = new ClueTeleportsOverlay.Display(ownHouse ? "Checking POH facilities" : "Visit your POH",
				ownHouse ? "Wait for the house to finish loading" : "Enter via Home or Teleport to House", null);
			return;
		}
		if (!pending.isEmpty())
		{
			invalidateRoute();
			display = ClueTeleportsOverlay.Display.learning(learned, required, learningHint);
			return;
		}
		if (!hasClue)
		{
			invalidateRoute();
			display = null;
			return;
		}
		switch (target.getState())
		{
			case NO_LOCATION:
				invalidateRoute();
				display = new ClueTeleportsOverlay.Display("Clue location not known", "Follow the clue's current task", null);
				break;
			case MULTIPLE_LOCATIONS:
				if (!target.hasAlternativeTaskLocations())
				{
					invalidateRoute();
					display = new ClueTeleportsOverlay.Display("Multiple clue locations", "Narrow down the current clue", null);
					break;
				}
				// Known alternative task sites are all valid, unlike unresolved clue steps.
			case RESOLVED:
				if (selected.isEmpty())
				{
					invalidateRoute();
					display = new ClueTeleportsOverlay.Display("No available POH teleports", "No usable destinations in learned sources", null);
					break;
				}
				TeleportRouting.Plan route = routing == null ? TeleportRouting.Plan.status(TeleportRouting.Plan.Status.UNAVAILABLE)
					: routing.update(client, profileKey, target.getLocations(), TeleportCatalog.all().stream()
						.filter(destination -> selected.contains(destination.getId())).collect(Collectors.toList()), target.getInteractionRadius());
				if (route.status == TeleportRouting.Plan.Status.FOUND)
				{
					guidance = new HouseLearningOverlay.Guidance(ownHouse, pending,
						route.destination.getSource(), route.destination.getId());
					display = new ClueTeleportsOverlay.Display(route.destination.getSource().getDisplayName(),
						route.destination.getName(), describeRoute(route));
				}
				else display = routeStatus(route.status);
				break;
		}
	}


	private void invalidateRoute()
	{
		if (routing != null) routing.invalidate();
	}

	private static String describeRoute(TeleportRouting.Plan route)
	{
		long seconds = (long) Math.ceil(route.costHalfTicks * 0.3);
		String duration = seconds < 60 ? seconds + "s" : seconds / 60 + "m " + seconds % 60 + "s";
		return "~" + duration + " running, " + route.walkingTiles + " walking tiles";
	}

	private static ClueTeleportsOverlay.Display routeStatus(TeleportRouting.Plan.Status status)
	{
		switch (status)
		{
			case PREPARING: return new ClueTeleportsOverlay.Display("Loading walking map", "Preparing paths and passage data", null);
			case SEARCHING: return new ClueTeleportsOverlay.Display("Finding best POH route", "Checking paths and usable shortcuts", null);
			case LIMIT: return new ClueTeleportsOverlay.Display("Route search incomplete", "Minimum route could not be confirmed", null);
			case NO_ROUTE: return new ClueTeleportsOverlay.Display("No mapped walking route", "Known paths do not reach this clue", null);
			default: return new ClueTeleportsOverlay.Display("Walking routes unavailable", "Unable to calculate a walking route", null);
		}
	}

	private void observeHouseTravel()
	{
		ownHouse = houseContext.isOwnHouse(client);
		if (!ownHouse)
		{
			learningHint = "Enter via Home or Teleport to House";
			observations.clear();
			nativeMenus.clear();
			pendingFixtures.clear();
			pendingInventory = null;
			return;
		}
		learningHint = "Open the travel menus marked Not learned";
		HouseTransportReader.Observation fixtureReading = HouseTransportReader.readSnapshot(client, nativeMenus);
		recordHouseInventory(fixtureReading);
		recordFixtures(TeleportSource.PORTAL_CHAMBER, fixtureReading.coveredTiles, fixtureReading.portalFixtures);
		recordFixtures(TeleportSource.CAPE_HANGER, fixtureReading.coveredTiles, fixtureReading.capeFixtures);
		Map<TeleportSource, Set<String>> fixtures = fixtureReading.sources;
		for (TeleportSource source : TeleportSource.values())
		{
			if (TeleportSource.menuSources().contains(source) || source == TeleportSource.PORTAL_CHAMBER
				|| source == TeleportSource.CAPE_HANGER)
			{
				continue;
			}
			Optional<Set<String>> reading = Optional.ofNullable(fixtures.get(source));
			recordObservation(source, reading);
		}
		for (TeleportSource source : TeleportSource.menuSources())
		{
			if (!houseInventory.installedSources().contains(source) && !fixtureReading.installedSources.contains(source))
			{
				observations.observe(source, Optional.empty());
				continue;
			}
			Optional<Set<String>> reading;
			switch (source)
			{
				case FAIRY_RING:
					reading = FairyRingReader.readOpenMenu(client);
					break;
				case SPIRIT_TREE:
					reading = SpiritTreeReader.readOpenMenu(client, nativeMenus);
					break;
				case PORTAL_NEXUS:
					reading = NexusImporter.readOpenMenu(client);
					break;
				case JEWELLERY_BOX:
					reading = JewelleryBoxReader.readOpenMenu(client);
					break;
				case MOUNTED_DIGSITE_PENDANT:
				case MOUNTED_XERICS_TALISMAN:
					reading = Optional.ofNullable(fixtures.get(source));
					break;
				default:
					throw new IllegalStateException("Unknown teleport source: " + source);
			}
			if (reading.isPresent())
			{
				learningHint = "Reading " + source.getDisplayName() + " destinations";
			}
			recordObservation(source, reading);
		}
	}


	private void recordHouseInventory(HouseTransportReader.Observation reading)
	{
		if (reading.coveredTiles.isEmpty() || !houseInventory.isSurveyed() && !reading.sceneReady)
		{
			pendingInventory = null;
			return;
		}
		HouseInventory next = new HouseInventory();
		next.restore(houseInventory.isSurveyed() ? houseInventory.encode() : null);
		next.observe(reading.coveredTiles, reading.installedFixtures, reading.installedFixtureIds);
		String encoded = next.encode();
		if (encoded.equals(pendingInventory))
		{
			if (houseInventory.isSurveyed())
			{
				for (TeleportSource source : next.requiredMenus())
				{
					if (!houseInventory.installedSources().contains(source)
						|| !houseInventory.capabilitySignature(source).isEmpty()
							&& !houseInventory.capabilitySignature(source).equals(next.capabilitySignature(source)))
					{
						configManager.unsetRSProfileConfiguration(CONFIG_GROUP, source.getCacheKey());
						availability.restore(source, null);
						observations.observe(source, Optional.empty());
					}
				}
			}
			String saved = configManager.getRSProfileConfiguration(CONFIG_GROUP, HOUSE_INVENTORY_KEY);
			if (!encoded.equals(saved)) configManager.setRSProfileConfiguration(CONFIG_GROUP, HOUSE_INVENTORY_KEY, encoded);
			houseInventory = next;
		}
		pendingInventory = encoded;
	}

	private void clearGuidance()
	{
		fairyTravelLog.reset();
		nexusMenuScroll.reset();
		guidance = new HouseLearningOverlay.Guidance(false, Collections.emptySet(), null, null);
	}

	HouseLearningOverlay.Guidance getGuidance() { return guidance; }

	private void recordFixtures(TeleportSource source, Set<String> coveredTiles, Map<String, Set<String>> seen)
	{
		String key = source.getCacheKey() + "Fixtures";
		String saved = configManager.getRSProfileConfiguration(CONFIG_GROUP, key);
		if (coveredTiles.isEmpty() || saved == null && seen.isEmpty())
		{
			pendingFixtures.remove(source);
			return;
		}
		FixtureCache fixtures = new FixtureCache(source);
		fixtures.restore(saved);
		fixtures.observe(coveredTiles, seen);
		String next = fixtures.encode();
		String pending = pendingFixtures.put(source, next);
		if (next.equals(pending) && running && Objects.equals(profileKey, configManager.getRSProfileKey()))
		{
			if (!next.equals(saved))
			{
				configManager.setRSProfileConfiguration(CONFIG_GROUP, key, next);
			}
			recordAvailable(source, fixtures.availableIds());
		}
	}

	private void recordAvailable(TeleportSource source, Set<String> ids)
	{
		if (availability.record(source, ids))
		{
			configManager.setRSProfileConfiguration(CONFIG_GROUP, source.getCacheKey(), AvailableTeleports.encode(ids));
			log.info("Cached {} {} destinations", ids.size(), source.getDisplayName());
		}
	}

	private void recordObservation(TeleportSource source, Optional<Set<String>> reading)
	{
		observations.observe(source, reading).ifPresent(ids ->
		{
			if (running && Objects.equals(profileKey, configManager.getRSProfileKey()))
			{
				recordAvailable(source, ids);
			}
		});
	}

	void forgetAvailability()
	{
		String expectedProfile = profileKey;
		clientThread.invokeLater(() ->
		{
			if (!running || expectedProfile == null || client.getGameState() != GameState.LOGGED_IN
				|| !expectedProfile.equals(configManager.getRSProfileKey()))
			{
				return;
			}
			for (TeleportSource source : TeleportSource.values())
			{
				configManager.unsetRSProfileConfiguration(CONFIG_GROUP, source.getCacheKey());
				configManager.unsetRSProfileConfiguration(CONFIG_GROUP, source.getCacheKey() + "Fixtures");
			}
			configManager.unsetRSProfileConfiguration(CONFIG_GROUP, HOUSE_INVENTORY_KEY);
			houseInventory = new HouseInventory();
			observations.clear();
			nativeMenus.clear();
			pendingFixtures.clear();
			pendingInventory = null;
			refresh(false);
		});
	}

	ClueTeleportsOverlay.Display getDisplay()
	{
		return display;
	}

}
