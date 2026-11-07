package com.clueteleports;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.cluescrolls.clues.CrypticClue;

/** Verified access points for combat cryptics whose core clues have no coordinates. */
final class CrypticCombatLocations
{
	// The Frost Nagua spawn used by Clue Helper (EliteClues.java at
	// b1a5e772d45373bac133bfa28a1ded6a38b910b3), near the Twilight Temple lift.
	private static final WorldPoint FROST_NAGUA = new WorldPoint(1627, 9623, 0);
	// The rope lands inside Quest Helper's Tonali Cavern F1 Nagua zone.
	// https://github.com/Zoinkwiz/quest-helper/blob/b9b93706618a7b7fe7e5ea30e9a9ef35ba09673e/src/main/java/com/questhelper/helpers/quests/thefinaldawn/TheFinalDawn.java#L572-L575
	private static final WorldPoint EARTHEN_NAGUA = new WorldPoint(1307, 9420, 1);

	private CrypticCombatLocations() { }

	static WorldPoint forClue(CrypticClue clue)
	{
		if (clue.getItemIds().contains(ItemID.TRAIL_ELITE_RIDDLE_VM02)) return FROST_NAGUA;
		if (clue.getItemIds().contains(ItemID.TRAIL_ELITE_RIDDLE_VM03)) return EARTHEN_NAGUA;
		return null;
	}
}
