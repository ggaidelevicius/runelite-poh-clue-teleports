package com.clueteleports;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.runelite.api.gameval.ObjectID;

/** Positive, per-position observations; unseen rooms never establish absence. */
final class HouseInventory
{
	private final Map<String, Set<TeleportSource>> fixtures = new TreeMap<>();
	private final Map<String, Set<Integer>> fixtureIds = new TreeMap<>();
	private boolean surveyed;

	void restore(String encoded)
	{
		fixtures.clear();
		fixtureIds.clear();
		surveyed = encoded != null;
		if (encoded == null || encoded.isEmpty())
		{
			return;
		}
		for (String entry : encoded.split(";"))
		{
			String[] parts = entry.split("=", 2);
			if (parts.length != 2 || !validPosition(parts[0]))
			{
				continue;
			}
			// Legacy entries contain sources only. Missing IDs mean unknown
			// version evidence, preserving previously learned menus on upgrade.
			String[] versioned = parts[1].split("#", 2);
			Set<TeleportSource> sources = EnumSet.noneOf(TeleportSource.class);
			for (String value : versioned[0].split(","))
			{
				try
				{
					sources.add(TeleportSource.valueOf(value.trim()));
				}
				catch (IllegalArgumentException ignored)
				{
					// Unknown future source.
				}
			}
			if (!sources.isEmpty())
			{
				fixtures.put(parts[0], sources);
				fixtureIds.remove(parts[0]);
				Set<Integer> ids = new TreeSet<>();
				if (versioned.length == 2)
				{
					for (String value : versioned[1].split(","))
					{
						try
						{
							int id = Integer.parseInt(value.trim());
							if (id > 0)
							{
								ids.add(id);
							}
						}
						catch (NumberFormatException ignored)
						{
							// Malformed version evidence cannot identify a fixture.
						}
					}
				}
				if (!ids.isEmpty())
				{
					fixtureIds.put(parts[0], ids);
				}
			}
		}
	}

	void observe(Set<String> coveredTiles, Map<String, Set<TeleportSource>> observed)
	{
		observe(coveredTiles, observed, Collections.emptyMap());
	}

	/** Only completely read, loaded positions replace earlier fixture versions. */
	void observe(Set<String> coveredTiles, Map<String, Set<TeleportSource>> observed,
		Map<String, Set<Integer>> observedIds)
	{
		coveredTiles.forEach(position ->
		{
			fixtures.remove(position);
			fixtureIds.remove(position);
		});
		observed.forEach((position, sources) ->
		{
			if (validPosition(position) && coveredTiles.contains(position) && !sources.isEmpty())
			{
				fixtures.put(position, EnumSet.copyOf(sources));
				Set<Integer> ids = observedIds.get(position);
				if (ids != null)
				{
					Set<Integer> validIds = ids.stream().filter(id -> id != null && id > 0)
						.collect(Collectors.toCollection(TreeSet::new));
					if (!validIds.isEmpty())
					{
						fixtureIds.put(position, validIds);
					}
				}
			}
		});
		surveyed = true;
	}

	boolean isSurveyed()
	{
		return surveyed;
	}

	Set<TeleportSource> installedSources()
	{
		Set<TeleportSource> sources = EnumSet.noneOf(TeleportSource.class);
		fixtures.values().forEach(sources::addAll);
		return Collections.unmodifiableSet(sources);
	}

	Set<TeleportSource> requiredMenus()
	{
		Set<TeleportSource> menus = EnumSet.noneOf(TeleportSource.class);
		menus.addAll(installedSources());
		menus.retainAll(TeleportSource.menuSources());
		return Collections.unmodifiableSet(menus);
	}

	/**
	 * Position-independent native capability evidence. An empty signature means
	 * no version is known, including inventories written before IDs were stored.
	 * Repeated identical fixtures and moves do not change the union. Default
	 * jewellery destinations normalize to their box tier; other exact native
	 * changes conservatively require the caller to learn the menu again.
	 */
	String capabilitySignature(TeleportSource source)
	{
		Set<Integer> ids = new TreeSet<>();
		fixtures.forEach((position, sources) ->
		{
			if (sources.contains(source))
			{
				for (int id : fixtureIds.getOrDefault(position, Collections.emptySet()))
				{
					// More than one wall/game fixture can occupy one tile. Only
					// this menu's native objects identify its capability version.
					if (!TeleportSource.menuSources().contains(source)
						|| HouseTransportReader.interfaceSources(id).contains(source))
					{
						ids.add(capabilityId(source, id));
					}
				}
			}
		});
		return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
	}

	String encode()
	{
		return fixtures.entrySet().stream().map(entry ->
		{
			String sources = entry.getValue().stream().map(Enum::name).collect(Collectors.joining(","));
			Set<Integer> ids = fixtureIds.getOrDefault(entry.getKey(), Collections.emptySet());
			String version = ids.isEmpty() ? "" : "#" + ids.stream().map(String::valueOf).collect(Collectors.joining(","));
			return entry.getKey() + "=" + sources + version;
		}).collect(Collectors.joining(";"));
	}

	private static int capabilityId(TeleportSource source, int id)
	{
		if (source != TeleportSource.JEWELLERY_BOX)
		{
			return id;
		}
		// Verified RuneLite gameval ObjectID ranges contain only each tier's
		// built default-destination variants, plus its Colosseum variant.
		if (id == ObjectID.POH_JEWELLERY_BOX_1 || id == ObjectID.POH_JEWELLERY_BOX_1_COLOSSEUM
			|| id >= ObjectID.POH_JEWELLERY_BOX_1_BASE && id <= ObjectID.POH_JEWELLERY_BOX_1_WINTERTODT)
		{
			return ObjectID.POH_JEWELLERY_BOX_1;
		}
		if (id == ObjectID.POH_JEWELLERY_BOX_2 || id == ObjectID.POH_JEWELLERY_BOX_2_COLOSSEUM
			|| id >= ObjectID.POH_JEWELLERY_BOX_2_BASE && id <= ObjectID.POH_JEWELLERY_BOX_2_FARMING_GUILD)
		{
			return ObjectID.POH_JEWELLERY_BOX_2;
		}
		if (id == ObjectID.POH_JEWELLERY_BOX_3 || id == ObjectID.POH_JEWELLERY_BOX_3_COLOSSEUM
			|| id >= ObjectID.POH_JEWELLERY_BOX_3_BASE && id <= ObjectID.POH_JEWELLERY_BOX_3_KHARID)
		{
			return ObjectID.POH_JEWELLERY_BOX_3;
		}
		return id;
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
