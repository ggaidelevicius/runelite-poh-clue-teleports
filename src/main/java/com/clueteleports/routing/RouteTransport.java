package com.clueteleports.routing;

import java.util.Objects;

/** A directed physical passage with immutable requirements and tick duration. */
public final class RouteTransport
{
	private final int from;
	private final int to;
	private final int durationTicks;
	private final String action;
	private final RouteRequirements requirements;

	public RouteTransport(int from, int to, int durationTicks, String action, RouteRequirements requirements)
	{
		if (durationTicks < 1)
		{
			throw new IllegalArgumentException("A passage must cost at least one tick");
		}
		this.from = from;
		this.to = to;
		this.durationTicks = durationTicks;
		this.action = Objects.requireNonNull(action);
		this.requirements = Objects.requireNonNull(requirements);
	}

	public int getFrom() { return from; }
	public int getTo() { return to; }
	public int getDurationTicks() { return durationTicks; }
	public String getAction() { return action; }
	public RouteRequirements getRequirements() { return requirements; }
}
