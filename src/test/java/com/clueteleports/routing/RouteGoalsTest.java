package com.clueteleports.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteGoalsTest
{
	private final SyntheticMap map = new SyntheticMap();

	@Test
	public void walkableDigGoalStaysExact()
	{
		WorldPoint target = point(10, 10);
		map.open(target, point(11, 10));

		assertEquals(List.of(target), RouteGoals.qualify(map, List.of(target), 0));
	}

	@Test
	public void blockedDigGoalNeverSnapsToAConvenientNearbyTile()
	{
		WorldPoint target = point(10, 10);
		map.open(point(11, 10), point(10, 11));

		assertTrue(RouteGoals.qualify(map, List.of(target), 0).isEmpty());
	}

	@Test
	public void walkableNpcUsesOnlyCardinalFacesWithARealForwardEdge()
	{
		WorldPoint target = point(10, 10);
		WorldPoint north = point(10, 11);
		WorldPoint east = point(11, 10);
		WorldPoint south = point(10, 9);
		WorldPoint west = point(9, 10);
		map.open(target, north, east, south, west, point(11, 11));
		map.edge(north, target);
		map.edge(east, target);
		map.edge(target, south); // The reverse direction alone is insufficient.

		assertEquals(Set.of(target, north, east),
			new HashSet<>(RouteGoals.qualify(map, List.of(target), 1)));
	}

	@Test
	public void walkableNpcDoesNotExpandToRadiusTwoThroughItsWall()
	{
		WorldPoint target = point(10, 10);
		map.open(target, point(10, 12));

		assertEquals(List.of(target), RouteGoals.qualify(map, List.of(target), 2));
	}

	@Test
	public void explicitSolidInteractionUsesItsNearestMappedCardinalFaces()
	{
		WorldPoint target = point(10, 10);
		WorldPoint north = point(10, 11);
		WorldPoint east = point(11, 10);
		map.open(north, east, point(10, 12), point(11, 11));

		assertEquals(Set.of(north, east),
			new HashSet<>(RouteGoals.qualify(map, List.of(target), 2)));
	}

	@Test
	public void solidInteractionCanReachRadiusTwoOnlyWhenRadiusOneHasNoFace()
	{
		WorldPoint target = point(10, 10);
		WorldPoint northTwo = point(10, 12);
		map.open(northTwo, point(11, 11));

		assertTrue(RouteGoals.qualify(map, List.of(target), 1).isEmpty());
		assertEquals(List.of(northTwo), RouteGoals.qualify(map, List.of(target), 2));
	}


	@Test
	public void blockedObjectNeverChoosesAFaceAcrossAStructuralWall()
	{
		WorldPoint target = point(10, 10);
		WorldPoint north = point(10, 11);
		WorldPoint east = point(11, 10);
		map.open(north, east);
		map.blockedInteractions.add(((long) RouteGoals.pack(north) << 32) | Integer.toUnsignedLong(RouteGoals.pack(target)));
		assertEquals(List.of(east), RouteGoals.qualify(map, List.of(target), 1));
	}

	@Test
	public void missingCollisionDataDoesNotCreateGoals()
	{
		assertTrue(RouteGoals.qualify(map, List.of(point(10, 10)), 2).isEmpty());
		assertTrue(RouteGoals.qualify(map, Collections.emptyList(), 2).isEmpty());
	}

	@Test
	public void interactionExpansionIsBoundedAndNeverTakesDiagonalFaces()
	{
		WorldPoint target = point(10, 10);
		map.open(point(11, 11), point(10, 13), point(13, 10));

		assertTrue(RouteGoals.qualify(map, List.of(target), 100).isEmpty());
	}

	@Test
	public void goalsRetainTheirPlaneAndDoNotBorrowSurfaceFaces()
	{
		WorldPoint upstairs = new WorldPoint(10, 10, 1);
		map.open(point(10, 11), point(11, 10));

		assertTrue(RouteGoals.qualify(map, List.of(upstairs), 2).isEmpty());
	}

	@Test
	public void worldEdgesDoNotWrapIntoNegativeCoordinates()
	{
		WorldPoint target = point(0, 0);
		map.open(point(0, 1), point(1, 0), point(16383, 0), point(0, 16383));

		assertEquals(Set.of(point(0, 1), point(1, 0)),
			new HashSet<>(RouteGoals.qualify(map, List.of(target), 2)));
	}

	@Test
	public void duplicatedTargetsAndOverlappingFacesAreDeduplicatedWithoutChangingInputs()
	{
		WorldPoint first = point(10, 10);
		WorldPoint second = point(12, 10);
		WorldPoint shared = point(11, 10);
		map.open(shared);
		List<WorldPoint> inputs = new ArrayList<>(List.of(first, second, first));

		assertEquals(List.of(shared), RouteGoals.qualify(map, inputs, 1));
		assertEquals(List.of(first, second, first), inputs);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void qualifiedGoalsAreAnImmutableSnapshot()
	{
		map.open(point(10, 10));
		RouteGoals.qualify(map, List.of(point(10, 10)), 0).clear();
	}

	private static WorldPoint point(int x, int y)
	{
		return new WorldPoint(x, y, 0);
	}

	private static final class SyntheticMap extends WalkingCollisionMap
	{
		private final Set<Integer> open = new HashSet<>();
		private final Set<Long> edges = new HashSet<>();
		private final Set<Long> blockedInteractions = new HashSet<>();

		private void open(WorldPoint... points)
		{
			for (WorldPoint point : points) open.add(RouteGoals.pack(point));
		}

		private void edge(WorldPoint from, WorldPoint to)
		{
			edges.add(((long) RouteGoals.pack(from) << 32) | Integer.toUnsignedLong(RouteGoals.pack(to)));
		}

		@Override public boolean canInteractFrom(int from, int to)
		{
			return isWalkable(from) && (!isWalkable(to) || canWalk(from, to))
				&& !blockedInteractions.contains(((long) from << 32) | Integer.toUnsignedLong(to));
		}

		@Override public boolean isWalkable(int point) { return open.contains(point); }
		@Override public boolean canWalk(int from, int to)
		{
			return edges.contains(((long) from << 32) | Integer.toUnsignedLong(to));
		}
	}
}
