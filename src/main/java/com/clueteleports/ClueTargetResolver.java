package com.clueteleports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.FaloTheBardClue;
import net.runelite.client.plugins.cluescrolls.clues.LocationClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.LocationsClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.NpcClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.ObjectClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.SkillChallengeClue;

/** Reads the destinations already identified by RuneLite's Clue Scroll plugin. */
public final class ClueTargetResolver
{
	// Core keeps Falo's coordinate private despite exposing his clue type publicly.
	// https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/cluescrolls/clues/FaloTheBardClue.java
	private static final WorldPoint FALO = new WorldPoint(2689, 3550, 0);
	// Return-task NPC coordinates match the corresponding talk-to clues in core.
	// https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/cluescrolls/clues/CrypticClue.java
	private static final WorldPoint CHARLIE = new WorldPoint(3209, 3390, 0);
	private static final WorldPoint SHERLOCK = new WorldPoint(2733, 3415, 0);

	public Resolution resolve(ClueScrollPlugin plugin)
	{
		return resolve(plugin == null ? null : plugin.getClue(), plugin);
	}

	public Resolution resolve(ClueScroll clue, ClueScrollPlugin plugin)
	{
		if (clue == null)
		{
			return new Resolution(State.NO_CLUE, Collections.emptyList());
		}

		WorldPoint[] locations;
		boolean alternativeTaskLocations = false;
		if (clue instanceof LocationsClueScroll)
		{
			locations = ((LocationsClueScroll) clue).getLocations(plugin);
		}
		else if (clue instanceof LocationClueScroll)
		{
			locations = ((LocationClueScroll) clue).getLocations(plugin);
		}
		else if (clue instanceof FaloTheBardClue)
		{
			locations = new WorldPoint[]{FALO};
		}
		else if (clue instanceof SkillChallengeClue)
		{
			SkillChallengeClue challenge = (SkillChallengeClue) clue;
			locations = getChallengeLocations(challenge, plugin);
			alternativeTaskLocations = !challenge.isChallengeCompleted() && locations.length > 1;
		}
		else
		{
			return new Resolution(State.NO_LOCATION, Collections.emptyList());
		}

		Set<WorldPoint> uniqueLocations = new LinkedHashSet<>();
		if (locations != null)
		{
			for (WorldPoint location : locations)
			{
				if (location != null)
				{
					uniqueLocations.add(location);
				}
			}
		}

		List<WorldPoint> targets = new ArrayList<>(uniqueLocations);
		State state = targets.isEmpty() ? State.NO_LOCATION
			: targets.size() == 1 ? State.RESOLVED : State.MULTIPLE_LOCATIONS;
		return new Resolution(state, targets, alternativeTaskLocations && targets.size() > 1, interactionRadius(clue, plugin));
	}


	private int interactionRadius(ClueScroll clue, ClueScrollPlugin plugin)
	{
		if (clue instanceof SkillChallengeClue && !((SkillChallengeClue) clue).isChallengeCompleted()) return 2;
		if (clue instanceof FaloTheBardClue) return 1;
		if (clue instanceof NpcClueScroll)
		{
			String[] npcs = ((NpcClueScroll) clue).getNpcs(plugin);
			if (npcs != null && npcs.length > 0) return 1;
		}
		if (clue instanceof ObjectClueScroll)
		{
			int[] objects = ((ObjectClueScroll) clue).getObjectIds();
			if (objects != null && objects.length > 0) return 1;
		}
		return 0;
	}

	private WorldPoint[] getChallengeLocations(SkillChallengeClue clue, ClueScrollPlugin plugin)
	{
		if (clue.isChallengeCompleted())
		{
			for (String npc : clue.getNpcs(plugin))
			{
				if ("Charlie the Tramp".equals(npc))
				{
					return new WorldPoint[]{CHARLIE};
				}
				if ("Sherlock".equals(npc))
				{
					return new WorldPoint[]{SHERLOCK};
				}
			}
		}
		if (!clue.isChallengeCompleted())
		{
			return ChallengeLocations.forChallenge(clue.getChallenge(), plugin == null ? null : plugin.getClient())
				.toArray(new WorldPoint[0]);
		}
		return new WorldPoint[0];
	}

	public enum State
	{
		NO_CLUE,
		NO_LOCATION,
		RESOLVED,
		MULTIPLE_LOCATIONS
	}

	/** A snapshot; a clue's possible locations can change without its identity changing. */
	public static final class Resolution
	{
		private final State state;
		private final List<WorldPoint> locations;
		private final boolean alternativeTaskLocations;
		private final int interactionRadius;

		private Resolution(State state, List<WorldPoint> locations)
		{
			this(state, locations, false, 0);
		}

		private Resolution(State state, List<WorldPoint> locations, boolean alternativeTaskLocations, int interactionRadius)
		{
			this.state = state;
			this.alternativeTaskLocations = alternativeTaskLocations;
			this.interactionRadius = interactionRadius;
			this.locations = Collections.unmodifiableList(new ArrayList<>(locations));
		}

		public int getInteractionRadius() { return interactionRadius; }

		public State getState()
		{
			return state;
		}

		/** All listed sites can complete this task; they are not unresolved clue candidates. */
		public boolean hasAlternativeTaskLocations()
		{
			return alternativeTaskLocations;
		}

		public List<WorldPoint> getLocations()
		{
			return locations;
		}
	}
}
