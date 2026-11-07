package com.clueteleports;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
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
	public void additionalSherlockChallengesResolveFromTheirActualClueText()
	{
		Map<String, WorldPoint> tasks = Map.ofEntries(
			Map.entry("slay a dust devil.", new WorldPoint(3218, 9373, 0)),
			Map.entry("kill a lizardman shaman.", new WorldPoint(1434, 3711, 0)),
			Map.entry("kill the spiritual, magic and godly whilst representing their own god.", new WorldPoint(2916, 3745, 0)),
			Map.entry("kill a fiyr shade inside mort'tons shade catacombs.", new WorldPoint(3484, 3320, 0)),
			Map.entry("kill a reanimated abyssal.", new WorldPoint(1716, 3883, 0)),
			Map.entry("angle for an anglerfish whilst sporting the finest fishing gear.", new WorldPoint(1827, 3770, 0)),
			Map.entry("catch a black warlock.", new WorldPoint(1282, 3021, 0)),
			Map.entry("catch a red chinchompa.", new WorldPoint(2266, 3411, 0)),
			Map.entry("chop a redwood log whilst sporting the finest lumberjack gear.", new WorldPoint(1566, 3483, 0)),
			Map.entry("fix a magical lamp in dorgesh-kaan.", new WorldPoint(2722, 5260, 0)),
			Map.entry("craft a light orb in the dorgesh-kaan bank.", new WorldPoint(2701, 5349, 0)),
			Map.entry("pickpocket an elf.", new WorldPoint(3291, 6061, 0)));
		Client account = account(2, Map.of(VarPlayerID.TROLL_QUEST, 20, VarPlayerID.DESERTTREASUREMAIN, 11), 60);
		ClueScrollPlugin core = new ClueScrollPlugin()
		{
			@Override public Client getClient() { return account; }
		};
		ClueTargetResolver resolver = new ClueTargetResolver();
		for (Map.Entry<String, WorldPoint> task : tasks.entrySet())
		{
			SkillChallengeClue clue = SkillChallengeClue.forText(task.getKey(), task.getKey());
			assertNotNull(task.getKey(), clue);
			ClueTargetResolver.Resolution target = resolver.resolve(clue, core);
			assertTrue(task.getKey(), target.getLocations().contains(task.getValue()));
			assertEquals(task.getKey(), target.getLocations().size() > 1, target.hasAlternativeTaskLocations());
		}
	}

	@Test
	public void unavailableTaskSitesDoNotCompeteWithAccessibleAlternatives()
	{
		Client locked = account(1, Map.of(VarPlayerID.TROLL_QUEST, 19, VarPlayerID.DESERTTREASUREMAIN, 10), 60);
		assertEquals(List.of(new WorldPoint(1711, 10011, 0)), ChallengeLocations.forChallenge("Kill a Dust Devil.", locked));
		for (String task : List.of("Catch a red chinchompa.", "Fix a magical lamp in Dorgesh-Kaan.",
			"Craft a light orb in the Dorgesh-Kaan bank.", "Kill a Spiritual Mage while wearing something from their god.",
			"Kill a Fiyr shade inside Mort'tons shade catacombs.", "Pickpocket an elf."))
		{
			assertTrue(task, ChallengeLocations.forChallenge(task, locked).isEmpty());
		}
		Client belowGodWarsLevel = account(2, Map.of(VarPlayerID.TROLL_QUEST, 20), 59);
		assertTrue(ChallengeLocations.forChallenge("Kill a Spiritual Mage while wearing something from their god.",
			belowGodWarsLevel).isEmpty());
	}

	@Test
	public void flexibleProductionAndResourceTasksDoNotChooseArbitrarySites()
	{
		for (String task : new String[]{"Burn a magic log.", "Smith a runite med helm.",
			"Catch a raw shark.", "Mine a mithril ore.", "Mine some nickel.", "Smith a tier 2 or above Shayzien platebody.",
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
		return account(state, Map.of(), 0);
	}

	private static Client account(int questState, Map<Integer, Integer> varps, int boostedAgility)
	{
		return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "runScript": return null;
					case "getIntStack": return new int[]{questState};
					case "getVarpValue": return varps.getOrDefault((Integer) args[0], 0);
					case "getBoostedSkillLevel": return args[0] == Skill.AGILITY ? boostedAgility : 0;
					default: throw new UnsupportedOperationException(method.getName());
				}
			});
	}
}
