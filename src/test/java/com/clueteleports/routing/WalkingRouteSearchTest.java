package com.clueteleports.routing;

import com.clueteleports.TeleportDestination;
import com.clueteleports.TeleportSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class WalkingRouteSearchTest
{
	private final SyntheticCollision collision = new SyntheticCollision();
	private final WalkingRouteSearch search = new WalkingRouteSearch(100_000, 5_000);

	@Test
	public void exactArrivalNeedsNoWalkingAndPackedTileZeroIsValid()
	{
		TeleportDestination arrival = arrival("zero", TeleportSource.FAIRY_RING, point(0, 0));

		WalkingRouteResult result = run(Collections.emptyList(), List.of(point(0, 0)), List.of(arrival));

		assertFound(result, arrival, 0, 0, 0);
		assertEquals(point(0, 0), result.getTarget());
		assertEquals(0.0, result.getEstimatedRunTicks(), 0);
	}

	@Test
	public void geographicallyCloserArrivalLosesWhenWallsForceALongerWalk()
	{
		WorldPoint goal = point(10, 10);
		collision.path(point(12, 10), point(12, 9), point(12, 8), point(11, 8),
			point(10, 8), point(10, 9), goal);
		collision.path(point(10, 14), point(10, 13), point(10, 12), point(10, 11), goal);
		TeleportDestination near = arrival("near", TeleportSource.FAIRY_RING, point(12, 10));
		TeleportDestination farther = arrival("farther", TeleportSource.JEWELLERY_BOX, point(10, 14));

		assertFound(run(List.of(), List.of(goal), List.of(near, farther)), farther, 4, 4, 0);
	}

	@Test
	public void eligibleAgilityPassageCanChangeTheWinningArrival()
	{
		for (int x = 0; x < 10; x++) collision.link(point(x, 0), point(x + 1, 0));
		for (int y = 0; y < 4; y++) collision.link(point(10, y), point(10, y + 1));
		TeleportDestination first = arrival("shortcut", TeleportSource.FAIRY_RING, point(0, 0));
		TeleportDestination second = arrival("ordinary", TeleportSource.JEWELLERY_BOX, point(10, 4));
		List<TeleportDestination> arrivals = List.of(first, second);
		RouteTransport shortcut = passage(point(0, 0), point(10, 0), 1);

		assertFound(run(List.of(), List.of(point(10, 0)), arrivals), second, 4, 4, 0);
		assertFound(run(List.of(shortcut), List.of(point(10, 0)), arrivals), first, 2, 0, 1);
	}

	@Test
	public void walkingTilesAndActionDurationHaveSeparateMetrics()
	{
		collision.link(point(0, 0), point(1, 0));
		collision.link(point(9, 0), point(10, 0));
		TeleportDestination arrival = arrival("mixed", TeleportSource.SPIRIT_TREE, point(0, 0));

		WalkingRouteResult result = run(List.of(passage(point(1, 0), point(9, 0), 3)),
			List.of(point(10, 0)), List.of(arrival));

		assertFound(result, arrival, 8, 2, 1);
		assertEquals(4.0, result.getEstimatedRunTicks(), 0);
	}

	@Test
	public void stairsConnectFloorsOnlyInTheirDeclaredDirection()
	{
		WorldPoint downstairs = new WorldPoint(100, 100, 0);
		WorldPoint upstairs = new WorldPoint(100, 100, 1);
		RouteTransport stairs = passage(downstairs, upstairs, 3);
		TeleportDestination lower = arrival("lower", TeleportSource.PORTAL_NEXUS, downstairs);
		TeleportDestination upper = arrival("upper", TeleportSource.PORTAL_NEXUS, upstairs);

		assertFound(run(List.of(stairs), List.of(upstairs), List.of(lower)), lower, 6, 0, 1);
		assertEquals(WalkingRouteResult.Status.NO_ROUTE,
			run(List.of(stairs), List.of(downstairs), List.of(upper)).getStatus());
	}

	@Test
	public void matchingXYOnAnotherFloorDoesNotCountAsAnArrival()
	{
		assertEquals(WalkingRouteResult.Status.NO_ROUTE, run(List.of(),
			List.of(new WorldPoint(100, 100, 1)),
			List.of(arrival("surface", TeleportSource.FAIRY_RING, point(100, 100)))).getStatus());
	}

	@Test
	public void reversedWalkingChecksTheOriginalForwardDirection()
	{
		WorldPoint start = point(10, 10);
		WorldPoint goal = point(11, 10);
		collision.directed(start, goal);
		TeleportDestination outward = arrival("outward", TeleportSource.FAIRY_RING, start);
		TeleportDestination back = arrival("back", TeleportSource.FAIRY_RING, goal);

		assertFound(run(List.of(), List.of(goal), List.of(outward)), outward, 1, 1, 0);
		assertEquals(WalkingRouteResult.Status.NO_ROUTE, run(List.of(), List.of(start), List.of(back)).getStatus());
	}

	@Test
	public void aBlockedArrivalCannotEscapeThroughNearbyWalls()
	{
		collision.link(point(0, 0), point(1, 0));
		TeleportDestination blocked = arrival("blocked", TeleportSource.FAIRY_RING, point(2, 0));

		assertEquals(WalkingRouteResult.Status.NO_ROUTE, run(List.of(), List.of(point(0, 0)),
			List.of(blocked)).getStatus());
	}

	@Test
	public void aKnownTeleportArrivalMayLeaveItsValidatedFixtureFootprint()
	{
		collision.link(point(0, 0), point(1, 0));
		collision.arrivalExit(point(2, 0), point(1, 0));
		TeleportDestination arrival = arrival("known-footprint", TeleportSource.FAIRY_RING, point(2, 0));

		assertFound(run(List.of(), List.of(point(0, 0)), List.of(arrival)), arrival, 2, 2, 0);
	}

	@Test
	public void aNonArrivalBlockedJunctionCannotUseTheTeleportExitException()
	{
		collision.link(point(0, 0), point(1, 0));
		collision.arrivalExit(point(2, 0), point(1, 0));
		TeleportDestination arrival = arrival("ordinary", TeleportSource.FAIRY_RING, point(3, 0));

		assertEquals(WalkingRouteResult.Status.NO_ROUTE,
			run(List.of(passage(point(3, 0), point(2, 0), 1)), List.of(point(0, 0)), List.of(arrival)).getStatus());
	}

	@Test
	public void diagonalMovementRequiresTheCollisionMapsPermission()
	{
		TeleportDestination start = arrival("diagonal", TeleportSource.FAIRY_RING, point(0, 0));
		collision.path(point(0, 0), point(0, 1), point(1, 1));

		assertFound(run(List.of(), List.of(point(1, 1)), List.of(start)), start, 2, 2, 0);
		collision.link(point(0, 0), point(1, 1));
		assertFound(run(List.of(), List.of(point(1, 1)), List.of(start)), start, 1, 1, 0);
	}

	@Test
	public void aCheaperRouteDecreasesAnAlreadyQueuedTransportCost()
	{
		WorldPoint a = point(0, 0);
		WorldPoint b = point(1, 0);
		WorldPoint goal = point(10, 0);
		collision.directed(a, b);
		TeleportDestination start = arrival("decrease", TeleportSource.FAIRY_RING, a);

		assertFound(run(List.of(passage(a, goal, 10), passage(b, goal, 1)),
			List.of(goal), List.of(start)), start, 3, 1, 1);
	}

	@Test
	public void equalCostsUseSourceThenIdEvenWhenAnotherTileLeavesTheHeapFirst()
	{
		WorldPoint goal = point(10, 10);
		collision.link(point(9, 10), goal);
		collision.link(point(10, 9), goal);
		TeleportDestination z = arrival("z", TeleportSource.FAIRY_RING, point(9, 10));
		TeleportDestination a = arrival("a", TeleportSource.FAIRY_RING, point(10, 9));
		TeleportDestination tree = arrival("0", TeleportSource.SPIRIT_TREE, point(9, 10));

		assertFound(run(List.of(), List.of(goal), List.of(z, tree, a)), a, 1, 1, 0);
		assertFound(run(List.of(), List.of(goal), List.of(a, tree, z)), a, 1, 1, 0);
	}

	@Test
	public void equalArrivalTilesStillUseSourceAndIdOrder()
	{
		WorldPoint tile = point(0, 0);
		TeleportDestination tree = arrival("a", TeleportSource.SPIRIT_TREE, tile);
		TeleportDestination ring = arrival("z", TeleportSource.FAIRY_RING, tile);

		assertFound(run(List.of(), List.of(tile), List.of(tree, ring)), ring, 0, 0, 0);
	}

	@Test
	public void allQualifiedClueGoalsParticipateInTheSearch()
	{
		collision.path(point(10, 0), point(11, 0), point(12, 0));
		collision.link(point(10, 0), point(10, 1));
		TeleportDestination start = arrival("multi", TeleportSource.JEWELLERY_BOX, point(10, 0));

		WalkingRouteResult result = run(List.of(), List.of(point(12, 0), point(10, 1)), List.of(start));

		assertFound(result, start, 1, 1, 0);
		assertEquals(point(10, 1), result.getTarget());
	}

	@Test
	public void equalGoalCostsUseStableCoordinatesWithoutMutatingInputs()
	{
		WorldPoint start = point(10, 10);
		collision.link(start, point(9, 10));
		collision.link(start, point(10, 9));
		List<WorldPoint> goals = new ArrayList<>(List.of(point(10, 9), point(9, 10)));
		TeleportDestination arrival = arrival("stable", TeleportSource.FAIRY_RING, start);
		List<TeleportDestination> arrivals = new ArrayList<>(List.of(arrival));
		List<RouteTransport> eligible = new ArrayList<>();

		WalkingRouteResult result = run(eligible, goals, arrivals);

		assertEquals(point(9, 10), result.getTarget());
		assertEquals(List.of(point(10, 9), point(9, 10)), goals);
		assertEquals(List.of(arrival), arrivals);
		assertTrue(eligible.isEmpty());
	}

	@Test
	public void invalidCoordinatesNeverWrapOntoAnotherArrival()
	{
		TeleportDestination invalid = arrival("invalid", TeleportSource.FAIRY_RING, point(16384, 0));

		assertEquals(WalkingRouteResult.Status.NO_ROUTE,
			run(List.of(), List.of(point(0, 0)), List.of(invalid)).getStatus());
	}

	@Test
	public void runeLitePackingRetainsPlaneThreeAndUndergroundCoordinates()
	{
		WorldPoint start = new WorldPoint(3000, 12000, 3);
		WorldPoint goal = new WorldPoint(3001, 12000, 3);
		collision.link(start, goal);
		TeleportDestination arrival = arrival("underground", TeleportSource.PORTAL_NEXUS, start);

		assertFound(run(List.of(), List.of(goal), List.of(arrival)), arrival, 1, 1, 0);
	}

	@Test
	public void nodeBudgetReturnsLimitWithoutAProvisionalRecommendation()
	{
		for (int x = 0; x < 5; x++) collision.link(point(x, 0), point(x + 1, 0));
		WalkingRouteResult result = new WalkingRouteSearch(2, 5_000).search(collision, List.of(),
			List.of(point(5, 0)), List.of(arrival("far", TeleportSource.FAIRY_RING, point(0, 0))), () -> false);

		assertEquals(WalkingRouteResult.Status.LIMIT, result.getStatus());
		assertFalse(result.isFound());
		assertNull(result.getDestination());
		assertEquals(-1, result.getCostHalfTicks());
	}

	@Test
	public void timeBudgetReturnsLimitUsingAnInjectedMonotonicClock()
	{
		AtomicLong clock = new AtomicLong();
		WalkingRouteSearch bounded = new WalkingRouteSearch(100, 1,
			() -> clock.getAndAdd(1_000_000));

		WalkingRouteResult result = bounded.search(collision, List.of(), List.of(point(0, 0)),
			List.of(arrival("clock", TeleportSource.FAIRY_RING, point(0, 0))), () -> false);

		assertEquals(WalkingRouteResult.Status.LIMIT, result.getStatus());
	}

	@Test
	public void aProvenMinimumIsRetainedWhenTheNextDeadlineCheckWouldExpire()
	{
		for (int x = 0; x < 255; x++) collision.link(point(x, 0), point(x + 1, 0));
		AtomicInteger clockReads = new AtomicInteger();
		WalkingRouteSearch bounded = new WalkingRouteSearch(1_000, 1,
			() -> clockReads.getAndIncrement() < 2 ? 0 : 1_000_000);
		TeleportDestination arrival = arrival("proven", TeleportSource.FAIRY_RING, point(255, 0));

		// The arrival is settled as node 256. Only the cost-2000 label remains,
		// so all minimum-cost ties are complete before the next budget check.
		WalkingRouteResult result = bounded.search(collision,
			List.of(passage(point(500, 0), point(0, 0), 1_000)),
			List.of(point(0, 0)), List.of(arrival), () -> false);

		assertFound(result, arrival, 255, 255, 0);
	}

	@Test
	public void cancellationStillWinsBeforeReturningAnAlreadyProvenMinimum()
	{
		for (int x = 0; x < 255; x++) collision.link(point(x, 0), point(x + 1, 0));
		AtomicInteger checks = new AtomicInteger();
		TeleportDestination arrival = arrival("cancelled-proven", TeleportSource.FAIRY_RING, point(255, 0));

		WalkingRouteResult result = search.search(collision,
			List.of(passage(point(500, 0), point(0, 0), 1_000)),
			List.of(point(0, 0)), List.of(arrival), () -> checks.incrementAndGet() > 2);

		assertEquals(WalkingRouteResult.Status.CANCELLED, result.getStatus());
		assertNull(result.getDestination());
	}

	@Test
	public void cancelledAndInterruptedSearchesNeverPublishAnArrival()
	{
		TeleportDestination arrival = arrival("cancel", TeleportSource.FAIRY_RING, point(0, 0));
		assertEquals(WalkingRouteResult.Status.CANCELLED, search.search(collision, List.of(),
			List.of(point(0, 0)), List.of(arrival), () -> true).getStatus());

		Thread.currentThread().interrupt();
		try
		{
			assertEquals(WalkingRouteResult.Status.CANCELLED, run(List.of(),
				List.of(point(0, 0)), List.of(arrival)).getStatus());
			assertTrue(Thread.currentThread().isInterrupted());
		}
		finally
		{
			Thread.interrupted();
		}
	}

	@Test
	public void cancellationIsRecheckedDuringALongSearch()
	{
		for (int x = 0; x < 599; x++) collision.link(point(x, 0), point(x + 1, 0));
		AtomicInteger checks = new AtomicInteger();

		WalkingRouteResult result = search.search(collision, List.of(), List.of(point(599, 0)),
			List.of(arrival("cancelled", TeleportSource.FAIRY_RING, point(0, 0))),
			() -> checks.incrementAndGet() > 2);

		assertEquals(WalkingRouteResult.Status.CANCELLED, result.getStatus());
		assertNull(result.getDestination());
	}

	@Test
	public void primitiveStorageGrowsAndRehashesWithoutLosingTheMinimum()
	{
		for (int x = 0; x < 5999; x++) collision.link(point(x, 0), point(x + 1, 0));
		TeleportDestination arrival = arrival("long", TeleportSource.FAIRY_RING, point(0, 0));

		assertFound(run(List.of(), List.of(point(5999, 0)), List.of(arrival)), arrival, 5999, 5999, 0);
	}

	@Test
	public void noInputsOrDisconnectedTargetsReportNoRoute()
	{
		TeleportDestination arrival = arrival("unreachable", TeleportSource.FAIRY_RING, point(10, 0));
		assertEquals(WalkingRouteResult.Status.NO_ROUTE, run(List.of(), List.of(point(0, 0)), List.of()).getStatus());
		assertEquals(WalkingRouteResult.Status.NO_ROUTE, run(List.of(), List.of(), List.of(arrival)).getStatus());
		assertEquals(WalkingRouteResult.Status.NO_ROUTE, run(List.of(), List.of(point(0, 0)), List.of(arrival)).getStatus());
	}

	private WalkingRouteResult run(List<RouteTransport> transports, List<WorldPoint> goals,
		List<TeleportDestination> arrivals)
	{
		return search.search(collision, transports, goals, arrivals, () -> false);
	}

	private static void assertFound(WalkingRouteResult result, TeleportDestination destination,
		long cost, int walked, int transitions)
	{
		assertEquals(WalkingRouteResult.Status.FOUND, result.getStatus());
		assertTrue(result.isFound());
		assertSame(destination, result.getDestination());
		assertEquals(cost, result.getCostHalfTicks());
		assertEquals(walked, result.getWalkingTiles());
		assertEquals(transitions, result.getTransitionCount());
	}

	private static RouteTransport passage(WorldPoint from, WorldPoint to, int ticks)
	{
		return new RouteTransport(pack(from), pack(to), ticks, "Traverse", RouteRequirements.none());
	}

	private static TeleportDestination arrival(String id, TeleportSource source, WorldPoint point)
	{
		return new TeleportDestination(id, source, id, point);
	}

	private static WorldPoint point(int x, int y)
	{
		return new WorldPoint(x, y, 0);
	}

	private static int pack(WorldPoint point)
	{
		return (point.getPlane() << 28) | (point.getX() << 14) | point.getY();
	}

	private static final class SyntheticCollision extends WalkingCollisionMap
	{
		private final Set<Long> links = new HashSet<>();
		private final Set<Long> arrivalExits = new HashSet<>();
		private final Set<Integer> tiles = new HashSet<>();

		private void directed(WorldPoint from, WorldPoint to)
		{
			assertEquals(from.getPlane(), to.getPlane());
			assertTrue(from.distanceTo2D(to) == 1);
			int a = pack(from);
			int b = pack(to);
			tiles.add(a);
			tiles.add(b);
			links.add(((long) a << 32) | Integer.toUnsignedLong(b));
		}

		private void arrivalExit(WorldPoint from, WorldPoint to)
		{
			arrivalExits.add(((long) pack(from) << 32) | Integer.toUnsignedLong(pack(to)));
		}

		@Override
		public boolean canLeaveArrival(int from, int to)
		{
			return arrivalExits.contains(((long) from << 32) | Integer.toUnsignedLong(to));
		}

		private void link(WorldPoint first, WorldPoint second)
		{
			directed(first, second);
			directed(second, first);
		}

		private void path(WorldPoint... points)
		{
			for (int index = 1; index < points.length; index++)
			{
				link(points[index - 1], points[index]);
			}
		}

		@Override
		public boolean isWalkable(int point)
		{
			return tiles.contains(point);
		}

		@Override
		public boolean canWalk(int from, int to)
		{
			return links.contains(((long) from << 32) | Integer.toUnsignedLong(to));
		}
	}
}
