package com.clueteleports.routing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Offline, version-pinned collision and physical-passage data; no global teleports. */
public final class RoutingData
{
	private final WalkingCollisionMap collisionMap;
	private final List<RouteTransport> transports;

	private RoutingData(WalkingCollisionMap collisionMap, List<RouteTransport> transports)
	{
		this.collisionMap = collisionMap;
		this.transports = Collections.unmodifiableList(new ArrayList<>(transports));
	}

	public static RoutingData load() throws IOException
	{
		try (InputStream collision = resource("collision-map.zip");
			InputStream passages = resource("transports.tsv");
			InputStream shortcuts = resource("agility_shortcuts.tsv"))
		{
			return load(collision, passages, shortcuts);
		}
	}

	/** Alternate streams make format, lock, and graph regressions independent of live game state. */
	public static RoutingData load(InputStream collision, InputStream passages, InputStream shortcuts) throws IOException
	{
		try (InputStream collisionStream = collision; InputStream passageStream = passages; InputStream shortcutStream = shortcuts)
		{
			WalkingCollisionMap map = WalkingCollisionMap.load(collisionStream);
			List<RouteTransport> transports = new ArrayList<>();
			Set<Long> barriers = new HashSet<>();
			read(passageStream, transports, barriers);
			read(shortcutStream, transports, barriers);
			return new RoutingData(map.withTransportBarriers(barriers), transports);
		}
	}

	public WalkingCollisionMap getCollisionMap() { return collisionMap; }
	public List<RouteTransport> getTransports() { return transports; }

	private static InputStream resource(String file) throws IOException
	{
		InputStream stream = RoutingData.class.getResourceAsStream("/routes/" + file);
		if (stream == null)
		{
			throw new IOException("Missing bundled routing resource: " + file);
		}
		return stream;
	}

	private static void read(InputStream stream, List<RouteTransport> transports, Set<Long> barriers) throws IOException
	{
		if (stream == null)
		{
			throw new IOException("Missing physical passage table");
		}
		Map<String, Integer> header = null;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.trim().isEmpty()) continue;
				if (header == null)
				{
					String candidate = line.startsWith("#") ? line.substring(1).trim() : line;
					List<String> names = Arrays.asList(candidate.split("\\t", -1));
					if (!names.contains("Origin") || !names.contains("Destination")) continue;
					header = new HashMap<>();
					for (int i = 0; i < names.size(); i++) header.put(names.get(i), i);
					continue;
				}
				if (line.startsWith("#")) continue;
				String[] fields = line.split("\\t", -1);
				try
				{
					int from = point(field(header, fields, "Origin"));
					int to = point(field(header, fields, "Destination"));
					if (from == to) continue;
					// Collision dumps open ordinary doors. Explicit passage edges,
					// including ones with unsupported requirements, must never gain
					// an unrestricted ordinary-walking bypass in either direction.
					if (WalkingCollisionMap.adjacent(from, to)) barriers.add(WalkingCollisionMap.edgeKey(from, to));
					Optional<RouteRequirements> requirements = RouteRequirements.parse(
						field(header, fields, "Skills"), field(header, fields, "Items"),
						field(header, fields, "Quests"), field(header, fields, "Varbits"),
						field(header, fields, "VarPlayers"));
					if (!requirements.isPresent()) continue;
					String duration = field(header, fields, "Duration");
					int ticks = duration.isEmpty() ? 1 : Integer.parseInt(duration);
					if (ticks < 0) continue;
					transports.add(new RouteTransport(from, to, Math.max(1, ticks),
						field(header, fields, "menuOption menuTarget objectID"), requirements.get()));
				}
				catch (IllegalArgumentException ignored)
				{
					// Invalid coordinates or unsupported row syntax grant no edge.
				}
			}
		}
		if (header == null) throw new IOException("Physical passage table has no coordinate header");
	}

	private static String field(Map<String, Integer> header, String[] fields, String name)
	{
		Integer index = header.get(name);
		return index == null || index >= fields.length ? "" : fields[index].trim();
	}

	private static int point(String text)
	{
		String[] xyz = text.split("\\s+");
		if (xyz.length != 3) throw new IllegalArgumentException("Invalid passage point");
		int x = Integer.parseInt(xyz[0]);
		int y = Integer.parseInt(xyz[1]);
		int plane = Integer.parseInt(xyz[2]);
		if (x < 0 || x > 16383 || y < 0 || y > 16383 || plane < 0 || plane > 3)
		{
			throw new IllegalArgumentException("Passage point outside world");
		}
		return plane << 28 | x << 14 | y;
	}
}
