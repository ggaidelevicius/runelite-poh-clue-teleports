package com.clueteleports;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;

/** Keeps the clue goal available for a bearing after the recommended house teleport. */
final class ArrivalGuidance
{
	private static final int ARRIVAL_WINDOW_TICKS = 8;
	private static final int ARRIVAL_RADIUS = 4;
	private WorldPoint landing;
	private WorldPoint goal;
	private String profile;
	private ClueScroll clue;
	private List<WorldPoint> targets;
	private int armedAt;
	private boolean arrived;
	private volatile WorldPoint visibleGoal;

	void update(int tick, String currentProfile, ClueScroll currentClue, List<WorldPoint> currentTargets,
		boolean ownHouse, TeleportRouting.Plan recommendation, Supplier<WorldPoint> position)
	{
		hide();
		if (ownHouse)
		{
			clear();
			if (recommendation != null && !recommendation.destination.getLocation().equals(recommendation.target))
			{
				landing = recommendation.destination.getLocation();
				goal = recommendation.target;
				profile = currentProfile;
				clue = currentClue;
				targets = currentTargets;
				armedAt = tick;
			}
			return;
		}
		if (goal == null) return;
		if (clue != currentClue || !Objects.equals(profile, currentProfile) || !targets.equals(currentTargets)
			|| !arrived && (tick < armedAt || tick - armedAt > ARRIVAL_WINDOW_TICKS))
		{
			clear();
			return;
		}
		WorldPoint here = position.get();
		if (here == null) return;
		if (!arrived)
		{
			if (here.distanceTo(landing) > ARRIVAL_RADIUS)
			{
				clear();
				return;
			}
			arrived = true;
		}

		if (here.equals(goal)) clear();
		else visibleGoal = goal;
	}

	WorldPoint getTarget() { return visibleGoal; }
	void hide() { visibleGoal = null; }

	void clear()
	{
		hide();
		landing = null;
		goal = null;
		profile = null;
		clue = null;
		targets = null;
		arrived = false;
	}
}
