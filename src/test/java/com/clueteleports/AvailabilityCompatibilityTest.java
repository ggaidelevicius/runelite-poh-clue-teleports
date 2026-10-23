package com.clueteleports;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AvailabilityCompatibilityTest
{
	@Test
	public void originalCharacterCacheKeysRemainCompatible()
	{
		Map<TeleportSource, String> originalKeys = Map.of(
			TeleportSource.FAIRY_RING, "fairyRingCache",
			TeleportSource.SPIRIT_TREE, "spiritTreeCache",
			TeleportSource.PORTAL_NEXUS, "portalNexusCache",
			TeleportSource.JEWELLERY_BOX, "jewelleryBoxCache");
		for (Map.Entry<TeleportSource, String> entry : originalKeys.entrySet())
		{
			assertEquals(entry.getValue(), entry.getKey().getCacheKey());
		}
		assertEquals("clueteleports", ClueTeleportsPlugin.CONFIG_GROUP);
	}

	@Test
	public void expandedFacilitiesHaveSeparateCharacterCaches()
	{
		Set<String> keys = new HashSet<>();
		for (TeleportSource source : TeleportSource.values())
		{
			assertTrue(source.name(), keys.add(source.getCacheKey()));
		}
	}

	@Test
	public void persistedOriginalDestinationIdsRemainUsableAfterTheExpansion()
	{
		Map<TeleportSource, String> originalDestinations = Map.of(
			TeleportSource.FAIRY_RING, "fairy-aiq",
			TeleportSource.SPIRIT_TREE, "tree-grand-exchange",
			TeleportSource.PORTAL_NEXUS, "nexus-varrock",
			TeleportSource.JEWELLERY_BOX, "jewellery-edgeville");
		AvailabilityCache cache = new AvailabilityCache();
		originalDestinations.forEach(cache::restore);
		assertEquals(new HashSet<>(originalDestinations.values()), cache.availableIds());
		assertEquals(originalDestinations.keySet(), cache.snapshot().keySet());
	}
}
