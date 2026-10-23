package com.clueteleports;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AvailabilityCacheTest
{
	private final AvailabilityCache cache = new AvailabilityCache();
	private static final String RING_ONE = "fairy-aiq";
	private static final String RING_TWO = "fairy-air";
	private static final String TREE = "tree-grand-exchange";
	private static final String NEXUS = "nexus-ardougne";

	@Test
	public void firstEmptyObservationIsPersistedAndRemainsDistinctFromUnknown()
	{
		assertTrue(cache.availableIds().isEmpty());
		assertFalse(cache.snapshot().containsKey(TeleportSource.FAIRY_RING));
		assertTrue(cache.record(TeleportSource.FAIRY_RING, Collections.emptySet()));
		assertTrue(cache.snapshot().containsKey(TeleportSource.FAIRY_RING));
		assertTrue(cache.snapshot().get(TeleportSource.FAIRY_RING).isEmpty());
		assertFalse(cache.record(TeleportSource.FAIRY_RING, Collections.emptySet()));
	}

	@Test
	public void reorderedAndDuplicateMenuEntriesDoNotTriggerAnotherWrite()
	{
		assertTrue(cache.record(TeleportSource.FAIRY_RING, Arrays.asList(RING_ONE, RING_TWO)));
		assertFalse(cache.record(TeleportSource.FAIRY_RING, Arrays.asList(RING_TWO, RING_ONE, RING_ONE)));
		assertEquals(set(RING_ONE, RING_TWO), cache.availableIds());
	}

	@Test
	public void changedSourceReplacesItsOldEntriesAndPreservesOtherSources()
	{
		cache.record(TeleportSource.FAIRY_RING, Collections.singleton(RING_ONE));
		cache.record(TeleportSource.SPIRIT_TREE, Collections.singleton(TREE));
		cache.record(TeleportSource.PORTAL_NEXUS, Collections.singleton(NEXUS));

		assertTrue(cache.record(TeleportSource.FAIRY_RING, Collections.singleton(RING_TWO)));
		assertEquals(set(RING_TWO, TREE, NEXUS), cache.availableIds());
		assertEquals(Collections.singleton(TREE), cache.snapshot().get(TeleportSource.SPIRIT_TREE));
		assertEquals(Collections.singleton(NEXUS), cache.snapshot().get(TeleportSource.PORTAL_NEXUS));

		assertTrue(cache.record(TeleportSource.FAIRY_RING, Collections.emptySet()));
		assertEquals(set(TREE, NEXUS), cache.availableIds());
		assertTrue(cache.snapshot().containsKey(TeleportSource.FAIRY_RING));
	}

	@Test
	public void restorationFiltersStaleAndCrossSourceIds()
	{
		cache.restore(TeleportSource.FAIRY_RING,
			AvailableTeleports.encode(Arrays.asList(RING_ONE, TREE, NEXUS, "removed-destination")));

		assertEquals(Collections.singleton(RING_ONE), cache.availableIds());
		assertFalse(cache.record(TeleportSource.FAIRY_RING, Collections.singleton(RING_ONE)));
	}

	@Test
	public void invalidObservedEntriesCannotBecomeAvailableOrCauseRepeatedWrites()
	{
		Collection<String> reading = Arrays.asList(RING_ONE, TREE, "removed-destination");
		assertTrue(cache.record(TeleportSource.FAIRY_RING, reading));
		assertEquals(Collections.singleton(RING_ONE), cache.availableIds());
		assertFalse(cache.record(TeleportSource.FAIRY_RING, Arrays.asList(RING_ONE, "different-stale-id")));
	}

	@Test
	public void nullRestorationRemovesOnlyThatSourcesKnownState()
	{
		cache.restore(TeleportSource.FAIRY_RING, "");
		cache.restore(TeleportSource.SPIRIT_TREE, TREE);
		cache.restore(TeleportSource.FAIRY_RING, null);

		assertFalse(cache.snapshot().containsKey(TeleportSource.FAIRY_RING));
		assertEquals(Collections.singleton(TREE), cache.availableIds());
		assertTrue(cache.record(TeleportSource.FAIRY_RING, Collections.emptySet()));
	}

	@Test
	public void savedEmptyStringRestoresAsObservedEmpty()
	{
		cache.restore(TeleportSource.PORTAL_NEXUS, "");

		assertTrue(cache.snapshot().containsKey(TeleportSource.PORTAL_NEXUS));
		assertFalse(cache.record(TeleportSource.PORTAL_NEXUS, Collections.emptySet()));
	}

	@Test
	public void snapshotsRemainStableAfterFurtherObservationsAndInputMutation()
	{
		List<String> reading = new ArrayList<>(Collections.singleton(RING_ONE));
		cache.record(TeleportSource.FAIRY_RING, reading);
		Set<String> previousAvailable = cache.availableIds();
		Map<TeleportSource, Set<String>> previousSnapshot = cache.snapshot();
		reading.clear();
		cache.record(TeleportSource.FAIRY_RING, Collections.singleton(RING_TWO));
		cache.record(TeleportSource.SPIRIT_TREE, Collections.singleton(TREE));

		assertEquals(Collections.singleton(RING_ONE), previousAvailable);
		assertEquals(Collections.singleton(RING_ONE), previousSnapshot.get(TeleportSource.FAIRY_RING));
		assertFalse(previousSnapshot.containsKey(TeleportSource.SPIRIT_TREE));
		assertEquals(set(RING_TWO, TREE), cache.availableIds());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void availableIdsCannotBeMutatedByCallers()
	{
		cache.record(TeleportSource.FAIRY_RING, Collections.singleton(RING_ONE));
		cache.availableIds().clear();
	}

	@Test(expected = UnsupportedOperationException.class)
	public void snapshotMapCannotBeMutatedByCallers()
	{
		cache.record(TeleportSource.FAIRY_RING, Collections.singleton(RING_ONE));
		cache.snapshot().clear();
	}

	@Test(expected = UnsupportedOperationException.class)
	public void snapshotSourceSetsCannotBeMutatedByCallers()
	{
		cache.record(TeleportSource.FAIRY_RING, Collections.singleton(RING_ONE));
		cache.snapshot().get(TeleportSource.FAIRY_RING).clear();
	}

	private static Set<String> set(String... ids)
	{
		return new HashSet<>(Arrays.asList(ids));
	}
}
