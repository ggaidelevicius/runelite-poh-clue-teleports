package com.clueteleports.routing;

import com.clueteleports.TeleportCatalog;
import com.clueteleports.TeleportDestination;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pinned-data connectivity checks. All bundled physical edges are supplied here
 * deliberately; account eligibility and unknown-state gates have separate tests.
 * No portable teleport edges are present in the bundled physical tables.
 */
public class BundledRoutingTest
{
	private static RoutingData data;

	@BeforeClass
	public static void loadPinnedData() throws Exception
	{
		data = RoutingData.load();
	}

	@Test
	public void surfaceClueChoosesTheNearbyMusicCapeArrival()
	{
		TeleportDestination music = TeleportCatalog.byId("cape-falo-the-bard").get();
		WalkingRouteResult result = route(new WorldPoint(2689, 3550, 0),
			List.of(music, TeleportCatalog.byId("fairy-blr").get()), 1);

		assertEquals(WalkingRouteResult.Status.FOUND, result.getStatus());
		assertEquals(music.getId(), result.getDestination().getId());
		assertTrue(result.getWalkingTiles() > 0);
		assertTrue(result.getCostHalfTicks() < 30);
	}

	@Test
	public void kourendBloodAltarIsReachedThroughTheMappedTerrain()
	{
		TeleportDestination ring = TeleportCatalog.byId("fairy-cis").get();
		// Core RuneLite RunecraftingAltarLocation/ChallengeLocations marker.
		WalkingRouteResult result = route(new WorldPoint(1716, 3827, 0), List.of(ring), 2);

		assertEquals(WalkingRouteResult.Status.FOUND, result.getStatus());
		assertEquals(ring.getId(), result.getDestination().getId());
		assertTrue(result.getWalkingTiles() > 0);
		assertTrue(result.getCostHalfTicks() < 1500);
	}

	@Test
	public void varrockStairsConnectTheSurfaceArrivalToTheUpperFloor()
	{
		TeleportDestination varrock = TeleportCatalog.byId("nexus-varrock").get();
		// Exact native destination of Staircase 11807, in the pinned physical TSV.
		WorldPoint upstairs = new WorldPoint(3212, 3476, 1);
		WalkingRouteResult result = new WalkingRouteSearch().search(data.getCollisionMap(),
			data.getTransports(), List.of(upstairs), List.of(varrock), () -> false);

		assertEquals(WalkingRouteResult.Status.FOUND, result.getStatus());
		assertEquals(1, result.getTarget().getPlane());
		assertTrue(result.getTransitionCount() >= 1);
		assertTrue(result.getWalkingTiles() > 0);
		assertTrue(result.getCostHalfTicks() < 250);
	}

	@Test
	public void dungeonStairsConnectSurfaceAndUndergroundCoordinateSpaces()
	{
		TeleportDestination barrows = TeleportCatalog.byId("nexus-barrows").get();
		// Staircase 39092 links 3633,3339,0 to this mapped dungeon destination.
		WorldPoint underground = new WorldPoint(3696, 9764, 2);
		WalkingRouteResult result = new WalkingRouteSearch().search(data.getCollisionMap(),
			data.getTransports(), List.of(underground), List.of(barrows), () -> false);

		assertEquals(WalkingRouteResult.Status.FOUND, result.getStatus());
		assertEquals(underground, result.getTarget());
		assertTrue(result.getTransitionCount() >= 1);
		assertTrue(result.getWalkingTiles() > 0);
		assertTrue("Pinned Darkmeyer dungeon route cost: " + result.getCostHalfTicks(),
			result.getCostHalfTicks() < 5000);
	}

	private static WalkingRouteResult route(WorldPoint target, List<TeleportDestination> arrivals, int radius)
	{
		return new WalkingRouteSearch().search(data.getCollisionMap(), data.getTransports(),
			RouteGoals.qualify(data.getCollisionMap(), List.of(target), radius), arrivals, () -> false);
	}
}
