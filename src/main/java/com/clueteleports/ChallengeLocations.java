/*
 * Copyright (c) 2018, Morgan Lewis <https://github.com/MESLewis>
 * Copyright (c) 2020, melky <https://github.com/melkypie>
 * Copyright (c) 2024, emerald000
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.clueteleports;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;

/**
 * Verified task sites for location-specific Sherlock challenges.
 *
 * Task labels are RuneLite SkillChallengeClue's display labels. Altar,
 * agility, and hunter markers come from RuneLite's world map data. The
 * Slayer Tower approach is core EmoteClue's top-floor entrance marker.
 * NPC, chest, gem stall, lectern, and pyre positions are adapted from
 * emerald000/clue-helper at b1a5e772d45373bac133bfa28a1ded6a38b910b3.
 *
 * These are task sites or their normal access points, rather than every
 * possible route to them. Dungeon entrance and rope markers are used when
 * the full route to the task cannot be verified from the bundled map.
 * Tasks possible at arbitrary banks, fires, furnaces, anvils, or resource
 * locations intentionally have no chosen point.
 */
final class ChallengeLocations
{
	private static final Map<String, List<Site>> SITES;

	static
	{
		Map<String, List<Site>> sites = new LinkedHashMap<>();
		sites.put("craft a nature rune.", List.of(site(2869, 3019, 0)));
		sites.put("craft multiple cosmic runes from a single essence.", List.of(site(2408, 4377, 0)));
		// Both altars complete the task. Only the true altar requires Sins of the Father.
		sites.put("bind a blood rune at the blood altar.", List.of(site(1716, 3827, 0),
			site(3625, 9717, 0, Quest.SINS_OF_THE_FATHER)));
		sites.put("score a goal in skullball.", List.of(site(3542, 3463, 0)));
		sites.put("complete a lap of ape atoll agility course.", List.of(site(2752, 2742, 0)));
		sites.put("complete a lap of rellekka's rooftop agility course", List.of(site(2624, 3677, 0)));
		sites.put("catch a mottled eel with aerial fishing in lake molch.", List.of(site(1363, 3632, 0)));
		sites.put("catch a tecu salamander.", List.of(site(1475, 3096, 0)));
		sites.put("create a barrows teleport tablet.", List.of(site(1679, 3765, 0)));
		sites.put("steal from a chest in ardougne castle.", List.of(site(2588, 3291, 1)));
		sites.put("steal a gem from the ardougne market.", List.of(site(2667, 3303, 0)));
		sites.put("cremate a set of fiyr remains.", List.of(site(3507, 3276, 0)));
		sites.put("kill a nechryael in the slayer tower.", List.of(site(3440, 3566, 2)));
		sites.put("equip an abyssal whip in front of the abyssal demons of the slayer tower.",
			List.of(site(3421, 3537, 2)));
		// Catacombs and the public Smoke Dungeon chamber. The Smoke Dungeon
		// marker is a recorded ID 423 spawn near the well, outside task-only areas.
		// Quest Helper's DesertTreasure opens the well route at the diamond stage (11).
		sites.put("kill a dust devil.", List.of(site(1711, 10011, 0),
			site(3218, 9373, 0, client -> client.getVarpValue(VarPlayerID.DESERTTREASUREMAIN) >= 11)));
		sites.put("kill a lizardman shaman.", List.of(site(1434, 3711, 0)));
		// Main God Wars entrance before choosing a faction. The base graph
		// omits boulder and crack requirements. Troll Stronghold stage 20 is
		// Dad defeated, as recorded by Quest Helper's TrollStronghold guide.
		sites.put("kill a spiritual mage while wearing something from their god.", List.of(site(2916, 3745, 0,
			client -> client.getVarpValue(VarPlayerID.TROLL_QUEST) >= 20
				&& Math.max(client.getBoostedSkillLevel(Skill.AGILITY), client.getBoostedSkillLevel(Skill.STRENGTH)) >= 60)));
		// Shade Catacombs surface entrance, cache objects 4132 and 4133.
		// The dungeon's silver and gold key doors are not in the walking graph.
		sites.put("kill a fiyr shade inside mort'tons shade catacombs.",
			List.of(site(3484, 3320, 0, Quest.SHADES_OF_MORTTON)));
		sites.put("kill a reanimated abyssal demon.", List.of(site(1716, 3883, 0)));
		// Recorded shore spawns (mejrs/data_osrs, NPC 6825).
		sites.put("catch an anglerfish.", List.of(site(1826, 3770, 0), site(1827, 3770, 0), site(1833, 3769, 0)));
		// Feldip and Kebos recorded spawns (NPC 5553), and RuneLite's Tlati area marker.
		sites.put("catch a black warlock.", List.of(site(2551, 2915, 0), site(1260, 3750, 0), site(1282, 3021, 0)));
		sites.put("catch a red chinchompa.", List.of(site(2557, 2936, 0, Quest.EAGLES_PEAK),
			site(1315, 3169, 0, Quest.EAGLES_PEAK), site(2266, 3411, 0, Quest.EAGLES_PEAK, Quest.SONG_OF_THE_ELVES)));
		// Public redwood rope approaches in Woodcutting Guild, cache object 28857.
		sites.put("chop a redwood log.", List.of(site(1566, 3483, 0), site(1566, 3493, 0),
			site(1575, 3483, 0), site(1575, 3493, 0)));
		// Broken lamps vary by player. Route to the city entrance rather than
		// claiming a specific lamp is broken. Light orbs must be made at the bank.
		sites.put("fix a magical lamp in dorgesh-kaan.", List.of(site(2747, 5374, 0, Quest.DEATH_TO_THE_DORGESHUUN),
			site(2722, 5260, 0, Quest.DEATH_TO_THE_DORGESHUUN)));
		sites.put("craft a light orb in the dorgesh-kaan bank.", List.of(site(2701, 5349, 0, Quest.DEATH_TO_THE_DORGESHUUN)));
		sites.put("pickpocket an elf.", List.of(site(2330, 3165, 0,
			client -> Quest.MOURNINGS_END_PART_I.getState(client) != QuestState.NOT_STARTED),
			site(3291, 6061, 0, Quest.SONG_OF_THE_ELVES)));
		SITES = Collections.unmodifiableMap(sites);
	}

	private ChallengeLocations()
	{
	}

	static List<WorldPoint> forChallenge(String challenge, Client client)
	{
		if (challenge == null)
		{
			return Collections.emptyList();
		}
		String key = challenge.trim().toLowerCase(Locale.ROOT);
		List<WorldPoint> available = new ArrayList<>();
		for (Site site : SITES.getOrDefault(key, List.of()))
		{
			if (site.isAvailable(client))
			{
				available.add(site.location);
			}
		}
		return Collections.unmodifiableList(available);
	}

	private static Site site(int x, int y, int plane, Quest... quests)
	{
		return site(x, y, plane, quests.length == 0 ? null
			: client -> Arrays.stream(quests).allMatch(quest -> quest.getState(client) == QuestState.FINISHED));
	}

	private static Site site(int x, int y, int plane, Predicate<Client> access)
	{
		return new Site(new WorldPoint(x, y, plane), access);
	}

	private static final class Site
	{
		private final WorldPoint location;
		private final Predicate<Client> access;

		private Site(WorldPoint location, Predicate<Client> access)
		{
			this.location = location;
			this.access = access;
		}

		private boolean isAvailable(Client client)
		{
			return access == null || client != null && access.test(client);
		}
	}
}
