package com.clueteleports;

import com.clueteleports.routing.WalkingRouteResult;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;

/** Follows a planned walking route after a matching departure from the player's house. */
final class ArrivalGuidance
{
	private static final int ARRIVAL_WINDOW_TICKS = 8;
	private static final int ARRIVAL_RADIUS = 4;
	private static final int WALK_AHEAD = 8;
	private TeleportRouting.Plan planned;
	private String profile;
	private ClueScroll clue;
	private List<WorldPoint> targets;
	private int armedAt;
	private int progress;
	private boolean arrived;
	private volatile WorldPoint waypoint;

	void update(int tick, String currentProfile, ClueScroll currentClue, List<WorldPoint> currentTargets,
		boolean ownHouse, TeleportRouting.Plan recommendation, Supplier<WorldPoint> position, TeleportRouting routing)
	{
		hide();
		if (ownHouse)
		{
			clear();
			if (recommendation != null && recommendation.steps.size() > 1)
			{
				planned = recommendation;
				profile = currentProfile;
				clue = currentClue;
				targets = currentTargets;
				armedAt = tick;
			}
			return;
		}
		if (planned == null) return;
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
			if (here.distanceTo(planned.destination.getLocation()) > ARRIVAL_RADIUS)
			{
				clear();
				return;
			}
			arrived = true;
		}

		List<WalkingRouteResult.Step> steps = planned.steps;
		for (int i = 0; i < steps.size(); i++)
		{
			if (!here.equals(steps.get(i).getLocation())) continue;
			progress = i;
			if (i == steps.size() - 1) clear();
			else if (routing.canFollow(steps, i)) waypoint = walkingWaypoint(steps, i);
			return;
		}
		if (!routing.canFollow(steps, progress)) return;
		// Catalog landings can be a few tiles off. Join a nearby walking leg
		// only if the collision map proves each step to the join is walkable.
		int closest = ARRIVAL_RADIUS;
		for (int i = progress; i < steps.size() && i <= progress + WALK_AHEAD; i++)
		{
			WalkingRouteResult.Step step = steps.get(i);
			WorldPoint join = step.getLocation();
			int distance = here.distanceTo(join);
			if (distance <= closest && canJoin(here, join, routing))
			{
				closest = distance;
				waypoint = join;
			}
			if (step.isPassageToNext()) break;
		}
	}

	private static WorldPoint walkingWaypoint(List<WalkingRouteResult.Step> steps, int start)
	{
		if (steps.get(start).isPassageToNext()) return null;
		WorldPoint origin = steps.get(start).getLocation();
		WorldPoint first = steps.get(start + 1).getLocation();
		int dx = first.getX() - origin.getX();
		int dy = first.getY() - origin.getY();
		int end = start + 1;
		while (end < start + WALK_AHEAD && end + 1 < steps.size() && !steps.get(end).isPassageToNext())
		{
			WorldPoint next = steps.get(end + 1).getLocation();
			int length = end + 1 - start;
			if (next.getX() != origin.getX() + length * dx || next.getY() != origin.getY() + length * dy) break;
			end++;
		}
		return steps.get(end).getLocation();
	}

	private static boolean canJoin(WorldPoint from, WorldPoint to, TeleportRouting routing)
	{
		// Each move closes the remaining gap by one tile on either axis.
		WorldPoint tile = from;
		while (!tile.equals(to))
		{
			WorldPoint next = new WorldPoint(tile.getX() + Integer.compare(to.getX(), tile.getX()),
				tile.getY() + Integer.compare(to.getY(), tile.getY()), tile.getPlane());
			if (!routing.canWalk(tile, next)) return false;
			tile = next;
		}
		return true;
	}

	WorldPoint getWaypoint() { return waypoint; }
	boolean hasRoute() { return planned != null; }
	void hide() { waypoint = null; }

	void clear()
	{
		hide();
		planned = null;
		profile = null;
		clue = null;
		targets = null;
		arrived = false;
		progress = 0;
	}
}
