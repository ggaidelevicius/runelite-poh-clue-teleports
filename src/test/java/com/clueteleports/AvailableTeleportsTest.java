package com.clueteleports;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class AvailableTeleportsTest
{
	@Test
	public void unknownProfileHasNoAssumedTeleports()
	{
		assertTrue(AvailableTeleports.decode(null).isEmpty());
		assertTrue(AvailableTeleports.decode("").isEmpty());
	}

	@Test
	public void selectionsRoundTripInStableOrder()
	{
		Set<String> ids = new HashSet<>(Arrays.asList("nexus-varrock", "fairy-aiq"));
		assertEquals("fairy-aiq,nexus-varrock", AvailableTeleports.encode(ids));
		assertEquals(ids, AvailableTeleports.decode(AvailableTeleports.encode(ids)));
		assertEquals(ids, AvailableTeleports.decode(" nexus-varrock, fairy-aiq,,fairy-aiq"));
	}
}
