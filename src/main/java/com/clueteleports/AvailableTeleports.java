package com.clueteleports;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Stable serialization for cached destination IDs. */
public final class AvailableTeleports
{
	private AvailableTeleports()
	{
	}

	public static Set<String> decode(String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			return Collections.emptySet();
		}
		Set<String> ids = Arrays.stream(value.split(","))
			.map(String::trim).filter(id -> !id.isEmpty()).collect(Collectors.toCollection(TreeSet::new));
		return Collections.unmodifiableSet(ids);
	}

	public static String encode(Collection<String> ids)
	{
		return String.join(",", new TreeSet<>(ids));
	}
}
