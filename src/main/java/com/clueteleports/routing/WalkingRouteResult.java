package com.clueteleports.routing;

import com.clueteleports.TeleportDestination;
import java.util.List;
import java.util.Objects;
import net.runelite.api.coords.WorldPoint;

/** A completed search, or an explicit reason no minimum route was established. */
public final class WalkingRouteResult
{
	public enum Status
	{
		FOUND, NO_ROUTE, LIMIT, CANCELLED
	}

	private final Status status;
	private final TeleportDestination destination;
	private final WorldPoint target;
	private final long costHalfTicks;
	private final int walkingTiles;
	private final int transitionCount;
	private final List<Step> steps;

	private WalkingRouteResult(Status status, TeleportDestination destination, WorldPoint target,
		long costHalfTicks, int walkingTiles, int transitionCount, List<Step> steps)
	{
		this.status = status;
		this.destination = destination;
		this.target = target;
		this.costHalfTicks = costHalfTicks;
		this.walkingTiles = walkingTiles;
		this.transitionCount = transitionCount;
		this.steps = List.copyOf(steps);
	}

	static WalkingRouteResult found(TeleportDestination destination, WorldPoint target,
		long costHalfTicks, int walkingTiles, int transitionCount)
	{
		return new WalkingRouteResult(Status.FOUND, Objects.requireNonNull(destination),
			Objects.requireNonNull(target), costHalfTicks, walkingTiles, transitionCount, List.of());
	}

	WalkingRouteResult withSteps(List<Step> steps)
	{
		return new WalkingRouteResult(status, destination, target, costHalfTicks, walkingTiles, transitionCount, steps);
	}

	public static WalkingRouteResult withoutRoute(Status status)
	{
		if (status == Status.FOUND)
		{
			throw new IllegalArgumentException("A found route requires a destination and target");
		}
		return new WalkingRouteResult(Objects.requireNonNull(status), null, null, -1, 0, 0, List.of());
	}

	public Status getStatus()
	{
		return status;
	}

	public boolean isFound()
	{
		return status == Status.FOUND;
	}

	/** Null unless a minimum route was found. */
	public TeleportDestination getDestination()
	{
		return destination;
	}

	/** The qualified clue goal reached by this route; null unless found. */
	public WorldPoint getTarget()
	{
		return target;
	}

	/**
	 * One unit per ordinary walking tile, plus twice each physical action's
	 * duration in ticks. This estimates continuous running, not elapsed time.
	 */
	public long getCostHalfTicks()
	{
		return costHalfTicks;
	}

	public int getWalkingTiles()
	{
		return walkingTiles;
	}

	public int getTransitionCount()
	{
		return transitionCount;
	}

	/** The proven route in travel order, including the arrival and goal. */
	public List<Step> getSteps()
	{
		return steps;
	}

	public double getEstimatedRunTicks()
	{
		return isFound() ? costHalfTicks / 2.0 : -1.0;
	}

	/** A tile and whether the following step requires a physical passage. */
	public static final class Step
	{
		private final WorldPoint location;
		private final boolean passageToNext;

		public Step(WorldPoint location, boolean passageToNext)
		{
			this.location = Objects.requireNonNull(location);
			this.passageToNext = passageToNext;
		}

		public WorldPoint getLocation() { return location; }
		public boolean isPassageToNext() { return passageToNext; }
	}
}
