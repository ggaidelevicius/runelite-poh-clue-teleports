package com.clueteleports;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ObeliskReaderTest
{
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
		new Class<?>[]{Client.class}, (proxy, method, args) ->
		{
			if ("getVarbitValue".equals(method.getName()))
			{
				return varbits.getOrDefault((Integer) args[0], 0);
			}
			throw new UnsupportedOperationException(method.getName());
		});

	@Test
	public void absentObeliskNeverProvidesDestinationsEvenWithDiaryRewards()
	{
		varbits.put(VarbitID.WILDERNESS_HARD_REWARD, 1);

		assertTrue(ObeliskReader.read(client, false).isEmpty());
	}

	@Test
	public void randomOnlyObeliskCannotProvideAGuaranteedClosestArrival()
	{
		assertTrue(ObeliskReader.read(client, true).isEmpty());
	}

	@Test
	public void collectedHardRewardEnablesAllSixFixedArrivals()
	{
		varbits.put(VarbitID.WILDERNESS_HARD_REWARD, 1);

		assertEquals(expectedDestinations(), ObeliskReader.read(client, true));
	}

	@Test
	public void collectedEliteRewardAlsoConfirmsDestinationSelection()
	{
		varbits.put(VarbitID.WILDERNESS_ELITE_REWARD, 1);

		assertEquals(expectedDestinations(), ObeliskReader.read(client, true));
	}

	@Test
	public void completedTasksWithoutCollectedRewardDoNotEnableSelection()
	{
		varbits.put(VarbitID.WILDERNESS_HARD_COUNT, 10);

		assertTrue(ObeliskReader.read(client, true).isEmpty());
	}

	@Test
	public void removingFixtureOrLosingRewardReplacesEarlierAvailability()
	{
		varbits.put(VarbitID.WILDERNESS_HARD_REWARD, 1);
		Set<String> previous = ObeliskReader.read(client, true);
		varbits.clear();

		assertTrue(ObeliskReader.read(client, true).isEmpty());
		assertTrue(ObeliskReader.read(client, false).isEmpty());
		assertEquals(expectedDestinations(), previous);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void returnedAvailabilityCannotBeMutated()
	{
		varbits.put(VarbitID.WILDERNESS_HARD_REWARD, 1);
		ObeliskReader.read(client, true).clear();
	}

	private static Set<String> expectedDestinations()
	{
		return new HashSet<>(Arrays.asList("obelisk-13", "obelisk-19", "obelisk-27",
			"obelisk-35", "obelisk-44", "obelisk-50"));
	}
}
