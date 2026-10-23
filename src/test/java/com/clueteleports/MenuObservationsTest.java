package com.clueteleports;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MenuObservationsTest
{
	private final MenuObservations observations = new MenuObservations();
	private static final TeleportSource SOURCE = TeleportSource.FAIRY_RING;

	@Test
	public void requiresMatchingObservationsOnTwoConsecutiveCaptures()
	{
		assertFalse(observations.observe(SOURCE, reading("fairy-aiq", "fairy-air")).isPresent());
		Optional<Set<String>> stable = observations.observe(SOURCE, reading("fairy-air", "fairy-aiq"));

		assertTrue(stable.isPresent());
		assertEquals(set("fairy-aiq", "fairy-air"), stable.get());
	}

	@Test
	public void aClosedOrIncompleteMenuDiscardsThePendingCapture()
	{
		assertFalse(observations.observe(SOURCE, reading("fairy-aiq")).isPresent());
		assertFalse(observations.observe(SOURCE, Optional.empty()).isPresent());
		assertFalse(observations.observe(SOURCE, reading("fairy-aiq")).isPresent());
		assertTrue(observations.observe(SOURCE, reading("fairy-aiq")).isPresent());
	}

	@Test
	public void anEmptyCompleteMenuCanBecomeStableWithoutMeaningClosed()
	{
		assertFalse(observations.observe(SOURCE, reading()).isPresent());
		Optional<Set<String>> emptyMenu = observations.observe(SOURCE, reading());
		assertTrue(emptyMenu.isPresent());
		assertTrue(emptyMenu.get().isEmpty());

		assertFalse(observations.observe(SOURCE, Optional.empty()).isPresent());
		assertFalse(observations.observe(SOURCE, reading()).isPresent());
	}

	@Test
	public void changedContentsNeedTwoConsecutiveCapturesAgain()
	{
		observations.observe(SOURCE, reading("fairy-aiq"));
		assertTrue(observations.observe(SOURCE, reading("fairy-aiq")).isPresent());

		assertFalse(observations.observe(SOURCE, reading("fairy-air")).isPresent());
		assertFalse(observations.observe(SOURCE, reading("fairy-aiq")).isPresent());
		assertFalse(observations.observe(SOURCE, reading("fairy-air")).isPresent());
		assertTrue(observations.observe(SOURCE, reading("fairy-air")).isPresent());
	}

	@Test
	public void resetRequiresFreshCapturesForEverySource()
	{
		observations.observe(SOURCE, reading("fairy-aiq"));
		observations.observe(TeleportSource.SPIRIT_TREE, reading("tree-grand-exchange"));
		observations.clear();

		// Profile switches and new scenes both use this full reset.
		assertFalse(observations.observe(SOURCE, reading("fairy-aiq")).isPresent());
		assertFalse(observations.observe(TeleportSource.SPIRIT_TREE, reading("tree-grand-exchange")).isPresent());
		assertTrue(observations.observe(SOURCE, reading("fairy-aiq")).isPresent());
		assertTrue(observations.observe(TeleportSource.SPIRIT_TREE, reading("tree-grand-exchange")).isPresent());
	}

	@Test
	public void incompleteReadingClearsOnlyItsOwnSource()
	{
		observations.observe(SOURCE, reading("fairy-aiq"));
		observations.observe(TeleportSource.SPIRIT_TREE, reading("tree-grand-exchange"));
		observations.observe(SOURCE, Optional.empty());

		assertTrue(observations.observe(TeleportSource.SPIRIT_TREE, reading("tree-grand-exchange")).isPresent());
		assertFalse(observations.observe(SOURCE, reading("fairy-aiq")).isPresent());
	}

	@Test
	public void mutatingAnInputSetCannotMakeAnIncompleteObservationStable()
	{
		Set<String> firstCapture = set("fairy-aiq");
		observations.observe(SOURCE, Optional.of(firstCapture));
		firstCapture.add("fairy-air");

		assertFalse(observations.observe(SOURCE, reading("fairy-aiq", "fairy-air")).isPresent());
		assertTrue(observations.observe(SOURCE, reading("fairy-aiq", "fairy-air")).isPresent());
	}

	@Test
	public void previouslyReturnedSnapshotDoesNotChangeWhenMenuChanges()
	{
		observations.observe(SOURCE, reading("fairy-aiq"));
		Set<String> first = observations.observe(SOURCE, reading("fairy-aiq")).get();
		observations.observe(SOURCE, reading("fairy-air"));
		observations.observe(SOURCE, reading("fairy-air"));

		assertEquals(Collections.singleton("fairy-aiq"), first);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void confirmedSnapshotsCannotBeMutatedByCallers()
	{
		observations.observe(SOURCE, reading("fairy-aiq"));
		observations.observe(SOURCE, reading("fairy-aiq")).get().clear();
	}

	private static Optional<Set<String>> reading(String... ids)
	{
		return Optional.of(set(ids));
	}

	private static Set<String> set(String... ids)
	{
		return new HashSet<>(Arrays.asList(ids));
	}
}
