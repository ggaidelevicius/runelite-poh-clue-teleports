package com.clueteleports;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;

/**
 * Reads the active perks of a transformed cape hanger in the player's own house.
 *
 * Object IDs are RuneLite gamevals at 9fad2c588ca957ae47d883b09544725a796ed9fe;
 * the Sailing cape IDs are in ObjectID1. Cosmetic max capes retain their own
 * original cape's perks, rather than the plain max cape's skillcape perks.
 *
 * Mounted behaviour: https://oldschool.runescape.wiki/w/Cape_hanger .
 * Music status and quest point requirements are also used by task-checker's
 * CapesTaskList at ddb4e230256ed459459f120d0f7f25b6f22fce51.
 */
public final class CapeReader
{
	private static final Set<String> CONSTRUCTION = ids(
		"cape-rimmington", "cape-taverley", "cape-pollnivneach", "cape-hosidius",
		"cape-rellekka", "cape-aldarin", "cape-brimhaven", "cape-yanille", "cape-prifddinas");
	private static final Set<String> CRAFTING = ids("cape-crafting-guild");
	private static final Set<String> FARMING = ids("cape-farming-guild");
	private static final Set<String> FISHING = ids("cape-fishing-guild", "cape-ottos-grotto");
	private static final Set<String> HUNTER = ids("cape-red-chinchompas", "cape-black-chinchompas");
	private static final Set<String> STRENGTH = ids("cape-warriors-guild");
	private static final Set<String> SAILING = ids("cape-pandemonium");
	private static final Set<String> QUEST = ids("cape-legends-guild");
	private static final Set<String> MUSIC = ids("cape-falo-the-bard");
	private static final Set<String> MYTHICAL = ids("cape-myths-guild");
	private static final Set<String> ARDOUGNE = ids("cape-ardougne-monastery", "cape-ardougne-farming-patch");
	private static final Set<String> DIARY = ids(
		"cape-diary-two-pints", "cape-diary-jarr", "cape-diary-sir-rebral", "cape-diary-thorodin",
		"cape-diary-flax-keeper", "cape-diary-jackie-the-fruit", "cape-diary-kaleb-paramaya",
		"cape-diary-jungle-forester", "cape-diary-tzhaar-mej", "cape-diary-elise",
		"cape-diary-hatius-cosaintus", "cape-diary-le-sabre", "cape-diary-toby",
		"cape-diary-lesser-fanatic", "cape-diary-elder-gnome-child", "cape-diary-twiggy-okorn");
	private static final Set<String> MAX = maxDestinations();

	private CapeReader()
	{
	}

	/**
	 * A present result describes the mounted cape's capabilities, including
	 * temporarily exhausted daily teleports. Call availableNow when ranking.
	 * Unsupported objects return empty Optional, not an empty cape snapshot.
	 */
	public static Optional<Set<String>> read(Client client, int transformedObjectId)
	{
		switch (transformedObjectId)
		{
			case 29167: // Achievement diary cape.
			case 29168:
				return Optional.of(DIARY);
			case 29170: // Plain max cape only.
				return Optional.of(MAX);
			case 29176: // Music cape.
			case 29177:
				return Optional.of(MUSIC);
			case 29178: // Quest point cape.
			case 29179:
				return Optional.of(QUEST);
			case 29184: // Construction cape.
			case 29185:
				return Optional.of(CONSTRUCTION);
			case 29188: // Crafting cape.
			case 29189:
				return Optional.of(CRAFTING);
			case 29192: // Farming cape.
			case 29193:
				return Optional.of(FARMING);
			case 29196: // Fishing cape.
			case 29197:
				return Optional.of(FISHING);
			case 29204: // Hunter cape.
			case 29205:
				return Optional.of(HUNTER);
			case 29220: // Strength cape.
			case 29221:
				return Optional.of(STRENGTH);
			case 29625: // Ardougne max cape retains cloak 4's unlimited teleports.
				return Optional.of(ARDOUGNE);
			case 31983: // Mythical cape on the hanger, not the quest hall trophy.
			case 39621: // Mythical max cape.
				return Optional.of(MYTHICAL);
			case 58664: // Sailing cape.
			case 58665:
				return Optional.of(SAILING);
			default:
				return isDecorativeCape(transformedObjectId)
					? Optional.of(Collections.emptySet()) : Optional.empty();
		}
	}

	/** Rechecks transient availability without forgetting the installed fixture. */
	public static boolean availableNow(Client client, String id)
	{
		if ("cape-red-chinchompas".equals(id) || "cape-black-chinchompas".equals(id))
		{
			int used = client.getVarbitValue(VarbitID.CHINCHOMPA_TELEPORTS);
			return used >= 0 && used < 5;
		}
		if ("cape-falo-the-bard".equals(id))
		{
			return client.getVarbitValue(VarbitID.MUSIC_CAPE_STATUS) >= 1;
		}
		if ("cape-legends-guild".equals(id))
		{
			int maximum = client.getVarbitValue(VarbitID.QP_MAX);
			return maximum > 0 && client.getVarpValue(VarPlayerID.QP) >= maximum;
		}
		if ("cape-prifddinas".equals(id))
		{
			return Quest.SONG_OF_THE_ELVES.getState(client) == QuestState.FINISHED;
		}
		// Apatura's Hunter Guild cape unlock is not exposed in client vars.
		// A mounted Hunter/Max cape alone does not prove it. Do not resurrect a
		// stale ID until a reliable positive unlock observation is implemented.
		return !"cape-hunter-guild".equals(id);
	}

	private static boolean isDecorativeCape(int id)
	{
		return id >= 29166 && id <= 29225
			|| id == 26713 || id == 26714 || id == 30403
			|| id >= 31979 && id <= 31982;
	}

	private static Set<String> maxDestinations()
	{
		Set<String> result = new LinkedHashSet<>();
		result.addAll(CONSTRUCTION);
		result.addAll(CRAFTING);
		result.addAll(FARMING);
		result.addAll(FISHING);
		result.addAll(HUNTER);
		result.addAll(STRENGTH);
		result.addAll(SAILING);
		return Collections.unmodifiableSet(result);
	}

	private static Set<String> ids(String... values)
	{
		return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(values)));
	}
}
