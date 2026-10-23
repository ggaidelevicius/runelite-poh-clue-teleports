package com.clueteleports;

import com.clueteleports.routing.WalkingRouteResult;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;

/** Client-thread requests; production implementations compute on a worker. */
interface TeleportRouting extends AutoCloseable
{
	Plan update(Client client, String profile, List<WorldPoint> targets,
		Collection<TeleportDestination> arrivals, int interactionRadius);
	default void invalidate() { }
	@Override default void close() { }

	final class Plan
	{
		enum Status { PREPARING, SEARCHING, FOUND, NO_ROUTE, LIMIT, UNAVAILABLE }
		final Status status;
		final TeleportDestination destination;
		final WorldPoint target;
		final long costHalfTicks;
		final int walkingTiles;
		final int transitions;

		private Plan(Status status, TeleportDestination destination, WorldPoint target,
			long costHalfTicks, int walkingTiles, int transitions)
		{
			this.status = status;
			this.destination = destination;
			this.target = target;
			this.costHalfTicks = costHalfTicks;
			this.walkingTiles = walkingTiles;
			this.transitions = transitions;
		}

		static Plan status(Status status) { return new Plan(status, null, null, 0, 0, 0); }

		static Plan found(TeleportDestination destination, WorldPoint target, long cost, int tiles, int transitions)
		{
			return new Plan(Status.FOUND, Objects.requireNonNull(destination), Objects.requireNonNull(target), cost, tiles, transitions);
		}

		static Plan from(WalkingRouteResult result)
		{
			switch (result.getStatus())
			{
				case FOUND: return found(result.getDestination(), result.getTarget(), result.getCostHalfTicks(), result.getWalkingTiles(), result.getTransitionCount());
				case LIMIT: return status(Status.LIMIT);
				case CANCELLED: return status(Status.SEARCHING);
				default: return status(Status.NO_ROUTE);
			}
		}
	}
}
