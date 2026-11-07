package com.clueteleports.routing;

import com.clueteleports.ClueTargetResolver;
import com.clueteleports.TeleportCatalog;
import com.clueteleports.TeleportDestination;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cluescrolls.clues.CrypticClue;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CrypticCombatRoutingTest
{
	private static RoutingData data;

	@BeforeClass
	public static void loadRoutes() throws Exception
	{
		data = RoutingData.load();
	}

	@Test
	public void frostNaguaClueFindsAnAvailablePohArrivalViaTheQuestGatedLift()
	{
		ClueTargetResolver.Resolution target = resolve("Banish a chilling spirit.", new WorldPoint(1627, 9623, 0));
		assertEquals(WalkingRouteResult.Status.NO_ROUTE, route(target, Map.of(), Map.of()).getStatus());

		WalkingRouteResult unlocked = route(target, Map.of(Quest.THE_HEART_OF_DARKNESS, QuestState.FINISHED), Map.of());
		assertEquals(WalkingRouteResult.Status.FOUND, unlocked.getStatus());
		assertEquals("fairy-ais", unlocked.getDestination().getId());
		assertTrue(unlocked.getTransitionCount() >= 1);
		assertTrue(unlocked.getTarget().distanceTo(target.getLocations().get(0)) <= 1);
	}

	@Test
	public void earthenNaguaClueUsesTheActivatedTonaliLiftAndRope()
	{
		ClueTargetResolver.Resolution target = resolve("Crush the spirited earth.", new WorldPoint(1307, 9420, 1));
		Map<Quest, QuestState> quests = Map.of(Quest.THE_FINAL_DAWN, QuestState.FINISHED);
		assertEquals(WalkingRouteResult.Status.NO_ROUTE, route(target, quests, Map.of(16694, 0)).getStatus());

		WalkingRouteResult unlocked = route(target, quests, Map.of(16694, 1));
		assertEquals(WalkingRouteResult.Status.FOUND, unlocked.getStatus());
		assertEquals("fairy-ais", unlocked.getDestination().getId());
		assertTrue(unlocked.getTransitionCount() >= 2);
		assertEquals(target.getLocations().get(0), unlocked.getTarget());
	}

	private static ClueTargetResolver.Resolution resolve(String text, WorldPoint expected)
	{
		CrypticClue clue = CrypticClue.forText(text);
		assertNotNull(clue);
		ClueTargetResolver.Resolution target = new ClueTargetResolver().resolve(clue, null);
		assertEquals(ClueTargetResolver.State.RESOLVED, target.getState());
		assertEquals(List.of(expected), target.getLocations());
		return target;
	}

	private static WalkingRouteResult route(ClueTargetResolver.Resolution target, Map<Quest, QuestState> quests,
		Map<Integer, Integer> varbits)
	{
		AccountRouteState state = new AccountRouteState(Map.of(), Map.of(), quests, varbits, Map.of(), Map.of(), Map.of(), false);
		List<RouteTransport> eligible = data.getTransports().stream()
			.filter(edge -> edge.getRequirements().isSatisfied(state)).collect(Collectors.toList());
		List<TeleportDestination> arrivals = List.of(TeleportCatalog.byId("fairy-ais").orElseThrow(),
			TeleportCatalog.byId("nexus-civitas-illa-fortis").orElseThrow());
		List<WorldPoint> goals = RouteGoals.qualify(data.getCollisionMap(), target.getLocations(), target.getInteractionRadius());
		return new WalkingRouteSearch().search(data.getCollisionMap(), eligible, goals, arrivals, () -> false);
	}
}
