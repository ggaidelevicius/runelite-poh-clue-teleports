package com.clueteleports;

import java.util.Objects;
import net.runelite.api.coords.WorldPoint;

public final class TeleportDestination
{
	private final String id;
	private final TeleportSource source;
	private final String name;
	private final WorldPoint location;

	public TeleportDestination(String id, TeleportSource source, String name, WorldPoint location)
	{
		this.id = Objects.requireNonNull(id, "id");
		this.source = Objects.requireNonNull(source, "source");
		this.name = Objects.requireNonNull(name, "name");
		this.location = Objects.requireNonNull(location, "location");
	}

	public String getId()
	{
		return id;
	}

	public TeleportSource getSource()
	{
		return source;
	}

	public String getName()
	{
		return name;
	}

	public WorldPoint getLocation()
	{
		return location;
	}
}
