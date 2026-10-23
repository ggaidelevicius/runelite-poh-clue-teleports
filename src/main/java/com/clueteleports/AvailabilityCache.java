package com.clueteleports;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Per-character snapshots. Missing and observed-empty sources are distinct. */
public final class AvailabilityCache
{
	private final Map<TeleportSource, Set<String>> sources = new EnumMap<>(TeleportSource.class);

	public void restore(TeleportSource source, String encoded)
	{
		if (encoded == null)
		{
			sources.remove(source);
		}
		else
		{
			sources.put(source, canonical(source, AvailableTeleports.decode(encoded)));
		}
	}

	/** True only for a new source or a changed set, independent of menu ordering. */
	public boolean record(TeleportSource source, Collection<String> ids)
	{
		Set<String> next = canonical(source, ids);
		if (next.equals(sources.get(source)))
		{
			return false;
		}
		sources.put(source, next);
		return true;
	}

	public Set<String> availableIds()
	{
		Set<String> result = new TreeSet<>();
		sources.values().forEach(result::addAll);
		return Collections.unmodifiableSet(result);
	}

	public Map<TeleportSource, Set<String>> snapshot()
	{
		return Collections.unmodifiableMap(new EnumMap<>(sources));
	}

	private static Set<String> canonical(TeleportSource source, Collection<String> ids)
	{
		Set<String> result = new TreeSet<>();
		for (String id : ids)
		{
			if (TeleportCatalog.byId(id).map(destination -> destination.getSource() == source).orElse(false))
			{
				result.add(id);
			}
		}
		return Collections.unmodifiableSet(result);
	}
}
