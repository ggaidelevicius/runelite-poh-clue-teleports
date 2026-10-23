package com.clueteleports;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

/**
 * Reads deterministic access through an observed POH Wilderness obelisk.
 *
 * Call only after confirming ownership and obtaining a complete house fixture
 * observation. The presence argument must describe ObjectID.POH_WILDERNESS_OBELISK
 * (31554), not a Wilderness obelisk outside the house.
 *
 * Wilderness Obelisk destination selection requires collecting the Hard
 * Wilderness Diary reward. Without it, activation chooses randomly and none of
 * the six arrivals can be recommended as a guaranteed destination.
 *
 * Reward IDs are RuneLite gamevals; destination coordinates are documented by
 * RuneLite TeleportLocationData at 9fad2c588ca957ae47d883b09544725a796ed9fe.
 * Behaviour: https://oldschool.runescape.wiki/w/Wilderness_Obelisk .
 */
public final class ObeliskReader
{
	private static final Set<String> DESTINATIONS;

	static
	{
		Set<String> ids = new LinkedHashSet<>();
		for (int level : new int[]{13, 19, 27, 35, 44, 50})
		{
			ids.add("obelisk-" + level);
		}
		DESTINATIONS = Collections.unmodifiableSet(ids);
	}

	private ObeliskReader()
	{
	}

	/** A complete result; an absent fixture or random-only access is known empty. */
	public static Set<String> read(Client client, boolean obeliskPresent)
	{
		if (!obeliskPresent)
		{
			return Collections.emptySet();
		}
		return client.getVarbitValue(VarbitID.WILDERNESS_HARD_REWARD) == 1
			|| client.getVarbitValue(VarbitID.WILDERNESS_ELITE_REWARD) == 1
			? DESTINATIONS : Collections.emptySet();
	}
}
