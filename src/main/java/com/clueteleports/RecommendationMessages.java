package com.clueteleports;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.HotColdClue;
import net.runelite.client.plugins.cluescrolls.clues.MusicClue;
import net.runelite.client.plugins.cluescrolls.clues.SkillChallengeClue;

/** Client-thread announcement state, independent of overlay visibility and route progress. */
final class RecommendationMessages
{
	private String profile;
	private Step lastStep;
	private TeleportSource lastSource;
	private String lastDestination;

	String update(String currentProfile, ClueScroll clue, ClueTargetResolver.Resolution target,
		TeleportDestination destination, boolean enabled)
	{
		if (!Objects.equals(profile, currentProfile))
		{
			reset();
			profile = currentProfile;
		}
		if (!enabled || currentProfile == null || clue == null || target == null
			|| target.getState() == ClueTargetResolver.State.NO_CLUE)
		{
			lastStep = null;
			return null;
		}
		if (destination == null || target.getState() != ClueTargetResolver.State.RESOLVED
			&& !target.hasAlternativeTaskLocations())
		{
			// A search, setup screen, or temporary lack of a target must not
			// reannounce the same result when it becomes available again.
			return null;
		}
		Step step = new Step(clue, target);
		if (step.equals(lastStep) && destination.getSource() == lastSource
			&& destination.getId().equals(lastDestination))
		{
			return null;
		}
		lastStep = step;
		lastSource = destination.getSource();
		lastDestination = destination.getId();
		return "[POH Clue Teleports] " + lastSource.getDisplayName() + ": " + destination.getName();
	}

	void reset()
	{
		profile = null;
		lastStep = null;
		lastSource = null;
		lastDestination = null;
	}

	private static final class Step
	{
		private final Class<?> clueType;
		private final int[] keys;
		private final String unkeyedText;
		private final boolean beginnerHotCold;
		private final List<WorldPoint> locations;
		private final ClueTargetResolver.State state;
		private final int interactionRadius;
		private final boolean alternatives;
		private final boolean challengeCompleted;

		private Step(ClueScroll clue, ClueTargetResolver.Resolution target)
		{
			clueType = clue.getClass();
			int[] configKeys = clue.getConfigKeys();
			keys = configKeys == null ? null : configKeys.clone();
			// Music and hot/cold clues expose no note keys. Their requests
			// can share a target, so location alone does not identify a step.
			unkeyedText = clue instanceof MusicClue ? ((MusicClue) clue).getSong()
				: clue instanceof HotColdClue ? ((HotColdClue) clue).getText() : null;
			beginnerHotCold = clue instanceof HotColdClue && ((HotColdClue) clue).isBeginner();
			locations = new ArrayList<>(target.getLocations());
			state = target.getState();
			interactionRadius = target.getInteractionRadius();
			alternatives = target.hasAlternativeTaskLocations();
			challengeCompleted = clue instanceof SkillChallengeClue && ((SkillChallengeClue) clue).isChallengeCompleted();
		}

		@Override
		public boolean equals(Object other)
		{
			if (!(other instanceof Step)) return false;
			Step step = (Step) other;
			return clueType == step.clueType && Arrays.equals(keys, step.keys)
				&& Objects.equals(unkeyedText, step.unkeyedText) && beginnerHotCold == step.beginnerHotCold
				&& locations.equals(step.locations)
				&& state == step.state && interactionRadius == step.interactionRadius
				&& alternatives == step.alternatives && challengeCompleted == step.challengeCompleted;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(clueType, Arrays.hashCode(keys), unkeyedText, beginnerHotCold, locations, state,
				interactionRadius, alternatives, challengeCompleted);
		}
	}
}
