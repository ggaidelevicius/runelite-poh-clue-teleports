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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;

/**
 * Verified task sites for location-specific Sherlock challenges.
 *
 * Task labels match RuneLite SkillChallengeClue in runelite-parent-1.13.3.
 * Altar, agility, and hunter coordinates follow that version's worldmap
 * RunecraftingAltarLocation, AgilityCourseLocation, and HunterAreaLocation.
 * The Slayer Tower approach is core EmoteClue's top-floor entrance marker.
 * Chest, gem stall, lectern, Nechryael, and pyre positions are adapted from
 * emerald000/clue-helper at b1a5e772d45373bac133bfa28a1ded6a38b910b3
 * (cluesteps/EliteClues.java and MasterClues.java).
 *
 * These are task sites or their normal access points, rather than every
 * possible route to them. Skullball uses the surface werewolf-course entrance.
 * Tasks possible at arbitrary banks, fires, furnaces, anvils, or resource
 * locations intentionally have no chosen point.
 */
final class ChallengeLocations
{
	private static final String BLOOD = "bind a blood rune at the blood altar.";
	private static final WorldPoint KOUREND_BLOOD_ALTAR = new WorldPoint(1716, 3827, 0);
	private static final WorldPoint TRUE_BLOOD_ALTAR = new WorldPoint(3625, 9717, 0);
	private static final Map<String, WorldPoint> FIXED;

	static
	{
		Map<String, WorldPoint> fixed = new LinkedHashMap<>();
		fixed.put("craft a nature rune.", new WorldPoint(2869, 3019, 0));
		fixed.put("craft multiple cosmic runes from a single essence.", new WorldPoint(2408, 4377, 0));
		fixed.put("score a goal in skullball.", new WorldPoint(3542, 3463, 0));
		fixed.put("complete a lap of ape atoll agility course.", new WorldPoint(2752, 2742, 0));
		fixed.put("complete a lap of rellekka's rooftop agility course", new WorldPoint(2624, 3677, 0));
		fixed.put("catch a mottled eel with aerial fishing in lake molch.", new WorldPoint(1363, 3632, 0));
		fixed.put("catch a tecu salamander.", new WorldPoint(1475, 3096, 0));
		fixed.put("create a barrows teleport tablet.", new WorldPoint(1679, 3765, 0));
		fixed.put("steal from a chest in ardougne castle.", new WorldPoint(2588, 3291, 1));
		fixed.put("steal a gem from the ardougne market.", new WorldPoint(2667, 3303, 0));
		fixed.put("cremate a set of fiyr remains.", new WorldPoint(3507, 3276, 0));
		fixed.put("kill a nechryael in the slayer tower.", new WorldPoint(3440, 3566, 2));
		fixed.put("equip an abyssal whip in front of the abyssal demons of the slayer tower.",
			new WorldPoint(3421, 3537, 2));
		FIXED = Collections.unmodifiableMap(fixed);
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
		if (BLOOD.equals(key))
		{
			List<WorldPoint> altars = new ArrayList<>();
			altars.add(KOUREND_BLOOD_ALTAR);
			// Both altars complete this task. The true altar requires Sins of
			// the Father; Kourend's altar has no equivalent quest gate.
			// https://oldschool.runescape.wiki/w/Sherlock#Master_skill_challenges
			if (client != null && Quest.SINS_OF_THE_FATHER.getState(client) == QuestState.FINISHED)
			{
				altars.add(TRUE_BLOOD_ALTAR);
			}
			return Collections.unmodifiableList(altars);
		}
		WorldPoint point = FIXED.get(key);
		return point == null ? Collections.emptyList() : Collections.singletonList(point);
	}
}
