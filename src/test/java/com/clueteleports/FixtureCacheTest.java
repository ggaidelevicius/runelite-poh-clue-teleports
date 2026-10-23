package com.clueteleports;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class FixtureCacheTest
{
	private final FixtureCache cache = new FixtureCache(TeleportSource.PORTAL_CHAMBER);

	@Test
	public void unloadedRoomRetainsItsArrivalsWhenAnotherRoomIsRead()
	{
		cache.restore("0:1930:7050=portal-barrows");
		cache.observe(Set.of("0:1938:7050"), Map.of("0:1938:7050", Set.of("portal-varrock")));
		assertEquals(Set.of("portal-barrows", "portal-varrock"), cache.availableIds());
	}

	@Test
	public void replacementAtALoadedPositionRemovesThePreviousArrival()
	{
		cache.restore("0:1930:7050=portal-barrows;0:1938:7050=portal-catherby");
		cache.observe(Set.of("0:1930:7050"), Map.of("0:1930:7050", Set.of("portal-varrock")));
		assertEquals(Set.of("portal-varrock", "portal-catherby"), cache.availableIds());
	}

	@Test
	public void loadedEmptyTileRemovesTheOldFixture()
	{
		cache.restore("0:1930:7050=portal-barrows");
		cache.observe(Set.of("0:1930:7050"), Collections.emptyMap());
		assertEquals(Collections.emptySet(), cache.availableIds());
	}

	@Test
	public void decorativeReplacementRemovesACapeTeleport()
	{
		FixtureCache capes = new FixtureCache(TeleportSource.CAPE_HANGER);
		capes.restore("1:1930:7050=cape-crafting-guild");
		capes.observe(Set.of("1:1930:7050"), Map.of("1:1930:7050", Collections.emptySet()));
		assertEquals(Collections.emptySet(), capes.availableIds());
	}

	@Test
	public void anotherFloorDoesNotRemoveTheFixture()
	{
		cache.restore("1:1930:7050=portal-barrows");
		cache.observe(Set.of("0:1930:7050"), Collections.emptyMap());
		assertEquals(Set.of("portal-barrows"), cache.availableIds());
	}

	@Test
	public void unhydratedTileCannotReplaceTheLastKnownFixture()
	{
		cache.restore("0:1930:7050=portal-barrows");
		cache.observe(Collections.emptySet(), Map.of("0:1930:7050", Set.of("portal-varrock")));
		assertEquals(Set.of("portal-barrows"), cache.availableIds());
	}

	@Test
	public void encodedPositionsAndDestinationsRoundTripWithoutTrustingForeignIds()
	{
		cache.restore("garbage;0:1930:7050=portal-barrows,fairy-aiq,unknown;9:1938:7050=portal-varrock");
		assertEquals("0:1930:7050=portal-barrows", cache.encode());
		FixtureCache restored = new FixtureCache(TeleportSource.PORTAL_CHAMBER);
		restored.restore(cache.encode());
		assertEquals(cache.availableIds(), restored.availableIds());
	}
}
