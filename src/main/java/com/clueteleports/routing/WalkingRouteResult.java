package com.clueteleports.routing;

import com.clueteleports.TeleportDestination;
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

	private WalkingRouteResult(Status status, TeleportDestination destination, WorldPoint target,
		long costHalfTicks, int walkingTiles, int transitionCount)
	{
		this.status = status;
		this.destination = destination;
		this.target = target;
		this.costHalfTicks = costHalfTicks;
		this.walkingTiles = walkingTiles;
		this.transitionCount = transitionCount;
	}

	static WalkingRouteResult found(TeleportDestination destination, WorldPoint target,
		long costHalfTicks, int walkingTiles, int transitionCount)
	{
		return new WalkingRouteResult(Status.FOUND, Objects.requireNonNull(destination),
			Objects.requireNonNull(target), costHalfTicks, walkingTiles, transitionCount);
	}

	public static WalkingRouteResult withoutRoute(Status status)
	{
		if (status == Status.FOUND)
		{
			throw new IllegalArgumentException("A found route requires a destination and target");
		}
		return new WalkingRouteResult(Objects.requireNonNull(status), null, null, -1, 0, 0);
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

	public double getEstimatedRunTicks()
	{
		return isFound() ? costHalfTicks / 2.0 : -1.0;
	}
}
