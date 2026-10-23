package com.clueteleports.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/** Exact dig/coordinate tiles remain exact; known interactions may use a nearby face. */
public final class RouteGoals
{
	private RouteGoals() { }

	static int pack(WorldPoint point) { return point.getPlane() << 28 | point.getX() << 14 | point.getY(); }

	public static List<WorldPoint> qualify(WalkingCollisionMap collision, List<WorldPoint> targets, int interactionRadius)
	{
		Set<WorldPoint> goals = new LinkedHashSet<>();
		for (WorldPoint target : targets)
		{
			if (target == null || target.getPlane() < 0 || target.getPlane() > 3
				|| target.getX() < 0 || target.getX() > 16383 || target.getY() < 0 || target.getY() > 16383) continue;
			boolean walkable = collision.isWalkable(pack(target));
			if (walkable) goals.add(target);
			if (interactionRadius < 1) continue;
			// For a walkable NPC tile, require the real edge to that tile. This
			// prevents ending outside a room through its wall. For a known solid
			// interaction object, inspect its nearest mapped cardinal faces.
			for (int radius = 1; radius <= Math.min(2, interactionRadius); radius++)
			{
				boolean foundFace = false;
				for (int[] direction : new int[][]{{0, 1}, {1, 0}, {0, -1}, {-1, 0}})
				{
					int x = target.getX() + direction[0] * radius;
					int y = target.getY() + direction[1] * radius;
					if (x < 0 || x > 16383 || y < 0 || y > 16383) continue;
					WorldPoint face = new WorldPoint(x, y, target.getPlane());
					if (collision.canInteractFrom(pack(face), pack(target)))
					{
						goals.add(face);
						foundFace = true;
					}
				}
				if (walkable || foundFace) break;
			}
		}
		return Collections.unmodifiableList(new ArrayList<>(goals));
	}
}
