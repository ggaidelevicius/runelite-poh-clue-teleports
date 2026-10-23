package com.clueteleports;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Requires matching snapshots on two game ticks before committing menu state. */
public final class MenuObservations
{
	private final Map<TeleportSource, Set<String>> pending = new EnumMap<>(TeleportSource.class);

	public Optional<Set<String>> observe(TeleportSource source, Optional<Set<String>> reading)
	{
		if (!reading.isPresent())
		{
			pending.remove(source);
			return Optional.empty();
		}
		Set<String> snapshot = Collections.unmodifiableSet(new TreeSet<>(reading.get()));
		Set<String> previous = pending.put(source, snapshot);
		return snapshot.equals(previous) ? Optional.of(snapshot) : Optional.empty();
	}

	public void clear()
	{
		pending.clear();
	}
}
