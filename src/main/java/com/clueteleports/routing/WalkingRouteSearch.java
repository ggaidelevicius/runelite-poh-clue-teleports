package com.clueteleports.routing;

import com.clueteleports.TeleportDestination;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.runelite.api.coords.WorldPoint;

/**
 * Reverse Dijkstra from qualified clue goals to available POH arrivals.
 *
 * The collision map and account-filtered physical transports are immutable input;
 * no RuneLite Client calls, portable teleports, or user settings are consulted.
 * Known catalog arrivals may leave a blocked fixture footprint only through
 * the collision map's strict, wall-aware arrival exit check.
 * Reverse walking checks canWalk(neighbour, current), and physical edges are
 * indexed destination-to-origin, preserving directed doors, stairs, and ladders.
 *
 * A found route is returned only after its arrival leaves the minimum-cost
 * queue. Equal-cost arrivals are exhausted to resolve source/id ties. Hitting
 * the work/time budget never promotes a provisional route to a minimum.
 */
public final class WalkingRouteSearch
{
	private static final int DEFAULT_NODE_LIMIT = 1_000_000;
	private static final long DEFAULT_TIME_LIMIT_MILLIS = 5_000;
	private static final int[] DX = {0, 1, 0, -1, 1, 1, -1, -1};
	private static final int[] DY = {1, 0, -1, 0, 1, -1, -1, 1};
	private static final Comparator<TeleportDestination> DESTINATIONS =
		Comparator.comparingInt((TeleportDestination destination) -> destination.getSource().ordinal())
			.thenComparing(TeleportDestination::getId);
	private static final Comparator<WorldPoint> GOALS = Comparator.comparingInt(WorldPoint::getPlane)
		.thenComparingInt(WorldPoint::getX).thenComparingInt(WorldPoint::getY);
	private final int maxNodes;
	private final long maxNanos;
	private final LongSupplier nanoTime;

	public WalkingRouteSearch()
	{
		this(DEFAULT_NODE_LIMIT, DEFAULT_TIME_LIMIT_MILLIS);
	}

	public WalkingRouteSearch(int maxNodes, long maxMillis)
	{
		this(maxNodes, maxMillis, System::nanoTime);
	}

	WalkingRouteSearch(int maxNodes, long maxMillis, LongSupplier nanoTime)
	{
		if (maxNodes < 1 || maxMillis < 1)
		{
			throw new IllegalArgumentException("Search limits must be positive");
		}
		this.maxNodes = maxNodes;
		this.maxNanos = TimeUnit.MILLISECONDS.toNanos(maxMillis);
		this.nanoTime = Objects.requireNonNull(nanoTime);
	}

	/**
	 * Goals must already represent valid standing/interaction tiles. The engine
	 * never invents nearby goals or escapes blocked teleport tiles through walls.
	 * Input transports must already satisfy the current account's requirements.
	 */
	public WalkingRouteResult search(WalkingCollisionMap collision, Collection<RouteTransport> eligible,
		List<WorldPoint> goals, Collection<TeleportDestination> arrivals, BooleanSupplier cancel)
	{
		Objects.requireNonNull(collision);
		Objects.requireNonNull(eligible);
		Objects.requireNonNull(goals);
		Objects.requireNonNull(arrivals);
		Objects.requireNonNull(cancel);
		if (cancelled(cancel))
		{
			return WalkingRouteResult.withoutRoute(WalkingRouteResult.Status.CANCELLED);
		}
		long started = nanoTime.getAsLong();
		Map<Integer, TeleportDestination> destinations = new HashMap<>();
		for (TeleportDestination arrival : arrivals)
		{
			if (arrival != null && valid(arrival.getLocation()))
			{
				destinations.merge(pack(arrival.getLocation()), arrival,
					(first, second) -> DESTINATIONS.compare(first, second) <= 0 ? first : second);
			}
		}
		List<WorldPoint> targets = new ArrayList<>();
		Map<Integer, WorldPoint> distinctGoals = new LinkedHashMap<>();
		goals.stream().filter(Objects::nonNull).filter(WalkingRouteSearch::valid).sorted(GOALS)
			.forEach(goal -> distinctGoals.putIfAbsent(pack(goal), goal));
		targets.addAll(distinctGoals.values());
		if (destinations.isEmpty() || targets.isEmpty())
		{
			return WalkingRouteResult.withoutRoute(WalkingRouteResult.Status.NO_ROUTE);
		}
		Map<Integer, List<RouteTransport>> reversed = new HashMap<>();
		for (RouteTransport edge : eligible)
		{
			if (edge != null && validPacked(edge.getFrom()) && validPacked(edge.getTo()))
			{
				reversed.computeIfAbsent(edge.getTo(), ignored -> new ArrayList<>()).add(edge);
			}
		}

		Nodes nodes = new Nodes(maxNodes);
		for (int index = 0; index < targets.size(); index++)
		{
			if (!nodes.relax(pack(targets.get(index)), 0, index, 0, 0))
			{
				return WalkingRouteResult.withoutRoute(WalkingRouteResult.Status.LIMIT);
			}
		}
		WalkingRouteResult best = null;
		int settled = 0;
		while (!nodes.isEmpty())
		{
			if ((settled & 255) == 0)
			{
				if (cancelled(cancel))
				{
					return WalkingRouteResult.withoutRoute(WalkingRouteResult.Status.CANCELLED);
				}
				// Cancellation still wins, but a completed minimum needs no more
				// budget once every remaining label is strictly more expensive.
				if (best != null && nodes.minimumCost() > best.getCostHalfTicks())
				{
					return best;
				}
				if (nanoTime.getAsLong() - started >= maxNanos)
				{
					return WalkingRouteResult.withoutRoute(WalkingRouteResult.Status.LIMIT);
				}
			}
			int node = nodes.pop();
			long cost = nodes.costs[node];
			if (best != null && cost > best.getCostHalfTicks())
			{
				return best;
			}
			settled++;
			int point = nodes.points[node];
			TeleportDestination arrival = destinations.get(point);
			if (arrival != null)
			{
				WalkingRouteResult candidate = WalkingRouteResult.found(arrival,
					targets.get(nodes.goals[node]), cost, nodes.walking[node], nodes.transitions[node]);
				if (best == null || compare(candidate, best) < 0)
				{
					best = candidate;
				}
			}
			if (best != null)
			{
				// Every edge has positive cost, so expanding an equal-cost tile
				// cannot discover another equal-cost arrival.
				continue;
			}
			int x = (point >>> 14) & 0x3fff;
			int y = point & 0x3fff;
			int plane = point >>> 28;
			for (int direction = 0; direction < DX.length; direction++)
			{
				int nx = x + DX[direction];
				int ny = y + DY[direction];
				if (nx < 0 || nx > 0x3fff || ny < 0 || ny > 0x3fff)
				{
					continue;
				}
				int neighbour = (plane << 28) | (nx << 14) | ny;
				if ((collision.canWalk(neighbour, point)
					|| destinations.containsKey(neighbour) && collision.canLeaveArrival(neighbour, point))
					&& !nodes.relax(neighbour, cost + 1, nodes.goals[node],
						nodes.walking[node] + 1, nodes.transitions[node]))
				{
					return WalkingRouteResult.withoutRoute(WalkingRouteResult.Status.LIMIT);
				}
			}
			List<RouteTransport> incoming = reversed.get(point);
			if (incoming != null)
			{
				for (RouteTransport edge : incoming)
				{
					long duration = 2L * Math.max(1, edge.getDurationTicks());
					if (!nodes.relax(edge.getFrom(), cost + duration, nodes.goals[node],
						nodes.walking[node], nodes.transitions[node] + 1))
					{
						return WalkingRouteResult.withoutRoute(WalkingRouteResult.Status.LIMIT);
					}
				}
			}
		}
		if (cancelled(cancel))
		{
			return WalkingRouteResult.withoutRoute(WalkingRouteResult.Status.CANCELLED);
		}
		return best == null ? WalkingRouteResult.withoutRoute(WalkingRouteResult.Status.NO_ROUTE) : best;
	}

	private static int compare(WalkingRouteResult first, WalkingRouteResult second)
	{
		int compared = Long.compare(first.getCostHalfTicks(), second.getCostHalfTicks());
		if (compared == 0)
		{
			compared = DESTINATIONS.compare(first.getDestination(), second.getDestination());
		}
		if (compared == 0)
		{
			compared = GOALS.compare(first.getTarget(), second.getTarget());
		}
		if (compared == 0)
		{
			compared = Integer.compare(first.getWalkingTiles(), second.getWalkingTiles());
		}
		return compared == 0 ? Integer.compare(first.getTransitionCount(), second.getTransitionCount()) : compared;
	}

	private static boolean cancelled(BooleanSupplier cancel)
	{
		return Thread.currentThread().isInterrupted() || cancel.getAsBoolean();
	}

	private static boolean valid(WorldPoint point)
	{
		return point != null && point.getX() >= 0 && point.getX() <= 0x3fff
			&& point.getY() >= 0 && point.getY() <= 0x3fff && point.getPlane() >= 0 && point.getPlane() <= 3;
	}

	private static boolean validPacked(int point)
	{
		return point >= 0 && (point & 0xc0000000) == 0;
	}

	private static int pack(WorldPoint point)
	{
		return (point.getPlane() << 28) | (point.getX() << 14) | point.getY();
	}

	/**
	 * Primitive tile labels and decrease-key heap avoid allocating an object for
	 * every explored tile. The hash table stores nodeIndex+1 (zero means empty),
	 * so even packed tile zero is a valid key. All storage belongs to one search.
	 */
	private static final class Nodes
	{
		private final int limit;
		private int size;
		private int[] points;
		private long[] costs;
		private int[] goals;
		private int[] walking;
		private int[] transitions;
		private int[] heapPositions;
		private int[] table;
		private int[] heap;
		private int heapSize;

		private Nodes(int limit)
		{
			this.limit = limit;
			int capacity = Math.min(limit, 4096);
			points = new int[capacity];
			costs = new long[capacity];
			goals = new int[capacity];
			walking = new int[capacity];
			transitions = new int[capacity];
			heapPositions = new int[capacity];
			table = new int[8192];
			heap = new int[capacity];
		}

		private boolean isEmpty()
		{
			return heapSize == 0;
		}

		private long minimumCost()
		{
			return costs[heap[0]];
		}

		private boolean relax(int point, long cost, int goal, int walked, int actions)
		{
			int slot = slot(point);
			int index = table[slot] - 1;
			if (index >= 0)
			{
				if (heapPositions[index] == -2 || !better(cost, goal, walked, actions, index))
				{
					return true;
				}
			}
			else
			{
				if (size == limit)
				{
					return false;
				}
				if (size == points.length)
				{
					grow();
				}
				if ((size + 1) * 10L >= table.length * 7L)
				{
					rehash();
					slot = slot(point);
				}
				index = size++;
				points[index] = point;
				heapPositions[index] = -1;
				table[slot] = index + 1;
			}
			costs[index] = cost;
			goals[index] = goal;
			walking[index] = walked;
			transitions[index] = actions;
			int position = heapPositions[index];
			if (position == -1)
			{
				position = heapSize++;
				heap[position] = index;
				heapPositions[index] = position;
			}
			up(position);
			return true;
		}

		private boolean better(long cost, int goal, int walked, int actions, int existing)
		{
			if (cost != costs[existing]) return cost < costs[existing];
			if (goal != goals[existing]) return goal < goals[existing];
			if (walked != walking[existing]) return walked < walking[existing];
			return actions < transitions[existing];
		}

		private int pop()
		{
			int result = heap[0];
			heapPositions[result] = -2;
			heapSize--;
			if (heapSize > 0)
			{
				heap[0] = heap[heapSize];
				heapPositions[heap[0]] = 0;
				down(0);
			}
			return result;
		}

		private void up(int position)
		{
			while (position > 0)
			{
				int parent = (position - 1) >>> 1;
				if (compareNodes(heap[parent], heap[position]) <= 0)
				{
					break;
				}
				swap(parent, position);
				position = parent;
			}
		}

		private void down(int position)
		{
			while (position * 2 + 1 < heapSize)
			{
				int child = position * 2 + 1;
				if (child + 1 < heapSize && compareNodes(heap[child + 1], heap[child]) < 0)
				{
					child++;
				}
				if (compareNodes(heap[position], heap[child]) <= 0)
				{
					break;
				}
				swap(position, child);
				position = child;
			}
		}

		private int compareNodes(int first, int second)
		{
			int compared = Long.compare(costs[first], costs[second]);
			if (compared == 0) compared = Integer.compare(goals[first], goals[second]);
			if (compared == 0) compared = Integer.compare(walking[first], walking[second]);
			if (compared == 0) compared = Integer.compare(transitions[first], transitions[second]);
			return compared == 0 ? Integer.compareUnsigned(points[first], points[second]) : compared;
		}

		private void swap(int first, int second)
		{
			int value = heap[first];
			heap[first] = heap[second];
			heap[second] = value;
			heapPositions[heap[first]] = first;
			heapPositions[heap[second]] = second;
		}

		private int slot(int point)
		{
			int hashed = point;
			hashed ^= hashed >>> 16;
			hashed *= 0x7feb352d;
			hashed ^= hashed >>> 15;
			hashed *= 0x846ca68b;
			hashed ^= hashed >>> 16;
			int slot = hashed & (table.length - 1);
			while (table[slot] != 0 && points[table[slot] - 1] != point)
			{
				slot = (slot + 1) & (table.length - 1);
			}
			return slot;
		}

		private void rehash()
		{
			table = new int[table.length * 2];
			for (int index = 0; index < size; index++)
			{
				table[slot(points[index])] = index + 1;
			}
		}

		private void grow()
		{
			int capacity = (int) Math.min(limit, Math.max((long) points.length + 1, points.length * 2L));
			points = Arrays.copyOf(points, capacity);
			costs = Arrays.copyOf(costs, capacity);
			goals = Arrays.copyOf(goals, capacity);
			walking = Arrays.copyOf(walking, capacity);
			transitions = Arrays.copyOf(transitions, capacity);
			heapPositions = Arrays.copyOf(heapPositions, capacity);
			heap = Arrays.copyOf(heap, capacity);
		}
	}
}
