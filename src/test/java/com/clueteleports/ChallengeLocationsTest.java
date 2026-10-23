package com.clueteleports;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cluescrolls.clues.SkillChallengeClue;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ChallengeLocationsTest
{
	@Test
	public void currentCoreChallengeLabelsResolveToVerifiedTaskSites()
	{
		Map<String, WorldPoint> tasks = new LinkedHashMap<>();
		tasks.put("craft a nature rune.", new WorldPoint(2869, 3019, 0));
		tasks.put("craft multiple cosmic runes from a single essence.", new WorldPoint(2408, 4377, 0));
		tasks.put("score a goal in skullball.", new WorldPoint(3542, 3463, 0));
		tasks.put("complete a lap of ape atoll agility course.", new WorldPoint(2752, 2742, 0));
		tasks.put("complete a lap of the rellekka rooftop agility course whilst sporting the finest amount of grace.",
			new WorldPoint(2624, 3677, 0));
		tasks.put("catch a mottled eel with aerial fishing in lake molch.", new WorldPoint(1363, 3632, 0));
		tasks.put("catch a tecu salamander.", new WorldPoint(1475, 3096, 0));
		tasks.put("create a barrows teleport tablet.", new WorldPoint(1679, 3765, 0));
		tasks.put("steal from a chest in ardougne castle.", new WorldPoint(2588, 3291, 1));
		tasks.put("steal a gem from the ardougne market.", new WorldPoint(2667, 3303, 0));
		tasks.put("cremate a set of fiyr remains.", new WorldPoint(3507, 3276, 0));
		tasks.put("slay a nechryael in the slayer tower.", new WorldPoint(3440, 3566, 2));
		tasks.put("equip an abyssal whip in front of the abyssal demons of the slayer tower.",
			new WorldPoint(3421, 3537, 2));
		tasks.put("bind a blood rune at the blood altar.", new WorldPoint(1716, 3827, 0));
		ClueTargetResolver resolver = new ClueTargetResolver();
		for (Map.Entry<String, WorldPoint> task : tasks.entrySet())
		{
			SkillChallengeClue clue = SkillChallengeClue.forText(task.getKey(), task.getKey());
			assertNotNull(task.getKey(), clue);
			ClueTargetResolver.Resolution result = resolver.resolve(clue, null);
			assertEquals(task.getKey(), ClueTargetResolver.State.RESOLVED, result.getState());
			assertEquals(task.getKey(), Collections.singletonList(task.getValue()), result.getLocations());
			assertFalse(result.hasAlternativeTaskLocations());
		}
	}

	@Test
	public void bloodAlwaysHasAnUnrestrictedKourendTaskSite()
	{
		assertEquals(Collections.singletonList(new WorldPoint(1716, 3827, 0)),
			ChallengeLocations.forChallenge("Bind a blood rune at the blood altar.", null));
		assertEquals(1, ChallengeLocations.forChallenge("bind a blood rune at the blood altar.", questClient(0)).size());
		assertEquals(1, ChallengeLocations.forChallenge("bind a blood rune at the blood altar.", questClient(1)).size());
	}

	@Test
	public void trueBloodAltarIsAddedOnlyAfterSinsOfTheFather()
	{
		List<WorldPoint> locations = ChallengeLocations.forChallenge("bind a blood rune at the blood altar.", questClient(2));
		assertEquals(2, locations.size());
		assertEquals(new WorldPoint(1716, 3827, 0), locations.get(0));
		assertEquals(new WorldPoint(3625, 9717, 0), locations.get(1));
	}

	@Test
	public void flexibleProductionAndResourceTasksDoNotChooseArbitrarySites()
	{
		for (String task : new String[]{"Burn a magic log.", "Smith a runite med helm.",
			"Catch a raw shark.", "Mine a mithril ore.", "Pickpocket an elf.",
			"Create an unstrung dragonstone amulet at a furnace.",
			"Teleport to a spirit tree you planted yourself.", "Dissect a sacred eel."})
		{
			assertTrue(task, ChallengeLocations.forChallenge(task, null).isEmpty());
		}
		assertTrue(ChallengeLocations.forChallenge(null, null).isEmpty());
		assertTrue(ChallengeLocations.forChallenge("Unknown future challenge.", null).isEmpty());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void taskAlternativesAreImmutable()
	{
		ChallengeLocations.forChallenge("bind a blood rune at the blood altar.", questClient(2)).clear();
	}

	static Client questClient(int state)
	{
		return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "runScript": return null;
					case "getIntStack": return new int[]{state};
					default: throw new UnsupportedOperationException(method.getName());
				}
			});
	}
}
