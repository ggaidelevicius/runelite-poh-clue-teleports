package com.clueteleports;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Keeps separate arrivals for repeated fixtures in positively observed POH tiles. */
final class FixtureCache
{
	private final TeleportSource source;
	private final Map<String, Set<String>> fixtures = new TreeMap<>();

	FixtureCache(TeleportSource source)
	{
		this.source = source;
	}

	void restore(String encoded)
	{
		fixtures.clear();
		if (encoded == null || encoded.isEmpty())
		{
			return;
		}
		for (String entry : encoded.split(";"))
		{
			String[] parts = entry.split("=", 2);
			if (parts.length == 2 && validPosition(parts[0]))
			{
				put(parts[0], AvailableTeleports.decode(parts[1]));
			}
		}
	}

	/** Only loaded, completely read tiles can replace or remove an older fixture. */
	void observe(Set<String> coveredTiles, Map<String, Set<String>> observed)
	{
		coveredTiles.forEach(fixtures::remove);
		observed.forEach((position, ids) ->
		{
			if (validPosition(position) && coveredTiles.contains(position))
			{
				put(position, ids);
			}
		});
	}

	Set<String> availableIds()
	{
		Set<String> ids = new TreeSet<>();
		fixtures.values().forEach(ids::addAll);
		return Collections.unmodifiableSet(ids);
	}

	String encode()
	{
		return fixtures.entrySet().stream().map(entry -> entry.getKey() + "=" + AvailableTeleports.encode(entry.getValue()))
			.collect(Collectors.joining(";"));
	}

	private void put(String position, Collection<String> candidates)
	{
		Set<String> ids = new TreeSet<>();
		for (String id : candidates)
		{
			TeleportCatalog.byId(id).filter(destination -> destination.getSource() == source)
				.ifPresent(destination -> ids.add(id));
		}
		if (!ids.isEmpty())
		{
			fixtures.put(position, Collections.unmodifiableSet(ids));
		}
	}

	private static boolean validPosition(String position)
	{
		if (position == null || !position.matches("[0-3]:[0-9]{1,5}:[0-9]{1,5}"))
		{
			return false;
		}
		String[] coordinates = position.split(":");
		return Integer.parseInt(coordinates[1]) <= 16383 && Integer.parseInt(coordinates[2]) <= 16383;
	}
}
