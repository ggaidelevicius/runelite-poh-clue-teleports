package com.clueteleports;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CapeReaderTest
{
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final Map<Integer, Integer> varps = new HashMap<>();
	private int questStatus = 1;
	private final Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
		new Class<?>[]{Client.class}, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getVarbitValue":
					return varbits.getOrDefault((Integer) args[0], 0);
				case "getVarpValue":
					return varps.getOrDefault((Integer) args[0], 0);
				case "runScript":
					return null;
				case "getIntStack":
					return new int[]{questStatus};
				default:
					throw new UnsupportedOperationException(method.getName());
			}
		});

	@Test
	public void unrelatedObjectIsNotAnEmptyCapeObservation()
	{
		assertFalse(CapeReader.read(client, 13523).isPresent());
		assertFalse(CapeReader.read(client, 31986).isPresent());
	}

	@Test
	public void constructionCapeHasAllNineExteriorPortals()
	{
		assertEquals(ids("cape-rimmington", "cape-taverley", "cape-pollnivneach", "cape-hosidius",
			"cape-rellekka", "cape-aldarin", "cape-brimhaven", "cape-yanille", "cape-prifddinas"), read(29184));
		assertEquals(read(29184), read(29185));
	}

	@Test
	public void skillCapesExposeOnlyTheirOwnTeleports()
	{
		assertEquals(ids("cape-crafting-guild"), read(29188));
		assertEquals(ids("cape-farming-guild"), read(29192));
		assertEquals(ids("cape-fishing-guild", "cape-ottos-grotto"), read(29196));
		assertEquals(ids("cape-warriors-guild"), read(29220));
		assertEquals(ids("cape-pandemonium"), read(58664));
		assertEquals(read(58664), read(58665));
	}

	@Test
	public void hunterCapeCapabilitiesSurviveDailyExhaustion()
	{
		varbits.put(VarbitID.CHINCHOMPA_TELEPORTS, 5);

		assertEquals(ids("cape-red-chinchompas", "cape-black-chinchompas"), read(29204));
		assertEquals(read(29204), read(29205));
		assertFalse(read(29204).contains("cape-hunter-guild"));
	}

	@Test
	public void plainMaxCapeCombinesSkillCapeTeleports()
	{
		Set<String> max = read(29170);

		assertEquals(17, max.size());
		assertTrue(max.containsAll(read(29184)));
		assertTrue(max.containsAll(read(29188)));
		assertTrue(max.containsAll(read(29192)));
		assertTrue(max.containsAll(read(29196)));
		assertTrue(max.containsAll(read(29204)));
		assertTrue(max.containsAll(read(29220)));
		assertTrue(max.containsAll(read(58664)));
		assertFalse(max.contains("cape-legends-guild"));
		assertFalse(max.contains("cape-falo-the-bard"));
		assertFalse(max.contains("cape-hunter-guild"));
	}

	@Test
	public void cosmeticMaxCapesDoNotInheritPlainMaxPerks()
	{
		assertTrue(read(29171).isEmpty());
		assertTrue(read(29175).isEmpty());
		assertTrue(read(31982).isEmpty());
		assertEquals(ids("cape-ardougne-monastery", "cape-ardougne-farming-patch"), read(29625));
		assertEquals(ids("cape-myths-guild"), read(39621));
	}

	@Test
	public void mythicalQuestAndMusicCapeCapabilitiesAreDistinct()
	{
		assertEquals(ids("cape-myths-guild"), read(31983));
		assertEquals(ids("cape-legends-guild"), read(29178));
		assertEquals(read(29178), read(29179));
		assertEquals(ids("cape-falo-the-bard"), read(29176));
		assertEquals(read(29176), read(29177));
	}

	@Test
	public void diaryCapeHasAllSixteenDiaryMastersIncludingTwiggy()
	{
		Set<String> diary = read(29167);

		assertEquals(16, diary.size());
		assertTrue(diary.contains("cape-diary-twiggy-okorn"));
		assertTrue(diary.contains("cape-diary-kaleb-paramaya"));
		assertEquals(diary, read(29168));
	}

	@Test
	public void decorativeSkillCapeIsAKnownEmptyObservation()
	{
		assertTrue(CapeReader.read(client, 29166).isPresent());
		assertTrue(read(29166).isEmpty());
		assertTrue(read(29180).isEmpty());
		assertTrue(read(29225).isEmpty());
	}

	@Test
	public void chinchompaCounterCountsUsedTeleportsAndRefreshesOutsideHouse()
	{
		assertTrue(CapeReader.availableNow(client, "cape-red-chinchompas"));
		varbits.put(VarbitID.CHINCHOMPA_TELEPORTS, 4);
		assertTrue(CapeReader.availableNow(client, "cape-black-chinchompas"));
		varbits.put(VarbitID.CHINCHOMPA_TELEPORTS, 5);
		assertFalse(CapeReader.availableNow(client, "cape-red-chinchompas"));
		assertFalse(CapeReader.availableNow(client, "cape-black-chinchompas"));
		assertTrue(CapeReader.availableNow(client, "cape-ardougne-farming-patch"));
		varbits.put(VarbitID.CHINCHOMPA_TELEPORTS, 0);
		assertTrue(CapeReader.availableNow(client, "cape-red-chinchompas"));
	}

	@Test
	public void musicCapeRequiresCurrentNonHolidayMusicCompletion()
	{
		assertFalse(CapeReader.availableNow(client, "cape-falo-the-bard"));
		varbits.put(VarbitID.MUSIC_CAPE_STATUS, 1);
		assertTrue(CapeReader.availableNow(client, "cape-falo-the-bard"));
		varbits.put(VarbitID.MUSIC_CAPE_STATUS, 2);
		assertTrue(CapeReader.availableNow(client, "cape-falo-the-bard"));
	}

	@Test
	public void questCapeRequiresCurrentMaximumQuestPoints()
	{
		assertFalse(CapeReader.availableNow(client, "cape-legends-guild"));
		varbits.put(VarbitID.QP_MAX, 343);
		varps.put(VarPlayerID.QP, 342);
		assertFalse(CapeReader.availableNow(client, "cape-legends-guild"));
		varps.put(VarPlayerID.QP, 343);
		assertTrue(CapeReader.availableNow(client, "cape-legends-guild"));
	}

	@Test
	public void prifddinasPortalRequiresSongOfTheElvesCompletion()
	{
		assertFalse(CapeReader.availableNow(client, "cape-prifddinas"));
		questStatus = 0;
		assertFalse(CapeReader.availableNow(client, "cape-prifddinas"));
		questStatus = 2;
		assertTrue(CapeReader.availableNow(client, "cape-prifddinas"));
	}

	@Test
	public void unavailableHunterGuildCannotBeRestoredFromAStaleId()
	{
		assertFalse(CapeReader.availableNow(client, "cape-hunter-guild"));
		assertTrue(CapeReader.availableNow(client, "glory-edgeville"));
	}

	@Test(expected = UnsupportedOperationException.class)
	public void returnedCapabilitiesAreImmutable()
	{
		read(29170).clear();
	}

	private Set<String> read(int objectId)
	{
		return CapeReader.read(client, objectId).get();
	}

	private static Set<String> ids(String... values)
	{
		return new HashSet<>(Arrays.asList(values));
	}
}
